package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilityConfig;
import com.lostglade.item.ModItems;
import com.mojang.brigadier.context.CommandContext;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AncientUkrCreditSystem {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final String FILE_NAME = "lg2-ancient-ukr-credits.json";
    private static final String RACE_ID = "ancient_ukr";
    private static final int DEFAULT_MAX_ACTIVE = 3;
    private static final int DEFAULT_MAX_PRINCIPAL = 1000;
    private static final double DEFAULT_MIN_RATE = 5.0D;
    private static final double DEFAULT_MAX_RATE = 10.0D;
    private static final double DEFAULT_MAX_DEBT_MULTIPLIER = 3.0D;
    private static final long LOAN_PAYOUT_STACK_INTERVAL_TICKS = 4L;
    private static final String COIN_GLYPH = "\ue981";
    private static final String FALLBACK_COIN = "\u20bf";
    private static final String LOAN_PAYOUT_ITEM_TAG = "lg2_ancient_ukr_loan_payout";
    private static final FontDescription COIN_FONT = new FontDescription.Resource(
            Objects.requireNonNull(Identifier.tryParse("lg2:upgrade_tooltip")));

    private static CreditStore store = new CreditStore();
    private static final Map<UUID, Objective> CLIENT_OBJECTIVES = new HashMap<>();
    private static final Map<UUID, PendingOffer> PENDING_OFFERS = new HashMap<>();
    private static final Map<UUID, RepaymentSession> REPAYMENTS = new HashMap<>();
    private static final Map<UUID, Deque<LoanPayout>> LOAN_PAYOUTS = new HashMap<>();
    private static long lastPeriodicTick = Long.MIN_VALUE;
    private static boolean loaded;

    private AncientUkrCreditSystem() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ServerLifecycleEvents.SERVER_STARTED.register(AncientUkrCreditSystem::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            save(server);
            CLIENT_OBJECTIVES.clear();
            PENDING_OFFERS.clear();
            REPAYMENTS.clear();
            LOAN_PAYOUTS.clear();
            loaded = false;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> onPlayerJoined(server, handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CLIENT_OBJECTIVES.remove(handler.player.getUUID());
            PENDING_OFFERS.remove(handler.player.getUUID());
            REPAYMENTS.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(AncientUkrCreditSystem::tick);
    }

    public static int forceInterestAccrual(CommandContext<CommandSourceStack> context) {
        BigDecimal total = accrueFixedInterest(context.getSource().getServer(), true);
        context.getSource().sendSuccess(() -> Component.literal(
                "\u0414\u043e\u0441\u0440\u043e\u0447\u043d\u043e \u043d\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u043e " + formatAmount(total) + " " + FALLBACK_COIN), true);
        return 1;
    }

    static void onCreditorSpawned(MinecraftServer server, UUID ownerId) {
        PENDING_OFFERS.remove(ownerId);
        REPAYMENTS.remove(ownerId);
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            borrower(ownerId, owner.getGameProfile().name());
            save(server);
            syncScoreboard(owner);
        }
        AncientUkrCreditorChatSystem.beginConversation(server, ownerId);
    }

    static void onCreditorRemoved(UUID ownerId) {
        PENDING_OFFERS.remove(ownerId);
        REPAYMENTS.remove(ownerId);
    }
    static String buildAiContext(MinecraftServer server, UUID ownerId) {
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(ownerId);
        BorrowerState borrower = store.borrowers.get(ownerId.toString());
        deduplicateCredits(borrower);
        CreditTerms terms = terms();
        StringBuilder out = new StringBuilder("SERVER FACTS (authoritative):\n");
        out.append("Client nickname: ").append(owner == null ? storedNickname(borrower, ownerId) : owner.getGameProfile().name()).append('\n');
        out.append("Active credits: ").append(borrower == null ? 0 : borrower.credits.size()).append('/').append(terms.maxActive()).append('\n');
        out.append("Current hourly rate for new credits: ").append(formatPercent(currentRatePercent())).append("%. It changes every real hour.\n");
        out.append("Maximum principal per credit: ").append(terms.maxPrincipal()).append(" bitcoins.\n");
        out.append("Each credit permanently keeps the hourly rate agreed when it was opened. Interest is simple, charged from principal each hour, including while offline.\n");
        out.append("Interest pauses at or above ").append(formatAmount(BigDecimal.valueOf(terms.maxDebtMultiplier())))
                .append("x principal; one full charge may cross the threshold.\n");
        if (borrower == null || borrower.credits.isEmpty()) {
            out.append("Credits: none.\n");
        } else {
            out.append("Credits:\n");
            borrower.credits.stream().sorted(Comparator.comparingInt(credit -> credit.number)).forEach(credit ->
                    out.append("- #").append(credit.number).append(": principal ").append(credit.principal)
                            .append(", debt ").append(formatAmount(credit.debt)).append(" bitcoins, fixed hourly rate ")
                            .append(formatPercent(credit.interestRatePercent)).append("%\n"));
        }
        PendingOffer offer = PENDING_OFFERS.get(ownerId);
        out.append("Pending final-confirmation offer: ").append(offer == null ? "none" : offer.amount() + " bitcoins at a fixed " + formatPercent(offer.interestRatePercent()) + "% hourly rate").append(".\n");
        RepaymentSession repayment = REPAYMENTS.get(ownerId);
        out.append("Active repayment: ").append(repayment == null ? "none" :
                "credits " + repayment.creditNumbers + ", paid " + repayment.totalPaid + " bitcoins").append(".\n");
        return out.toString();
    }

    static boolean hasPendingOffer(UUID ownerId) {
        return ownerId != null && PENDING_OFFERS.containsKey(ownerId);
    }

    static List<Integer> activeCreditNumbers(UUID ownerId) {
        if (ownerId == null) return List.of();
        BorrowerState borrower = store.borrowers.get(ownerId.toString());
        if (borrower == null) return List.of();
        return borrower.credits.stream()
                .filter(credit -> credit.debt.signum() > 0)
                .map(credit -> credit.number)
                .distinct()
                .sorted()
                .toList();
    }

    static boolean hasActiveRepayment(UUID ownerId) {
        return ownerId != null && REPAYMENTS.containsKey(ownerId);
    }

    public static boolean handleInventoryBitcoinThrow(ServerPlayer player, AbstractContainerMenu menu,
                                                       int slotIndex, ClickType clickType, int button) {
        if (player == null || menu == null || clickType != ClickType.THROW
                || ServerRaceSystem.getActiveAncientUkrCreditorId(player.getUUID()) == null
                || (button != 0 && button != 1) || !menu.isValidSlotIndex(slotIndex)) {
            return false;
        }
        Slot slot = menu.getSlot(slotIndex);
        ItemStack source = slot.getItem();
        if (source.isEmpty() || !source.is(ModItems.BITCOIN) || !slot.mayPickup(player)) return false;

        int count = button == 0 ? 1 : source.getCount();
        ItemStack dropped = source.split(Math.min(count, source.getCount()));
        if (dropped.isEmpty()) return false;
        ItemEntity item = player.drop(dropped, true);
        if (item == null) source.grow(dropped.getCount());
        slot.setChanged();
        menu.sendAllDataToRemote();
        return true;
    }
    static ActionResolution applyAiAction(MinecraftServer server, UUID ownerId, String actionType,
                                          Integer creditNumber, List<Integer> creditNumbers,
                                          Integer amount, String modelReply) {
        String type = actionType == null ? "none" : actionType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "", "none" -> new ActionResolution(modelReply, false);
            case "offer_credit" -> offerCredit(server, ownerId, amount);
            case "open_credit" -> openCredit(server, ownerId, amount);
            case "start_repayment" -> startRepayment(server, ownerId, creditNumber, creditNumbers);
            case "continue_repayment" -> continueRepayment(server, ownerId);
            case "stop_repayment" -> stopRepayment(ownerId);
            case "finish" -> new ActionResolution(
                    "\u0411\u043b\u0430\u0433\u043e\u0434\u0430\u0440\u044e \u0437\u0430 \u043e\u0431\u0440\u0430\u0449\u0435\u043d\u0438\u0435. \u0412\u0441\u0435\u0433\u043e \u0434\u043e\u0431\u0440\u043e\u0433\u043e.", true);
            default -> result("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c \u043e\u043f\u0435\u0440\u0430\u0446\u0438\u044e. \u0423\u0442\u043e\u0447\u043d\u0438\u0442\u0435 \u0437\u0430\u043f\u0440\u043e\u0441.");
        };
    }

    static void syncScoreboard(ServerPlayer player) {
        if (player == null || player.connection == null) return;
        UUID playerId = player.getUUID();
        Objective previous = CLIENT_OBJECTIVES.remove(playerId);
        if (previous != null) {
            player.connection.send(new ClientboundSetObjectivePacket(previous, ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
        BorrowerState borrower = store.borrowers.get(playerId.toString());
        deduplicateCredits(borrower);
        if (borrower == null || borrower.credits.isEmpty()) {
            restoreServerSidebar(player);
            return;
        }
        Objective objective = new Objective(new Scoreboard(), objectiveName(playerId), ObjectiveCriteria.DUMMY,
                Component.literal("\u041a\u0440\u0435\u0434\u0438\u0442\u044b").withStyle(ChatFormatting.GOLD),
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        CLIENT_OBJECTIVES.put(playerId, objective);
        player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        List<Credit> credits = borrower.credits.stream().sorted(Comparator.comparingInt(credit -> credit.number)).toList();
        for (int index = 0; index < credits.size(); index++) {
            Credit credit = credits.get(index);
            player.connection.send(new ClientboundSetScorePacket("lg2_credit_" + credit.number, objective.getName(),
                    credits.size() - index, Optional.of(creditLine(player, credit)), Optional.of(BlankFormat.INSTANCE)));
        }
    }
    private static void load(MinecraftServer server) {
        lastPeriodicTick = Long.MIN_VALUE;
        CLIENT_OBJECTIVES.clear();
        PENDING_OFFERS.clear();
        REPAYMENTS.clear();
        LOAN_PAYOUTS.clear();
        store = new CreditStore();
        Path path = statePath(server);
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                CreditStore loadedStore = GSON.fromJson(reader, CreditStore.class);
                if (loadedStore != null) store = loadedStore;
            } catch (IOException | RuntimeException exception) {
                Lg2.LOGGER.warn("Failed to load Ancient Ukr credits", exception);
            }
        }
        long currentHour = currentEpochHour();
        if (store.rateSeed == 0L) store.rateSeed = ThreadLocalRandom.current().nextLong();
        sanitizeStore(rateForHour(currentHour));
        if (store.lastAccruedEpochHour <= 0L || store.lastAccruedEpochHour > currentHour) {
            store.lastAccruedEpochHour = currentHour;
        }
        loaded = true;
        accrueMissedHours(server, currentHour);
        save(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) onPlayerJoined(server, player);
        Lg2.LOGGER.info("Loaded Ancient Ukr credit state for {} borrowers", store.borrowers.size());
    }

    private static void tick(MinecraftServer server) {
        if (!loaded || server == null) return;
        tickLoanPayouts(server);
        tickRepayments(server);
        long tick = server.overworld().getGameTime();
        if (lastPeriodicTick != Long.MIN_VALUE && tick - lastPeriodicTick < 20L) return;
        lastPeriodicTick = tick;
        accrueMissedHours(server, currentEpochHour());
    }

    private static void accrueMissedHours(MinecraftServer server, long currentHour) {
        if (store.lastAccruedEpochHour >= currentHour) return;
        Map<UUID, BigDecimal> notifications = new HashMap<>();
        while (store.lastAccruedEpochHour < currentHour) {
            accrueFixedInterest(notifications);
            store.lastAccruedEpochHour++;
        }
        save(server);
        notifyAccruedInterest(server, notifications);
        syncAllOnlineBorrowers(server);
    }

    private static BigDecimal accrueFixedInterest(MinecraftServer server, boolean notify) {
        Map<UUID, BigDecimal> notifications = new HashMap<>();
        BigDecimal total = accrueFixedInterest(notifications);
        save(server);
        if (notify) notifyAccruedInterest(server, notifications);
        syncAllOnlineBorrowers(server);
        return total;
    }

    private static BigDecimal accrueFixedInterest(Map<UUID, BigDecimal> notifications) {
        BigDecimal multiplier = BigDecimal.valueOf(terms().maxDebtMultiplier());
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BorrowerState> entry : store.borrowers.entrySet()) {
            BigDecimal borrowerTotal = BigDecimal.ZERO;
            for (Credit credit : entry.getValue().credits) {
                BigDecimal cap = BigDecimal.valueOf(credit.principal).multiply(multiplier);
                if (credit.debt.compareTo(cap) >= 0) continue;
                BigDecimal rate = BigDecimal.valueOf(credit.interestRatePercent)
                        .divide(BigDecimal.valueOf(100L), 8, RoundingMode.HALF_UP);
                BigDecimal interest = normalizeMoney(BigDecimal.valueOf(credit.principal).multiply(rate));
                if (interest.signum() <= 0) continue;
                credit.debt = normalizeMoney(credit.debt.add(interest));
                borrowerTotal = borrowerTotal.add(interest);
            }
            if (borrowerTotal.signum() > 0) {
                entry.getValue().pendingInterestNotification = normalizeMoney(
                        entry.getValue().pendingInterestNotification.add(borrowerTotal));
                UUID ownerId = parseUuid(entry.getKey());
                if (ownerId != null) notifications.merge(ownerId, borrowerTotal, BigDecimal::add);
                total = total.add(borrowerTotal);
            }
        }
        return normalizeMoney(total);
    }

    private static void notifyAccruedInterest(MinecraftServer server, Map<UUID, BigDecimal> notifications) {
        boolean clearedPending = false;
        for (UUID ownerId : notifications.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
            BorrowerState borrower = store.borrowers.get(ownerId.toString());
            if (player == null || borrower == null || borrower.pendingInterestNotification.signum() <= 0) continue;
            showInterestNotification(player, borrower.pendingInterestNotification);
            borrower.pendingInterestNotification = BigDecimal.ZERO;
            clearedPending = true;
        }
        if (clearedPending) save(server);
    }

    private static void showPendingInterestNotification(MinecraftServer server, ServerPlayer player) {
        BorrowerState borrower = store.borrowers.get(player.getUUID().toString());
        if (borrower == null || borrower.pendingInterestNotification.signum() <= 0) return;
        showInterestNotification(player, borrower.pendingInterestNotification);
        borrower.pendingInterestNotification = BigDecimal.ZERO;
        save(server);
    }

    private static void showInterestNotification(ServerPlayer player, BigDecimal amount) {
        MutableComponent message = Component.literal("+" + formatAmount(amount)).withStyle(ChatFormatting.RED);
        message.append(coinComponent(player));
        player.displayClientMessage(message, true);
        Holder<SoundEvent> sound = Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP);
        Vec3 position = player.position();
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS,
                position.x, position.y, position.z, 0.8F, 1.0F, player.getRandom().nextLong()));
    }
    private static ActionResolution offerCredit(MinecraftServer server, UUID ownerId, Integer amount) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        CreditTerms terms = terms();
        BorrowerState borrower = borrower(ownerId, owner == null ? ownerId.toString() : owner.getGameProfile().name());
        if (borrower.credits.size() >= terms.maxActive()) {
            return result("\u0414\u043e\u0441\u0442\u0438\u0433\u043d\u0443\u0442 \u043b\u0438\u043c\u0438\u0442 \u0432 " + terms.maxActive() + " \u043e\u0434\u043d\u043e\u0432\u0440\u0435\u043c\u0435\u043d\u043d\u044b\u0445 \u043a\u0440\u0435\u0434\u0438\u0442\u0430.");
        }
        if (amount == null || amount <= 0 || amount > terms.maxPrincipal()) {
            return result("\u0414\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u0430\u044f \u0441\u0443\u043c\u043c\u0430: \u043e\u0442 1 \u0434\u043e " + terms.maxPrincipal() + " " + FALLBACK_COIN + ".");
        }
        PendingOffer existing = PENDING_OFFERS.get(ownerId);
        if (existing != null && existing.amount() == amount) {
            return result("The pending offer is unchanged. Reply briefly to the client's latest message without repeating the confirmation question.");
        }
        double fixedRatePercent = currentRatePercent();
        PENDING_OFFERS.put(ownerId, new PendingOffer(amount, fixedRatePercent));
        return result("Final confirmation for a credit of " + amount + " bitcoins at a fixed "
                + formatPercent(fixedRatePercent) + "% hourly rate. State only the amount and rate, then ask once for confirmation.");
    }
    private static ActionResolution openCredit(MinecraftServer server, UUID ownerId, Integer amount) {
        PendingOffer pending = PENDING_OFFERS.get(ownerId);
        if (pending == null) {
            return result("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043d\u0443\u0436\u043d\u043e \u0441\u043e\u0433\u043b\u0430\u0441\u043e\u0432\u0430\u0442\u044c \u0441\u0443\u043c\u043c\u0443 \u0438 \u043f\u043e\u043b\u0443\u0447\u0438\u0442\u044c \u0432\u0430\u0448\u0435 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u0435.");
        }
        int confirmedAmount = amount == null ? pending.amount() : amount;
        if (pending.amount() != confirmedAmount) {
            return result("\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u0435 \u043d\u0435 \u0441\u043e\u0432\u043f\u0430\u0434\u0430\u0435\u0442 \u0441 \u0441\u043e\u0433\u043b\u0430\u0441\u043e\u0432\u0430\u043d\u043d\u043e\u0439 \u0441\u0443\u043c\u043c\u043e\u0439.");
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null || ServerRaceSystem.getActiveAncientUkrCreditorId(ownerId) == null) {
            return result("\u041e\u0444\u043e\u0440\u043c\u043b\u0435\u043d\u0438\u0435 \u0441\u0435\u0439\u0447\u0430\u0441 \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u043d\u043e.");
        }
        CreditTerms terms = terms();
        BorrowerState borrower = borrower(ownerId, owner.getGameProfile().name());
        if (borrower.credits.size() >= terms.maxActive() || confirmedAmount <= 0 || confirmedAmount > terms.maxPrincipal()) {
            PENDING_OFFERS.remove(ownerId);
            return result("\u0423\u0441\u043b\u043e\u0432\u0438\u044f \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u0441\u043e\u043e\u0442\u0432\u0435\u0442\u0441\u0442\u0432\u0443\u044e\u0442 \u043b\u0438\u043c\u0438\u0442\u0430\u043c. \u0421\u043e\u0433\u043b\u0430\u0441\u0443\u0435\u043c \u0438\u0445 \u0437\u0430\u043d\u043e\u0432\u043e.");
        }
        int number = nextCreditNumber(borrower, terms.maxActive());
        if (number <= 0 || !queueLoanBitcoinPayout(owner, confirmedAmount)) {
            return result("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0432\u044b\u0434\u0430\u0442\u044c \u0431\u0438\u0442\u043a\u043e\u0438\u043d\u044b. \u041a\u0440\u0435\u0434\u0438\u0442 \u043d\u0435 \u043e\u0444\u043e\u0440\u043c\u043b\u0435\u043d.");
        }
        borrower.credits.add(new Credit(number, confirmedAmount, BigDecimal.valueOf(confirmedAmount), pending.interestRatePercent()));
        PENDING_OFFERS.remove(ownerId);
        save(server);
        syncScoreboard(owner);
        return result("\u041a\u0440\u0435\u0434\u0438\u0442 \u2116" + number + " \u043e\u0444\u043e\u0440\u043c\u043b\u0435\u043d \u043d\u0430 " + confirmedAmount + " " + FALLBACK_COIN
                + ". \u0411\u0438\u0442\u043a\u043e\u0438\u043d\u044b \u0432\u044b\u0434\u0430\u043d\u044b. \u0416\u0435\u043b\u0430\u0435\u0442\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c \u0435\u0449\u0451 \u043e\u0434\u043d\u0443 \u043e\u043f\u0435\u0440\u0430\u0446\u0438\u044e?");
    }

    private static ActionResolution startRepayment(MinecraftServer server, UUID ownerId,
                                                    Integer singleNumber, List<Integer> requestedNumbers) {
        List<Integer> numbers = new ArrayList<>();
        if (requestedNumbers != null) {
            for (Integer number : requestedNumbers) {
                if (number != null && number > 0 && !numbers.contains(number)) numbers.add(number);
            }
        }
        if (numbers.isEmpty() && singleNumber != null && singleNumber > 0) numbers.add(singleNumber);
        numbers.sort(Integer::compareTo);
        if (numbers.isEmpty()) {
            return result("No credit numbers were selected. Ask the client which credit or credits they want to repay.");
        }
        List<Credit> credits = new ArrayList<>();
        for (Integer number : numbers) {
            Credit credit = findCredit(ownerId, number);
            if (credit == null) {
                return result("At least one selected credit does not exist. Ask the client to choose only existing credit numbers.");
            }
            credits.add(credit);
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null || activeCreditorEntity(owner, ownerId) == null) {
            return result("Repayment cannot be started right now.");
        }
        REPAYMENTS.put(ownerId, new RepaymentSession(List.copyOf(numbers)));
        return result("Repayment has started for credits " + numbers
                + ". Briefly ask the client to give you bitcoins. Do not explain distribution unless asked.");
    }

    private static ActionResolution continueRepayment(MinecraftServer server, UUID ownerId) {
        RepaymentSession session = REPAYMENTS.get(ownerId);
        if (session == null) return result("\u0410\u043a\u0442\u0438\u0432\u043d\u043e\u0433\u043e \u043f\u043e\u0433\u0430\u0448\u0435\u043d\u0438\u044f \u043d\u0435\u0442. \u0423\u043a\u0430\u0436\u0438\u0442\u0435 \u043d\u043e\u043c\u0435\u0440 \u043a\u0440\u0435\u0434\u0438\u0442\u0430.");

        return result("\u0425\u043e\u0440\u043e\u0448\u043e, \u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0430\u044e \u043f\u0440\u0438\u043d\u0438\u043c\u0430\u0442\u044c \u0431\u0438\u0442\u043a\u043e\u0438\u043d\u044b.");
    }

    private static ActionResolution stopRepayment(UUID ownerId) {
        REPAYMENTS.remove(ownerId);
        return result("\u041f\u0440\u0438\u0451\u043c \u043f\u043b\u0430\u0442\u0435\u0436\u0435\u0439 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d. \u0416\u0435\u043b\u0430\u0435\u0442\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c \u0434\u0440\u0443\u0433\u0443\u044e \u043e\u043f\u0435\u0440\u0430\u0446\u0438\u044e?");
    }

    private static void tickRepayments(MinecraftServer server) {
        if (REPAYMENTS.isEmpty()) return;
        Iterator<Map.Entry<UUID, RepaymentSession>> iterator = REPAYMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RepaymentSession> entry = iterator.next();
            UUID ownerId = entry.getKey();
            RepaymentSession session = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            Entity creditor = activeCreditorEntity(owner, ownerId);
            List<Credit> credits = repaymentCredits(ownerId, session.creditNumbers);
            if (owner == null || creditor == null || credits.isEmpty()) {
                iterator.remove();
                continue;
            }
            int accepted = collectNearbyBitcoins(creditor, owner, credits);
            if (accepted > 0) {
                session.totalPaid += accepted;
                BorrowerState borrower = store.borrowers.get(ownerId.toString());
                if (borrower != null) {
                    borrower.credits.removeIf(credit -> credit.debt.signum() <= 0);
                    removeEmptyBorrower(ownerId, borrower);
                }
                save(server);
                syncScoreboard(owner);
            }
            List<Credit> remainingCredits = repaymentCredits(ownerId, session.creditNumbers);
            if (remainingCredits.isEmpty()) {
                iterator.remove();
                AncientUkrCreditorChatSystem.narrateEvent(server, ownerId,
                        "All selected credits " + session.creditNumbers
                                + " have been fully repaid. Ask whether the client needs another available service.");
                continue;
            }

        }
    }

    private static List<Credit> repaymentCredits(UUID ownerId, List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) return List.of();
        List<Credit> credits = new ArrayList<>();
        for (Integer number : numbers) {
            Credit credit = findCredit(ownerId, number);
            if (credit != null && credit.debt.signum() > 0) credits.add(credit);
        }
        return credits;
    }

    private static int collectNearbyBitcoins(Entity creditor, ServerPlayer owner, List<Credit> credits) {
        if (!(creditor.level() instanceof ServerLevel level) || credits.isEmpty()) return 0;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                creditor.getBoundingBox().inflate(1.0D, 0.5D, 1.0D),
                item -> item.isAlive() && !item.hasPickUpDelay()
                        && !item.getTags().contains(LOAN_PAYOUT_ITEM_TAG)
                        && item.getItem().is(ModItems.BITCOIN));
        items.sort(Comparator.comparingDouble(item -> item.distanceToSqr(creditor)));
        int accepted = 0;
        for (ItemEntity item : items) {
            Entity itemOwner = item.getOwner();
            if (itemOwner != null && itemOwner != owner) continue;
            BigDecimal totalDebt = credits.stream()
                    .map(credit -> credit.debt.max(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int needed = totalDebt.setScale(0, RoundingMode.CEILING).intValue();
            if (needed <= 0) break;
            ItemStack stack = item.getItem();
            int take = Math.min(stack.getCount(), needed);
            if (take <= 0) continue;
            ItemStack remainder = stack.copy();
            remainder.shrink(take);
            if (remainder.isEmpty()) item.discard(); else item.setItem(remainder);
            playSafeCreditorPickupFeedback(level, creditor);
            distributePaymentEvenly(credits, BigDecimal.valueOf(take));
            accepted += take;
        }
        return accepted;
    }

    private static void playSafeCreditorPickupFeedback(ServerLevel level, Entity creditor) {
        level.playSound(null, creditor.getX(), creditor.getY(), creditor.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                1.6F + level.getRandom().nextFloat() * 0.4F);
    }

    private static void distributePaymentEvenly(List<Credit> credits, BigDecimal payment) {
        int remaining = normalizeMoney(payment).intValueExact();
        List<Credit> unpaid = new ArrayList<>();
        for (Credit credit : credits) {
            if (credit != null && credit.debt.signum() > 0) {
                credit.debt = normalizeMoney(credit.debt);
                unpaid.add(credit);
            }
        }
        int index = 0;
        while (remaining > 0 && !unpaid.isEmpty()) {
            if (index >= unpaid.size()) index = 0;
            Credit credit = unpaid.get(index);
            credit.debt = credit.debt.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
            remaining--;
            if (credit.debt.signum() <= 0) {
                unpaid.remove(index);
            } else {
                index++;
            }
        }
    }
    private static boolean queueLoanBitcoinPayout(ServerPlayer owner, int amount) {
        if (owner == null || amount <= 0 || !(owner.level() instanceof ServerLevel)) return false;
        Entity creditor = activeCreditorEntity(owner, owner.getUUID());
        if (creditor == null || !creditor.isAlive()) return false;
        MinecraftServer server = owner.level().getServer();
        if (server == null) return false;
        long tick = server.overworld().getGameTime();
        LOAN_PAYOUTS.computeIfAbsent(owner.getUUID(), ignored -> new ArrayDeque<>())
                .addLast(new LoanPayout(amount, tick));
        return true;
    }

    private static void tickLoanPayouts(MinecraftServer server) {
        if (LOAN_PAYOUTS.isEmpty()) return;
        long tick = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Deque<LoanPayout>>> iterator = LOAN_PAYOUTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Deque<LoanPayout>> entry = iterator.next();
            Deque<LoanPayout> payouts = entry.getValue();
            LoanPayout payout = payouts.peekFirst();
            if (payout == null) {
                iterator.remove();
                continue;
            }
            if (tick < payout.nextStackTick) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            Entity creditor = activeCreditorEntity(owner, entry.getKey());
            if (owner == null || creditor == null || !creditor.isAlive()) {
                iterator.remove();
                continue;
            }
            int stackSize = Math.min(payout.remaining, ModItems.BITCOIN.getDefaultMaxStackSize());
            if (!throwLoanBitcoinStack(owner, creditor, stackSize)) {
                payout.nextStackTick = tick + 1L;
                continue;
            }
            payout.remaining -= stackSize;
            payout.nextStackTick = tick + LOAN_PAYOUT_STACK_INTERVAL_TICKS;
            if (payout.remaining <= 0) {
                payouts.removeFirst();
                if (payouts.isEmpty()) iterator.remove();
            }
        }
    }

    private static boolean throwLoanBitcoinStack(ServerPlayer owner, Entity creditor, int count) {
        if (count <= 0 || creditor.level() != owner.level() || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        double sourceY = creditor.getY() + creditor.getBbHeight() * 0.65D;
        Vec3 source = new Vec3(creditor.getX(), sourceY, creditor.getZ());
        Vec3 target = owner.position().add(0.0D, 0.1D, 0.0D);
        Vec3 direction = target.subtract(source);
        if (direction.lengthSqr() < 1.0E-6D) direction = creditor.getLookAngle();
        Vec3 velocity = direction.normalize().scale(0.28D).add(0.0D, 0.12D, 0.0D);

        ItemEntity item = new ItemEntity(level, source.x, source.y, source.z,
                new ItemStack(ModItems.BITCOIN, count));
        item.setDefaultPickUpDelay();
        item.setThrower(creditor);
        item.addTag(LOAN_PAYOUT_ITEM_TAG);
        item.setTarget(owner.getUUID());
        item.setDeltaMovement(velocity);
        if (!level.addFreshEntity(item)) return false;
        if (creditor instanceof LivingEntity living) living.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }
    private static Entity activeCreditorEntity(ServerPlayer owner, UUID ownerId) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) return null;
        UUID creditorId = ServerRaceSystem.getActiveAncientUkrCreditorId(ownerId);
        return creditorId == null ? null : level.getEntity(creditorId);
    }

    private static void onPlayerJoined(MinecraftServer server, ServerPlayer player) {
        BorrowerState borrower = store.borrowers.get(player.getUUID().toString());
        if (borrower != null) {
            borrower.nickname = player.getGameProfile().name();
            save(server);
        }
        showPendingInterestNotification(server, player);
        syncScoreboard(player);
    }

    private static void syncAllOnlineBorrowers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (store.borrowers.containsKey(player.getUUID().toString()) || CLIENT_OBJECTIVES.containsKey(player.getUUID())) {
                syncScoreboard(player);
            }
        }
    }

    private static Component creditLine(ServerPlayer player, Credit credit) {
        MutableComponent line = Component.literal("\u041a\u0440\u0435\u0434\u0438\u0442 \u2116" + credit.number + " - " + formatAmount(credit.debt) + " ")
                .withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false));
        line.append(coinComponent(player));
        return line;
    }

    private static Component coinComponent(ServerPlayer player) {
        if (PolymerResourcePackUtils.hasMainPack(player)) {
            return Component.literal(COIN_GLYPH).withStyle(style ->
                    style.withColor(0xFFFFFF).withItalic(false).withFont(COIN_FONT));
        }
        return Component.literal(FALLBACK_COIN).withStyle(style -> style.withColor(0xF6B800).withItalic(false));
    }

    private static void restoreServerSidebar(ServerPlayer player) {
        Objective sidebar = player.level().getServer().getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, sidebar));
    }

    private static String objectiveName(UUID playerId) {
        return "lg2cr_" + playerId.toString().replace("-", "").substring(0, 10);
    }

    private static BorrowerState borrower(UUID ownerId, String nickname) {
        BorrowerState borrower = store.borrowers.computeIfAbsent(ownerId.toString(), ignored -> new BorrowerState());
        if (nickname != null && !nickname.isBlank()) borrower.nickname = nickname;
        deduplicateCredits(borrower);
        return borrower;
    }

    private static void deduplicateCredits(BorrowerState borrower) {
        if (borrower == null || borrower.credits == null || borrower.credits.size() < 2) return;
        Map<Integer, Credit> unique = new LinkedHashMap<>();
        for (Credit credit : borrower.credits) {
            if (credit != null && credit.number > 0) unique.putIfAbsent(credit.number, credit);
        }
        if (unique.size() != borrower.credits.size()) {
            borrower.credits = new ArrayList<>(unique.values());
            borrower.credits.sort(Comparator.comparingInt(credit -> credit.number));
        }
    }

    private static Credit findCredit(UUID ownerId, Integer number) {
        if (ownerId == null || number == null) return null;
        BorrowerState borrower = store.borrowers.get(ownerId.toString());
        if (borrower == null) return null;
        return borrower.credits.stream().filter(credit -> credit.number == number).findFirst().orElse(null);
    }

    private static int nextCreditNumber(BorrowerState borrower, int maxActive) {
        for (int number = 1; number <= maxActive; number++) {
            int candidate = number;
            if (borrower.credits.stream().noneMatch(credit -> credit.number == candidate)) return number;
        }
        return -1;
    }

    private static void removeEmptyBorrower(UUID ownerId, BorrowerState borrower) {
        if (borrower.credits.isEmpty()) store.borrowers.remove(ownerId.toString());
    }

    private static CreditTerms terms() {
        RaceAbilityConfig config = null;
        for (PlayerRaceConfig race : RaceConfig.get().races) {
            if (race != null && race.id != null && RACE_ID.equals(race.id.trim().toLowerCase(Locale.ROOT))) {
                config = race.shnyaga;
                break;
            }
        }
        int maxActive = config == null || config.ancientUkrCreditMaxActive <= 0 ? DEFAULT_MAX_ACTIVE : config.ancientUkrCreditMaxActive;
        int maxPrincipal = config == null || config.ancientUkrCreditMaxPrincipalBitcoins <= 0 ? DEFAULT_MAX_PRINCIPAL : config.ancientUkrCreditMaxPrincipalBitcoins;
        double minRate = config == null || config.ancientUkrCreditMinHourlyPercent <= 0.0D ? DEFAULT_MIN_RATE : config.ancientUkrCreditMinHourlyPercent;
        double maxRate = config == null || config.ancientUkrCreditMaxHourlyPercent <= 0.0D ? DEFAULT_MAX_RATE : config.ancientUkrCreditMaxHourlyPercent;
        double multiplier = config == null || config.ancientUkrCreditMaxDebtMultiplier <= 0.0D ? DEFAULT_MAX_DEBT_MULTIPLIER : config.ancientUkrCreditMaxDebtMultiplier;
        return new CreditTerms(maxActive, maxPrincipal, Math.min(minRate, maxRate), Math.max(minRate, maxRate), multiplier);
    }
    private static double currentRatePercent() {
        return rateForHour(currentEpochHour());
    }

    private static double rateForHour(long epochHour) {
        CreditTerms terms = terms();
        int minTenths = (int) Math.round(terms.minRate() * 10.0D);
        int maxTenths = (int) Math.round(terms.maxRate() * 10.0D);
        int count = Math.max(1, maxTenths - minTenths + 1);
        int offset = (int) Math.floorMod(mix64(store.rateSeed ^ epochHour), count);
        return (minTenths + offset) / 10.0D;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static long currentEpochHour() {
        return ChronoUnit.HOURS.between(Instant.EPOCH, Instant.now());
    }

    private static String formatPercent(double percent) {
        return BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString();
    }

    private static String formatAmount(BigDecimal amount) {
        return normalizeMoney(amount).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.CEILING);
    }

    private static String storedNickname(BorrowerState borrower, UUID ownerId) {
        return borrower == null || borrower.nickname == null || borrower.nickname.isBlank()
                ? ownerId.toString() : borrower.nickname;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ActionResolution result(String reply) {
        return new ActionResolution(reply, false);
    }

    private static void sanitizeStore(double legacyRatePercent) {
        if (store.borrowers == null) store.borrowers = new LinkedHashMap<>();
        Iterator<Map.Entry<String, BorrowerState>> iterator = store.borrowers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BorrowerState> entry = iterator.next();
            if (parseUuid(entry.getKey()) == null || entry.getValue() == null) {
                iterator.remove();
                continue;
            }
            BorrowerState borrower = entry.getValue();
            borrower.pendingInterestNotification = normalizeMoney(borrower.pendingInterestNotification);
            if (borrower.credits == null) borrower.credits = new ArrayList<>();
            borrower.credits.removeIf(credit -> credit == null || credit.number <= 0 || credit.principal <= 0
                    || credit.debt == null || credit.debt.signum() <= 0);
            for (Credit credit : borrower.credits) {
                credit.debt = normalizeMoney(credit.debt);
                if (!Double.isFinite(credit.interestRatePercent) || credit.interestRatePercent <= 0.0D) {
                    credit.interestRatePercent = legacyRatePercent;
                }
            }
            deduplicateCredits(borrower);
            if (borrower.credits.isEmpty()) iterator.remove();
        }
    }

    private static void save(MinecraftServer server) {
        if (!loaded || server == null) return;
        Path path = statePath(server);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(store, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Lg2.LOGGER.warn("Failed to save Ancient Ukr credits", exception);
        }
    }

    private static Path statePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    record ActionResolution(String event, boolean closeCreditor) {
    }

    private record CreditTerms(int maxActive, int maxPrincipal, double minRate,
                               double maxRate, double maxDebtMultiplier) {
    }

    private record PendingOffer(int amount, double interestRatePercent) {
    }

    private static final class RepaymentSession {
        private final List<Integer> creditNumbers;
        private int totalPaid;

        private RepaymentSession(List<Integer> creditNumbers) {
            this.creditNumbers = creditNumbers;
        }
    }

    private static final class LoanPayout {
        private int remaining;
        private long nextStackTick;

        private LoanPayout(int remaining, long nextStackTick) {
            this.remaining = remaining;
            this.nextStackTick = nextStackTick;
        }
    }

    private static final class CreditStore {
        private long rateSeed;
        private long lastAccruedEpochHour;
        private Map<String, BorrowerState> borrowers = new LinkedHashMap<>();
    }

    private static final class BorrowerState {
        private String nickname = "";
        private BigDecimal pendingInterestNotification = BigDecimal.ZERO;
        private List<Credit> credits = new ArrayList<>();
    }

    private static final class Credit {
        private int number;
        private int principal;
        private BigDecimal debt = BigDecimal.ZERO;
        private double interestRatePercent;

        private Credit() {
        }

        private Credit(int number, int principal, BigDecimal debt, double interestRatePercent) {
            this.number = number;
            this.principal = principal;
            this.debt = debt;
            this.interestRatePercent = interestRatePercent;
        }
    }
}

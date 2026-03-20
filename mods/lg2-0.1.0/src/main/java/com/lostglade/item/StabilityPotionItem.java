package com.lostglade.item;

import com.lostglade.server.ServerStabilitySystem;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Optional;

public final class StabilityPotionItem extends PotionItem implements PolymerItem {
	private static final int POTION_COLOR = 0xF2CD26;
	private static final int VANILLA_POTION_TEXT_COLOR = 0x5555FF;

	private final Identifier modelId;
	private final PotionContents contents;
	private final int durationTicks;
	private final boolean anyWorldVisibility;
	private final String fallbackEn;
	private final String fallbackRu;
	private final String fallbackUk;
	private final String fallbackRpr;
	private final String fallbackJa;

	public StabilityPotionItem(
			Item.Properties properties,
			Identifier modelId,
			int durationTicks,
			boolean anyWorldVisibility,
			String fallbackEn,
			String fallbackRu,
			String fallbackUk,
			String fallbackRpr,
			String fallbackJa
	) {
		super(properties);
		this.modelId = modelId;
		this.durationTicks = durationTicks;
		this.anyWorldVisibility = anyWorldVisibility;
		this.contents = createPotionContents(durationTicks, anyWorldVisibility);
		this.fallbackEn = fallbackEn;
		this.fallbackRu = fallbackRu;
		this.fallbackUk = fallbackUk;
		this.fallbackRpr = fallbackRpr;
		this.fallbackJa = fallbackJa;
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable(this.getDescriptionId());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.POTION;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? this.modelId : null;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		Player player = entity instanceof Player usedByPlayer ? usedByPlayer : null;
		if (player instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
		}

		if (!level.isClientSide()) {
			if (entity instanceof ServerPlayer serverPlayer) {
				ServerStabilitySystem.activateStabilityPotion(serverPlayer, this.durationTicks, this.anyWorldVisibility);
			}
		}

		if (player != null) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}

		if (player == null) {
			stack.consume(1, entity);
			return stack.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : stack;
		}

		return ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE));
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		out.set(DataComponents.POTION_CONTENTS, this.contents);
		out.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true));
		out.set(DataComponents.LORE, ItemLore.EMPTY.withLineAdded(buildEffectTooltipLine(context)));
		if (!PolymerResourcePackUtils.hasMainPack(context) || !hasSupportedLanguage(context)) {
			out.set(DataComponents.CUSTOM_NAME, getLocalizedName(context).withStyle(style -> style.withItalic(false)));
		}
	}

	public static PotionContents createPotionContents(int durationTicks, boolean anyWorldVisibility) {
		MobEffectInstance effect = ServerStabilitySystem.createStabilityPotionEffect(durationTicks, anyWorldVisibility);
		return new PotionContents(Optional.empty(), Optional.of(POTION_COLOR), List.of(effect), Optional.empty());
	}

	private MutableComponent getLocalizedName(PacketContext context) {
		String normalized = getNormalizedLanguage(context);
		if (normalized == null) {
			return Component.literal(this.fallbackEn);
		}
		if (normalized.startsWith("rpr")) {
			return Component.literal(this.fallbackRpr);
		}
		if (normalized.startsWith("uk")) {
			return Component.literal(this.fallbackUk);
		}
		if (normalized.startsWith("ru")) {
			return Component.literal(this.fallbackRu);
		}
		if (normalized.startsWith("ja")) {
			return Component.literal(this.fallbackJa);
		}
		return Component.literal(this.fallbackEn);
	}

	private MutableComponent getLocalizedEffectName(PacketContext context) {
		String normalized = getNormalizedLanguage(context);
		if (normalized == null) {
			return Component.literal("Stability");
		}
		if (normalized.startsWith("rpr")) {
			return Component.literal("Стабильность");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Стабільність");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Стабильность");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("安定性");
		}
		return Component.literal("Stability");
	}

	private Component buildEffectTooltipLine(PacketContext context) {
		MutableComponent line = getLocalizedEffectName(context)
				.withStyle(style -> style.withColor(VANILLA_POTION_TEXT_COLOR).withItalic(false));
		if (this.anyWorldVisibility) {
			line.append(Component.literal(" II").withStyle(style -> style.withColor(VANILLA_POTION_TEXT_COLOR).withItalic(false)));
		}
		line.append(
				Component.literal(" (" + formatDuration(this.durationTicks) + ")")
						.withStyle(style -> style.withColor(VANILLA_POTION_TEXT_COLOR).withItalic(false))
		);
		return line;
	}

	private static String formatDuration(int durationTicks) {
		int totalSeconds = Math.max(0, durationTicks / 20);
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return String.format("%d:%02d", minutes, seconds);
	}

	private boolean hasSupportedLanguage(PacketContext context) {
		String normalized = getNormalizedLanguage(context);
		if (normalized == null) {
			return false;
		}
		return normalized.startsWith("en")
				|| normalized.startsWith("ru")
				|| normalized.startsWith("uk")
				|| normalized.startsWith("rpr")
				|| normalized.startsWith("ja");
	}

	private String getNormalizedLanguage(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return null;
		}
		return player.clientInformation().language().toLowerCase();
	}
}

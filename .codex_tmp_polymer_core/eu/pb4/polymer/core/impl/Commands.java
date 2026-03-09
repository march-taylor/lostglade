package eu.pb4.polymer.core.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.core.api.block.BlockMapper;
import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.other.PolymerStat;
import eu.pb4.polymer.core.api.utils.PolymerSyncUtils;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.impl.networking.PolymerServerProtocol;
import eu.pb4.polymer.core.impl.ui.CreativeTabListUi;
import eu.pb4.polymer.core.impl.ui.CreativeTabUi;
import eu.pb4.polymer.core.impl.ui.PotionUi;
import eu.pb4.polymer.core.mixin.block.PalettedContainerAccessor;
import eu.pb4.polymer.networking.impl.ExtConnection;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.class_11416;
import net.minecraft.class_11417;
import net.minecraft.class_11426;
import net.minecraft.class_11428;
import net.minecraft.class_11432;
import net.minecraft.class_11434;
import net.minecraft.class_11435;
import net.minecraft.class_11519;
import net.minecraft.class_11520;
import net.minecraft.class_11525;
import net.minecraft.class_124;
import net.minecraft.class_1299;
import net.minecraft.class_156;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2168;
import net.minecraft.class_2172;
import net.minecraft.class_2232;
import net.minecraft.class_2248;
import net.minecraft.class_2509;
import net.minecraft.class_2558;
import net.minecraft.class_2561;
import net.minecraft.class_2568;
import net.minecraft.class_2583;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_3448;
import net.minecraft.class_3965;
import net.minecraft.class_5242;
import net.minecraft.class_5244;
import net.minecraft.class_5628;
import net.minecraft.class_6880;
import net.minecraft.class_7157;
import net.minecraft.class_7733;
import net.minecraft.class_7923;
import net.minecraft.class_7924;
import net.minecraft.class_9334;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import org.jetbrains.annotations.ApiStatus;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static net.minecraft.class_2170.method_9244;
import static net.minecraft.class_2170.method_9247;

@SuppressWarnings("ResultOfMethodCallIgnored")
@ApiStatus.Internal
public class Commands {
    public static void register(LiteralArgumentBuilder<class_2168> command, class_7157 access) {
        command.then(method_9247("stats")
                        .requires(CommonImplUtils.permission("command.stats", 0))
                        .executes(Commands::statsGeneral)
                        .then(method_9244("type", class_7733.method_45603(access, class_7924.field_41226)).executes(Commands::stats))
                )
                .then(method_9247("effects")
                        .requires(CommonImplUtils.permission("command.effects", 0))
                        .executes(Commands::effects)
                )
                .then(method_9247("client-item")
                        .requires(CommonImplUtils.permission("command.client-item", 3))
                        .executes(Commands::displayClientItem)
                        .then(method_9247("get").executes(Commands::getClientItem))
                )
                .then(method_9247("export-registry")
                        .requires(CommonImplUtils.permission("command.export-registry", 3))
                        .executes(Commands::dumpRegistries)
                )
                .then(method_9247("target-block")
                        .requires(CommonImplUtils.permission("command.target-block", 3))
                        .executes(Commands::targetBlock)
                )
                .then(method_9247("target-item")
                        .requires(CommonImplUtils.permission("command.target-item", 3))
                        .executes(Commands::targetItem)
                )
                .then(method_9247("creative")
                        .requires(CommonImplUtils.permission("command.creative", 0))
                        .then(method_9244("itemGroup", class_2232.method_9441())
                                .suggests((context, builder) -> {
                                    var remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

                                    var groups = PolymerItemGroupUtils.getItemGroups(context.getSource().method_9207());

                                    class_2172.method_9268(groups, remaining, PolymerItemGroupUtils::getId, group -> builder.suggest(PolymerItemGroupUtils.getId(group).toString(), group.method_7737()));
                                    return builder.buildFuture();
                                })
                                .executes(Commands::creativeTab)
                        )
                        .executes(Commands::creativeTab));
    }

    public static void registerDev(LiteralArgumentBuilder<class_2168> dev) {
        dev
                .then(method_9247("reload-world")
                        .executes((ctx) -> {
                            PolymerUtils.reloadWorld(ctx.getSource().method_44023());
                            return 0;
                        })
                )
                .then(method_9247("get-mapper")
                        .executes((ctx) -> {
                            ctx.getSource().method_9226(() -> class_2561.method_43470(BlockMapper.getFrom(ctx.getSource().method_44023()).getMapperName()), false);
                            return 0;
                        })
                )
                .then(method_9247("reset-mapper")
                        .executes((ctx) -> {
                            BlockMapper.resetMapper(ctx.getSource().method_44023());
                            return 0;
                        })
                )
                .then(method_9247("run-sync")
                        .executes((ctx) -> {
                            PolymerSyncUtils.synchronizePolymerRegistries(ctx.getSource().method_44023().field_13987);
                            return 0;
                        }))
                .then(method_9247("protocol-info")
                        .executes((ctx) -> {
                            ctx.getSource().method_9226(() -> class_2561.method_43470("Protocol supported by your client:"), false);
                            for (var entry : ExtConnection.of(ctx.getSource().method_44023().field_13987).polymerNet$getSupportMap().object2IntEntrySet()) {
                                ctx.getSource().method_9226(() -> class_2561.method_43470("- " + entry.getKey() + " = " + entry.getIntValue()), false);
                            }
                            return 0;
                        })
                )
                .then(method_9247("validate_states")
                        .executes((ctx) -> {
                            PolymerServerProtocol.sendDebugValidateStatesPackets(ctx.getSource().method_44023().field_13987);
                            return 0;
                        })
                )
                .then(method_9247("set-pack-status")
                        .then(class_156.method_654(method_9244("status", BoolArgumentType.bool()), x -> {
                                            if (CompatStatus.POLYMER_RESOURCE_PACK) {
                                                x = x.executes((ctx) -> {
                                                    var status = ctx.getArgument("status", Boolean.class);
                                                    PolymerCommonUtils.setHasResourcePack(ctx.getSource().method_9207(), PolymerResourcePackUtils.getMainUuid(), status);
                                                    ctx.getSource().method_9226(() -> class_2561.method_43470("New resource pack status: " + status), false);
                                                    return 0;
                                                });
                                            }

                                            x.then(method_9244("uuid", class_5242.method_27643())
                                                    .executes((ctx) -> {
                                                        var status = ctx.getArgument("status", Boolean.class);
                                                        PolymerCommonUtils.setHasResourcePack(ctx.getSource().method_9207(), class_5242.method_27645(ctx, "uuid"), status);
                                                        ctx.getSource().method_9226(() -> class_2561.method_43470("New resource pack status: " + status), false);
                                                        return 0;
                                                    }));
                                        }
                                )
                        )
                )
                .then(class_156.method_654(method_9247("get-pack-status"), x -> {
                            if (CompatStatus.POLYMER_RESOURCE_PACK) {
                                x = x.executes((ctx) -> {
                                    var status = PolymerUtils.hasResourcePack(ctx.getSource().method_44023(), PolymerResourcePackUtils.getMainUuid());
                                    ctx.getSource().method_9226(() -> class_2561.method_43470("Resource pack status: " + status), false);
                                    return 0;
                                });
                            }
                            x.then(method_9244("uuid", class_5242.method_27643())
                                    .executes((ctx) -> {
                                        var status = PolymerCommonUtils.hasResourcePack(ctx.getSource().method_9207(), class_5242.method_27645(ctx, "uuid"));
                                        ctx.getSource().method_9226(() -> class_2561.method_43470("Resource pack status: " + status), false);
                                        return 0;
                                    }));
                        })
                )
                .then(method_9247("chunk_section_info")
                        .executes((ctx) -> {
                            var chunk = ctx.getSource().method_9225().method_22350(ctx.getSource().method_44023().method_24515());
                            var s = chunk.method_38259(ctx.getSource().method_9225().method_31602(ctx.getSource().method_44023().method_31478()));

                            var a = ((PalettedContainerAccessor<class_2680>) s.method_12265()).getData();

                            ctx.getSource().method_9226(() -> class_2561.method_43470("Chunk: " + chunk.method_12004() + " Palette: " + a.comp_119() + " | " + " Storage: " + a.comp_118() + " | Bits: " + a.comp_118().method_34896()), false);
                            return 0;
                        })
                );
    }

    private static int targetBlock(CommandContext<class_2168> context) {
        var raycast = (class_3965) context.getSource().method_44023().method_5745(10, 0, true);

        var builder = new StringBuilder();
        var state = context.getSource().method_9225().method_8320(raycast.method_17777());

        builder.append(class_7923.field_41175.method_10221(state.method_26204()));

        if (!state.method_26204().method_9595().method_11659().isEmpty()) {
            builder.append("[");
            var iterator = state.method_26204().method_9595().method_11659().iterator();

            while (iterator.hasNext()) {
                var property = iterator.next();
                builder.append(property.method_11899());
                builder.append("=");
                builder.append(((class_2769) property).method_11901(state.method_11654(property)));

                if (iterator.hasNext()) {
                    builder.append(",");
                }
            }
            builder.append("]");
        }

        context.getSource().method_9226(() -> class_2561.method_43470(builder.toString())
                .method_10862(class_2583.field_24360
                        .method_10949(new class_2568.class_10613(class_2561.method_43471("chat.copy.click")))
                        .method_10958(new class_2558.class_10606(builder.toString()))), false);

        return 0;
    }

    private static int targetItem(CommandContext<class_2168> context) throws CommandSyntaxException {
        var itemStack = context.getSource().method_9207().method_6047();
        var id = class_7923.field_41178.method_10221(itemStack.method_7909());
        context.getSource().method_9226(() -> class_2561.method_43470(id.toString())
                .method_10862(class_2583.field_24360
                        .method_10949(new class_2568.class_10613(class_2561.method_43471("chat.copy.click")))
                        .method_10958(new class_2558.class_10606(id.toString()))), false);
        return 0;
    }

    private static int dumpRegistries(CommandContext<class_2168> context) {
        var path = PolymerImplUtils.dumpRegistry();
        if (path != null) {
            context.getSource().method_9226(() -> class_2561.method_43470("Exported registry state as " + path), false);
        } else {
            context.getSource().method_9213(class_2561.method_43470("Couldn't export registry!"));
        }
        return 0;
    }

    private static int effects(CommandContext<class_2168> context) throws CommandSyntaxException {
        new PotionUi(context.getSource().method_44023());
        return 1;
    }

    private static int statsGeneral(CommandContext<class_2168> context) throws CommandSyntaxException {
        var player = context.getSource().method_44023();

        var list = new ArrayList<class_11519>();

        for (var statType : class_7923.field_41193) {
            list.add(new class_11519(new class_11416(class_2561.method_43470(class_7923.field_41193.method_10221(statType).toString()), 150),
                    Optional.of(new class_11525(new class_2558.class_10609("polymer stats " + class_7923.field_41193.method_10221(statType))))));
        }

        player.method_71753(class_6880.method_40223(new class_11426(new class_11417(
                class_2561.method_43471("gui.stats"),
                Optional.empty(),
                true, true,
                class_11520.field_60962,
                List.of(),
                List.of()
        ), list, Optional.of(new class_11519(new class_11416(class_5244.field_24334, 150), Optional.empty())), 1)));

        return 1;
    }

    private static int stats(CommandContext<class_2168> context) throws CommandSyntaxException {
        var player = context.getSource().method_44023();

        var list = new ArrayList<class_11432>();

        var type = (class_3448<Object>) class_7733.method_45602(context, "type", class_7924.field_41226).comp_349();
        for (var statObj : type.method_14959()) {
            if (PolymerUtils.isServerOnly(type.method_14959(), statObj) && type.method_14958(statObj)) {
                var stat = type.method_14956(statObj);

                var statVal = player.method_14248().method_15025(stat);

                class_1799 stack = class_1799.field_8037;

                class_2561 title;

                if (statObj instanceof class_2960 stat1) {
                    title = PolymerStat.getName(stat1);
                } else if (statObj instanceof class_1792 item) {
                    title = item.method_63680();
                    stack = item.method_7854();
                } else if (statObj instanceof class_2248 item) {
                    title = item.method_9518();
                    stack = item.method_8389().method_7854();
                } else if (statObj instanceof class_1299 item) {
                    title = item.method_5897();
                } else {
                    title = class_2561.method_43471(class_156.method_646(type.method_14959().method_46765().method_29177().method_12832(), type.method_14959().method_10221(statObj)));
                }

                var text = class_2561.method_43473().method_10852(title).method_10852(class_2561.method_43470(": ").method_27692(class_124.field_1080)).method_10852(class_2561.method_43470(stat.method_14953(statVal)).method_27692(class_124.field_1068));

                if (stack.method_7960()) {
                    list.add(new class_11435(text, 200));
                } else {
                    list.add(new class_11434(stack, Optional.of(new class_11435(text, 200)), true, true, 16, 16));
                }
            }
        }

        player.method_71753(class_6880.method_40223(new class_11428(new class_11417(
                class_2561.method_43471("gui.stats"),
                Optional.empty(),
                true, true,
                class_11520.field_60962,
                list,
                List.of()
        ), new class_11519(new class_11416(class_5244.field_24334, 150), Optional.empty()))));

        return 1;
    }

    private static int creativeTab(CommandContext<class_2168> context) {
        if (context.getSource().method_44023().method_68878()) {
            try {
                var id = context.getArgument("itemGroup", class_2960.class);

                var itemGroup = class_7923.field_44687.method_63535(id);
                if (itemGroup != null) {
                    new CreativeTabUi(context.getSource().method_44023(), itemGroup);
                    return 2;
                }
            } catch (Exception e) {
                //
            }

            new CreativeTabListUi(context.getSource().method_44023());
            return 1;
        } else {
            return 0;
        }
    }

    private static int displayClientItem(CommandContext<class_2168> context) throws CommandSyntaxException {
        var player = context.getSource().method_9207();
        var stack = PolymerItemUtils.getPolymerItemStack(player.method_6047(), PacketContext.create(player)).method_7972();
        stack.method_57381(class_9334.field_49628);

        context.getSource().method_9226(() -> (new class_5628("")).method_32305(
                class_1799.field_49266.encodeStart(context.getSource().method_30497().method_57093(class_2509.field_11560), stack).getOrThrow()
        ), false);

        return 1;
    }

    private static int getClientItem(CommandContext<class_2168> context) throws CommandSyntaxException {
        var player = context.getSource().method_9207();

        var stack = PolymerItemUtils.getPolymerItemStack(player.method_6047(), PacketContext.create(player));
        stack.method_57381(class_9334.field_49628);
        player.method_7270(stack);
        context.getSource().method_9226(() -> class_2561.method_43470("Given client representation to player"), true);

        return 1;
    }
}

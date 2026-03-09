package eu.pb4.polymer.virtualentity.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import net.fabricmc.api.ModInitializer;
import net.minecraft.class_2168;
import net.minecraft.class_2262;
import net.minecraft.class_2561;
import net.minecraft.class_7157;
import org.jetbrains.annotations.ApiStatus;

import static net.minecraft.class_2170.method_9244;
import static net.minecraft.class_2170.method_9247;


@ApiStatus.Internal
public class VirtualEntityMod implements ModInitializer {
	@Override
	public void onInitialize() {
		CommonImplUtils.registerDevCommands(this::commands);
	}

	private void commands(LiteralArgumentBuilder<class_2168> builder, class_7157 commandRegistryAccess) {
		builder.then(method_9247("ve_blockbound").then(method_9244("pos", class_2262.method_9698()).executes((ctx) -> {
			var b = BlockBoundAttachment.get(ctx.getSource().method_9225(), class_2262.method_48299(ctx, "pos"));

			if (b == null) {
				ctx.getSource().method_9226(() -> class_2561.method_43470("No block bound!"), false);
			} else {
				ctx.getSource().method_9226(() -> class_2561.method_43470("Found: " + b.holder()), false);
				for (var e : b.holder().getElements()) {
					ctx.getSource().method_9226(() -> class_2561.method_43470("- " + e), false);
				}
			}


			return b != null ? b.holder().getElements().size() : -1;
		})));
	}
}

package eu.pb4.polymer.core.mixin.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.class_2168;
import net.minecraft.class_2170;
import net.minecraft.class_2257;
import net.minecraft.class_2287;
import net.minecraft.class_7733;
import net.minecraft.class_9433;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_2170.class)
public abstract class CommandsMixin {
    @Inject(method = "argument", at = @At("TAIL"))
    private static void polymer$handleSuggestions(String name, ArgumentType<?> type, CallbackInfoReturnable<RequiredArgumentBuilder<class_2168, ?>> cir) {
        if (type instanceof class_2287 || type instanceof class_2257
                || type instanceof class_9433<?> || type instanceof class_7733<?>) {
            cir.getReturnValue().suggests(type::listSuggestions);
        }
    }
}

package eu.pb4.polymer.core.impl.compat;


import eu.pb4.polymer.common.impl.CommonImplUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_3244;
import net.minecraft.class_9290;
import net.minecraft.class_9334;
import org.jetbrains.annotations.ApiStatus;
import xyz.nucleoid.server.translations.api.Localization;

@ApiStatus.Internal
public class ServerTranslationUtils {
    public static final boolean IS_PRESENT;
    public static class_2561 parseFor(class_3244 handler, class_2561 text) {
        if (IS_PRESENT && !CommonImplUtils.isMainPlayer(handler.field_14140)) {
            return Localization.text(text, handler.field_14140);
        } else {
            return text;
        }
    }

    public static class_1799 parseFor(class_3244 handler, class_1799 stack) {
        stack = stack.method_7972();

        if (IS_PRESENT && !CommonImplUtils.isMainPlayer(handler.field_14140)) {
            stack.method_57368(class_9334.field_50239, null, x -> x != null ? parseFor(handler, x) : null);
            stack.method_57368(class_9334.field_49631, null, x -> x != null ? parseFor(handler, x) : null);
            stack.method_57368(class_9334.field_49632, null, x -> x != null ? new class_9290(x.comp_2400()
                    .stream().map(y -> parseFor(handler, y)).toList()) : null);
        }
        return stack;
    }

    static {
        var present = FabricLoader.getInstance().isModLoaded("server_translations_api");

        if (present) {
            try {
                present &= FabricLoader.getInstance().getModContainer("server_translations_api").get().getMetadata().getVersion().compareTo(Version.parse("2.0.0-")) != -1;
            } catch (Throwable e) {
                present = false;
            }
        }

        IS_PRESENT = present;
    }
}

package eu.pb4.polymer.core.impl.client.compat;

import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import java.util.ArrayList;

public class JeiCompatibility implements IModPlugin {
    private static final class_2960 ID = class_2960.method_60655("polymer", "jei_plugin");


    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (PolymerImpl.IS_CLIENT) {
            update(registration.getIngredientManager());
        }
    }

    private static void update(IIngredientManager manager) {
        synchronized (manager) {
            try {
                var list = manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().filter(PolymerImplUtils::isPolymerControlled).toList();
                if (!list.isEmpty()) {
                    manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, list);
                }

                var stacks = new ArrayList<class_1799>();
                CompatUtils.iterateItems(stacks::add);
                manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public class_2960 getPluginUid() {
        return ID;
    }
}

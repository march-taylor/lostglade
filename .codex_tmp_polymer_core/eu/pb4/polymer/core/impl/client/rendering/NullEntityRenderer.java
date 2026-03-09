package eu.pb4.polymer.core.impl.client.rendering;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_10017;
import net.minecraft.class_11659;
import net.minecraft.class_12075;
import net.minecraft.class_1297;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_4587;
import net.minecraft.class_4604;
import net.minecraft.class_5617;
import net.minecraft.class_6344;
import net.minecraft.class_7923;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class NullEntityRenderer extends class_6344<class_1297> {


    public NullEntityRenderer(class_5617.class_5618 context) {
        super(context);
    }

    @Override
    public boolean method_3933(class_1297 entity, class_4604 frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void method_3936(class_10017 renderState, class_4587 matrices, class_11659 queue, class_12075 cameraRenderState) {
        super.method_3936(renderState, matrices, queue, cameraRenderState);
        var text = "NO RENDERER: " + class_7923.field_41177.method_10221(renderState.field_58171);

        matrices.method_22903();
        matrices.method_46416(0, renderState.field_53330 / 2, 0);
        matrices.method_22907(cameraRenderState.field_63081);
        matrices.method_22905(0.025F, -0.025F, 0.025F);
        class_327 textRenderer = this.method_3932();
        float f = (float)(-textRenderer.method_1727(text)) / 2.0F;
        int j = (int)(class_310.method_1551().field_1690.method_19343(0.25F) * 255.0F) << 24;
        queue.method_73478(matrices, f, 1, class_2561.method_43470(text).method_30937(), true, class_327.class_6415.field_33993, renderState.field_61820, 0xbb3333, j, 0);
        matrices.method_22909();

    }
}

package io.blodhgarm.personality.mixin.client.owo;

import io.blodhgarm.personality.client.gui.utils.polygons.AbstractPolygon;
import io.blodhgarm.personality.misc.pond.owo.ExclusiveBoundingArea;
import io.blodhgarm.personality.misc.pond.owo.InclusiveBoundingArea;
import io.blodhgarm.personality.misc.pond.owo.RefinedBoundingArea;
import io.blodhgarm.personality.misc.pond.owo.UnimportantToggleHelper;
import io.blodhgarm.personality.utils.Constants;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.ParentComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.Iterator;

@Mixin(value = OwoUIDrawContext.class, remap = false)
public abstract class OwoUIDrawContextMixin extends DrawContext implements UnimportantToggleHelper {

    @Unique private boolean toggleUnimportantComp = true;

    public OwoUIDrawContextMixin(MinecraftClient client, VertexConsumerProvider.Immediate vertexConsumers) {
        super(client, vertexConsumers);
    }

    @Override
    public void toggleUnimportantComponents() {
        this.toggleUnimportantComp = !this.toggleUnimportantComp;
    }

    @Override
    public boolean filterUnimportantComponents() {
        return toggleUnimportantComp;
    }

    @Inject(method = "drawInspector", at = @At(value = "HEAD"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void personality$toggleFilterOn(ParentComponent root, double mouseX, double mouseY, boolean onlyHovered, CallbackInfo ci) {
        if (toggleUnimportantComp) Constants.shouldFilterUnimportantComponents = true;
    }

    @Inject(method = "drawInspector", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;"))
    private void personality$toggleFilterOff(ParentComponent root, double mouseX, double mouseY, boolean onlyHovered, CallbackInfo ci) {
        Constants.shouldFilterUnimportantComponents = false;
    }

    @Inject(method = "drawInspector", at = @At(value = "JUMP", opcode = Opcodes.IFEQ, ordinal = 1), locals = LocalCapture.CAPTURE_FAILHARD)
    private void renderTriangles(ParentComponent root, double mouseX, double mouseY, boolean onlyHovered, CallbackInfo ci, MinecraftClient client, TextRenderer textRenderer, ArrayList<Component> children, Iterator<Component> iterator, Component child){
        if(child instanceof ExclusiveBoundingArea excludableBoundingArea && !excludableBoundingArea.getExclusionZones().isEmpty()){
            excludableBoundingArea.getExclusionZones().forEach(polygon -> ((AbstractPolygon)polygon).drawPolygon(this.getMatrices(), new Color(1.0f, 0f, 0f, 0.5f).argb()));
        }

        if(child instanceof RefinedBoundingArea refinedBoundingArea && refinedBoundingArea.getRefinedBound() != null){
            refinedBoundingArea.getRefinedBound().drawPolygon(this.getMatrices(), new Color(0.8f, 0.6f, 0.2f, 0.5f).argb(), true, true);
        }

        if(child instanceof InclusiveBoundingArea inclusiveBoundingArea && !inclusiveBoundingArea.getInclusionZones().isEmpty()){
            inclusiveBoundingArea.getInclusionZones().forEach(polygon -> ((AbstractPolygon)polygon).drawPolygon(this.getMatrices(), new Color(0.4f, 0.2f, 0.6f, 0.5f).argb()));
        }
    }

}

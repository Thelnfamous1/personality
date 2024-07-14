package io.blodhgarm.personality.mixin.client.owo;

import io.blodhgarm.personality.client.gui.utils.polygons.AbstractPolygon;
import io.blodhgarm.personality.misc.pond.owo.CustomFocusHighlighting;
import io.blodhgarm.personality.misc.pond.owo.ExclusiveBoundingArea;
import io.blodhgarm.personality.misc.pond.owo.InclusiveBoundingArea;
import io.blodhgarm.personality.misc.pond.owo.RefinedBoundingArea;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Component.class, remap = false)
public interface ComponentMixin{

    @Inject(method = "isInBoundingBox", at = @At("HEAD"), cancellable = true)
    private void checkIfInExclusionZones(double x, double y, CallbackInfoReturnable<Boolean> cir) {
        if(this instanceof ExclusiveBoundingArea area && !area.getExclusionZones().isEmpty() && area.isWithinExclusionZone((float) x, (float) y)){
            cir.setReturnValue(false);
        }

        if(this instanceof RefinedBoundingArea area) {
            AbstractPolygon polygon = area.getRefinedBound();

            if(polygon != null) cir.setReturnValue(polygon.withinShape((float) x, (float) y));
        }

        if(this instanceof InclusiveBoundingArea area && !area.getInclusionZones().isEmpty() && area.isWithinInclusionZone((float) x, (float) y)){
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "drawFocusHighlight", at = @At("HEAD"), cancellable = true)
    private void checkForCustomRender(OwoUIDrawContext matrices, int mouseX, int mouseY, float partialTicks, float delta, CallbackInfo ci){
        if(this instanceof CustomFocusHighlighting customFocusHighlighting && customFocusHighlighting.getEvent() != null){
            if(customFocusHighlighting.getEvent().drawHighlight(matrices, mouseX, mouseY, partialTicks, delta)) ci.cancel();
        }
    }
}

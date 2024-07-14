package io.blodhgarm.personality.mixin.client.owo;

import io.blodhgarm.personality.client.gui.utils.owo.layout.ButtonAddon;
import io.blodhgarm.personality.client.gui.utils.polygons.AbstractPolygon;
import io.blodhgarm.personality.client.gui.utils.owo.layout.LineEvent;
import io.blodhgarm.personality.misc.pond.owo.*;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.util.EventSource;
import io.wispforest.owo.util.EventStream;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Mixin(value = FlowLayout.class, remap = false)
public abstract class FlowLayoutMixin extends BaseParentComponent implements MutableBoundingArea<FlowLayout>, LineManageable<FlowLayout>, FocusCheckable, CustomFocusHighlighting<FlowLayout>, ButtonAddonDuck<FlowLayout> {

    private final List<AbstractPolygon> inclusionZones = new ArrayList<>();

    //----------------------------

    private final List<AbstractPolygon> exclusionZones = new ArrayList<>();

    //----------------------------

    @Nullable private AbstractPolygon refinedBoundingArea = null;

    //----------------------------

    private final List<Component> lines = new ArrayList<>();

    private final List<LineEvent> lineEvents = new ArrayList<>();

    private boolean setupInteractionEvents = false;

    //----------------------------

    private final EventStream<FocusCheck> allowFocusEvents = FocusCheck.newStream();

    //----------------------------

    @Nullable private HighlightRenderEvent highlightRenderEvent = null;

    //----------------------------

    private ButtonAddon<FlowLayout> buttonAddon = null;

    //----------------------------

    protected FlowLayoutMixin(Sizing horizontalSizing, Sizing verticalSizing) {
        super(horizontalSizing, verticalSizing);
    }

    //----------------------------

    @Override
    public List<AbstractPolygon> getExclusionZones() {
        return exclusionZones;
    }

    @SafeVarargs
    @Override
    public final <P extends AbstractPolygon> FlowLayout addExclusionZone(P... polygons) {
        this.exclusionZones.addAll(List.of(polygons));

        return (FlowLayout) (Object) this;
    }

    @Override
    public final <P extends AbstractPolygon> FlowLayout addExclusionZone(List<P> polygons) {
        this.exclusionZones.addAll(polygons);

        return (FlowLayout) (Object) this;
    }

    //----------------------------

    @Override
    public List<AbstractPolygon> getInclusionZones() {
        return inclusionZones;
    }

    @SafeVarargs
    @Override
    public final <P extends AbstractPolygon> FlowLayout addInclusionZone(P... polygons) {
        this.inclusionZones.addAll(List.of(polygons));

        return (FlowLayout) (Object) this;
    }

    @Override
    public final <P extends AbstractPolygon> FlowLayout addInclusionZone(List<P> polygons) {
        this.inclusionZones.addAll(polygons);

        return (FlowLayout) (Object) this;
    }

    //----------------------------

    @Override
    public <P extends AbstractPolygon> FlowLayout setRefinedBound(P polygon) {
        this.refinedBoundingArea = polygon;

        return (FlowLayout) (Object) this;
    }

    @Nullable
    @Override
    public AbstractPolygon getRefinedBound() {
        return refinedBoundingArea;
    }

    //----------------------------

    @Override
    public List<Component> getLines() {
        return lines;
    }

    @Override
    public List<LineEvent> getLineEvents() {
        return lineEvents;
    }

    @Override
    public boolean hasSetupInteractionEvents() {
        return setupInteractionEvents;
    }

    @Override
    public void toggleSetupInteractionEvents() {
        setupInteractionEvents = !setupInteractionEvents;
    }


    //----------------------------

    @Override
    public boolean canFocus(FocusSource source) {
        return allowFocusEvents.sink().allowFocusSource(source);
    }

    @Override
    public EventSource<FocusCheck> focusCheck() {
        return allowFocusEvents.source();
    }

    //----------------------------

    @Override
    public FlowLayout addCustomFocusRendering(HighlightRenderEvent event) {
        this.highlightRenderEvent = event;

        return (FlowLayout) (Object) this;
    }

    @Override
    @Nullable
    public HighlightRenderEvent getEvent() {
        return this.highlightRenderEvent;
    }

    //----------------------------

    @Inject(method = "draw", at = @At("HEAD"))
    private void beforeRender(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta, CallbackInfo ci){
        if(buttonAddon != null) buttonAddon.beforeDraw(context, mouseX, mouseY, partialTicks, delta);
    }

    @Override
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if(buttonAddon != null && buttonAddon.onKeyPress(keyCode, scanCode, modifiers)) return true;

        return super.onKeyPress(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if(buttonAddon != null && buttonAddon.onMouseDown(mouseX, mouseY, button)) return true;

        return super.onMouseDown(mouseX, mouseY, button);
    }

    @Override
    public FlowLayout setButtonAddon(Function<FlowLayout, ButtonAddon<FlowLayout>> addonBuilder) {
        this.buttonAddon = addonBuilder.apply((FlowLayout) (Object) this);

        return (FlowLayout) (Object) this;
    }

    @Override
    @Nullable
    public ButtonAddon<FlowLayout> getAddon() {
        return buttonAddon;
    }
}

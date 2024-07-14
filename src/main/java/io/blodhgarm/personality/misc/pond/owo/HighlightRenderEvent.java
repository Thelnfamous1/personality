package io.blodhgarm.personality.misc.pond.owo;

import io.wispforest.owo.ui.core.OwoUIDrawContext;

public interface HighlightRenderEvent {

    boolean drawHighlight(OwoUIDrawContext drawContext, int mouseX, int mouseY, float partialTicks, float delta);
}

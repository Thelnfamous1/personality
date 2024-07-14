package io.blodhgarm.personality.misc.pond.owo;

import io.blodhgarm.personality.client.gui.utils.polygons.AbstractPolygon;
import io.wispforest.owo.ui.core.Component;

import org.jetbrains.annotations.Nullable;

public interface RefinedBoundingArea<T extends Component> {

    <P extends AbstractPolygon> T setRefinedBound(P polygon);

    @Nullable AbstractPolygon getRefinedBound();
}

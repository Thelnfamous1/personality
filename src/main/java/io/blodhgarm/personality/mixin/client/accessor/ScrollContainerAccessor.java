package io.blodhgarm.personality.mixin.client.accessor;

import io.wispforest.owo.ui.container.ScrollContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ScrollContainer.class)
public interface ScrollContainerAccessor {

    @Accessor("direction")
    ScrollContainer.ScrollDirection personality$direction();
}

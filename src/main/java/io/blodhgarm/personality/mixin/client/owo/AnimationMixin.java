package io.blodhgarm.personality.mixin.client.owo;

import io.blodhgarm.personality.misc.pond.owo.AnimationExtension;
import io.wispforest.owo.ui.core.Animatable;
import io.wispforest.owo.ui.core.Animation;
import io.wispforest.owo.ui.core.Easing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Animation.class, remap = false)
public abstract class AnimationMixin<A extends Animatable<A>> implements AnimationExtension<A> {

    @Shadow private float delta;

    @Shadow @Final private Easing easing;
    @Shadow @Final private A from;
    @Shadow @Final private A to;

    @Override
    public A getCurrentValue() {
        return this.from.interpolate(this.to, this.easing.apply(this.delta));
    }

    @Override
    public A getStartingValue() {
        return this.from;
    }

    @Override
    public A getEndingValue() {
        return this.to;
    }
}

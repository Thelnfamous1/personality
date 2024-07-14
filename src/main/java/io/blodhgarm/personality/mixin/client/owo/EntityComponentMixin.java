package io.blodhgarm.personality.mixin.client.owo;

import io.blodhgarm.personality.misc.pond.EntityComponentExtension;
import io.blodhgarm.personality.misc.pond.ShouldRenderNameTagExtension;
import io.wispforest.owo.ui.component.EntityComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Objects;

//Based on owo implementation
@Mixin(EntityComponent.class)
public class EntityComponentMixin<E extends Entity> implements ShouldRenderNameTagExtension<EntityComponent<E>>, EntityComponentExtension<E> {

    @Unique boolean shouldRenderNameTag = true;

    @Shadow @Final protected EntityRenderDispatcher dispatcher;

    @Override
    public EntityComponent<E> personality$setShouldNameTagRender(boolean value) {
        shouldRenderNameTag = value;

        return (EntityComponent<E>) (Object) this;
    }

    @Override
    public boolean personality$shouldNameTagRender() {
        return shouldRenderNameTag;
    }

    @Inject(method = "draw",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", id = "before"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", shift = At.Shift.AFTER, id = "after")
            }
    )
    private void personality$setShouldNameTagRenderOnDispatcher(OwoUIDrawContext drawContext, int mouseX, int mouseY, float partialTicks, float delta, CallbackInfo ci){
        ((ShouldRenderNameTagExtension)this.dispatcher).personality$setShouldNameTagRender(
                Objects.equals(ci.getId(), "draw:before") ? personality$shouldNameTagRender() : true
        );
    }

    //-----

    @Unique private boolean personality_removeXAngle = false;

    @Override
    public EntityComponent<E> removeXAngle(boolean value) {
        personality_removeXAngle = value;

        return (EntityComponent<E>) (Object) this;
    }

    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 2))
    private float personality$modifyXAngle(float angle){
        return personality_removeXAngle ? 0f : angle;
    }

    @ModifyArgs(method = "draw", remap = false, at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;<init>(FFF)V"))
    private void personality$modifyShaderLightVecs(Args args){
        if(!personality_removeXAngle) return;

        args.set(0, 0f);
        args.set(1, Math.copySign(1, args.get(1)));
        args.set(2, 0.45f);
    }

}

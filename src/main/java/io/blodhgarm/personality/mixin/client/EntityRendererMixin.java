package io.blodhgarm.personality.mixin.client;

import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.misc.pond.CharacterToPlayerLink;
import io.blodhgarm.personality.misc.pond.ShouldRenderNameTagExtension;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = 1001)
public abstract class EntityRendererMixin<T extends Entity> {

    @Shadow @Final protected EntityRenderDispatcher dispatcher;

    @ModifyVariable(method = "renderLabelIfPresent", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getNameLabelHeight()F", shift = At.Shift.BY, by = 4))
    private float personality$changeLabelHeight(float value, T entity){
        return (PersonalityMod.CONFIG.showPlayerNamePlateAtChestLevel() && entity instanceof PlayerEntity) ? (value / 2) : value;
    }

    //Based on owo implementation
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void cancelLabel(T entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (((ShouldRenderNameTagExtension) this.dispatcher).personality$shouldNameTagRender()) return;

        ci.cancel();
    }

    @Inject(method = "renderLabelIfPresent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void personality$translateLabel(T entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci){
        if(PersonalityMod.CONFIG.showPlayerNamePlateAtChestLevel() && entity instanceof PlayerEntity) {
            matrices.scale(0.75f, 0.75f, 1.0f);
            matrices.translate(0, 0, -0.25f);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderLabelIfPresent(Lnet/minecraft/entity/Entity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void personality$togglePlayerNameInPlate(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci){
        if(entity instanceof CharacterToPlayerLink l) l.toggleOnlyCharacterName(!PersonalityMod.CONFIG.showPlayerNameInNamePlate());
    }
}

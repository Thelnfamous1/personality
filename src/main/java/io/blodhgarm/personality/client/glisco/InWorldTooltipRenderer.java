package io.blodhgarm.personality.client.glisco;

import com.mojang.blaze3d.systems.RenderSystem;
import io.blodhgarm.personality.PersonalityMod;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.*;

/*
 * A 1:1-ish copy from the mod Affinity by glisco. Link to github: https://github.com/wisp-forest/affinity/blob/master/src/main/java/io/wispforest/affinity/client/InWorldTooltipRenderer.java
 *
 * I am not the original author and this follows the MIT license from the original mod
 */

public class InWorldTooltipRenderer implements WorldRenderEvents.AfterTranslucent {

    public static InWorldTooltipRenderer INSTANCE = new InWorldTooltipRenderer();

    private Vec3d lastTargetPos = null;

    private float currentTargetViewTime = -1;
    private float lastTargetViewTime = 0;

    @Nullable public InWorldTooltipProvider lastProvider = null;

    private boolean targetViewGap = false;
    private boolean differingProvider = false;

    public List<InWorldTooltipProvider.Entry> entryCache = new ArrayList<>();

    public Map<Identifier, Renderer> REGISTERED_RENDERS = new HashMap<>();

    public void initialize() {
        this.REGISTERED_RENDERS.put(PersonalityMod.id("description"), DescriptionRenderer.INSTANCE);

        WorldRenderEvents.AFTER_TRANSLUCENT.register(this);
    }

    @Override
    public void afterTranslucent(WorldRenderContext context) {
        if(!PersonalityMod.CONFIG.descriptionConfig.descriptionView()) return;

        var client = MinecraftClient.getInstance();

        HitResultInfo info = HitResultInfo.of(client, client.crosshairTarget);

        if(info == null) {
            lastTargetPos = null;
            targetViewGap = true;

            return;
        }

        var target = info.target();
        var targetShape = info.targetShape();
        var provider = info.provider();

        if (!target.equals(lastTargetPos)) {
            lastTargetPos = target;
            currentTargetViewTime = 0;
        }

        currentTargetViewTime += client.getLastFrameDuration();

        if (currentTargetViewTime < 5){
            lastTargetViewTime = currentTargetViewTime;

            return;
        }

        if(!Objects.equals(provider, lastProvider)) differingProvider = true;

        lastProvider = provider;

        if(provider == null) return;

        provider.updateTooltipEntries(Math.floor(currentTargetViewTime) == 5f, client.getLastFrameDuration());

        if(differingProvider || targetViewGap) {
            entryCache.clear();

            provider.appendTooltipEntries(entryCache);
        }

        if (entryCache.isEmpty()) return;

        //--

        Vec3d pos;

        var xOffset = targetShape.minX + (targetShape.maxX - targetShape.minX) / 2;
        var zOffset = targetShape.minZ + (targetShape.maxZ - targetShape.minZ) / 2;

        if(FabricLoader.getInstance().isDevelopmentEnvironment()) {
            pos = target.add(xOffset, targetShape.maxY + .15, zOffset);

            //context.world().addParticle(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 0, 0, 0);

            pos = pos.subtract(context.camera().getPos());
        } else {
            pos = target.add(xOffset, targetShape.maxY + .15, zOffset)
                    .subtract(context.camera().getPos());
        }

        //--

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.push();
        modelViewStack.multiplyPositionMatrix(context.matrixStack().peek().getPositionMatrix());

        modelViewStack.translate(pos.x, pos.y, pos.z);

        modelViewStack.scale(.01f, -.01f, .01f);//modelViewStack.scale(.01f, -.01f, .01f);

        var offset = pos.multiply(-1);

        double horizontalAngle = Math.atan2(offset.z, offset.x);
        //double verticalAngle = Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z));

        modelViewStack.multiply(new Quaternionf(RotationAxis.POSITIVE_Y.rotation((float) (-horizontalAngle + Math.PI / 2))));
        RenderSystem.applyModelViewMatrix();

        var matrices = new MatrixStack();
        matrices.translate(50, 0, 60);
//            matrices.multiply(new Quaternion(Vec3f.POSITIVE_X, (float) (verticalAngle), false));

        //---

        var renderer = REGISTERED_RENDERS.get(provider.getTooltipId());

        if(renderer != null) {
            boolean shouldReset = targetViewGap || differingProvider;

            float delta = currentTargetViewTime - lastTargetViewTime;

            OwoUIDrawContext drawContext = OwoUIDrawContext.of(new DrawContext(client, (VertexConsumerProvider.Immediate) context.consumers()));
            renderer.render(entryCache, info, drawContext, shouldReset, delta);
        }

        targetViewGap = false;
        differingProvider = false;

        lastTargetViewTime = currentTargetViewTime;

        //---

        modelViewStack.pop();
        RenderSystem.applyModelViewMatrix();
    }

    public boolean attemptingRendering(){
        return lastTargetPos != null && lastProvider != null;
    }

    public interface Renderer {
        void render(List<InWorldTooltipProvider.Entry> entries, HitResultInfo hitResult, OwoUIDrawContext drawContext, boolean shouldReset, float delta);
    }

    public record HitResultInfo(Vec3d target, Box targetShape, @Nullable InWorldTooltipProvider provider){
        public HitResultInfo(BlockPos target, Box targetShape, Object o){
            this(Vec3d.of(target), targetShape, (o instanceof InWorldTooltipProvider p) ? p : null);
        }

        @Nullable
        public static HitResultInfo of(MinecraftClient client, HitResult hitResult){
            HitResultInfo resultInfo = null;

            if(hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof InWorldTooltipProvider p){
                resultInfo = new HitResultInfo(entityHit.getEntity().getPos(), entityHit.getEntity().getBoundingBox().offset(entityHit.getEntity().getPos().multiply(-1)), p);
            } else if(hitResult instanceof BlockHitResult blockHit && blockHit.getType() != HitResult.Type.MISS) {
                BlockEntity blockEntity = client.world.getBlockEntity(blockHit.getBlockPos());

                if(blockEntity != null) resultInfo = new HitResultInfo(blockHit.getBlockPos(), blockEntity.getCachedState().getOutlineShape(client.world, blockHit.getBlockPos()).getBoundingBox(), blockEntity);
            }

            return resultInfo;
        }

    }
}

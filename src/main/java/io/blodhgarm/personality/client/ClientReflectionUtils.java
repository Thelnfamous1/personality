package io.blodhgarm.personality.client;

import io.wispforest.owo.ui.component.EntityComponent;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class ClientReflectionUtils {
    public static final Class<?> renderablePlayerEntityClass;
    public static final VarHandle renderablePlayerEntity$skinTextureId;
    public static final VarHandle renderablePlayerEntity$model;

    static {
        try {
            Class<?> clazz = EntityComponent.class.getDeclaredClasses()[0];

            Field field1 = clazz.getDeclaredField("skinTextureId");
            Field field2 = clazz.getDeclaredField("model");

            field1.setAccessible(true);
            field2.setAccessible(true);

            renderablePlayerEntity$skinTextureId = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).unreflectVarHandle(field1);
            renderablePlayerEntity$model = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).unreflectVarHandle(field2);

            renderablePlayerEntityClass = clazz;
        }
        catch (NoSuchFieldException | SecurityException exception){ throw new RuntimeException("Attempting to get the Field [skinTextureId or model] within [RenderablePlayerEntity] has failed!", exception); }
        catch (IllegalAccessException exception){ throw new RuntimeException("Attempting to get the Field [skinTextureId or model] unreflected VarHandle within [RenderablePlayerEntity] has failed!", exception); }
    }

    public static <E extends Entity> E editRenderablePlayerEntity(E playerEntity, Identifier id, String model){
        if(renderablePlayerEntityClass.isInstance(playerEntity)){
            renderablePlayerEntity$skinTextureId.set(playerEntity, id);
            renderablePlayerEntity$model.set(playerEntity, model);
        }

        return playerEntity;
    }
}

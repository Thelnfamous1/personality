package io.blodhgarm.personality.client.compat.origins;

import io.blodhgarm.personality.api.addons.BaseAddon;
import io.blodhgarm.personality.mixin.ModPacketsC2SAccessor;
import io.blodhgarm.personality.utils.ServerAccess;
import io.github.apace100.origins.Origins;
import io.github.apace100.origins.component.OriginComponent;
import io.github.apace100.origins.component.PlayerOriginComponent;
import io.github.apace100.origins.origin.Origin;
import io.github.apace100.origins.origin.OriginLayer;
import io.github.apace100.origins.origin.OriginLayers;
import io.github.apace100.origins.origin.OriginRegistry;
import io.github.apace100.origins.registry.ModComponents;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class OriginAddon extends BaseAddon<Origin> {

    private Identifier origin_id;
    private Identifier layer_id;

    public OriginAddon(Identifier origin_id, Identifier layer_id){
        this.origin_id = origin_id;
        this.layer_id = layer_id;
    }

    public void setOrigin(Identifier origin_id, Identifier layer_id){
        this.origin_id = origin_id;
        this.layer_id = layer_id;
    }

    public Identifier getOriginId(){
        return origin_id;
    }

    public Identifier getOriginLayerId(){
        return layer_id;
    }

    @Override
    public void applyAddon(PlayerEntity player) {
        OriginComponent component = ModComponents.ORIGIN.get(player);

        Origin origin = OriginRegistry.get(origin_id);
        OriginLayer layer = OriginLayers.getLayer(layer_id);

        if (origin.isChoosable() && layer.contains(origin)) {
            boolean hadOriginBefore = component.hadOriginBefore();
            boolean hadAllOrigins = component.hasAllOrigins();

            component.setOrigin(layer, origin);
            component.checkAutoChoosingLayers(player, false);
            component.sync();

            //TODO: Origins have certain spawns or places to be meaning we may need to save the characters position or something idk.
            if (component.hasAllOrigins() && !hadAllOrigins) {
                OriginComponent.onChosen(player, true/*hadOriginBefore*/);
            }

            Origins.LOGGER.info("Player " + player.getDisplayName().getContent() + " chose Origin: " + origin_id + ", for layer: " + layer_id);
        } else {
            Origins.LOGGER.info("Player " + player.getDisplayName().getContent() + " tried to choose unchoosable Origin for layer " + layer_id + ": " + origin_id + ".");
            component.setOrigin(layer, Origin.EMPTY);
        }
    }

    @Override
    public boolean applyOnClient() {
        return false;
    }
}

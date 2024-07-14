package io.blodhgarm.personality.mixin.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphContainer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontStorage.class)
public abstract class FontStorageMixin {

    @Unique private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow @Final private GlyphContainer<FontStorage.GlyphPair> glyphCache;

    @Shadow protected abstract FontStorage.GlyphPair findGlyph(int codePoint);

    @Inject(method = "getGlyph", at = @At("HEAD"))
    private void personality$catch_index_exception(int codePoint, boolean validateAdvance, CallbackInfoReturnable<Glyph> cir){
        try {
            this.glyphCache.computeIfAbsent(codePoint, this::findGlyph).getGlyph(validateAdvance);
        } catch (ArrayIndexOutOfBoundsException e){
            LOGGER.error("A glyph was found to have a messed up index or something!");
            LOGGER.error("CodePoint: " + codePoint);

            LOGGER.error(this.glyphCache.toString());

            throw e;
        }
    }
}

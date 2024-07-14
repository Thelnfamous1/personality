package io.blodhgarm.personality.client.gui.components;

import io.wispforest.owo.mixin.ui.access.ClickableWidgetAccessor;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class CustomButtonComponent extends ButtonComponent {

    private int yTextOffset = 0;

    private boolean floatPrecision = false;

    public CustomButtonComponent(Text message, Consumer<ButtonComponent> onPress) {
        super(message, onPress);
    }

    public CustomButtonComponent setYTextOffset(int yTextOffset){
        this.yTextOffset = yTextOffset;

        return this;
    }

    public CustomButtonComponent setFloatPrecision(boolean floatPrecision){
        this.floatPrecision = floatPrecision;

        return this;
    }

    @Override
    public void renderButton(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderer.draw((OwoUIDrawContext) drawContext, this, delta);

        int color = this.active ? 0xffffff : 0xa0a0a0;

        var textRenderer = MinecraftClient.getInstance().textRenderer;

        var x = this.x() + (this.width / 2f) - (textRenderer.getWidth(this.getMessage()) / 2f);
        var y = this.y() + (this.height - 8) / 2f + yTextOffset;

        if (!floatPrecision) {
            x = Math.round(x);
            y = Math.round(y);
        }

        if(this.textShadow){
            drawContext.drawTextWithShadow(textRenderer, this.getMessage(), (int)x, (int)y, color);
        } else{
            drawContext.drawText(textRenderer, this.getMessage(), (int)x, (int)y, color, false);
        }

        var tooltip = ((ClickableWidgetAccessor) this).owo$getTooltip();

        if (this.hovered && tooltip != null) drawContext.drawOrderedTooltip(textRenderer, tooltip.getLines(MinecraftClient.getInstance()), mouseX, mouseY);
    }
}

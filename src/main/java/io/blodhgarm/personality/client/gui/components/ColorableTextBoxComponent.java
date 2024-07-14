package io.blodhgarm.personality.client.gui.components;

import io.blodhgarm.personality.mixin.client.accessor.TextFieldWidgetAccessor;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.DrawContext;

public class ColorableTextBoxComponent extends TextBoxComponent {

    private Color backgroundColor = Color.BLACK;//Color.ofArgb(0xFF555555);
    private Color outlineColor = Color.ofArgb(0xFFa0a0a0);

    protected ColorableTextBoxComponent(Sizing horizontalSizing) {
        super(horizontalSizing);
    }

    public static ColorableTextBoxComponent textBox(Sizing horizontalSizing, String text) {
        final var textBox = new ColorableTextBoxComponent(horizontalSizing);
        textBox.setText(text);
        textBox.setCursorToStart();
        return textBox;
    }

    public ColorableTextBoxComponent bqColor(Color color){
        this.backgroundColor = color;

        return this;
    }

    public ColorableTextBoxComponent outlineColor(Color color){
        this.outlineColor = color;

        return this;
    }

    public ColorableTextBoxComponent setEditAbility(boolean editable) {
        super.setEditable(editable);

        return this;
    }

    //--------------------------------------------------------------------

    public int getInnerWidth() {
        return this.width - 8;
    }

    @Override
    public void renderButton(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (!this.isVisible()) return;

        if(((TextFieldWidgetAccessor)this).personality$drawsBackground()) {
            int i = this.isFocused() ? -1 : outlineColor.argb();

            drawContext.fill(this.x() - 1, this.y() - 1, this.x() + this.width + 1, this.y() + this.height + 1, i);
            drawContext.fill(this.x(), this.y(), this.x() + this.width, this.y() + this.height, backgroundColor.argb());
        }

        super.renderButton(drawContext, mouseX, mouseY, delta);
    }
}

package io.blodhgarm.personality.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import io.blodhgarm.personality.misc.pond.owo.UnimportantComponent;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.core.Color;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

public class LineComponent extends BaseComponent implements UnimportantComponent {

    private final Corner startPoint;
    private final Corner endPoint;

    private Positioning startPos;
    private Positioning endPos;

    private Vec2f offsetVec;

    protected AnimatableProperty<Color> startColor = AnimatableProperty.of(Color.BLACK);
    protected AnimatableProperty<Color> endColor = AnimatableProperty.of(Color.BLACK);

    protected boolean hasBeenMoved = true;

    private float lineWidth = 1.0f;

    public LineComponent(int startX, int startY, int endX, int endY){
        boolean startRight = startX > endX;
        boolean startBottom = startY > endY;

        startPoint = Corner.parse(startRight, startBottom);
        endPoint = Corner.parse(!startRight, !startBottom);

        int width = MathHelper.abs(startX - endX);
        int height = MathHelper.abs(startY - endY);

        this.sizing(Sizing.fixed(width), Sizing.fixed(height));

        int positionX = (startRight ? endX : startX);
        int positionY = (startBottom ? endY : startY);

        this.positioning(Positioning.absolute(positionX, positionY));
    }

    /**
     * Set the color of this component. Equivalent to calling
     * both {@link #startColor(Color)} and {@link #endColor(Color)}
     *
     * @param color The start and end color of this
     *              component's color gradient
     */
    public LineComponent color(Color color) {
        this.startColor.set(color);
        this.endColor.set(color);
        return this;
    }

    /**
     * Set the start color of this component's gradient
     */
    public LineComponent startColor(Color startColor) {
        this.startColor.set(startColor);
        return this;
    }

    /**
     * @return The current start color of this component's gradient
     */
    public AnimatableProperty<Color> startColor() {
        return this.startColor;
    }

    /**
     * Set the end color of this component's gradient
     */
    public LineComponent endColor(Color endColor) {
        this.endColor.set(endColor);
        return this;
    }

    /**
     * @return The current end color of this component's gradient
     */
    public AnimatableProperty<Color> endColor() {
        return this.endColor;
    }

    public LineComponent setLineWidth(float lineWidth){
        this.lineWidth = lineWidth;

        return this;
    }

    @Override
    public void updateX(int x) {
        if(this.x() != x) this.hasBeenMoved = true;

        super.updateX(x);
    }

    @Override
    public void updateY(int y) {
        if(this.y() != y) this.hasBeenMoved = true;

        super.updateY(y);
    }

    @Override
    protected void applySizing() {
        final var oldWidth = this.width;
        final var oldHeight = this.height;

        super.applySizing();

        if(this.width != oldWidth || this.height != oldHeight) this.hasBeenMoved = true;
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);

        this.startColor.update(delta);
        this.endColor.update(delta);
    }

    @Override
    public void draw(OwoUIDrawContext drawContext, int mouseX, int mouseY, float partialTicks, float delta) {
        if(hasBeenMoved){
            startPos = startPoint.getPos(this);
            endPos = endPoint.getPos(this);

            Vec2f vec = new Vec2f(endPos.x - startPos.x, endPos.y - startPos.y).normalize();

            this.offsetVec = new Vec2f(vec.y, -vec.x).multiply(0.5F);

            if(lineWidth > 0) this.offsetVec = this.offsetVec.multiply(lineWidth);

            hasBeenMoved = false;
        }

        final int startColor = this.startColor.get().argb();
        final int endColor = this.endColor.get().argb();

        drawLine(drawContext.getMatrices(), new Vec2f(startPos.x, startPos.y), new Vec2f(endPos.x, endPos.y), offsetVec, startColor, endColor);
    }

    public static void drawLine(MatrixStack matrices, Vec2f startPos, Vec2f endPos, float lineWidth, int startColor, int endColor){
        Vec2f vec = new Vec2f(endPos.x - startPos.x, endPos.y - startPos.y).normalize();

        Vec2f offsetVec = new Vec2f(vec.y, -vec.x).multiply(0.5F);

        if(lineWidth > 0) offsetVec = offsetVec.multiply(lineWidth);

        drawLine(matrices, startPos, endPos, offsetVec, startColor, endColor);
    }

    public static void drawLine(MatrixStack matrices, Vec2f startPos, Vec2f endPos, Vec2f offsetVec, int startColor, int endColor){
        float x1 = startPos.x;
        float y1 = startPos.y;

        float x2 = endPos.x;
        float y2 = endPos.y;

        var buffer = Tessellator.getInstance().getBuffer();
        var matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.enableDepthTest();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x2 + offsetVec.x, y2 + offsetVec.y, 0)
                .color(endColor)
                .next();
        buffer.vertex(matrix, x1 + offsetVec.x, y1 + offsetVec.y, 0)
                .color(startColor)
                .next();
        buffer.vertex(matrix, x1 - offsetVec.x, y1 - offsetVec.y, 0)
                .color(startColor)
                .next();
        buffer.vertex(matrix, x2 - offsetVec.x, y2 - offsetVec.y, 0)
                .color(endColor)
                .next();

        Tessellator.getInstance().draw();

        RenderSystem.disableDepthTest();

        RenderSystem.disableBlend();
    }

    @Override
    public boolean isInBoundingBox(double x, double y) {
        double lineX = this.x();
        double lineY = this.y();

        double lineWidth = this.width();
        double lineHeight = this.height();

        if(lineWidth == 0){
            lineX -= (this.lineWidth / 2);
            lineWidth += this.lineWidth;
        } else if (lineHeight == 0) {
            lineY -= (this.lineWidth / 2);
            lineHeight += this.lineWidth;
        }

        return x >= lineX && x < lineX + lineWidth
            && y >= lineY && y < lineY + lineHeight;
    }

    public enum Corner {
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_LEFT,
        TOP_RIGHT;

        public static Corner parse(boolean isRight, boolean isBottom){
            if(isRight && isBottom) return BOTTOM_RIGHT;
            else if(isRight) return TOP_RIGHT;
            else if(isBottom) return BOTTOM_LEFT;
            else return TOP_LEFT;
        }

        public Positioning getPos(PositionedRectangle rectangle){
            int x, y;

            switch (this) {
                case BOTTOM_LEFT -> {
                    x = rectangle.x();
                    y = rectangle.y() + rectangle.height();
                } case BOTTOM_RIGHT -> {
                    x = rectangle.x() + rectangle.width();
                    y = rectangle.y() + rectangle.height();
                } case TOP_LEFT -> {
                    x = rectangle.x();
                    y = rectangle.y();
                } default -> {
                    x = rectangle.x() + rectangle.width();
                    y = rectangle.y();
                }
            }

            return Positioning.absolute(x, y);
        }
    }
}

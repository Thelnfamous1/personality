package io.blodhgarm.personality.client.gui.components.builders;

import earcut4j.Earcut;
import io.blodhgarm.personality.client.gui.components.LineComponent;
import io.blodhgarm.personality.client.gui.utils.polygons.Triangle;
import io.blodhgarm.personality.client.gui.utils.polygons.TriangleBasedPolygon;
import io.blodhgarm.personality.misc.pond.owo.*;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;

public class SimpleRadialLayoutBuilder {

    private final Queue<Component> selectedComponents = new ArrayDeque<>();

    //-------------------------------------

    private final List<Component> components = new ArrayList<>();

    private Function<Component, Boolean> onSelectionEvent = component -> false;

    private int rInnerOffset = 0,
                rInnerLine   = 15,
                rOuterLine   = 120,
                rOuterEntry  = 80,
                rOuterMax    = 80;

    @Nullable public String mainComponentID = null;

    private double angleOffset = (Math.PI / 2);

    //-------------------------------------

    @Nullable private FlowLayout mainLayout = null;

    public SimpleRadialLayoutBuilder() {}

    public SimpleRadialLayoutBuilder adjustRadi(int rInnerOffset, int rInnerLine, int rOuterLine, int rOuterEntry) {
        this.rInnerOffset = rInnerOffset;

        this.rInnerLine = rInnerLine;
        this.rOuterLine = rOuterLine;

        this.rOuterEntry = rOuterEntry;

        return this;
    }

    public SimpleRadialLayoutBuilder changeRadialStartOffset(double angleOffset){
        this.angleOffset = angleOffset;

        return this;
    }

    public SimpleRadialLayoutBuilder addComponent(Component component) {
        this.components.add(component);

        return this;
    }

    public SimpleRadialLayoutBuilder addComponents(List<Component> components) {
        this.components.addAll(components);

        return this;
    }

    public SimpleRadialLayoutBuilder onSelection(Function<Component, Boolean> event){
        this.onSelectionEvent = event;

        return this;
    }

    public SimpleRadialLayoutBuilder setComponentId(String componentId){
        this.mainComponentID = componentId;

        return this;
    }

    public SimpleRadialLayoutBuilder build(FlowLayout root) {
        int entries = this.components.size(); //this.components.size() % 2 == 0 ? this.components.size() : this.components.size() + 1;

        int rInnerLine = this.rInnerOffset + this.rInnerLine;

        int rOuterEntry = this.rInnerOffset + this.rOuterEntry;
        int rOuterLine = this.rInnerOffset + this.rOuterLine;

        if(mainLayout == null){
            this.mainLayout = Containers.verticalFlow(
                    Sizing.fixed(MinecraftClient.getInstance().getWindow().getScaledWidth()),
                    Sizing.fixed(MinecraftClient.getInstance().getWindow().getScaledHeight())
            ); // Sizing.fixed(rOuterLine * 2), Sizing.fixed(rOuterLine * 2)

            this.mainLayout.positioning(Positioning.relative(50, 50));

            this.mainLayout.id(mainComponentID);
        } else {
            this.mainLayout.clearChildren();

            this.mainLayout.sizing(
                    Sizing.fixed(MinecraftClient.getInstance().getWindow().getScaledWidth()),
                    Sizing.fixed(MinecraftClient.getInstance().getWindow().getScaledHeight())
            );// Sizing.fixed(rOuterLine * 2), Sizing.fixed(rOuterLine * 2)
        }

        int centerX = mainLayout.horizontalSizing().get().value / 2;
        int centerY = mainLayout.verticalSizing().get().value / 2;

        double angleStep = (2 * Math.PI) / entries;

        for (int i = 0; i < entries; i++) {
            Component entryComponent = components.get(i);

            double baseAngle = (angleStep * i) - angleOffset;

            double lineAngle1 = baseAngle + (angleStep / 2);
            double lineAngle2 = baseAngle - (angleStep / 2);

            int line1EndX = MathHelper.floor(centerX + (Math.cos(lineAngle1) * rOuterLine));
            int line1EndY = MathHelper.floor(centerY + (Math.sin(lineAngle1) * rOuterLine));

            int line1StartX = rInnerLine == 0 ? centerX : MathHelper.floor(centerX + (Math.cos(lineAngle1) * rInnerLine));
            int line1StartY = rInnerLine == 0 ? centerY : MathHelper.floor(centerY + (Math.sin(lineAngle1) * rInnerLine));

            int line2EndX = MathHelper.floor(centerX + (Math.cos(lineAngle2) * rOuterLine));
            int line2EndY = MathHelper.floor(centerY + (Math.sin(lineAngle2) * rOuterLine));

            int line2StartX = rInnerLine == 0 ? centerX : MathHelper.floor(centerX + (Math.cos(lineAngle2) * rInnerLine));
            int line2StartY = rInnerLine == 0 ? centerY : MathHelper.floor(centerY + (Math.sin(lineAngle2) * rInnerLine));

            int line3EndX = MathHelper.floor(centerX + (Math.cos(baseAngle) * (rOuterLine + 10)));
            int line3EndY = MathHelper.floor(centerY + (Math.sin(baseAngle) * (rOuterLine + 10)));

            //------------------------------------------------------------------------------------------------------------------

            Integer[] xNumbers = new Integer[]{line1StartX, line1EndX, line2StartX, line2EndX};
            Integer[] yNumbers = new Integer[]{line1StartY, line1EndY, line2StartY, line2EndY};

            Arrays.sort(xNumbers);
            Arrays.sort(yNumbers);

            int layoutStartXPos = xNumbers[0];
            int layoutStartYPos = yNumbers[0];

            int layoutEndXPos = xNumbers[3];
            int layoutEndYPos = yNumbers[3];

            int layoutWidth = layoutEndXPos - layoutStartXPos;
            int layoutHeight = layoutEndYPos - layoutStartYPos;

            FlowLayout entryLayout = (FlowLayout) Containers.verticalFlow(Sizing.fixed(layoutWidth), Sizing.fixed(layoutHeight))
                    .positioning(Positioning.absolute(layoutStartXPos, layoutStartYPos))
                    .surface((drawContext, component) -> {
                           if(component instanceof RefinedBoundingArea area && area.getRefinedBound() != null) {
                               area.getRefinedBound().drawPolygon(drawContext.getMatrices(), new Color(0.0f, 0.0f, 0.0f, 0.25f).argb());
                           }
                    });

            mainLayout.child(entryLayout);

            //------------------------------------------------------------------------------------------------------------------

            double[] setOfLinePoints = new double[]{
                    line1StartX, line1StartY,
                    line1EndX, line1EndY,
//                    line3EndX,   line3EndY,
                    line2EndX, line2EndY, // change
                    line2StartX, line2StartY,
            };

            List<Integer> points = Earcut.earcut(setOfLinePoints);

            List<Triangle> triangles = new ArrayList<>();

            for (int triangleGroup = 0; triangleGroup < (points.size() / 3); triangleGroup++) {
                List<Vector3f> trianglePositions = new ArrayList<>();

                for (int posIndex = triangleGroup * 3; posIndex < triangleGroup * 3 + 3; posIndex++) {
                    int lowerDoubleArrayIndex = (points.get(posIndex) * 2);
                    int upperDoubleArrayIndex = lowerDoubleArrayIndex + 1;

                    double x = setOfLinePoints[lowerDoubleArrayIndex];
                    double y = setOfLinePoints[upperDoubleArrayIndex];

                    Vector3f position = new Vector3f((float) x - layoutStartXPos, (float) y - layoutStartYPos, 0f);

                    trianglePositions.add(position);
                }

                triangles.add(new Triangle(trianglePositions.get(0), trianglePositions.get(1), trianglePositions.get(2)));
            }

            ((RefinedBoundingArea<FlowLayout>) entryLayout).setRefinedBound(new TriangleBasedPolygon(triangles));

            //------------------------------------------------------------------------------------------------------------------

            int entryWidth = entryComponent.horizontalSizing().get().value;
            int entryHeight = entryComponent.verticalSizing().get().value;

            int entryX = (MathHelper.floor(centerX + (Math.cos(baseAngle) * rOuterEntry)) - (entryWidth / 2)) - layoutStartXPos;
            int entryY = (MathHelper.floor(centerY + (Math.sin(baseAngle) * rOuterEntry)) - (entryHeight / 2)) - layoutStartYPos;

            entryLayout.child(
                    entryComponent
                            .positioning(Positioning.absolute(entryX, entryY))
            ).configure(component -> {
                if(entryWidth != 0 && entryHeight != 0) {
                    component.mouseEnter()
                            .subscribe(() -> {
                                onSelectedAdded(component);

//                                System.out.println("Mouse: Entered Component: [" + component.toString() + "]");
                            });

                    component.mouseLeave()
                            .subscribe(() -> {
                                onSelectedRemoved(component);

//                                System.out.println("Mouse: Left Component: [" + component.toString() + "]");
                            });

                    component.focusGained()
                            .subscribe(source -> {
                                onSelectedAdded(component);

//                                System.out.println("Focus: Gained on Component: [" + component.toString() + "]");
                            });

                    component.focusLost()
                            .subscribe(() -> {
                                onSelectedRemoved(component);

//                                System.out.println("Focus: Lost on Component: [" + component.toString() + "]");
                            });

                    component.mouseDown()
                            .subscribe((mouseX, mouseY, button) -> {
                                return button == 0
                                        ? this.onSelectionEvent.apply(this.getSelectedComponent())
                                        : false;
                            });

                    ((FocusCheckable) component).focusCheck()
                            .subscribe(source -> {
                                return true;
                            });

                    component.keyPress()
                            .subscribe((keyCode, scanCode, modifiers) -> {
                                        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_KP_ENTER
                                                ? this.onSelectionEvent.apply(this.getSelectedComponent())
                                                : false;
                                    }
                            );
                }
            });

            //------------------------------------------------------------------------------------------------------------------

            List<LineComponent> lines = new ArrayList<>();

            LineComponent line1Component = new LineComponent(line1StartX, line1StartY, line1EndX, line1EndY)
                    .configure(component -> {
                            mainLayout.surface();
                    });

            mainLayout.child(line1Component);

            lines.add(line1Component);

            //------------------------------------------------------------------------------------------------------------------

            LineComponent line2Component = new LineComponent(line2StartX, line2StartY, line2EndX, line2EndY)
                    .configure(component -> {
                        mainLayout.surface();
                    });

            mainLayout.child(line2Component);

            lines.add(line2Component);

            //------------------------------------------------------------------------------------------------------------------

            LineComponent line3Component = new LineComponent(line1StartX, line1StartY, line2StartX, line2StartY)
                    .configure(component -> {
                        mainLayout.surface();
                    });

            mainLayout.child(line3Component);

            lines.add(line3Component);

            //------------------------------------------------------------------------------------------------------------------

            ((LineManageable<FlowLayout>)entryLayout).addLines(lines);

            ((LineManageable<FlowLayout>)entryLayout)
                    .addLineEvent((manager, eventType) -> {
                        if(Objects.equals(eventType, "selected")){
                            manager.getLines().forEach(component -> {
                                LineComponent line = (LineComponent) component;

                                line.zIndex(10);

                                Easing linearWrapped = x -> {
                                    if(x == 0.0f){ line.zIndex(0); }

                                    return Easing.LINEAR.apply(x);
                                };

                                Animation<Color> startColor = line.startColor().animation();

                                if(startColor == null) {
                                    startColor = line.startColor().animate(100, Easing.LINEAR, new Color(1.0f, 1.0f, 1.0f));

                                    startColor.finished().subscribe((direction, looping) -> {
                                        if(direction == Animation.Direction.BACKWARDS) line.zIndex(0);
                                    });
                                }

                                startColor.forwards();

                                Animation<Color> endColor = line.endColor().animation();

                                if(endColor == null) {
                                    endColor = line.endColor().animate(100, Easing.LINEAR, new Color(1.0f, 1.0f, 1.0f));

                                    endColor.finished().subscribe((direction, looping) -> {
                                        if(direction == Animation.Direction.BACKWARDS) line.zIndex(0);
                                    });
                                }

                                endColor.forwards();
                            });
                        } else if(Objects.equals(eventType, "deselected")){
                            manager.getLines().forEach(component -> {
                                LineComponent line = (LineComponent) component;

                                if(line.startColor().animation() != null) line.startColor().animation().backwards();
                                if(line.endColor().animation() != null) line.endColor().animation().backwards();
                            });
                        }

                        return true;
                    });

            //------------------------------------------------------------------------------------------------------------------

            ((CustomFocusHighlighting<FlowLayout>) entryLayout)
                    .addCustomFocusRendering((drawContext, mouseX, mouseY, partialTicks, delta) -> {
                        return true;
                    });
        }



        return this;
    }

    public Component getComponent(FlowLayout root){
        if(this.mainLayout == null){
            this.build(root);
        }

        return this.mainLayout;
    }

    @Nullable
    public Component getComponent(){
        return this.mainLayout;
    }

    public void onSelectedRemoved(Component component){
        this.selectedComponents.remove(component);

        Component component1 = this.selectedComponents.peek();

        if(component instanceof LineManageable<?> manager){
            manager.getLineEvents().forEach(event -> event.action(manager, "deselected"));
        }

        if(component1 instanceof LineManageable<?> manager){
            manager.getLineEvents().forEach(event -> event.action(manager, "selected"));
        }
    }

    public void onSelectedAdded(Component component){
        this.selectedComponents.remove(component);

        this.selectedComponents.add(component);

        Component component1 = this.selectedComponents.peek();

        if(component.equals(component1) && component instanceof LineManageable<?> manager){
            manager.getLineEvents().forEach(event -> event.action(manager, "selected"));
        }
    }

    @Nullable
    public Component getSelectedComponent(){
        return this.selectedComponents.peek();
    }

}

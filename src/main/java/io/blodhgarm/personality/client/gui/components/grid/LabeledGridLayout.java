package io.blodhgarm.personality.client.gui.components.grid;

import com.mojang.logging.LogUtils;
import io.blodhgarm.personality.client.gui.components.builders.LabeledObjectToComponent;
import io.blodhgarm.personality.client.gui.components.builders.ObjectToComponent;
import io.blodhgarm.personality.client.gui.components.LineComponent;
import io.blodhgarm.personality.client.gui.utils.ListWithinListView;
import io.blodhgarm.personality.client.gui.utils.ModifiableCollectionHelper;
import io.blodhgarm.personality.client.gui.utils.owo.layout.LineEvent;
import io.blodhgarm.personality.misc.pond.owo.LineManageable;
import io.blodhgarm.personality.utils.ReflectionUtils;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public class LabeledGridLayout<T> extends BaseParentComponent implements LineManageable<LabeledGridLayout<T>>, ModifiableCollectionHelper<LabeledGridLayout<T>, T> {

    private static final Logger LOGGER = LogUtils.getLogger();

    //--------------------------------------------------------------------

    protected final List<LabeledObjectToComponent<T>> builders = new ArrayList<>();

    protected final ListOrderedMap<T, List<Component>> entryToComponents = new ListOrderedMap<>();
    protected final List<T> originalEntriesList = new ArrayList<>();

    public boolean builtYet = false;

    private boolean isVertical = true;

    protected int rowDividingLineWidth = -1;
    protected int columnDividingLineWidth = -1;

    protected Color rowDividingLineColor = Color.BLACK;
    protected Color columnDividingLineColor = Color.BLACK;

    @Nullable protected FilterFunc<T> cached_filter = null;
    @Nullable protected Comparator<T> cached_comparator = null;

    //--------------------------------------------------------------------

    protected List<Component> lines = new ArrayList<>();

    private final List<LineEvent> lineEvents = new ArrayList<>();

    private boolean setupInteractionEvents = false;

    //--------------------------------------------------------------------

    private static boolean forceRebuildSizing = false;

    public int[] prevColumnSizes = new int[]{};
    public int[] prevRowSizes = new int[]{};

    protected final ListWithinListView<Component> gridChildrenView = new ListWithinListView<>(entryToComponents.valueList());
    protected final ListWithinListView<Component> finalChildrenView = new ListWithinListView<>();

    protected Size contentSize = Size.zero();

    //--------------------------------------------------------------------

    public LabeledGridLayout(Sizing horizontalSizing, Sizing verticalSizing) {
        super(horizontalSizing, verticalSizing);

        this.entryToComponents.put(null, new ArrayList<>());

        finalChildrenView.addList(gridChildrenView);
        finalChildrenView.addList(lines);
    }

    //-----

    public LabeledGridLayout<T> setRowDividingLine(int width){
        this.rowDividingLineWidth = width;

        return this;
    }

    public LabeledGridLayout<T> setColumnDividingLine(int width){
        this.columnDividingLineWidth = width;

        return this;
    }

    public LabeledGridLayout<T> setRowDividingLineColor(Color color){
        this.rowDividingLineColor = color;

        return this;
    }

    public LabeledGridLayout<T> setColumnDividingLineColor(Color color){
        this.columnDividingLineColor = color;

        return this;
    }

    //-----

    public LabeledGridLayout<T> changeDirection(boolean vertical){
        this.isVertical = vertical;

        return this;
    }

    public LabeledGridLayout<T> addBuilder(Text text, ObjectToComponent<T> builder){
        return addBuilder(-1, text, builder);
    }

    public LabeledGridLayout<T> addBuilder(int index, Text text, ObjectToComponent<T> builder){
        return addBuilder(index, LabeledObjectToComponent.of(text, builder));
    }

    public LabeledGridLayout<T> addBuilder(int index, LabeledObjectToComponent<T> builder){
        if(index != -1){
            this.builders.add(index, builder);
        } else {
            this.builders.add(builder);
        }

        return this;
    }

    public LabeledGridLayout<T> addEntry(T entry){
        if(entry != null) {
            this.entryToComponents.put(entry, new ArrayList<>());
            this.originalEntriesList.add(entry);
        }

        return this;
    }

    public LabeledGridLayout<T> removeEntry(T entry){
        this.entryToComponents.remove(entry);
        this.originalEntriesList.remove(entry);

        return this;
    }

    public LabeledGridLayout<T> addEntries(Collection<T> entries){
        for(T entry : entries) addEntry(entry);

        return this;
    }

    public LabeledGridLayout<T> removeEntries(Collection<T> entries){
        for(T entry : entries) removeEntry(entry);

        return this;
    }

    public LabeledGridLayout<T> clearEntries(){
        this.entryToComponents.valueList().forEach(components -> {
            components.forEach(component -> component.dismount(DismountReason.REMOVED));
        });

        List<Component> headerInfo = this.entryToComponents.get(null);

        this.entryToComponents.clear();

        this.entryToComponents.put(null, headerInfo);

        this.updateLayout();

        return this;
    }

    @Override public void setFilter(FilterFunc<T> filter) { this.cached_filter = filter; }
    @Override public FilterFunc<T> getFilter() { return this.cached_filter; }
    @Override public void setComparator(Comparator<T> comparator) { this.cached_comparator = comparator; }
    @Override public Comparator<T> getComparator() { return this.cached_comparator; }

    @Override public List<T> getList() { return getMapInsertOrder();}
    @Override public List<T> getDefaultList() { return originalEntriesList; }

    @Override
    public void applyFiltersAndSorting(){
        if(!this.builtYet) this.buildComponents();

        ModifiableCollectionHelper.super.applyFiltersAndSorting();

        getList().add(0, null);

        this.updateLayout();
    }

    protected List<T> getMapInsertOrder(){
        return ReflectionUtils.getMapInsertOrder(this.entryToComponents);
    }

    //--------------------------------------------------------------------

    public void rebuildComponents(){
        this.builtYet = false;

        this.updateLayout();
    }

    public void buildComponents(){
        if(this.builtYet) return;

//        boolean isVertical = true; //((ScrollContainerAccessor) parent).personality$direction() == ScrollContainer.ScrollDirection.VERTICAL;

        if (isVertical) {
            this.verticalAlignment(VerticalAlignment.CENTER);
        } else {
            this.horizontalAlignment(HorizontalAlignment.CENTER);
        }

        List<Component> labelComponents = entryToComponents.get(null);

        if(labelComponents.isEmpty()) {
            builders.forEach(helper -> labelComponents.add(helper.buildLabel(false)));
        }

        entryToComponents.forEach((entry, components) -> {
            if(entry != null) components.addAll(gatherComponentsForObject(builders, entry, isVertical));
        });

        this.builtYet = true;
    }

    public List<Component> gatherComponentsForObject(List<LabeledObjectToComponent<T>> wrappers, T entry, boolean isParentVertical){
        List<Component> mainComponentList = new ArrayList<>();

        wrappers.forEach(componentBuilder -> {
            mainComponentList.add(componentBuilder.build(entry, isParentVertical));
        });

        return mainComponentList;
    }

    private int getColumnSize(){
        return this.isVertical ? this.builders.size() : getMapInsertOrder().size();
    }

    private int getRowSize(){
        return this.isVertical ? getMapInsertOrder().size() : this.builders.size();
    }

    //--------------------------------------------------------------------

    @Override
    public List<Component> getLines() {
        return this.lines;
    }

    @Override
    public List<LineEvent> getLineEvents() {
        return this.lineEvents;
    }

    @Override
    public boolean hasSetupInteractionEvents() {
        return setupInteractionEvents;
    }

    @Override
    public void toggleSetupInteractionEvents() {
        setupInteractionEvents = !setupInteractionEvents;
    }

    //--------------------------------------------------------------------

    //This section is taken from GridLayout and modified to work with the Custom Map and List

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return this.contentSize.width() + this.padding.get().right();
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return this.contentSize.height() + this.padding.get().bottom();
    }

    @Override
    public void onChildMutated(Component child) {
        super.onChildMutated(child);

        forceRebuildSizing = true;
    }

    @Override
    public void layout(Size space) {
        boolean rebuildSizingArrays = !this.builtYet;

        this.buildComponents();

        this.getLines().forEach(component -> component.dismount(DismountReason.REMOVED));
        this.getLines().clear();

        int[] columnSizes = new int[getColumnSize()];
        int[] rowSizes = new int[getRowSize()];

        var childSpace = this.calculateChildSpace(space);

        this.finalChildrenView.forEach((child) -> {
            if (child != null) child.inflate(childSpace);
        });

        if(rebuildSizingArrays || forceRebuildSizing) {
            this.determineSizes(columnSizes, false);
            this.determineSizes(rowSizes, true);

            this.prevColumnSizes = columnSizes;
            this.prevRowSizes = rowSizes;

            forceRebuildSizing = false;
        } else {
            columnSizes = prevColumnSizes;
            rowSizes = prevRowSizes;
        }

        var mountingOffset = this.childMountingOffset();
        var layoutX = new MutableInt(this.x + mountingOffset.width());
        var layoutY = new MutableInt(this.y + mountingOffset.height());

        //------------

        var addedColumnDividers = false;

        var addColumnDividers = columnDividingLineWidth > 0;
        var addRowDividers = rowDividingLineWidth > 0;

        Insets columnMargin = Insets.horizontal(3);
        Insets rowMargin = Insets.vertical(3);

        int totalColumnSize = Arrays.stream(columnSizes).sum() + ((columnMargin.horizontal() + columnDividingLineWidth) * (this.getColumnSize() - 1));
        int totalRowSize = Arrays.stream(rowSizes).sum() + ((rowMargin.vertical() * (this.getRowSize() - 2)) + (rowDividingLineWidth * (this.getRowSize() - 1))) + 3;

        //------------

        for (int row = 0; row < getRowSize(); row++) {
            layoutX.setValue(this.x + mountingOffset.width());

            int rowSize = rowSizes[row];

            for (int column = 0; column < getColumnSize(); column++) {
                int columnSize = columnSizes[column];

                this.mountChild(this.getChild(row, column), childSpace, child -> {
                    child.mount(
                            this,
                            layoutX.intValue() + child.margins().get().left() + this.horizontalAlignment().align(child.fullSize().width(), columnSize),
                            layoutY.intValue() + child.margins().get().top() + this.verticalAlignment().align(child.fullSize().height(), rowSize)
                    );
                });

                layoutX.add(columnSizes[column]);

                if(addColumnDividers && column + 1 != this.getColumnSize()) {
                    if(!addedColumnDividers) {
                        int x1 = layoutX.intValue() + Math.round(columnDividingLineWidth / 2f) - this.x();
                        int y1 = layoutY.intValue() - this.y();

                        int x2 = x1;
                        int y2 = y1 + totalRowSize;

                        LineComponent columnComponent = (LineComponent) new LineComponent(x1, y1, x2, y2)
                                .color(columnDividingLineColor)
                                .setLineWidth(columnDividingLineWidth)
                                .margins(columnMargin.add(0,0, 0, 0));

                        this.addLine(columnComponent);

                        this.mountChild(columnComponent, childSpace, child -> {
                            child.mount(this,
                                    layoutX.intValue(),
                                    layoutY.intValue()
                            );
                        });
                    }

                    layoutX.add(columnDividingLineWidth + columnMargin.horizontal());
                }
            }

            layoutY.add(rowSizes[row]);

            if(addColumnDividers && !addedColumnDividers) addedColumnDividers = true;

            if(addRowDividers && row + 1 != this.getRowSize()){
                layoutX.setValue(this.x + mountingOffset.width());

                int x1 = layoutX.intValue() - this.x();
                int y1 = layoutY.intValue() + Math.round(rowDividingLineWidth / 2f) - this.y(); //+ (row == 0 ? 0 : Math.round(rowMargin.vertical() / 2f));

                int x2 = x1 + totalColumnSize;
                int y2 = y1;

                var currentMargin = row == 0 ? Insets.bottom(3) : rowMargin;

                LineComponent rowComponent = (LineComponent) new LineComponent(x1, y1, x2, y2)
                        .color(rowDividingLineColor)
                        .setLineWidth(rowDividingLineWidth)
                        .margins(currentMargin);

                this.addLine(rowComponent);

                this.mountChild(rowComponent, childSpace, child -> {
                    child.mount(this,
                            layoutX.intValue(),
                            layoutY.intValue()
                    );
                });

                layoutY.add(rowDividingLineWidth + currentMargin.vertical());
            }
        }

        this.contentSize = Size.of(layoutX.intValue() - this.x, layoutY.intValue() - this.y);
    }

    @Override
    public void draw(OwoUIDrawContext matrices, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(matrices, mouseX, mouseY, partialTicks, delta);

        this.lines.forEach(component -> {
            if(component.id() != null && Objects.equals(component.id(), "debug_line_on")){
//                LOGGER.info("x: {}, y: {}, width: {}, height:{}", component.x(), component.y(), component.width(), component.height());

//                component.id("debug_line_off");
            }
        });



        this.drawChildren(matrices, mouseX, mouseY, partialTicks, delta, this.finalChildrenView);
    }

    protected @Nullable Component getChild(int row, int column) {
        return this.entryToComponents.getValue(isVertical ? row : column).get(isVertical ? column : row);
    }

    protected void determineSizes(int[] sizes, boolean rows) {
        if ((rows ? this.verticalSizing : this.horizontalSizing).get().method != Sizing.Method.CONTENT) {
            Arrays.fill(sizes, (rows ? this.height - this.padding().get().vertical() : this.width - this.padding().get().horizontal()) / (rows ? getRowSize() : getColumnSize()));
        } else {
            for (int row = 0; row < getRowSize(); row++) {
                for (int column = 0; column < getColumnSize(); column++) {
                    final var child = this.getChild(row, column);
                    if (child == null) continue;

                    if (rows) {
                        sizes[row] = Math.max(sizes[row], child.fullSize().height());
                    } else {
                        sizes[column] = Math.max(sizes[column], child.fullSize().width());
                    }
                }
            }
        }
    }

    @Override
    public LabeledGridLayout<T> removeChild(Component child) {
        return this;
    }

    @Override
    public List<Component> children() { return this.finalChildrenView; }

    //-----------------------------------------
}

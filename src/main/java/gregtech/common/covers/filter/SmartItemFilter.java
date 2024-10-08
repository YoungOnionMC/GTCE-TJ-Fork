package gregtech.common.covers.filter;

import gregtech.api.gui.Widget;
import gregtech.api.gui.widgets.CycleButtonWidget;
import gregtech.api.recipes.*;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.util.ItemStackKey;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IStringSerializable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class SmartItemFilter extends ItemFilter {

    private SmartFilteringMode filteringMode = SmartFilteringMode.ELECTROLYZER;
    private SmartMatchingMode matchingMode = SmartMatchingMode.DEFAULT;

    public SmartFilteringMode getFilteringMode() {
        return filteringMode;
    }

    public void setFilteringMode(SmartFilteringMode filteringMode) {
        this.filteringMode = filteringMode;
        markDirty();
    }

    public SmartMatchingMode getMatchingMode() {
        return matchingMode;
    }

    public void setMatchingMode(SmartMatchingMode matchingMode) {
        filteringMode.transferStackSizesCache.clear();
        this.matchingMode = matchingMode;
        markDirty();
    }

    @Override
    public int getSlotTransferLimit(Object matchSlot, Set<ItemStackKey> matchedStacks, int globalTransferLimit) {
        ItemAndMetadataAndStackSize itemAndMetadata = (ItemAndMetadataAndStackSize) matchSlot;
        return itemAndMetadata.transferStackSize;
    }

    @Override
    public Object matchItemStack(ItemStack itemStack) {
        ItemAndMetadata itemAndMetadata = new ItemAndMetadata(itemStack);
        Integer cachedTransferRateValue = filteringMode.transferStackSizesCache.get(itemAndMetadata);

        if (cachedTransferRateValue == null) {
            ItemStack infinitelyBigStack = itemStack.copy();
            infinitelyBigStack.setCount(Integer.MAX_VALUE);

            Recipe recipe = filteringMode.recipeMap.findRecipe(Long.MAX_VALUE, Collections.singletonList(infinitelyBigStack), Collections.emptyList(), Integer.MAX_VALUE, matchingMode.matchingMode, false);
            if (recipe == null) {
                filteringMode.transferStackSizesCache.put(itemAndMetadata, 0);
                cachedTransferRateValue = 0;
            } else {
                CountableIngredient inputIngredient = recipe.getInputs().iterator().next();
                filteringMode.transferStackSizesCache.put(itemAndMetadata, inputIngredient.getCount());
                cachedTransferRateValue = inputIngredient.getCount();
            }
        }

        if (cachedTransferRateValue == 0) {
            return null;
        }
        return new ItemAndMetadataAndStackSize(itemAndMetadata, cachedTransferRateValue);
    }

    @Override
    public void initUI(Consumer<Widget> widgetGroup) {
        widgetGroup.accept(new CycleButtonWidget(10, 0, 75, 20,
            SmartFilteringMode.class, this::getFilteringMode, this::setFilteringMode)
            .setTooltipHoverString("cover.smart_item_filter.filtering_mode.description"));
        widgetGroup.accept(new CycleButtonWidget(10, 20, 75, 20,
            SmartMatchingMode.class, this::getMatchingMode, this::setMatchingMode)
            .setTooltipHoverString("cover.smart_item_filter.matching_mode.description"));
    }

    @Override
    public int getTotalOccupiedHeight() {
        return 20;
    }

    @Override
    public boolean showGlobalTransferLimitSlider() {
        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound tagCompound) {
        tagCompound.setInteger("FilterMode", filteringMode.ordinal());
        tagCompound.setInteger("MatchingMode", matchingMode.ordinal());
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        this.filteringMode = SmartFilteringMode.values()[tagCompound.getInteger("FilterMode")];
        if (tagCompound.hasKey("MatchingMode")) {
            this.matchingMode = SmartMatchingMode.values()[tagCompound.getInteger("MatchingMode")];
        }
    }

    private class ItemAndMetadataAndStackSize {
        public final ItemAndMetadata itemAndMetadata;
        public final int transferStackSize;

        public ItemAndMetadataAndStackSize(ItemAndMetadata itemAndMetadata, int transferStackSize) {
            this.itemAndMetadata = itemAndMetadata;
            this.transferStackSize = transferStackSize;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemAndMetadataAndStackSize)) return false;
            ItemAndMetadataAndStackSize that = (ItemAndMetadataAndStackSize) o;
            return itemAndMetadata.equals(that.itemAndMetadata);
        }

        @Override
        public int hashCode() {
            return itemAndMetadata.hashCode();
        }
    }

    public enum SmartFilteringMode implements IStringSerializable {
        ELECTROLYZER("cover.smart_item_filter.filtering_mode.electrolyzer", RecipeMaps.ELECTROLYZER_RECIPES),
        CENTRIFUGE("cover.smart_item_filter.filtering_mode.centrifuge", RecipeMaps.CENTRIFUGE_RECIPES),
        SIFTER("cover.smart_item_filter.filtering_mode.sifter", RecipeMaps.SIFTER_RECIPES);

        private final Map<ItemAndMetadata, Integer> transferStackSizesCache = new HashMap<>();
        public final String localeName;
        public final RecipeMap<?> recipeMap;

        SmartFilteringMode(String localeName, RecipeMap<?> recipeMap) {
            this.localeName = localeName;
            this.recipeMap = recipeMap;
        }

        @Override
        public String getName() {
            return localeName;
        }
    }

    public enum SmartMatchingMode implements IStringSerializable {

        DEFAULT("cover.smart_item_filter.matching_mode.default", MatchingMode.DEFAULT),
        IGNORE_FLUID("cover.smart_item_filter.matching_mode.ignore_fluid", MatchingMode.IGNORE_FLUIDS);

        public final String localeName;
        public final MatchingMode matchingMode;

        SmartMatchingMode(String localeName, MatchingMode matchingMode) {
            this.localeName = localeName;
            this.matchingMode = matchingMode;
        }

        @Override
        public String getName() {
            return localeName;
        }

    }

}

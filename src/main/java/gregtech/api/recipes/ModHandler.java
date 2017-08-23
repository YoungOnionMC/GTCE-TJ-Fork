package gregtech.api.recipes;

import gregtech.GT_Mod;
import gregtech.api.ConfigCategories;
import gregtech.api.GregTech_API;
import gregtech.api.enchants.EnchantmentData;
import gregtech.api.items.IDamagableItem;
import gregtech.api.items.OreDictNames;
import gregtech.api.items.ToolDictNames;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.unification.OreDictionaryUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.ItemMaterialInfo;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.api.util.GTLog;
import gregtech.api.util.GT_Utility;
import gregtech.common.items.MetaItems;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import ic2.api.item.ISpecialElectricItem;
import ic2.api.reactor.IReactorComponent;
import ic2.api.recipe.IMachineRecipeManager;
import ic2.api.recipe.Recipes;
import ic2.core.block.state.IIdProvider;
import ic2.core.item.type.CraftingItemType;
import ic2.core.ref.BlockName;
import ic2.core.ref.FluidName;
import ic2.core.ref.ItemName;
import ic2.core.ref.TeBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import java.util.*;

import static gregtech.api.GT_Values.*;

public class ModHandler {
    public static final List<IRecipe> SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST = new ArrayList<>(1000);

    private static final List<IRecipe> ALL_RECIPE_LIST = /*Collections.synchronizedList(*/new ArrayList<>(5000)/*)*/;

    public static Collection<String> nativeRecipeClasses = new HashSet<>(), specialRecipeClasses = new HashSet<>();

    static {
        nativeRecipeClasses.add(ShapedRecipes.class.getName());
        nativeRecipeClasses.add(ShapedOreRecipe.class.getName());
        nativeRecipeClasses.add(GTShapedRecipe.class.getName());
        nativeRecipeClasses.add(ShapelessRecipes.class.getName());
        nativeRecipeClasses.add(ShapelessOreRecipe.class.getName());
        nativeRecipeClasses.add(GTShapelessRecipe.class.getName());
        nativeRecipeClasses.add(ic2.core.recipe.AdvRecipe.class.getName());
        nativeRecipeClasses.add(ic2.core.recipe.AdvShapelessRecipe.class.getName());
        nativeRecipeClasses.add("appeng.recipes.game.ShapedRecipe");
        nativeRecipeClasses.add("appeng.recipes.game.ShapelessRecipe");
        nativeRecipeClasses.add("forestry.core.utils.ShapedRecipeCustom");

        // Recipe Classes, which should never be removed.
        specialRecipeClasses.add(net.minecraft.item.crafting.RecipeFireworks.class.getName());
        specialRecipeClasses.add(net.minecraft.item.crafting.RecipesArmorDyes.class.getName());
        specialRecipeClasses.add(net.minecraft.item.crafting.RecipeBookCloning.class.getName());
        specialRecipeClasses.add(net.minecraft.item.crafting.RecipesMapCloning.class.getName());
        specialRecipeClasses.add(net.minecraft.item.crafting.RecipesMapExtending.class.getName());
        specialRecipeClasses.add("jds.bibliocraft.BiblioSpecialRecipes");
        specialRecipeClasses.add("dan200.qcraft.shared.EntangledQBlockRecipe");
        specialRecipeClasses.add("dan200.qcraft.shared.EntangledQuantumComputerRecipe");
        specialRecipeClasses.add("dan200.qcraft.shared.QBlockRecipe");
        specialRecipeClasses.add("appeng.recipes.game.FacadeRecipe");
        specialRecipeClasses.add("appeng.recipes.game.DisassembleRecipe");
        specialRecipeClasses.add("mods.railcraft.common.carts.LocomotivePaintingRecipe");
        specialRecipeClasses.add("mods.railcraft.common.util.crafting.RotorRepairRecipe");
        specialRecipeClasses.add("mods.railcraft.common.util.crafting.RoutingTableCopyRecipe");
        specialRecipeClasses.add("mods.railcraft.common.util.crafting.RoutingTicketCopyRecipe");
        specialRecipeClasses.add("mods.railcraft.common.util.crafting.TankCartFilterRecipe");
        specialRecipeClasses.add("mods.railcraft.common.emblems.LocomotiveEmblemRecipe");
        specialRecipeClasses.add("mods.railcraft.common.emblems.EmblemPostColorRecipe");
        specialRecipeClasses.add("mods.railcraft.common.emblems.EmblemPostEmblemRecipe");
        specialRecipeClasses.add("mods.immibis.redlogic.interaction.RecipeDyeLumarButton");
        specialRecipeClasses.add("twilightforest.item.TFMapCloningRecipe");
        specialRecipeClasses.add("forestry.lepidopterology.MatingRecipe");
        specialRecipeClasses.add("micdoodle8.mods.galacticraft.planets.asteroids.recipe.CanisterRecipes");
        specialRecipeClasses.add("shedar.mods.ic2.nuclearcontrol.StorageArrayRecipe");
    }

    /**
     * Returns if that Liquid is Water or Distilled Water
     */
    public static boolean isWater(FluidStack fluid) {
        return new FluidStack(FluidRegistry.WATER, 1).isFluidEqual(fluid)
                || new FluidStack(FluidName.distilled_water.getInstance(), 1).isFluidEqual(fluid);
    }

    /**
     * Returns a Liquid Stack with given amount of Water.
     */
    public static FluidStack getWater(int amount) {
        return new FluidStack(FluidRegistry.WATER, amount);
    }

    /**
     * Returns a Liquid Stack with given amount of distilled Water.
     */
    public static FluidStack getDistilledWater(int amount) {
        return new FluidStack(FluidName.distilled_water.getInstance(), amount);
    }

    /**
     * Returns if that Liquid is Lava
     */
    public static boolean isLava(FluidStack fluid) {
        return new FluidStack(FluidRegistry.LAVA, 0).isFluidEqual(fluid);
    }

    /**
     * Returns a Liquid Stack with given amount of Lava.
     */
    public static FluidStack getLava(int amount) {
        return new FluidStack(FluidRegistry.LAVA, amount);
    }

    /**
     * Returns if that Liquid is Steam
     */
    public static boolean isSteam(FluidStack fluid) {
        return getSteam(1).isFluidEqual(fluid);
    }

    /**
     * Returns a Liquid Stack with given amount of Steam.
     */
    public static FluidStack getSteam(int amount) {
        return Materials.Steam.getFluid(amount);
    }

    /**
     * Returns if that Liquid is Milk
     */
    public static boolean isMilk(Fluid fluid) {
        return getMilk(1).getFluid() == fluid;
    }

    /**
     * Returns a Liquid Stack with given amount of Milk.
     */
    public static FluidStack getMilk(int amount) {
        return FluidRegistry.getFluidStack("milk", amount);
    }

//    /**
//     * @return the Value of this Stack, when burning inside a Furnace (200 = 1 Burn Process = 500 EU, max = 32767 (that is 81917.5 EU)).
//     */
//    public static int getFuelValue(ItemStack stack) {
//        return TileEntityFurnace.getItemBurnTime(stack);
//    }
//
//    /**
//     * @param value Fuel value in EU
//     */
//    public static ItemStack getFuelCan(int value) {
//        if (value < 5) return ItemList.IC2_Fuel_Can_Empty.get(1);
//        ItemStack fuelCanStack = ItemList.IC2_Fuel_Can_Filled.get(1);
//        if (fuelCanStack == null) return null;
//        NBTTagCompound tagCompound = new NBTTagCompound();
//        tagCompound.setInteger("value", value / 5);
//        fuelCanStack.setTagCompound(tagCompound);
//        return fuelCanStack;
//    }
//
//    /**
//     * @param fuelCan the Item you want to check
//     * @return the exact Value in EU the Fuel Can is worth if its even a Fuel Can.
//     */
//    public static int getFuelCanValue(ItemStack fuelCan) {
//        if (!GT_Utility.isStackValid(fuelCan) || !ItemList.IC2_Fuel_Can_Filled.isStackEqual(fuelCan, false, true))
//            return 0;
//        NBTTagCompound tag = fuelCan.getTagCompound();
//        return tag == null ? 0 : tag.getInteger("value") * 5;
//    }

    /**
     * Gets an Item from mods
     */
    @Nullable
    public static ItemStack getModItem(String modID, String itemName, int amount) {
        return getModItem(modID, itemName, amount, 0);
    }

    /**
     * Gets an Item from mods, with metadata specified
     */
    @Nullable
    public static ItemStack getModItem(String modID, String itemName, int amount, int meta) {
        return GameRegistry.makeItemStack(modID + ":" + itemName, meta, amount, null);
    }

    ///////////////////////////////////////////////////
    //        Furnace Smelting Recipe Helpers        //
    ///////////////////////////////////////////////////

    /**
     * Just simple Furnace smelting
     */
    public static void addSmeltingRecipe(ItemStack input, ItemStack output) {
        output = OreDictionaryUnifier.getUnificated(output);

        Validate.notNull(input, "Input cannot be null");
        Validate.notNull(output, "Output cannot be null");
        Validate.isTrue(GT_Utility.getContainerItem(input, false) != null, "Input item cannot have container item");

//        if (!GregTech_API.sRecipeFile.get(ConfigCategories.Machines.smelting, input, true)) return;

        GameRegistry.addSmelting(input, output.copy(), 0.0F);
    }

    /**
     * Adds to Furnace AND Alloysmelter AND Induction Smelter
     */
    public static void addSmeltingAndAlloySmeltingRecipe(ItemStack input, ItemStack output, boolean hidden) {
        Validate.notNull(input, "Input cannot be null");
        Validate.notNull(output, "Output cannot be null");

        if (input.stackSize == 1) {
            addSmeltingRecipe(input, output);
        }

        RecipeBuilder.NotConsumableInputRecipeBuilder recipeBuilder =
                RecipeMap.ALLOY_SMELTER_RECIPES.recipeBuilder()
                        .inputs(input)
                        .outputs(output)
                        .duration(130)
                        .EUt(3);

        OrePrefix prefix = OreDictionaryUnifier.getPrefix(output);
        if (prefix != null) {
            switch (prefix) {
                case ingot:
                    recipeBuilder.notConsumable(MetaItems.SHAPE_MOLD_INGOT);
                    break;
                case block:
                    recipeBuilder.notConsumable(MetaItems.SHAPE_MOLD_BLOCK);
                    break;
                case nugget:
                    recipeBuilder.notConsumable(MetaItems.SHAPE_MOLD_NUGGET);
                    break;
            }

            if (hidden) {
                recipeBuilder.hidden();
            }
            recipeBuilder.buildAndRegister();
        }

        if (GT_Mod.gregtechproxy.mTEMachineRecipes) {
            ThermalExpansion.addInductionSmelterRecipe(input, null, output, null, output.stackSize * 1600, 0);
        }
    }

    /**
     * IC2-Extractor Recipe. Overrides old Recipe
     */
    public static void addExtractionRecipe(ItemStack input, ItemStack output) {
        output = OreDictionaryUnifier.getUnificated(output);

        Validate.notNull(input, "Input cannot be null");
        Validate.notNull(output, "Output cannot be null");

        GT_Utility.removeSimpleIC2MachineRecipe(input, Recipes.extractor.getRecipes(), null);
//        if (!GregTech_API.sRecipeFile.get(ConfigCategories.Machines.extractor, input, true)) return;
        GT_Utility.addSimpleIC2MachineRecipe(input, Recipes.extractor, null, output);
    }

    ///////////////////////////////////////////////////
    //              Crafting Recipe Helpers          //
    ///////////////////////////////////////////////////

    /**
     * Shapeless Crafting Recipes. Deletes conflicting Recipes too.
     */
    public static void addCraftingRecipe(ItemStack result, EnchantmentData[] enchantmentsAdded, Object... recipe) {
        addCraftingRecipe(result, enchantmentsAdded, false, true, false, false, false, false, false, false, false, false, false, false, recipe);
    }

    /**
     * Regular Crafting Recipes. Deletes conflicting Recipes too.
     * <p/>
     * You can insert instances of IItemContainer into the Recipe Input Array directly without having to call "get(1)" on them.
     * <p/>
     * Enums are automatically getting their "name()"-Method called in order to deliver an OreDict String.
     * <p/>
     * Lowercase Letters are reserved for Tools. They are as follows:
     * <p/>
     * 'b' ToolDictNames.craftingToolBlade
     * 'c' ToolDictNames.craftingToolCrowbar,
     * 'd' ToolDictNames.craftingToolScrewdriver,
     * 'f' ToolDictNames.craftingToolFile,
     * 'h' ToolDictNames.craftingToolHardHammer,
     * 'i' ToolDictNames.craftingToolSolderingIron,
     * 'j' ToolDictNames.craftingToolSolderingMetal,
     * 'k' ToolDictNames.craftingToolKnive
     * 'm' ToolDictNames.craftingToolMortar,
     * 'p' ToolDictNames.craftingToolDrawplate,
     * 'r' ToolDictNames.craftingToolSoftHammer,
     * 's' ToolDictNames.craftingToolSaw,
     * 'w' ToolDictNames.craftingToolWrench,
     * 'x' ToolDictNames.craftingToolWireCutter,
     */
    public static void addCraftingRecipe(ItemStack result, Object... recipe) {
        addCraftingRecipe(result, 0, recipe);
    }

    /**
     * Regular Crafting Recipes. Deletes conflicting Recipes too.
     * <p/>
     * You can insert instances of IItemContainer into the Recipe Input Array directly without having to call "get(1)" on them.
     * <p/>
     * Enums are automatically getting their "name()"-Method called in order to deliver an OreDict String.
     * <p/>
     * Lowercase Letters are reserved for Tools. They are as follows:
     * <p/>
     * 'b' ToolDictNames.craftingToolBlade
     * 'c' ToolDictNames.craftingToolCrowbar,
     * 'd' ToolDictNames.craftingToolScrewdriver,
     * 'f' ToolDictNames.craftingToolFile,
     * 'h' ToolDictNames.craftingToolHardHammer,
     * 'i' ToolDictNames.craftingToolSolderingIron,
     * 'j' ToolDictNames.craftingToolSolderingMetal,
     * 'k' ToolDictNames.craftingToolKnive
     * 'm' ToolDictNames.craftingToolMortar,
     * 'p' ToolDictNames.craftingToolDrawplate,
     * 'r' ToolDictNames.craftingToolSoftHammer,
     * 's' ToolDictNames.craftingToolSaw,
     * 'w' ToolDictNames.craftingToolWrench,
     * 'x' ToolDictNames.craftingToolWireCutter,
     */
    public static void addCraftingRecipe(ItemStack result, long bitMask, Object... recipe) {
        addCraftingRecipe(result,
                new EnchantmentData[0],
                (bitMask & RecipeBits.MIRRORED) != 0,
                (bitMask & RecipeBits.BUFFERED) != 0,
                (bitMask & RecipeBits.KEEPNBT) != 0,
                (bitMask & RecipeBits.DISMANTLEABLE) != 0,
                (bitMask & RecipeBits.NOT_REMOVABLE) == 0,
                (bitMask & RecipeBits.REVERSIBLE) != 0,
                (bitMask & RecipeBits.DELETE_ALL_OTHER_RECIPES) != 0,
                (bitMask & RecipeBits.DELETE_ALL_OTHER_RECIPES_IF_SAME_NBT) != 0,
                (bitMask & RecipeBits.DELETE_ALL_OTHER_SHAPED_RECIPES) != 0,
                (bitMask & RecipeBits.DELETE_ALL_OTHER_NATIVE_RECIPES) != 0,
                (bitMask & RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS) == 0,
                (bitMask & RecipeBits.ONLY_ADD_IF_THERE_IS_ANOTHER_RECIPE_FOR_IT) != 0,
                recipe);
    }

    /**
     * Internal realisation of the Crafting Recipe adding Process.
     */
    private static void addCraftingRecipe(ItemStack result, EnchantmentData[] enchantmentsAdded,
                                          boolean mirrored, boolean buffered, boolean keepNBT, boolean dismantleable,
                                          boolean removable, boolean reversible, boolean removeAllOthersWithSameOutput,
                                          boolean removeAllOthersWithSameOutputIfTheyHaveSameNBT, boolean removeAllOtherShapedsWithSameOutput,
                                          boolean removeAllOtherNativeRecipes, boolean checkForCollisions,
                                          boolean onlyAddIfThereIsAnyRecipeOutputtingThis,
                                          Object[] recipe) {
        result = OreDictionaryUnifier.getUnificated(result);
        Validate.notNull(result, "Result cannot be null");
        Validate.notNull(recipe, "Recipe cannot be null");
        Validate.isTrue(recipe.length > 0, "Recipe cannot be empty");
        Validate.noNullElements(recipe, "Recipe cannot contain null elements");

        if (Items.FEATHER.getDamage(result) == W) Items.FEATHER.setDamage(result, 0);

        boolean thereWasARecipe = false;

        for (byte i = 0; i < recipe.length; i++) {
            if (recipe[i] instanceof MetaItem.MetaValueItem) {
                recipe[i] = ((MetaItem<?>.MetaValueItem) recipe[i]).getStackForm();
            } else if (recipe[i] instanceof Enum) {
                recipe[i] = ((Enum<?>) recipe[i]).name();
            } else if (recipe[i] instanceof UnificationEntry ) {
                recipe[i] = recipe[i].toString();
            } else if (!(recipe[i] instanceof ItemStack || recipe[i] instanceof Item || recipe[i] instanceof Block
                    || recipe[i] instanceof String || recipe[i] instanceof Character)) {
                recipe[i] = recipe[i].toString();
            }
        }

        StringBuilder shape = new StringBuilder();
        int idx = 0;
        if (recipe[idx] instanceof Boolean) {
            throw new IllegalArgumentException();
        }

        ArrayList<Object> recipeList = new ArrayList<>(Arrays.asList(recipe));

        while (recipe[idx] instanceof String) {
            StringBuilder s = new StringBuilder((String) recipe[idx++]);
            shape.append(s);
            while (s.length() < 3) s.append(" ");
            if (s.length() > 3) throw new IllegalArgumentException();

            for (char c : s.toString().toCharArray()) {
                switch (c) {
                    case 'b':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolBlade.name());
                        break;
                    case 'c':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolCrowbar.name());
                        break;
                    case 'd':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolScrewdriver.name());
                        break;
                    case 'f':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolFile.name());
                        break;
                    case 'h':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolHardHammer.name());
                        break;
                    case 'i':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolSolderingIron.name());
                        break;
                    case 'j':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolSolderingMetal.name());
                        break;
                    case 'k':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolKnife.name());
                        break;
                    case 'm':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolMortar.name());
                        break;
                    case 'p':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolDrawplate.name());
                        break;
                    case 'r':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolSoftHammer.name());
                        break;
                    case 's':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolSaw.name());
                        break;
                    case 'w':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolWrench.name());
                        break;
                    case 'x':
                        recipeList.add(c);
                        recipeList.add(ToolDictNames.craftingToolWireCutter.name());
                        break;
                }
            }
        }

        recipe = recipeList.toArray();

        if (recipe[idx] instanceof Boolean) {
            idx++;
        }
        /*ConcurrentHash*/
        Map<Character, ItemStack> itemStackMap = new /*ConcurrentHash*/HashMap<>();
        /*ConcurrentHash*/
        Map<Character, ItemMaterialInfo> itemDataMap = new /*ConcurrentHash*/HashMap<>();
        itemStackMap.put(' ', null);

        boolean removeRecipe = true;

        for (; idx < recipe.length; idx += 2) {

            if (recipe[idx] == null || recipe[idx + 1] == null) {
                if (D1) {
                    GTLog.logger.error("WARNING: Missing Item for shaped Recipe: " + result.getDisplayName());
                    for (Object content : recipe) GTLog.logger.error(content);
                }
                return;
            }

            Character chr = (Character) recipe[idx];
            Object in = recipe[idx + 1];
            if (in instanceof ItemStack) {

                itemStackMap.put(chr, GT_Utility.copy((ItemStack) in));
                itemDataMap.put(chr, OreDictionaryUnifier.getItemData((ItemStack) in));
            } else if (in instanceof ItemMaterialInfo) {

                String string = in.toString();
                switch (string) {
                    case "plankWood":
                        itemDataMap.put(chr, new ItemMaterialInfo(Materials.Wood, M));
                        break;
                    case "stoneNetherrack":
                        itemDataMap.put(chr, new ItemMaterialInfo(Materials.Netherrack, M));
                        break;
                    case "stoneObsidian":
                        itemDataMap.put(chr, new ItemMaterialInfo(Materials.Obsidian, M));
                        break;
                    case "stoneEndstone":
                        itemDataMap.put(chr, new ItemMaterialInfo(Materials.Endstone, M));
                        break;
                    default:
                        itemDataMap.put(chr, (ItemMaterialInfo) in);
                        break;
                }

                ItemStack stack = OreDictionaryUnifier.getFirstOre(in, 1);
                if (stack == null) removeRecipe = false;
                else itemStackMap.put(chr, stack);
                in = recipe[idx + 1] = in.toString();
            } else if (in instanceof String) {

                if (in.equals(OreDictNames.craftingChest.toString()))
                    itemDataMap.put(chr, new ItemMaterialInfo(Materials.Wood, M * 8));
                else if (in.equals(OreDictNames.craftingBook.toString()))
                    itemDataMap.put(chr, new ItemMaterialInfo(Materials.Paper, M * 3));
                else if (in.equals(OreDictNames.craftingPiston.toString()))
                    itemDataMap.put(chr, new ItemMaterialInfo(Materials.Stone, M * 4, Materials.Wood, M * 3));
                else if (in.equals(OreDictNames.craftingFurnace.toString()))
                    itemDataMap.put(chr, new ItemMaterialInfo(Materials.Stone, M * 8));
                else if (in.equals(OreDictNames.craftingIndustrialDiamond.toString()))
                    itemDataMap.put(chr, new ItemMaterialInfo(Materials.Diamond, M));
                else if (in.equals(OreDictNames.craftingAnvil.toString()))
                    itemDataMap.put(chr, new ItemMaterialInfo(Materials.Iron, M * 10));

                ItemStack stack = OreDictionaryUnifier.getFirstOre(in, 1);
                if (stack == null) removeRecipe = false;
                else itemStackMap.put(chr, stack);
            } else {
                throw new IllegalArgumentException();
            }
        }

        if (reversible && result != null) {
            ItemMaterialInfo[] itemData = new ItemMaterialInfo[9];

            int x = -1;
            boolean hasNulls = false;
            for (char chr : shape.toString().toCharArray()) {
                ItemMaterialInfo data = itemDataMap.get(chr);
                if (data == null) {
                    hasNulls = true;
                    break;
                }
                itemData[++x] = data;
            }

            if (!hasNulls) {
                OreDictionaryUnifier.addItemData(result, new ItemMaterialInfo(itemData));
            }
        }

        if (checkForCollisions && removeRecipe) {
            ItemStack[] stacks = new ItemStack[9];

            int x = -1;
            for (char chr : shape.toString().toCharArray()) {
                stacks[++x] = itemStackMap.get(chr);

                if (stacks[x] != null && Items.FEATHER.getDamage(stacks[x]) == W)
                    Items.FEATHER.setDamage(stacks[x], 0);
            }

            thereWasARecipe |= removeRecipe(stacks) != null;
        }

        if (removeAllOthersWithSameOutput || removeAllOthersWithSameOutputIfTheyHaveSameNBT || removeAllOtherShapedsWithSameOutput || removeAllOtherNativeRecipes)
            thereWasARecipe |= removeRecipeByOutput(result, !removeAllOthersWithSameOutputIfTheyHaveSameNBT, removeAllOtherShapedsWithSameOutput, removeAllOtherNativeRecipes);

        if (onlyAddIfThereIsAnyRecipeOutputtingThis && !thereWasARecipe) {
            ArrayList<IRecipe> list = (ArrayList<IRecipe>) CraftingManager.getInstance().getRecipeList();

            int listSize = list.size();
            for (int i = 0; i < listSize && !thereWasARecipe; i++) {
                IRecipe tmpRecipe = list.get(i);

                if (specialRecipeClasses.contains(tmpRecipe.getClass().getName())) continue;
                if (GT_Utility.areStacksEqual(OreDictionaryUnifier.get(tmpRecipe.getRecipeOutput()), result, true)) {
                    list.remove(i--);
                    listSize = list.size();
                    thereWasARecipe = true;
                }
            }
        }

        if (Items.FEATHER.getDamage(result) == W || Items.FEATHER.getDamage(result) < 0)
            Items.FEATHER.setDamage(result, 0);

        if (thereWasARecipe || !onlyAddIfThereIsAnyRecipeOutputtingThis) {
            GameRegistry.addRecipe(new GTShapedRecipe(GT_Utility.copy(result), dismantleable, removable, keepNBT, enchantmentsAdded, enchantmentLevelsAdded, recipe).setMirrored(mirrored));
        }
    }

    /**
     * Shapeless Crafting Recipes. Deletes conflicting Recipes too.
     */
    public static void addShapelessEnchantingRecipe(ItemStack result, EnchantmentData[] enchantmentsAdded, Object[] recipe) {
        addShapelessCraftingRecipe(result, enchantmentsAdded, true, false, false, false, recipe);
    }

    /**
     * Shapeless Crafting Recipes. Deletes conflicting Recipes too.
     */
    public static void addShapelessCraftingRecipe(ItemStack result, Object... recipe) {
        addShapelessCraftingRecipe(result, RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS, recipe);
    }

    /**
     * Shapeless Crafting Recipes. Deletes conflicting Recipes too.
     */
    public static void addShapelessCraftingRecipe(ItemStack result, long bitMask, Object... recipe) {
        addShapelessCraftingRecipe(result,
                new EnchantmentData[0],
                (bitMask & RecipeBits.KEEPNBT) != 0,
                (bitMask & RecipeBits.DISMANTLEABLE) != 0,
                (bitMask & RecipeBits.NOT_REMOVABLE) == 0,
                recipe);
    }

    /**
     * Shapeless Crafting Recipes. Deletes conflicting Recipes too.
     */
    private static void addShapelessCraftingRecipe(ItemStack result, EnchantmentData[] enchantmentsAdded,
                                                   boolean keepNBT, boolean dismantleable, boolean removable,
                                                   Object[] recipe) {
        result = OreDictionaryUnifier.getUnificated(result);
        Validate.notNull(result, "Result cannot be null");
        Validate.notNull(recipe, "Recipe cannot be null");
        Validate.isTrue(recipe.length > 0, "Recipe cannot be empty");

        for (byte i = 0; i < recipe.length; i++) {
            if (recipe[i] instanceof IItemContainer) {
                recipe[i] = ((IItemContainer) recipe[i]).get(1);
            } else if (recipe[i] instanceof Enum) {
                recipe[i] = ((Enum<?>) recipe[i]).name();
            } else if (!(recipe[i] == null ||
                    recipe[i] instanceof ItemStack ||
                    recipe[i] instanceof String ||
                    recipe[i] instanceof Character)) {
                recipe[i] = recipe[i].toString();
            }
        }


        ItemStack[] recipeStacks = new ItemStack[9];
        int i = 0;
        for (Object object : recipe) {
            if (object == null) {
                if (D1)
                    GTLog.logger.error("WARNING: Missing Item for shapeless Recipe: " + result.getDisplayName()););
                for (Object content : recipe) GTLog.logger.error((content);
                return;
            }

            if (object instanceof ItemStack) {
                recipeStacks[i] = (ItemStack) object;
            } else if (object instanceof String) {
                recipeStacks[i] = OreDictionaryUnifier.getFirstOre(object, 1);
                if (recipeStacks[i] == null) break;
            }/* else if (object instanceof Boolean) {
                //
            } else {
                throw new IllegalArgumentException();
            }*/
            i++;
        }
        removeRecipe(recipeStacks);


        if (Items.FEATHER.getDamage(result) == W || Items.FEATHER.getDamage(result) < 0)
            Items.FEATHER.setDamage(result, 0);

        GameRegistry.addRecipe(new GTShapelessRecipe(GT_Utility.copy(result), dismantleable, removable, keepNBT, enchantmentsAdded, enchantmentLevelsAdded, recipe));
    }

    ///////////////////////////////////////////////////
    //              Recipe Remove Helpers            //
    ///////////////////////////////////////////////////

    /**
     * Removes a Smelting Recipe
     */
    public static boolean removeFurnaceSmelting(ItemStack input) {
        if (input != null) {
            for (ItemStack stack : FurnaceRecipes.instance().getSmeltingList().keySet()) {
                if (GT_Utility.isStackValid(stack) && GT_Utility.areStacksEqual(input, stack, true)) {
                    FurnaceRecipes.instance().getSmeltingList().remove(stack);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Removes a Crafting Recipe and gives you the former output of it.
     *
     * @param recipe The content of the Crafting Grid as ItemStackArray with length 9
     * @return the output of the old Recipe or null if there was nothing.
     */
    public static ItemStack removeRecipe(ItemStack... recipe) {
        if (recipe == null) return null;

        boolean temp = false;
        for (ItemStack stack : recipe) {
            if (stack != null) {
                temp = true;
                break;
            }
        }

        if (!temp) return null;

        ItemStack returnStack = null;
        InventoryCrafting craftingGrid = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer var1) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < recipe.length && i < 9; i++) craftingGrid.setInventorySlotContents(i, recipe[i]);

        ArrayList<IRecipe> list = (ArrayList<IRecipe>) CraftingManager.getInstance().getRecipeList();
        int listSize = list.size();

        for (int i = 0; i < listSize; i++) {
            for (; i < listSize; i++) {
                if ((!(list.get(i) instanceof IRemovableRecipe) || ((IRemovableRecipe) list.get(i)).isRemovable()) && list.get(i).matches(craftingGrid, DW)) {
                    returnStack = list.get(i).getCraftingResult(craftingGrid);
                    if (returnStack != null) list.remove(i--);
                    listSize = list.size();
                }
            }
        }
        return returnStack;
    }

    public static boolean removeRecipeByOutput(ItemStack output) {
        return removeRecipeByOutput(output, true, false, false);
    }

    /**
     * Removes a Crafting Recipe.
     *
     * @param output The output of the Recipe.
     * @return if it has removed at least one Recipe.
     */
    public static boolean removeRecipeByOutput(ItemStack output, boolean ignoreNBT, boolean notRemoveShapelessRecipes, boolean onlyRemoveNativeHandlers) {
        if (output == null) return false;

        boolean removed = false;

        List<IRecipe> list = CraftingManager.getInstance().getRecipeList();
        output = OreDictionaryUnifier.get(output);
        int listSize = list.size();

        for (int i = 0; i < listSize; i++) {
            IRecipe recipe = list.get(i);

            if (notRemoveShapelessRecipes && (recipe instanceof ShapelessRecipes || recipe instanceof ShapelessOreRecipe))
                continue;

            if (onlyRemoveNativeHandlers) {
                if (!nativeRecipeClasses.contains(recipe.getClass().getName())) continue;
            } else {
                if (specialRecipeClasses.contains(recipe.getClass().getName())) continue;
            }

            ItemStack stack = recipe.getRecipeOutput();
            if ((!(recipe instanceof IRemovableRecipe) || ((IRemovableRecipe) recipe).isRemovable()) &&
                    GT_Utility.areStacksEqual(OreDictionaryUnifier.get(stack), output, ignoreNBT)) {
                list.remove(i--);
                listSize = list.size();
                removed = true;
            }
        }
        return removed;
    }

    ///////////////////////////////////////////////////
    //            Get Recipe Output Helpers          //
    ///////////////////////////////////////////////////

    /**
     * Checks all Crafting Handlers for Recipe Output
     * Used for the Autocrafting Table
     */
    public static ItemStack getAllRecipeOutput(World world, ItemStack... recipe) {
        if (recipe == null || recipe.length == 0) return null;

        if (world == null) world = DW;

        boolean temp = false;
        for (ItemStack stack : recipe) {
            if (stack != null) {
                temp = true;
                break;
            }
        }
        if (!temp) return null;

        InventoryCrafting craftingGrid = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer var1) {
                return false;
            }
        }, 3, 3);

        for (int i = 0; i < 9 && i < recipe.length; i++) craftingGrid.setInventorySlotContents(i, recipe[i]);

        List<IRecipe> list = CraftingManager.getInstance().getRecipeList();

        synchronized (ALL_RECIPE_LIST) {
            if (ALL_RECIPE_LIST.size() != list.size()) {
                ALL_RECIPE_LIST.clear();
                ALL_RECIPE_LIST.addAll(list);
            }

            for (int i = 0, j = ALL_RECIPE_LIST.size(); i < j; i++) {
                IRecipe tmpRecipe = ALL_RECIPE_LIST.get(i);

                if (tmpRecipe.matches(craftingGrid, world)) {
                    if (i > 10) {
                        ALL_RECIPE_LIST.remove(i);
                        ALL_RECIPE_LIST.add(i - 10, tmpRecipe);
                    }
                    return tmpRecipe.getCraftingResult(craftingGrid);
                }
            }
        }

        int index = 0;
        ItemStack stack1 = null, stack2 = null;
        for (int i = 0, j = craftingGrid.getSizeInventory(); i < j; i++) {
            ItemStack stack = craftingGrid.getStackInSlot(i);
            if (stack != null) {
                if (index == 0) stack1 = stack;
                if (index == 1) stack2 = stack;
                index++;
            }
        }

        if (index == 2) {
            Validate.isTrue(stack1 != null && stack2 != null);
            if (stack1.getItem() == stack2.getItem() && stack1.stackSize == 1 && stack2.stackSize == 1 && stack1.getItem().isRepairable()) {
                int newDamage = stack1.getMaxDamage() + stack1.getItemDamage() - stack2.getItemDamage() + stack1.getMaxDamage() / 20;
                return new ItemStack(stack1.getItem(), 1, newDamage < 0 ? 0 : newDamage);
            }
        }

        return null;
    }

    /**
     * Gives you a copy of the Output from a Crafting Recipe
     * Used for Recipe Detection.
     */
    public static ItemStack getRecipeOutput(ItemStack... recipe) {
        return getRecipeOutput(false, recipe);
    }

    /**
     * Gives you a copy of the Output from a Crafting Recipe
     * Used for Recipe Detection.
     */
    public static ItemStack getRecipeOutput(boolean uncopiedStack, ItemStack... recipe) {
        if (recipe == null) return null;

        boolean temp = false;
        for (ItemStack stack : recipe) {
            if (stack != null) {
                temp = true;
                break;
            }
        }
        if (!temp) return null;

        InventoryCrafting craftingGrid = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer var1) {
                return false;
            }
        }, 3, 3);

        for (int i = 0; i < 9 && i < recipe.length; i++) craftingGrid.setInventorySlotContents(i, recipe[i]);

        ArrayList<IRecipe> list = (ArrayList<IRecipe>) CraftingManager.getInstance().getRecipeList();
        for (IRecipe recipe1 : list) {

            temp = recipe1.matches(craftingGrid, DW);

            if (temp) {
                ItemStack output = uncopiedStack ? recipe1.getRecipeOutput() : recipe1.getCraftingResult(craftingGrid);
                if (output == null || output.stackSize <= 0) {
                    // Seriously, who would ever do that shit?
                    if (!GregTech_API.sPostloadFinished)
                        throw new GT_ItsNotMyFaultException("Seems another Mod added a Crafting Recipe with null Output. Tell the Developer of said Mod to fix that.");
                } else {
                    if (uncopiedStack) return output;
                    return GT_Utility.copy(output);
                }
            }
        }
        return null;
    }

    /**
     * Gives you a list of the Outputs from a Crafting Recipe
     * If you have multiple Mods, which add Bronze Armor for example
     * This also removes old Recipes from the List.
     */
    public static List<ItemStack> getVanillyToolRecipeOutputs(ItemStack... recipeIn) {
        if (!GregTech_API.sPostloadStarted || GregTech_API.sPostloadFinished)
            SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.clear();

        if (SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.isEmpty()) {
            for (IRecipe recipe : CraftingManager.getInstance().getRecipeList()) {
                ItemStack stack = recipe.getRecipeOutput();

                if (GT_Utility.isStackValid(stack) &&
                        stack.getMaxStackSize() == 1 &&
                        stack.getMaxDamage() > 0 &&
                        !(stack.getItem() instanceof ItemBlock) &&
                        !(stack.getItem() instanceof IReactorComponent) &&
                        !isElectricItem(stack) &&
                        !GT_Utility.isStackInList(stack, nonReplaceableItems)) {

                    if (!(recipe instanceof ShapelessRecipes || recipe instanceof ShapelessOreRecipe)) {
                        if (recipe instanceof ShapedOreRecipe) {

                            boolean temp = true;
                            for (Object input : ((ShapedOreRecipe) recipe).getInput()) {
                                if (input != null) {
                                    if (input instanceof ItemStack &&
                                            (((ItemStack) input).getItem() == null ||
                                                    ((ItemStack) input).getMaxStackSize() < 2 ||
                                                    ((ItemStack) input).getMaxDamage() > 0 ||
                                                    ((ItemStack) input).getItem() instanceof ItemBlock)) {
                                        temp = false;
                                        break;
                                    }
                                    if (input instanceof List && ((List<?>) input).isEmpty()) {
                                        temp = false;
                                        break;
                                    }
                                }
                            }
                            if (temp) SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.add(recipe);

                        } else if (recipe instanceof ShapedRecipes) {

                            boolean temp = true;
                            for (ItemStack itemStack : ((ShapedRecipes) recipe).recipeItems) {
                                if (itemStack != null &&
                                        (itemStack.getItem() == null ||
                                                itemStack.getMaxStackSize() < 2 ||
                                                itemStack.getMaxDamage() > 0 ||
                                                itemStack.getItem() instanceof ItemBlock)) {
                                    temp = false;
                                    break;
                                }
                            }
                            if (temp) SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.add(recipe);
                        } else {
                            SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.add(recipe);
                        }
                    }
                }
            }
            GTLog.out.println("GT_Mod: Created a List of Tool Recipes containing " + SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.size() + " Recipes for recycling." + (SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.size() > 1024 ? " Scanning all these Recipes is the reason for the startup Lag you receive right now." : ""));
        }

        List<ItemStack> outputs = getRecipeOutputs(SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST, true, recipeIn);
        if (!GregTech_API.sPostloadStarted || GregTech_API.sPostloadFinished)
            SINGLE_NON_BLOCK_DAMAGABLE_RECIPE_LIST.clear();
        return outputs;
    }

    /**
     * Gives you a list of the Outputs from a Crafting Recipe
     * If you have multiple Mods, which add Bronze Armor for example
     */
    public static List<ItemStack> getRecipeOutputs(ItemStack... recipe) {
        return getRecipeOutputs(CraftingManager.getInstance().getRecipeList(), false, recipe);
    }

    /**
     * Gives you a list of the Outputs from a Crafting Recipe
     * If you have multiple Mods, which add Bronze Armor for example
     */
    public static List<ItemStack> getRecipeOutputs(List<IRecipe> recipes, boolean deleteFromList, ItemStack... recipe) {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        if (recipe == null) return stacks;

        boolean temp = false;
        for (ItemStack stack : recipe) {
            if (stack != null) {
                temp = true;
                break;
            }
        }
        if (!temp) return stacks;

        InventoryCrafting craftingGrid = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer var1) {
                return false;
            }
        }, 3, 3);

        for (int i = 0; i < 9 && i < recipe.length; i++) craftingGrid.setInventorySlotContents(i, recipe[i]);

        for (int i = 0; i < recipes.size(); i++) {

            temp = recipes.get(i).matches(craftingGrid, DW);

            if (temp) {
                ItemStack craftingResult = recipes.get(i).getCraftingResult(craftingGrid);
                if (craftingResult == null || craftingResult.stackSize <= 0) {
                    // Seriously, who would ever do that shit?
                    if (!GregTech_API.sPostloadFinished)
                        throw new GT_ItsNotMyFaultException("Seems another Mod added a Crafting Recipe with null Output. Tell the Developer of said Mod to fix that.");
                } else {
                    stacks.add(GT_Utility.copy(craftingResult));
                    if (deleteFromList) recipes.remove(i--);
                }
            }
        }
        return stacks;
    }

    /**
     * Used in my own Furnace.
     */
    public static ItemStack getSmeltingOutput(ItemStack input, boolean removeInput, ItemStack outputSlot) {
        if (input == null || input.stackSize < 1) return null;

        ItemStack stack = OreDictionaryUnifier.get(FurnaceRecipes.instance().getSmeltingResult(input));
        if (stack != null && (outputSlot == null || (GT_Utility.areStacksEqual(stack, outputSlot) && stack.stackSize + outputSlot.stackSize <= outputSlot.getMaxStackSize()))) {
            if (removeInput) input.stackSize--;
            return stack;
        }
        return null;
    }

    /**
     * Used in my own Machines. Decreases StackSize of the Input if wanted.
     * <p/>
     * Checks also if there is enough Space in the Output Slots.
     */
    public static ItemStack[] getMachineOutput(ItemStack input, Iterable<IMachineRecipeManager.RecipeIoContainer> recipeList, boolean removeInput, NBTTagCompound recipeMetaData, ItemStack... outputSlots) {
        if (outputSlots == null || outputSlots.length <= 0) return new ItemStack[0];
        if (input == null) return new ItemStack[outputSlots.length];

        for (IMachineRecipeManager.RecipeIoContainer entry : recipeList) {
            if (entry.input.matches(input)) {
                if (entry.input.getAmount() <= input.stackSize) {

                    ItemStack[] stackList = entry.output.items.toArray(new ItemStack[entry.output.items.size()]);
                    if (stackList.length == 0) break;

                    ItemStack[] slotList = new ItemStack[outputSlots.length];
                    if (entry.output.metadata != null) {
                        recipeMetaData.setTag("return", entry.output.metadata);
                    }

                    for (byte i = 0; i < outputSlots.length && i < stackList.length; i++) {
                        if (stackList[i] != null) {
                            if (outputSlots[i] == null || (GT_Utility.areStacksEqual(stackList[i], outputSlots[i]) && stackList[i].stackSize + outputSlots[i].stackSize <= outputSlots[i].getMaxStackSize())) {
                                slotList[i] = GT_Utility.copy(stackList[i]);
                            } else {
                                return new ItemStack[outputSlots.length];
                            }
                        }
                    }

                    if (removeInput) input.stackSize -= entry.input.getAmount();
                    return slotList;
                }
                break;
            }
        }
        return new ItemStack[outputSlots.length];
    }

    /**
     * Used in my own Recycler.
     * <p/>
     * Only produces Scrap if scrapChance == 0. scrapChance is usually the random Number I give to the Function
     * If you directly insert 0 as scrapChance then you can check if its Recycler-Blacklisted or similar
     */
    public static ItemStack getRecyclerOutput(ItemStack input, int scrapChance) {
        if (input == null || scrapChance != 0) return null;

        if (Recipes.recyclerWhitelist.isEmpty())
            return Recipes.recyclerBlacklist.contains(input) ? null : IC2.getScrap(1);
        return Recipes.recyclerWhitelist.contains(input) ? IC2.getScrap(1) : null;
    }

    ///////////////////////////////////////////////////
    //             Electric Items Helpers            //
    ///////////////////////////////////////////////////

    /**
     * Charges an Electric Item. Only if it's a valid Electric Item of course.
     * This forces the Usage of proper Voltages (so not the transfer limits defined by the Items) unless you ignore the Transfer Limit.
     * If tier is Integer.MAX_VALUE it will ignore Tier based Limitations.
     *
     * @return the actually used Energy.
     */
    public static int chargeElectricItem(ItemStack stack, int charge, int tier, boolean ignoreLimit, boolean simulate) {

        if (isElectricItem(stack)) {
            int tmpTier = ElectricItem.manager.getTier(stack);
            if (tmpTier < 0 || tmpTier == tier || tier == Integer.MAX_VALUE) {

                if (!ignoreLimit && tmpTier >= 0) {
                    charge = (int) Math.min(charge, V[Math.max(0, Math.min(V.length - 1, tmpTier))]);
                }

                if (charge > 0) {
                    int charge = (int) Math.max(0.0, ElectricItem.manager.charge(stack, charge, tmpTier, true, simulate));
                    return charge + (charge * 4 > tier ? tier : 0);
                }
            }
        }
        return 0;
    }

    /**
     * Discharges an Electric Item. Only if it's a valid Electric Item for that of course.
     * This forces the Usage of proper Voltages (so not the transfer limits defined by the Items) unless you ignore the Transfer Limit.
     * If tier is Integer.MAX_VALUE it will ignore Tier based Limitations.
     *
     * @return the Energy got from the Item.
     */
    public static double dischargeElectricItem(ItemStack stack, double charge, int tier, boolean ignoreLimit, boolean simulate, boolean ignoreDischargability) {

        if (isElectricItem(stack)) {
            int tmpTier = ElectricItem.manager.getTier(stack);
            if (tmpTier < 0 || tmpTier == tier || tier == Integer.MAX_VALUE) {

                if (!ignoreLimit && tmpTier >= 0) {
                    charge = Math.min(charge, V[Math.max(0, Math.min(V.length - 1, tmpTier))]);
                }

                if (charge > 0) {
                    double chargeTmp = Math.max(0, ElectricItem.manager.discharge(stack, charge + (charge * 4 > tier ? tier : 0), tmpTier, true, !ignoreDischargability, simulate));
                    return chargeTmp - (chargeTmp * 4 > tier ? tier : 0);
                }
            }
        }
        return 0;
    }

    /**
     * Uses an Electric Item. Only if it's a valid Electric Item for that of course.
     *
     * @return if the action was successful
     */
    public static boolean canUseElectricItem(ItemStack stack, int charge) {
        return isElectricItem(stack) && ElectricItem.manager.canUse(stack, charge);
    }

    /**
     * Uses an Electric Item. Only if it's a valid Electric Item for that of course.
     *
     * @return if the action was successful
     */
    public static boolean useElectricItem(ItemStack stack, int charge, EntityPlayer player) {
        return isElectricItem(stack) && ElectricItem.manager.use(stack, charge, player);
    }

    /**
     * Uses an Item. Tries to discharge in case of Electric Items
     */
    public static boolean damageOrDechargeItem(ItemStack stack, int damage, int decharge, EntityLivingBase player) {
        if (!GT_Utility.isStackValid(stack) || (stack.getMaxStackSize() <= 1 && stack.stackSize > 1)) return false;

        if (player != null && player instanceof EntityPlayer && ((EntityPlayer) player).capabilities.isCreativeMode) {
            return true;
        }

        if (stack.getItem() instanceof IDamagableItem) {
            return ((IDamagableItem) stack.getItem()).doDamageToItem(stack, damage);
        } else if (ModHandler.isElectricItem(stack)) {

            if (canUseElectricItem(stack, decharge)) {
                if (player != null && player instanceof EntityPlayer) {
                    return ModHandler.useElectricItem(stack, decharge, (EntityPlayer) player);
                }
                return ModHandler.dischargeElectricItem(stack, decharge, Integer.MAX_VALUE, true, false, true) >= decharge;
            }
        } else if (stack.getItem().isDamageable()) {

            if (player == null) {
                stack.setItemDamage(stack.getItemDamage() + damage);
            } else {
                stack.damageItem(damage, player);
            }

            if (stack.getItemDamage() >= stack.getMaxDamage()) {
                stack.setItemDamage(stack.getMaxDamage() + 1);
                ItemStack containerItem = GT_Utility.getContainerItem(stack, true);
                if (containerItem != null) {
                    stack = containerItem.copy();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Uses a Soldering Iron
     */
    public static boolean useSolderingIron(ItemStack stack, EntityLivingBase playerIn) {
        if (playerIn == null || stack == null) return false;

        if (GT_Utility.isStackInList(stack, GregTech_API.sSolderingToolList)) {
            if (playerIn instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) playerIn;
                if (player.capabilities.isCreativeMode) return true;

                if (isElectricItem(stack) && ElectricItem.manager.getCharge(stack) > 1000.0D) {

                    for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                        if (GT_Utility.isStackInList(player.inventory.mainInventory[i], GregTech_API.sSolderingMetalList)) {
                            if (player.inventory.mainInventory[i].stackSize < 1) return false;

                            if (player.inventory.mainInventory[i].stackSize == 1) {
                                player.inventory.mainInventory[i] = null;
                            } else {
                                player.inventory.mainInventory[i].stackSize--;
                            }

                            if (player.inventoryContainer != null) player.inventoryContainer.detectAndSendChanges();

                            if (canUseElectricItem(stack, 10000)) {
                                return ModHandler.useElectricItem(stack, 10000, (EntityPlayer) playerIn);
                            }

                            ModHandler.useElectricItem(stack, (int) ElectricItem.manager.getCharge(stack), (EntityPlayer) playerIn);
                            return false;
                        }
                    }
                }
            } else {
                damageOrDechargeItem(stack, 1, 1000, playerIn);
                return true;
            }
        }
        return false;
    }

    /**
     * Is this an electric Item, which can charge other Items?
     */
    public static boolean isChargerItem(ItemStack stack) {
        if (stack != null && isElectricItem(stack)) {
            if (stack.getItem() instanceof ISpecialElectricItem) {
                return true;
            } else if (stack.getItem() instanceof IElectricItem) {
                return ((IElectricItem) stack.getItem()).canProvideEnergy(stack);
            }
        }
        return false;
    }

    /**
     * Is this an electric Item?
     */
    public static boolean isElectricItem(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof IElectricItem || stack.getItem() instanceof ISpecialElectricItem);
    }

    public static boolean isElectricItem(ItemStack stack, int tier) {
        return stack != null && isElectricItem(stack) && ElectricItem.manager.getTier(stack) == tier;
    }

//    public static int getCapsuleCellContainerCountMultipliedWithStackSize(ItemStack... stacks) {
//        int amount = 0;
//        for (ItemStack stack : stacks)
//            if (stack != null) amount += getCapsuleCellContainerCount(stack) * stack.stackSize;
//        return amount;
//    }
//
//    public static int getCapsuleCellContainerCount(ItemStack stack) {
//        if (stack == null) return 0;
//        return GT_Utility.areStacksEqual(GT_Utility.getContainerForFilledItem(stack, true), ItemList.Cell_Empty.get(1)) || OrePrefix.cell.contains(stack) || OrePrefix.cellPlasma.contains(stack) ? 1 : 0;
//    }

    public static class RecipeBits {
        /**
         * Mirrors the Recipe
         */
        public static long MIRRORED = B[0];
        /**
         * This is a special Tag I used for crafting Coins up and down.
         */
        public static long KEEPNBT = B[2];
        /**
         * Makes the Recipe Reverse Craftable in the Disassembler.
         */
        public static long DISMANTLEABLE = B[3];
        /**
         * Prevents the Recipe from accidentally getting removed by my own Handlers.
         */
        public static long NOT_REMOVABLE = B[4];
        /**
         * Reverses the Output of the Recipe for smelting and pulverising.
         */
        public static long REVERSIBLE = B[5];
        /**
         * Removes all Recipes with the same Output Item regardless of NBT, unless another Recipe Deletion Bit is added too.
         */
        public static long DELETE_ALL_OTHER_RECIPES = B[6];
        /**
         * Removes all Recipes with the same Output Item limited to the same NBT.
         */
        public static long DELETE_ALL_OTHER_RECIPES_IF_SAME_NBT = B[7];
        /**
         * Removes all Recipes with the same Output Item limited to Shaped Recipes.
         */
        public static long DELETE_ALL_OTHER_SHAPED_RECIPES = B[8];
        /**
         * Removes all Recipes with the same Output Item limited to native Recipe Handlers.
         */
        public static long DELETE_ALL_OTHER_NATIVE_RECIPES = B[9];
        /**
         * Disables the check for colliding Recipes.
         */
        public static long DO_NOT_CHECK_FOR_COLLISIONS = B[10];
        /**
         * Only adds the Recipe if there is another Recipe having that Output
         */
        public static long ONLY_ADD_IF_THERE_IS_ANOTHER_RECIPE_FOR_IT = B[11];
    }

    public static class IC2 {

        public static <T extends IIdProvider> IBlockState getIC2BlockState(BlockName blockName, T type) {
            return blockName.getBlockState(type);
        }

        public static ItemStack getIC2Item(ItemName itemName, int amount) {
            ItemStack stack = itemName.getItemStack();
            stack.stackSize = amount;
            return stack;
        }

        public static ItemStack getIC2Item(ItemName itemName, int amount, boolean wildcard) {
            ItemStack stack = itemName.getItemStack();
            if (wildcard) stack.setItemDamage(OreDictionary.WILDCARD_VALUE);
            stack.stackSize = amount;
            return stack;
        }

        public static ItemStack getIC2Item(ItemName itemName, int amount, int explicitDamage) {
            ItemStack stack = itemName.getItemStack();
            stack.setItemDamage(explicitDamage);
            stack.stackSize = amount;
            return stack;
        }

        public static ItemStack getIC2Item(BlockName itemName, int amount) {
            ItemStack stack = itemName.getItemStack();
            stack.stackSize = amount;
            return stack;
        }

        public static <T extends Enum<T> & IIdProvider> ItemStack getIC2Item(BlockName itemName, T variant, int amount) {
            ItemStack stack = itemName.getItemStack(variant);
            stack.stackSize = amount;
            return stack;
        }

        public static ItemStack getIC2TEItem(TeBlock variant, int amount) {
            ItemStack stack = BlockName.te.getItemStack(variant);
            stack.stackSize = amount;
            return stack;
        }

        public static ItemStack getIC2Item(ItemName itemName, String variant, int amount) {
            ItemStack stack = itemName.getItemStack(variant);
            stack.stackSize = amount;
            return stack;
        }

        public static <T extends Enum<T> & IIdProvider> ItemStack getIC2Item(ItemName itemName, T type, int amount) {
            ItemStack stack = itemName.getItemStack(type);
            stack.stackSize = amount;
            return stack;
        }

        public static ItemStack getScrapBox(int amount) {
            return ModHandler.IC2.getIC2Item(ItemName.crafting, CraftingItemType.scrap_box, amount);
        }

        public static ItemStack getScrap(int amount) {
            return ModHandler.IC2.getIC2Item(ItemName.crafting, CraftingItemType.scrap, amount);
        }

        /**
         * IC2-ThermalCentrifuge Recipe. Overloads old Recipes automatically
         */
        public static void addThermalCentrifugeRecipe(ItemStack input, int heat, Object... output) {
            Validate.notNull(input, "Input cannot be null");
            Validate.notNull(output, "Output cannot be null");
            Validate.isTrue(output.length > 0, "Output cannot be empty");
            Validate.notNull(output[0], "Output cannot be null");

            GT_Utility.removeSimpleIC2MachineRecipe(input, Recipes.centrifuge.getRecipes(), null);
            if (!GregTech_API.sRecipeFile.get(ConfigCategories.Machines.thermalcentrifuge, input, true))
                return;

            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("minHeat", heat);
            GT_Utility.addSimpleIC2MachineRecipe(input, Recipes.centrifuge, tag, output);
        }

        /**
         * IC2-OreWasher Recipe. Overloads old Recipes automatically
         */
        public static void addOreWasherRecipe(ItemStack input, int waterAmount, Object... output) {
            Validate.notNull(input, "Input cannot be null");
            Validate.notNull(output, "Output cannot be null");
            Validate.isTrue(output.length > 0, "Output cannot be empty");
            Validate.notNull(output[0], "Output cannot be null");

            GT_Utility.removeSimpleIC2MachineRecipe(input, Recipes.oreWashing.getRecipes(), null);
            if (!GregTech_API.sRecipeFile.get(ConfigCategories.Machines.orewashing, input, true))
                return;

            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("amount", waterAmount);
            GT_Utility.addSimpleIC2MachineRecipe(input, Recipes.oreWashing, tag, output);
        }

        /**
         * IC2-Compressor Recipe. Overloads old Recipes automatically
         */
        public static void addCompressionRecipe(ItemStack input, ItemStack output) {
            output = OreDictionaryUnifier.get(true, output);
            Validate.notNull(input, "Input cannot be null");
            Validate.notNull(output, "Output cannot be null");

            GT_Utility.removeSimpleIC2MachineRecipe(input, Recipes.compressor.getRecipes(), null);
            if (!GregTech_API.sRecipeFile.get(ConfigCategories.Machines.compression, input, true))
                return;

            GT_Utility.addSimpleIC2MachineRecipe(input, Recipes.compressor, null, output);
        }

        /**
         * @param value Scrap = 5000, Scrapbox = 45000, Diamond Dust 125000
         */
        public static void addIC2MatterAmplifier(ItemStack amplifier, int value) {
            Validate.notNull(amplifier, "Amplifier cannot be null");
            Validate.isTrue(value > 0, "Amplifier value cannot be less or equal to zero");

            if (!GregTech_API.sRecipeFile.get(ConfigCategories.Machines.massfabamplifier, amplifier, true))
                return;

            try {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("amplification", value);
                GT_Utility.callMethod(Recipes.matterAmplifier, "addRecipe", false, false, false, amplifier, tag);
            } catch (Throwable e) {/*Do nothing*/}
        }

    }

    /**
     * Copy of the original Helper Class of Thermal Expansion, just to make sure it works even when other Mods include TE-APIs
     */
    public static class ThermalExpansion {

        /**
         * LiquidTransposer Recipe for both directions
         */
        public static void addLiquidTransposerRecipe(ItemStack emptyContainer, FluidStack fluid, ItemStack fullContainer, int RF) {
            fullContainer = OreDictionaryUnifier.getUnificated(fullContainer);
            Validate.notNull(emptyContainer, "Empty Container cannot be null");
            Validate.notNull(fullContainer, "Full Container cannot be null");
            Validate.notNull(fluid, "Fluid Container cannot be null");

            if (!GT_Mod.gregtechproxy.mTEMachineRecipes &&
                    !GregTech_API.sRecipeFile.get(ConfigCategories.Machines.liquidtransposer, fullContainer, true))
                return;

            try {
                ThermalExpansion.addTransposerFill(RF * 10, emptyContainer, fullContainer, fluid, true);
            } catch (Throwable e) {/*Do nothing*/}
        }

        /**
         * LiquidTransposer Recipe for filling Containers
         */
        public static void addLiquidTransposerFillRecipe(ItemStack emptyContainer, FluidStack fluid, ItemStack fullContainer, int MJ) {
            fullContainer = OreDictionaryUnifier.getUnificated(fullContainer);
            Validate.notNull(emptyContainer, "Empty Container cannot be null");
            Validate.notNull(fullContainer, "Full Container cannot be null");
            Validate.notNull(fluid, "Fluid Container cannot be null");

            if (!GT_Mod.gregtechproxy.mTEMachineRecipes &&
                    !GregTech_API.sRecipeFile.get(ConfigCategories.Machines.liquidtransposerfilling, fullContainer, true))
                return;

            try {
                ThermalExpansion.addTransposerFill(MJ * 10, emptyContainer, fullContainer, fluid, false);
            } catch (Throwable e) {/*Do nothing*/}
        }

        /**
         * LiquidTransposer Recipe for emptying Containers
         */
        public static void addLiquidTransposerEmptyRecipe(ItemStack fullContainer, FluidStack fluid, ItemStack emptyContainer, int RF) {
            emptyContainer = OreDictionaryUnifier.getUnificated(emptyContainer);
            Validate.notNull(emptyContainer, "Empty Container cannot be null");
            Validate.notNull(fullContainer, "Full Container cannot be null");
            Validate.notNull(fluid, "Fluid Container cannot be null");

            if (!GT_Mod.gregtechproxy.mTEMachineRecipes &&
                    !GregTech_API.sRecipeFile.get(ConfigCategories.Machines.liquidtransposeremptying, fullContainer, true))
                return;

            try {
                ThermalExpansion.addTransposerExtract(RF * 10, fullContainer, emptyContainer, fluid, 100, false);
            } catch (Throwable e) {/*Do nothing*/}
        }

        /**
         * Adds a Recipe to the Sawmills of GregTech and ThermalCraft
         */
        public static void addSawmillRecipe(ItemStack input1, ItemStack output1, ItemStack output2) {
            output2 = OreDictionaryUnifier.getUnificated(output2);
            Validate.notNull(input1, "Input cannot be null");
            Validate.notNull(output1, "Output cannot be null");

            if (!GT_Mod.gregtechproxy.mTEMachineRecipes &&
                    !GregTech_API.sRecipeFile.get(ConfigCategories.Machines.sawmill, input1, true))
                return;

            try {
                ThermalExpansion.addSawmillRecipe(1600, input1, output1, output2, 100);
            } catch (Throwable e) {/*Do nothing*/}
        }

        /**
         * Induction Smelter Recipes for TE
         */
        public static void addInductionSmelterRecipe(ItemStack input1, ItemStack input2, ItemStack output1, ItemStack output2, int energy, int chance) {
            output1 = OreDictionaryUnifier.getUnificated(output1);
            output2 = OreDictionaryUnifier.getUnificated(output2);
            Validate.notNull(input1, "Input cannot be null");
            Validate.notNull(output1, "Output cannot be null");
            Validate.isTrue(GT_Utility.getContainerItem(input1, false) != null, "Input item cannot have container item");

            if (!GT_Mod.gregtechproxy.mTEMachineRecipes &&
                    !GregTech_API.sRecipeFile.get(ConfigCategories.Machines.inductionsmelter, input2 == null ? input1 : output1, true))
                return;

            try {
                ThermalExpansion.addSmelterRecipe(energy * 10, GT_Utility.copy(input1), input2 == null ? new ItemStack(Blocks.SAND, 1, 0) : input2, output1, output2, chance);
            } catch (Throwable e) {/*Do nothing*/}
        }

        public static void addFurnaceRecipe(int energy, ItemStack input, ItemStack output) {
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setInteger("energy", energy);
            toSend.setTag("input", new NBTTagCompound());
            toSend.setTag("output", new NBTTagCompound());
            input.writeToNBT(toSend.getCompoundTag("input"));
            output.writeToNBT(toSend.getCompoundTag("output"));
            FMLInterModComms.sendMessage("ThermalExpansion", "FurnaceRecipe", toSend);
        }

        public static void addPulverizerRecipe(int energy, ItemStack input, ItemStack primaryOutput) {
            addPulverizerRecipe(energy, input, primaryOutput, null, 0);
        }

        public static void addPulverizerRecipe(int energy, ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput) {
            addPulverizerRecipe(energy, input, primaryOutput, secondaryOutput, 100);
        }

        public static void addPulverizerRecipe(int energy, ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput, int secondaryChance) {
            if (input == null || primaryOutput == null) return;
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setInteger("energy", energy);
            toSend.setTag("input", new NBTTagCompound());
            toSend.setTag("primaryOutput", new NBTTagCompound());
            toSend.setTag("secondaryOutput", new NBTTagCompound());
            input.writeToNBT(toSend.getCompoundTag("input"));
            primaryOutput.writeToNBT(toSend.getCompoundTag("primaryOutput"));
            if (secondaryOutput != null) secondaryOutput.writeToNBT(toSend.getCompoundTag("secondaryOutput"));
            toSend.setInteger("secondaryChance", secondaryChance);
            FMLInterModComms.sendMessage("ThermalExpansion", "PulverizerRecipe", toSend);
        }

        public static void addSawmillRecipe(int energy, ItemStack input, ItemStack primaryOutput) {
            addSawmillRecipe(energy, input, primaryOutput, null, 0);
        }

        public static void addSawmillRecipe(int energy, ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput) {
            addSawmillRecipe(energy, input, primaryOutput, secondaryOutput, 100);
        }

        public static void addSawmillRecipe(int energy, ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput, int secondaryChance) {
            if (input == null || primaryOutput == null) return;
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setInteger("energy", energy);
            toSend.setTag("input", new NBTTagCompound());
            toSend.setTag("primaryOutput", new NBTTagCompound());
            toSend.setTag("secondaryOutput", new NBTTagCompound());
            input.writeToNBT(toSend.getCompoundTag("input"));
            primaryOutput.writeToNBT(toSend.getCompoundTag("primaryOutput"));
            if (secondaryOutput != null) secondaryOutput.writeToNBT(toSend.getCompoundTag("secondaryOutput"));
            toSend.setInteger("secondaryChance", secondaryChance);
            FMLInterModComms.sendMessage("ThermalExpansion", "SawmillRecipe", toSend);
        }

        public static void addSmelterRecipe(int energy, ItemStack primaryInput, ItemStack secondaryInput, ItemStack primaryOutput) {
            addSmelterRecipe(energy, primaryInput, secondaryInput, primaryOutput, null, 0);
        }

        public static void addSmelterRecipe(int energy, ItemStack primaryInput, ItemStack secondaryInput, ItemStack primaryOutput, ItemStack secondaryOutput) {
            addSmelterRecipe(energy, primaryInput, secondaryInput, primaryOutput, secondaryOutput, 100);
        }

        public static void addSmelterRecipe(int energy, ItemStack primaryInput, ItemStack secondaryInput, ItemStack primaryOutput, ItemStack secondaryOutput, int secondaryChance) {
            if (primaryInput == null || secondaryInput == null || primaryOutput == null) return;
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setInteger("energy", energy);
            toSend.setTag("primaryInput", new NBTTagCompound());
            toSend.setTag("secondaryInput", new NBTTagCompound());
            toSend.setTag("primaryOutput", new NBTTagCompound());
            toSend.setTag("secondaryOutput", new NBTTagCompound());
            primaryInput.writeToNBT(toSend.getCompoundTag("primaryInput"));
            secondaryInput.writeToNBT(toSend.getCompoundTag("secondaryInput"));
            primaryOutput.writeToNBT(toSend.getCompoundTag("primaryOutput"));
            if (secondaryOutput != null) secondaryOutput.writeToNBT(toSend.getCompoundTag("secondaryOutput"));
            toSend.setInteger("secondaryChance", secondaryChance);
            FMLInterModComms.sendMessage("ThermalExpansion", "SmelterRecipe", toSend);
        }

        public static void addSmelterBlastOre(Material material) {
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setString("oreType", material.toString());
            FMLInterModComms.sendMessage("ThermalExpansion", "SmelterBlastOreType", toSend);
        }

        public static void addCrucibleRecipe(int energy, ItemStack input, FluidStack output) {
            if (input == null || output == null) return;
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setInteger("energy", energy);
            toSend.setTag("input", new NBTTagCompound());
            toSend.setTag("output", new NBTTagCompound());
            input.writeToNBT(toSend.getCompoundTag("input"));
            output.writeToNBT(toSend.getCompoundTag("output"));
            FMLInterModComms.sendMessage("ThermalExpansion", "CrucibleRecipe", toSend);
        }

        public static void addTransposerFill(int energy, ItemStack input, ItemStack output, FluidStack fluid, boolean reversible) {
            if (input == null || output == null || fluid == null) return;
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setInteger("energy", energy);
            toSend.setTag("input", new NBTTagCompound());
            toSend.setTag("output", new NBTTagCompound());
            toSend.setTag("fluid", new NBTTagCompound());
            input.writeToNBT(toSend.getCompoundTag("input"));
            output.writeToNBT(toSend.getCompoundTag("output"));
            toSend.setBoolean("reversible", reversible);
            fluid.writeToNBT(toSend.getCompoundTag("fluid"));
            FMLInterModComms.sendMessage("ThermalExpansion", "TransposerFillRecipe", toSend);
        }

        public static void addTransposerExtract(int energy, ItemStack input, ItemStack output, FluidStack fluid, int chance, boolean reversible) {
            if (input == null || output == null || fluid == null) return;
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setInteger("energy", energy);
            toSend.setTag("input", new NBTTagCompound());
            toSend.setTag("output", new NBTTagCompound());
            toSend.setTag("fluid", new NBTTagCompound());
            input.writeToNBT(toSend.getCompoundTag("input"));
            output.writeToNBT(toSend.getCompoundTag("output"));
            toSend.setBoolean("reversible", reversible);
            toSend.setInteger("chance", chance);
            fluid.writeToNBT(toSend.getCompoundTag("fluid"));
            FMLInterModComms.sendMessage("ThermalExpansion", "TransposerExtractRecipe", toSend);
        }

        public static void addMagmaticFuel(String fluidName, int energy) {
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setString("fluidName", fluidName);
            toSend.setInteger("energy", energy);
            FMLInterModComms.sendMessage("ThermalExpansion", "MagmaticFuel", toSend);
        }

        public static void addCompressionFuel(String fluidName, int energy) {
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setString("fluidName", fluidName);
            toSend.setInteger("energy", energy);
            FMLInterModComms.sendMessage("ThermalExpansion", "CompressionFuel", toSend);
        }

        public static void addCoolant(String fluidName, int energy) {
            NBTTagCompound toSend = new NBTTagCompound();
            toSend.setString("fluidName", fluidName);
            toSend.setInteger("energy", energy);
            FMLInterModComms.sendMessage("ThermalExpansion", "Coolant", toSend);
        }
    }
}
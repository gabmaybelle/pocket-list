package com.gabbymaybelle.pocketlist.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A smithing recipe's three inputs, which it doesn't list as ingredients on 1.20.1. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
	@Accessor("template")
	Ingredient pocketList$template();

	@Accessor("base")
	Ingredient pocketList$base();

	@Accessor("addition")
	Ingredient pocketList$addition();
}

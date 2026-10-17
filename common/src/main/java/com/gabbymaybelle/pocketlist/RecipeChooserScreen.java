package com.gabbymaybelle.pocketlist;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** When something can be made more than one way: pick the recipe to break a line down with. */
public class RecipeChooserScreen extends Screen {
	private static final int MAX_PANEL = 360;
	/** As wide as fits the window, up to MAX_PANEL. */
	private int panel = MAX_PANEL;
	private static final int ROW = 24;
	private static final int TOP = 40;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFAAAAAA;
	/** Room for "Crafting Table" or "Furnace (or Blast Furnace)"; longer names get a tooltip. */
	private static final int WHERE_WIDTH = 165;

	private final PocketListScreen parent;
	private final Entry entry;
	private final List<FoundRecipe> recipes;
	private int page;
	private int perPage;

	public RecipeChooserScreen(PocketListScreen parent, Entry entry, List<FoundRecipe> recipes) {
		super(Component.translatable("pocket_list.chooser.title", entry.name()));
		this.parent = parent;
		this.entry = entry;
		this.recipes = recipes;
	}

	@Override
	protected void init() {
		panel = Math.min(MAX_PANEL, width - 16);
		int left = (width - panel) / 2;
		perPage = Math.max(1, (height - TOP - 60) / ROW);
		int pages = (recipes.size() + perPage - 1) / perPage;
		page = Math.clamp(page, 0, pages - 1);
		for (int i = 0; i < perPage; i++) {
			int index = page * perPage + i;
			if (index >= recipes.size()) {
				break;
			}
			addRenderableWidget(new RecipeRow(left, TOP + i * ROW, recipes.get(index)));
		}
		int bottom = height - 26;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(left, bottom, 20, 20).build()).active = page > 0;
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(left + 24, bottom, 20, 20).build()).active = page < pages - 1;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(left + panel - 100, bottom, 100, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, TEXT);
		graphics.drawCenteredString(font, Component.translatable("pocket_list.chooser.hint"), width / 2, 24, MUTED);
		int pages = (recipes.size() + perPage - 1) / perPage;
		if (pages > 1) {
			graphics.drawString(font, Component.translatable("pocket_list.screen.page", page + 1, pages), (width - panel) / 2 + 50, height - 20, MUTED, true);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** A recipe: where it's made, what goes in, and how many come out. Click to use it. */
	private final class RecipeRow extends AbstractWidget {
		private final FoundRecipe recipe;

		RecipeRow(int x, int y, FoundRecipe recipe) {
			super(x, y, panel, ROW - 2, recipe.where());
			this.recipe = recipe;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0x60FFFFFF : 0x40000000);
			int x = getX() + 3;
			int y = getY() + 3;
			if (!recipe.station().isEmpty()) {
				graphics.renderItem(recipe.station(), x, y);
			}
			x += 20;
			String where = recipe.where().getString();
			String shown = font.plainSubstrByWidth(where, WHERE_WIDTH);
			graphics.drawString(font, shown.length() < where.length() ? font.plainSubstrByWidth(where, WHERE_WIDTH - 8) + "..." : where, x, y + 4, MUTED, true);
			if (shown.length() < where.length() && mouseX >= x && mouseX < x + WHERE_WIDTH && mouseY >= getY() && mouseY < getY() + height) {
				setTooltipForNextRenderPass(recipe.where());
			}
			x += WHERE_WIDTH + 6;
			for (Entry ingredient : recipe.ingredients()) {
				if (x > getX() + width - 70) {
					graphics.drawString(font, "...", x, y + 4, MUTED, true);
					break;
				}
				ItemStack icon = ingredient.icon();
				graphics.renderItem(icon, x, y);
				if (ingredient.need > 1) {
					graphics.renderItemDecorations(font, icon, x, y, Integer.toString(ingredient.need));
				}
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
					setTooltipForNextRenderPass(ingredient.name());
				}
				x += 20;
			}
			Component makes = Component.translatable("pocket_list.chooser.makes", recipe.makes());
			graphics.drawString(font, makes, getX() + width - 4 - font.width(makes), y + 4, TEXT, true);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			Lists.breakDown(entry, recipe);
			minecraft.setScreen(parent);
			parent.changed();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}

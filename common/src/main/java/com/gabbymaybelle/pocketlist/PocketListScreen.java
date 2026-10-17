package com.gabbymaybelle.pocketlist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Where you look after your lists: switch between them, rename them, type amounts, add items
 * by name, break a line down into its ingredients, clear off what you've gathered, and paste
 * a list in (or one from Litematica) or copy one out.
 */
public class PocketListScreen extends Screen {
	private static final int MAX_PANEL = 320;
	/** As wide as fits the window, up to MAX_PANEL. */
	private int panel = MAX_PANEL;
	private static final int ROW = 22;
	private static final int TOP = 54;
	private static final int SEARCH_RESULTS = 15;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DONE = 0xFF77DD77;
	private static final int MUTED = 0xFFAAAAAA;
	private static final int ROW_BACKGROUND = 0x40000000;
	/** How long the Delete button waits for a second click, and how long a message shows. */
	private static final long CONFIRM_MILLIS = 3000;
	private static final long MESSAGE_MILLIS = 4000;
	/** The right-hand end of a line: amount box, then Break down, -, + and x. */
	private static final int CONTROLS = 126;

	private final Screen parent;
	private int page;
	private int rowsPerPage;
	private String search = "";
	/** The results for {@link #search}, worked out once per change: the registry is big in a modpack. */
	private String resultsFor = "";
	private List<Item> results = List.of();
	private String addAmount = "";
	private boolean searchFocused;
	private long deleteArmedUntil;
	private Component message;
	private long messageUntil;
	private EditBox searchBox;
	private EditBox amountBox;

	public PocketListScreen(Screen parent) {
		super(Component.translatable("pocket_list.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panel = Math.min(MAX_PANEL, width - 16);
		Counter.update(minecraft);
		Lists.PocketListData list = Lists.active();
		int left = (width - panel) / 2;
		int right = left + panel;

		// Which list: < name > and New.
		addRenderableWidget(Button.builder(Component.literal("<"), b -> switchList(-1))
				.bounds(left, 24, 20, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.previous"))).build());
		EditBox nameBox = new EditBox(font, left + 24, 24, panel - 24 - 24 - 64, 20, Component.translatable("pocket_list.screen.name"));
		nameBox.setMaxLength(40);
		nameBox.setValue(list.name);
		nameBox.setResponder(value -> list.name = value.isBlank() ? list.name : value);
		addRenderableWidget(nameBox);
		addRenderableWidget(Button.builder(Component.literal(">"), b -> switchList(1))
				.bounds(right - 64 - 20, 24, 20, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.next"))).build());
		addRenderableWidget(Button.builder(Component.translatable("pocket_list.screen.new"), b -> {
			Lists.data().lists.add(Lists.newList(Lists.data().lists.size() + 1));
			Lists.data().active = Lists.data().lists.size() - 1;
			page = 0;
			changed();
		}).bounds(right - 60, 24, 60, 20).build());

		// The lines, a page at a time.
		int bottomBlock = 24 + 22 + 24 + 28 + 36;
		rowsPerPage = Math.max(2, (height - TOP - bottomBlock) / ROW);
		int pages = pages(list);
		page = Mth.clamp(page, 0, pages - 1);
		for (int i = 0; i < rowsPerPage; i++) {
			int index = page * rowsPerPage + i;
			if (index >= list.entries.size()) {
				break;
			}
			addRow(list.entries.get(index), right, TOP + i * ROW);
		}
		int afterRows = TOP + rowsPerPage * ROW + 2;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(left + panel / 2 - 64, afterRows, 20, 20).build()).active = page > 0;
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(left + panel / 2 + 44, afterRows, 20, 20).build()).active = page < pages - 1;
		}

		// Add an item by name, with how many.
		int searchY = afterRows + 24;
		searchBox = new EditBox(font, left, searchY, panel - 56, 20, Component.translatable("pocket_list.screen.search"));
		searchBox.setHint(Component.translatable("pocket_list.screen.search_hint"));
		searchBox.setMaxLength(50);
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			if (!value.equals(search)) {
				search = value;
				searchFocused = true;
				rebuildWidgets();
			}
		});
		addRenderableWidget(searchBox);
		amountBox = numberBox(right - 52, searchY, 52, addAmount, value -> addAmount = value);
		amountBox.setHint(Component.translatable("pocket_list.screen.amount_hint"));
		amountBox.setTooltip(Tooltip.create(Component.translatable("pocket_list.screen.amount_box")));
		addRenderableWidget(amountBox);
		int resultX = left;
		for (Item item : searchResults()) {
			if (resultX + 18 > left + panel) {
				break;
			}
			addRenderableWidget(new ItemButton(resultX, searchY + 22, new ItemStack(item), shift -> {
				Lists.add(Entry.ofItem(item, shift ? new ItemStack(item).getMaxStackSize() : parseAmount(addAmount, 1)));
				changed();
			}, Component.translatable("pocket_list.screen.add_item", new ItemStack(item).getHoverName())));
			resultX += 20;
		}

		// Lists as text: paste one in, copy this one out, or bring in Litematica's material list.
		int tools = height - 50;
		boolean litematica = Platform.isModLoaded("litematica") || ListText.latestLitematicaList() != null;
		int toolWidth = litematica ? (panel - 8) / 3 : (panel - 4) / 2;
		addRenderableWidget(Button.builder(Component.translatable("pocket_list.screen.paste"), b -> pasteList(minecraft.keyboardHandler.getClipboard(), null))
				.bounds(left, tools, toolWidth, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.paste.tooltip"))).build());
		addRenderableWidget(Button.builder(Component.translatable("pocket_list.screen.copy"), b -> copyList())
				.bounds(left + toolWidth + 4, tools, toolWidth, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.copy.tooltip"))).build())
				.active = !list.entries.isEmpty();
		if (litematica) {
			addRenderableWidget(Button.builder(Component.translatable("pocket_list.screen.import"), b -> importLitematica())
					.bounds(left + (toolWidth + 4) * 2, tools, toolWidth, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.import.tooltip"))).build());
		}

		// Housekeeping.
		int bottom = height - 26;
		int quarter = (panel - 12) / 4;
		addRenderableWidget(Button.builder(Component.translatable("pocket_list.screen.clear_done"), b -> {
			list.entries.removeIf(Entry::done);
			changed();
		}).bounds(left, bottom, quarter, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.clear_done.tooltip"))).build());
		boolean armed = deleteArmedUntil > Util.getMillis();
		addRenderableWidget(Button.builder(Component.translatable(armed ? "pocket_list.screen.delete_confirm" : "pocket_list.screen.delete"), b -> {
			if (deleteArmedUntil > Util.getMillis()) {
				deleteArmedUntil = 0;
				deleteActiveList();
			} else {
				deleteArmedUntil = Util.getMillis() + CONFIRM_MILLIS;
				rebuildWidgets();
			}
		}).bounds(left + quarter + 4, bottom, quarter, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("pocket_list.screen.settings"), b -> {
			Lists.save();
			minecraft.setScreen(new PocketListSettingsScreen(this));
		}).bounds(left + (quarter + 4) * 2, bottom, quarter, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.settings.tooltip"))).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + (quarter + 4) * 3, bottom, quarter, 20).build());

		if (searchFocused) {
			setInitialFocus(searchBox);
		}
	}

	/** One line's controls: a box to type the amount, Break down, -, + and x. */
	private void addRow(Entry entry, int right, int y) {
		Component step = Component.translatable("pocket_list.screen.step", entry.stackSize());
		EditBox amount = numberBox(right - CONTROLS, y, 34, Integer.toString(entry.need), value -> {
			int parsed = parseAmount(value, -1);
			if (parsed > 0) {
				entry.need = parsed;
			}
		});
		amount.setTooltip(Tooltip.create(Component.translatable("pocket_list.screen.amount_tooltip")));
		addRenderableWidget(amount);
		addRenderableWidget(new ItemButton(right - 88, y + 1, new ItemStack(Items.CRAFTING_TABLE), shift -> breakDown(entry),
				Component.translatable(entry.done() ? "pocket_list.screen.break_down.done" : "pocket_list.screen.break_down")));
		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			entry.need = Math.max(1, entry.need - (hasShiftDown() ? entry.stackSize() : 1));
			changed();
		}).bounds(right - 66, y, 20, 20).tooltip(Tooltip.create(step)).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			entry.need = Math.min(Lists.MAX_NEED, entry.need + (hasShiftDown() ? entry.stackSize() : 1));
			changed();
		}).bounds(right - 44, y, 20, 20).tooltip(Tooltip.create(step)).build());
		addRenderableWidget(Button.builder(Component.literal("x"), b -> {
			Lists.active().entries.remove(entry);
			changed();
		}).bounds(right - 20, y, 20, 20).tooltip(Tooltip.create(Component.translatable("pocket_list.screen.remove"))).build());
	}

	/** A small box that only takes digits. */
	private EditBox numberBox(int x, int y, int width, String value, java.util.function.Consumer<String> onChange) {
		EditBox box = new NumberBox(x, y, width);
		box.setMaxLength(5);
		box.setValue(value);
		box.setResponder(text -> {
			String digits = text.replaceAll("[^0-9]", "");
			if (!digits.equals(text)) {
				box.setValue(digits);
				return;
			}
			onChange.accept(digits);
		});
		return box;
	}

	private static int parseAmount(String text, int fallback) {
		try {
			int value = Integer.parseInt(text.trim());
			return value > 0 ? Math.min(Lists.MAX_NEED, value) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Swaps a line for what it takes to make it, asking which recipe when there's a choice. */
	private void breakDown(Entry entry) {
		// Count right now, so what just arrived in your inventory is taken off.
		Counter.update(minecraft);
		if (entry.done()) {
			return;
		}
		Item item = entry.mainItem();
		List<FoundRecipe> recipes = Recipes.forItem(minecraft, item);
		if (recipes.isEmpty()) {
			showMessage(Component.translatable("pocket_list.screen.no_recipe", entry.name()));
		} else if (recipes.size() == 1) {
			Lists.breakDown(entry, recipes.get(0));
			changed();
		} else {
			minecraft.setScreen(new RecipeChooserScreen(this, entry, recipes));
		}
	}

	/**
	 * Makes a new list from text: one item per line with how many, or a Litematica material list.
	 * Lines whose item isn't found are named in the message, never guessed at.
	 */
	void pasteList(String text, String fallbackName) {
		if (text == null || text.isBlank()) {
			showMessage(Component.translatable("pocket_list.screen.paste_nothing"));
			rebuildWidgets();
			return;
		}
		ListText.Result result = ListText.read(text, minecraft.level == null ? null : minecraft.level.enabledFeatures());
		String unknown = String.join(", ", result.unknown().stream().limit(3).toList()) + (result.unknown().size() > 3 ? ", ..." : "");
		if (!result.unknown().isEmpty()) {
			PocketList.LOGGER.info("Pocket List: no item found for {}", result.unknown());
		}
		if (result.entries().isEmpty()) {
			showMessage(result.unknown().isEmpty() ? Component.translatable("pocket_list.screen.paste_nothing")
					: Component.translatable("pocket_list.screen.paste_none_known", unknown));
			rebuildWidgets();
			return;
		}
		Lists.PocketListData list = Lists.addList(result.title() != null ? result.title() : fallbackName, result.entries());
		page = 0;
		showMessage(result.unknown().isEmpty() ? Component.translatable("pocket_list.screen.pasted", list.entries.size())
				: Component.translatable("pocket_list.screen.pasted_unknown", list.entries.size(), unknown));
		changed();
	}

	private void copyList() {
		Lists.PocketListData list = Lists.active();
		minecraft.keyboardHandler.setClipboard(ListText.write(list));
		showMessage(Component.translatable("pocket_list.screen.copied", list.entries.size()));
	}

	private void importLitematica() {
		Path file = ListText.latestLitematicaList();
		if (file == null) {
			showMessage(Component.translatable("pocket_list.screen.import.none"));
			return;
		}
		try {
			pasteList(Files.readString(file), Component.translatable("pocket_list.screen.litematica_name").getString());
		} catch (IOException | RuntimeException e) {
			PocketList.LOGGER.warn("Pocket List: couldn't read {}", file, e);
			showMessage(Component.translatable("pocket_list.screen.import.none"));
		}
	}

	private void showMessage(Component text) {
		message = text;
		messageUntil = Util.getMillis() + MESSAGE_MILLIS;
	}

	private List<Item> searchResults() {
		if (!search.equals(resultsFor)) {
			results = findItems(search);
			resultsFor = search;
		}
		return results;
	}

	/** The first few items whose name contains the text, in registry order. */
	private List<Item> findItems(String text) {
		List<Item> out = new ArrayList<>();
		String query = text.trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			return out;
		}
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR || (minecraft.level != null && !item.isEnabled(minecraft.level.enabledFeatures()))) {
				continue;
			}
			if (new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
				out.add(item);
				if (out.size() >= SEARCH_RESULTS) {
					break;
				}
			}
		}
		return out;
	}

	private int pages(Lists.PocketListData list) {
		return Math.max(1, (list.entries.size() + rowsPerPage - 1) / rowsPerPage);
	}

	private void switchList(int by) {
		Lists.data().active = Math.floorMod(Lists.data().active + by, Lists.data().lists.size());
		page = 0;
		changed();
	}

	private void deleteActiveList() {
		List<Lists.PocketListData> lists = Lists.data().lists;
		lists.remove(Lists.active());
		if (lists.isEmpty()) {
			lists.add(Lists.newList(1));
		}
		Lists.data().active = Math.min(Lists.data().active, lists.size() - 1);
		page = 0;
		changed();
	}

	void changed() {
		searchFocused = searchBox != null && searchBox.isFocused();
		Lists.save();
		Counter.update(minecraft);
		rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (deleteArmedUntil != 0 && deleteArmedUntil <= Util.getMillis()) {
			deleteArmedUntil = 0;
			rebuildWidgets();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 1.20.1 screens draw their own backdrop.
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 10, TEXT);
		Lists.PocketListData list = Lists.active();
		int left = (width - panel) / 2;
		int right = left + panel;
		if (list.entries.isEmpty()) {
			int y = TOP + 4;
			for (FormattedCharSequence line : font.split(Component.translatable("pocket_list.screen.empty",
					PocketList.pinKey.getTranslatedKeyMessage()), panel)) {
				graphics.drawString(font, line, left, y, MUTED, true);
				y += font.lineHeight;
			}
		}
		for (int i = 0; i < rowsPerPage; i++) {
			int index = page * rowsPerPage + i;
			if (index >= list.entries.size()) {
				break;
			}
			Entry entry = list.entries.get(index);
			int y = TOP + i * ROW;
			int rowEnd = right - CONTROLS - 4;
			graphics.fill(left, y, rowEnd, y + 20, ROW_BACKGROUND);
			graphics.renderItem(entry.icon(), left + 2, y + 2);
			Component have = Component.translatable("pocket_list.screen.have", Math.min(entry.have(), entry.need));
			int haveWidth = font.width(have);
			graphics.drawString(font, have, rowEnd - 4 - haveWidth, y + 6, entry.done() ? DONE : MUTED, true);
			int nameWidth = rowEnd - 4 - haveWidth - 6 - (left + 22);
			Component name = entry.name();
			graphics.drawString(font, font.plainSubstrByWidth(name.getString(), nameWidth), left + 22, y + 6, entry.done() ? DONE : TEXT, true);
			if (mouseX >= left && mouseX < rowEnd && mouseY >= y && mouseY < y + 20) {
				setTooltipForNextRenderPass(name);
			}
		}
		int pages = pages(list);
		if (pages > 1) {
			graphics.drawCenteredString(font, Component.translatable("pocket_list.screen.page", page + 1, pages), width / 2, TOP + rowsPerPage * ROW + 8, MUTED);
		}
		if (!search.isBlank() && searchResults().isEmpty()) {
			graphics.drawString(font, Component.translatable("pocket_list.screen.no_results"), left, searchBox.getY() + 28, MUTED, true);
		}
		if (message != null && messageUntil > Util.getMillis()) {
			// Wrapped, upwards from just above the Paste row.
			List<FormattedCharSequence> lines = font.split(message, panel);
			int y = height - 62 - (lines.size() - 1) * font.lineHeight;
			for (FormattedCharSequence line : lines) {
				graphics.drawCenteredString(font, line, width / 2, y, 0xFFFFDD77);
				y += font.lineHeight;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int next = Mth.clamp(page - (int) Math.signum(scrollY), 0, pages(Lists.active()) - 1);
		if (next != page) {
			page = next;
			rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// The open key closes it again, unless you're typing.
		if (PocketList.openKey.matches(keyCode, scanCode) && !Compat.typing(this)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		Lists.save();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** A number box that selects its number when you click in, so typing replaces it. */
	private final class NumberBox extends EditBox {
		NumberBox(int x, int y, int width) {
			super(font, x, y, width, 20, Component.translatable("pocket_list.screen.amount"));
		}

		@Override
		public void setFocused(boolean focused) {
			boolean was = isFocused();
			super.setFocused(focused);
			if (focused && !was) {
				moveCursorToEnd();
				setHighlightPos(0);
			}
		}
	}

	/** A button showing an item: a search result to add, or the crafting table that breaks a line down. */
	static final class ItemButton extends AbstractWidget {
		private final ItemStack stack;
		private final java.util.function.Consumer<Boolean> onClick;

		ItemButton(int x, int y, ItemStack stack, java.util.function.Consumer<Boolean> onClick, Component tooltip) {
			super(x, y, 18, 18, tooltip);
			this.stack = stack;
			this.onClick = onClick;
			setTooltip(Tooltip.create(tooltip));
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.fill(getX(), getY(), getX() + 18, getY() + 18, isHoveredOrFocused() ? 0x80FFFFFF : ROW_BACKGROUND);
			graphics.renderItem(stack, getX() + 1, getY() + 1);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			onClick.accept(Screen.hasShiftDown());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}

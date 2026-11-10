package dev.smolagent.ui;

import dev.smolagent.SmolConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * /smolpp menu. Left column: rename rules (name part → replacement). Right column: name parts that
 * make a player translucent, plus the opacity slider. Edits go straight into {@link SmolConfig}
 * (so they show live); closing the screen (Done or Esc) saves to disk.
 */
public class SmolScreen extends Screen {

    public static final String ADD_RENAME = "+ Add rename";
    public static final String ADD_PART = "+ Add name part";
    public static final String DONE = "Done";

    private static final int TOP = 44;
    private static final int ROW_H = 24;
    private static final int LABEL = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;

    private final SmolConfig config = SmolConfig.get();
    private int renameScroll;
    private int partScroll;
    private int colW;
    private int leftX;
    private int rightX;
    private int visibleRows;
    /** Index of a just-added row whose first box gets focus after rebuilding. */
    private int focusRename = -1;
    private int focusPart = -1;

    public SmolScreen() {
        super(Component.literal("Smol Agent"));
    }

    @Override
    protected void init() {
        colW = Math.min(230, (width - 30) / 2);
        leftX = width / 2 - colW - 5;
        rightX = width / 2 + 5;
        int listBottom = height - 84;
        visibleRows = Math.max(1, (listBottom - TOP) / ROW_H);
        renameScroll = clampScroll(renameScroll, config.renames.size());
        partScroll = clampScroll(partScroll, config.translucent.size());

        buildRenameColumn();
        buildPartColumn();

        int addY = TOP + visibleRows * ROW_H + 2;
        addRenderableWidget(Button.builder(Component.literal(ADD_RENAME), b -> {
            config.renames.add(new SmolConfig.Rename());
            focusRename = config.renames.size() - 1;
            renameScroll = clampScroll(Integer.MAX_VALUE, config.renames.size());
            rebuildWidgets();
        }).bounds(leftX, addY, colW, 20).build());
        addRenderableWidget(Button.builder(Component.literal(ADD_PART), b -> {
            config.translucent.add("");
            focusPart = config.translucent.size() - 1;
            partScroll = clampScroll(Integer.MAX_VALUE, config.translucent.size());
            rebuildWidgets();
        }).bounds(rightX, addY, colW, 20).build());

        addRenderableWidget(new OpacitySlider(rightX, addY + 26, colW));
        addRenderableWidget(Button.builder(Component.literal(DONE), b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private void buildRenameColumn() {
        int boxW = (colW - 24 - 14) / 2;
        List<SmolConfig.Rename> renames = config.renames;
        for (int row = 0; row < visibleRows; row++) {
            int index = renameScroll + row;
            if (index >= renames.size()) break;
            SmolConfig.Rename rule = renames.get(index);
            int y = TOP + row * ROW_H;

            EditBox from = box(leftX, y, boxW, rule.from, "name part");
            from.setResponder(text -> rule.from = text);
            EditBox to = box(leftX + boxW + 14, y, boxW, rule.to, "replace with");
            to.setResponder(text -> rule.to = text);
            addRenderableWidget(from);
            addRenderableWidget(to);
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                renames.remove(rule);
                rebuildWidgets();
            }).bounds(leftX + colW - 20, y, 20, 20).build());
            if (index == focusRename) setInitialFocus(from);
        }
        focusRename = -1;
    }

    private void buildPartColumn() {
        List<String> parts = config.translucent;
        for (int row = 0; row < visibleRows; row++) {
            int index = partScroll + row;
            if (index >= parts.size()) break;
            int y = TOP + row * ROW_H;
            EditBox part = box(rightX, y, colW - 24, parts.get(index), "part of a name");
            part.setResponder(text -> parts.set(index, text));
            addRenderableWidget(part);
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                parts.remove(index);
                rebuildWidgets();
            }).bounds(rightX + colW - 20, y, 20, 20).build());
            if (index == focusPart) setInitialFocus(part);
        }
        focusPart = -1;
    }

    private EditBox box(int x, int y, int w, String value, String hint) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setMaxLength(64);
        box.setValue(value);
        box.setHint(Component.literal(hint));
        return box;
    }

    private int clampScroll(int scroll, int size) {
        return Math.clamp(scroll, 0, Math.max(0, size - visibleRows));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int step = scrollY > 0 ? -1 : 1;
        if (mouseX < width / 2.0) {
            int next = clampScroll(renameScroll + step, config.renames.size());
            if (next == renameScroll) return false;
            renameScroll = next;
        } else {
            int next = clampScroll(partScroll + step, config.translucent.size());
            if (next == partScroll) return false;
            partScroll = next;
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, 10, LABEL);
        g.text(font, "Rename: name part → replacement", leftX, TOP - 22, LABEL, true);
        g.text(font, "Case-insensitive, hits every player", leftX, TOP - 11, HINT, false);
        g.text(font, "Translucent: names containing", rightX, TOP - 22, LABEL, true);
        g.text(font, "any of these parts", rightX, TOP - 11, HINT, false);

        int boxW = (colW - 24 - 14) / 2;
        for (int row = 0; row < visibleRows && renameScroll + row < config.renames.size(); row++) {
            g.text(font, "→", leftX + boxW + 3, TOP + row * ROW_H + 6, LABEL, false);
        }
        if (config.renames.isEmpty()) g.text(font, "No renames.", leftX + 2, TOP + 6, HINT, false);
        if (config.translucent.isEmpty()) g.text(font, "Nobody is translucent.", rightX + 2, TOP + 6, HINT, false);
        moreHint(g, leftX, renameScroll, config.renames.size());
        moreHint(g, rightX, partScroll, config.translucent.size());
    }

    private void moreHint(GuiGraphicsExtractor g, int x, int scroll, int size) {
        int below = size - scroll - visibleRows;
        if (scroll > 0 || below > 0) {
            String s = (scroll > 0 ? "↑ " + scroll : "") + (scroll > 0 && below > 0 ? "  " : "")
                    + (below > 0 ? "↓ " + below : "") + "  (scroll)";
            g.text(font, s, x + colW - font.width(s), TOP - 11, HINT, false);
        }
    }

    @Override
    public void onClose() {
        config.save();
        super.onClose();
    }

    /** 1..100 % opacity. Slider value 0..1 maps linearly onto it. */
    private final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, int w) {
            super(x, y, w, 20, Component.empty(), toValue(config.opacity));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Opacity: " + config.opacity + "%"));
        }

        @Override
        protected void applyValue() {
            config.opacity = (int) Math.round(SmolConfig.MIN_OPACITY
                    + value * (SmolConfig.MAX_OPACITY - SmolConfig.MIN_OPACITY));
        }
    }

    private static double toValue(int opacity) {
        return (opacity - SmolConfig.MIN_OPACITY) / (double) (SmolConfig.MAX_OPACITY - SmolConfig.MIN_OPACITY);
    }
}

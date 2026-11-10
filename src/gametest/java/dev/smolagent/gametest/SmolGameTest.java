package dev.smolagent.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.smolagent.SmolConfig;
import dev.smolagent.ui.SmolScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives /smolpp like a player: opens it from chat, adds a rename and a translucent name part,
 * drags the slider to 1 %, and checks tab list, chat and the rendered body (screenshots).
 * Prints SMOL-GAMETEST ok/FAIL per check and fails at the end if anything failed.
 */
public class SmolGameTest implements FabricClientGameTest {

    private final List<String> failures = new ArrayList<>();
    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamerule advance_time false");

            check("defaults: Agent rule, no effect on Tester", tabName(), "Tester");
            // Nickname plugin stand-in: account "Tester", nickname "Bobster"
            ctx.runOnClient(mc -> mc.getConnection().getPlayerInfo(mc.player.getUUID())
                    .setTabListDisplayName(net.minecraft.network.chat.Component.literal("Bobster")));

            openMenu();
            check("/smolpp opens the menu", ctx.computeOnClient(mc -> mc.gui.screen() instanceof SmolScreen), true);
            ctx.takeScreenshot("smol-menu-defaults");

            // Rename: test -> Smol
            ctx.clickScreenButton(SmolScreen.ADD_RENAME);
            ctx.getInput().typeChars("test");
            click(lastBox("replace with"));
            ctx.getInput().typeChars("Smol");

            // Translucent: names containing "ster"
            ctx.clickScreenButton(SmolScreen.ADD_PART);
            ctx.getInput().typeChars("bob");

            // Slider all the way left = 1 %
            AbstractWidget slider = ctx.computeOnClient(mc -> widgets(mc).stream()
                    .filter(w -> w instanceof AbstractSliderButton).map(w -> (AbstractWidget) w).findFirst().orElseThrow());
            clickAt(slider.getX() + 1, slider.getY() + 10);
            ctx.waitTick();
            check("slider left edge = 1%", ctx.computeOnClient(mc -> SmolConfig.get().opacity), 1);
            ctx.takeScreenshot("smol-menu-filled");

            ctx.clickScreenButton(SmolScreen.DONE);
            ctx.waitFor(mc -> mc.gui.screen() == null, 40);

            String json = read(ctx.computeOnClient(mc -> SmolConfig.path()));
            check("saved: rename rule", json.contains("\"from\": \"test\"") && json.contains("\"to\": \"Smol\""), true);
            check("saved: name part", json.contains("\"bob\""), true);
            check("saved: opacity", json.contains("\"opacity\": 1"), true);

            // Rename shows up everywhere
            check("tab list shows the nickname, rule \"test\" does not hit it", tabName(), "Bobster");
            // Vanilla player chat: the name is an argument of a translatable component
            world.getServer().runCommand("tellraw @a {\"translate\":\"chat.type.text\",\"with\":[\"Tester\",\"hello Tester\"]}");
            ctx.waitFor(mc -> newestChat(mc).contains("hello"), 100);
            check("chat renamed (name argument and text)", ctx.computeOnClient(SmolGameTest::newestChat), "<Smoler> hello Smoler");
            // Plugin-style literal line
            world.getServer().runCommand("tellraw @a {\"text\":\"[G] TESTER > hi\"}");
            ctx.waitFor(mc -> newestChat(mc).contains("hi"), 100);
            check("chat renamed (literal, all caps)", ctx.computeOnClient(SmolGameTest::newestChat), "[G] SMOLER > hi");

            // Translucency, seen from the front in third person
            ctx.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            });
            ctx.getInput().lookAt(0, 0);
            ctx.waitTicks(10);
            BufferedImage at1 = shot("smol-fade-1pct");
            setOpacity(50);
            BufferedImage at50 = shot("smol-fade-50pct");
            setOpacity(100);
            BufferedImage at100 = shot("smol-fade-100pct");
            setOpacity(1);
            ctx.runOnClient(mc -> SmolConfig.get().translucent.clear());
            BufferedImage noMatch = shot("smol-fade-nomatch");
            ctx.runOnClient(mc -> SmolConfig.get().translucent.add("TEST"));
            BufferedImage accountOnly = shot("smol-fade-account-name-part");
            ctx.runOnClient(mc -> SmolConfig.get().translucent.set(0, "BOB"));
            BufferedImage upper = shot("smol-fade-1pct-uppercase-part");

            double d1to100 = diff(at1, at100);
            double d50to100 = diff(at50, at100);
            double dNoMatch = diff(noMatch, at100);
            double dUpper = diff(upper, at1);
            double dAccount = diff(accountOnly, at100);
            System.out.printf("SMOL-GAMETEST diffs 1/100=%.2f 50/100=%.2f nomatch/100=%.2f upper/1=%.2f%n",
                    d1to100, d50to100, dNoMatch, dUpper);
            check("1% differs clearly from opaque", d1to100 > 5, true);
            check("50% lies between", d50to100 > 1 && d50to100 < d1to100, true);
            check("no matching part = opaque", dNoMatch < 0.5, true);
            check("part match is case-insensitive", dUpper < 0.5, true);
            check("account name part (TEST) does not match", dAccount < 0.5, true);
            ctx.runOnClient(mc -> mc.getConnection().getPlayerInfo(mc.player.getUUID()).setTabListDisplayName(null));
            BufferedImage noNick = shot("smol-fade-no-nickname");
            check("no nickname, part only in nickname (BOB) = opaque", diff(noNick, at100) < 0.5, true);
            ctx.runOnClient(mc -> SmolConfig.get().translucent.set(0, "test"));
            BufferedImage fallback = shot("smol-fade-account-fallback");
            check("no nickname: account name part (test) matches", diff(fallback, at1) < 0.5, true);
            ctx.runOnClient(mc -> SmolConfig.get().translucent.set(0, "BOB"));

            ctx.runOnClient(mc -> {
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });

            // Reopen: rows come back from the config
            openMenu();
            List<String> values = ctx.computeOnClient(mc -> widgets(mc).stream()
                    .filter(w -> w instanceof EditBox).map(w -> ((EditBox) w).getValue()).toList());
            check("reopened menu shows saved rows", values, List.of("Agent", "Smol", "test", "Smol", "BOB"));

            // Remove the first rename row with its x button
            AbstractWidget x = ctx.computeOnClient(mc -> widgets(mc).stream()
                    .filter(w -> w instanceof AbstractWidget a && a.getMessage().getString().equals("x"))
                    .map(w -> (AbstractWidget) w).findFirst().orElseThrow());
            click(x);
            check("x removes the row", ctx.computeOnClient(mc -> SmolConfig.get().renames.size()), 1);
            ctx.clickScreenButton(SmolScreen.DONE);
        }

        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " check(s) failed:\n  " + String.join("\n  ", failures));
        }
    }

    private void openMenu() {
        ctx.setScreen(() -> new ChatScreen("", false));
        ctx.waitForScreen(ChatScreen.class);
        ctx.getInput().typeChars("/smolpp");
        ctx.getInput().pressKey(InputConstants.KEY_RETURN);
        ctx.waitTicks(2);
    }

    private void setOpacity(int percent) {
        ctx.runOnClient(mc -> SmolConfig.get().opacity = percent);
    }

    private BufferedImage shot(String name) {
        ctx.waitTicks(3);
        Path p = ctx.takeScreenshot(name);
        try {
            return ImageIO.read(p.toFile());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mean absolute RGB difference over the central third of the image (where the player stands). */
    private static double diff(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        long sum = 0;
        long n = 0;
        for (int y = h / 3; y < 2 * h / 3; y++) {
            for (int x = w / 3; x < 2 * w / 3; x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                sum += Math.abs(((p >> 16) & 255) - ((q >> 16) & 255))
                        + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255))
                        + Math.abs((p & 255) - (q & 255));
                n += 3;
            }
        }
        return (double) sum / n;
    }

    private String tabName() {
        return ctx.computeOnClient(mc -> mc.gui.hud.getTabList()
                .getNameForDisplay(mc.getConnection().getPlayerInfo(mc.player.getUUID())).getString());
    }

    @SuppressWarnings("unchecked")
    private static String newestChat(Minecraft mc) {
        try {
            Field f = ChatComponent.class.getDeclaredField("allMessages");
            f.setAccessible(true);
            List<GuiMessage> all = (List<GuiMessage>) f.get(mc.gui.hud.getChat());
            return all.isEmpty() ? "" : all.getFirst().content().getString();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<? extends GuiEventListener> widgets(Minecraft mc) {
        return mc.gui.screen().children();
    }

    private AbstractWidget lastBox(String hint) {
        return ctx.computeOnClient(mc -> {
            AbstractWidget found = null;
            for (GuiEventListener w : widgets(mc)) {
                if (w instanceof EditBox box && box.getMessage().getString().equals(hint)) found = box;
            }
            return found;
        });
    }

    private void click(AbstractWidget w) {
        clickAt(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
    }

    private void clickAt(double guiX, double guiY) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }

    private void check(String name, Object actual, Object expected) {
        if (actual.equals(expected)) {
            System.out.println("SMOL-GAMETEST ok " + name);
        } else {
            System.out.println("SMOL-GAMETEST FAIL " + name + ": got " + actual + ", expected " + expected);
            failures.add(name + ": got " + actual + ", expected " + expected);
        }
    }
}

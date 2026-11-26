package com.osr.jei.ui;

import com.osr.jei.JEIConfig;
import com.osr.jei.JEIPanel;
import com.osr.jei.drops.DropSort;
import com.osr.jei.ironman.IronmanMode;
import com.osr.jei.map.PinSize;
import com.osr.jei.model.EquipmentStats;
import com.osr.jei.model.SearchItem;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.service.ItemDetails;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShopSectionClickTest {

    @Test
    public void pressingTheShopHeaderExpandsVisibleShops() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                pressShops(true);
                pressShops(false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void everySectionHeaderOpensOnPress() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String[] keys = {
                SectionState.TRADING,
                SectionState.EQUIPMENT,
                SectionState.SHOPS,
                SectionState.USED_IN,
                SectionState.DROPS,
                SectionState.CRAFTING,
                SectionState.LOCATIONS
            };
            for (boolean wrap : new boolean[] {true, false}) {
                UiStyle style = UiStyle.from(16, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF)
                    .withWrap(wrap);
                for (String key : keys) {
                    SectionState state = new SectionState();
                    CollapsibleSection section = new CollapsibleSection(key, state, style);
                    section.setTitle(key);
                    JLabel row = new JLabel(key + " row");
                    section.setOnToggle(() -> {
                        if (section.isExpanded() && section.body().getComponentCount() == 0) {
                            section.body().add(row);
                        }
                    });
                    style.applyTree(section);
                    JLabel title = titleOf(section);
                    int listeners = title.getMouseListeners().length;
                    JLabel child = new JLabel("child");
                    title.add(child);
                    assertEquals(listeners, title.getMouseListeners().length);
                    assertTrue(child.getMouseListeners().length > 0);
                    press(child);
                    assertTrue(key + " wrap=" + wrap, section.isExpanded());
                    assertTrue(section.body().isVisible());
                    assertTrue(section.body().getComponentCount() > 0);
                }
            }
        });
    }

    private static void pressShops(boolean wrap) throws Exception {
        JEIPanel panel = new JEIPanel();
        set(panel, "config", config(wrap));
        UiStyle style = UiStyle.from(16, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF).withWrap(wrap);
        set(panel, "style", style);
        JPanel detail = (JPanel) invoke(panel, "buildDetailPanel");
        SearchItem item = new SearchItem(1203, "Iron dagger", false, 8);
        ItemDetails details = new ItemDetails(
            java.util.Collections.emptyList(),
            Optional.of(new EquipmentStats(
                0, 1, 0, 0, 0,
                0, 0, 0, 0, 0,
                4, 0, 0, 0,
                "weapon", 4)),
            0.4,
            Arrays.asList(
                new ShopEntry("Varrock Swordshop", 32, 5, "gp", true),
                new ShopEntry("Warriors' Guild Armoury", 32, 0, "gp", true)
            ),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList());
        set(panel, "shownItem", item);
        set(panel, "shownDetails", details);
        set(panel, "selectedItemName", item.name);
        invoke(panel, "renderDetails");
        style.applyTree(detail);

        JLabel title = findLabel(detail, "Shop sources");
        assertTrue(title != null);
        assertTrue(title.getMouseListeners().length > 0);
        assertFalse(title.getText().contains("Warriors"));
        press(title);

        CollapsibleSection shops = (CollapsibleSection) get(panel, "shopsSection");
        assertTrue(shops.isExpanded());
        assertTrue(shops.body().isVisible());
        String rows = textOf(shops.body());
        assertTrue(rows.contains("Varrock Swordshop"));
        assertFalse(rows.contains("Warriors"));
    }

    private static void press(Component target) {
        MouseEvent event = new MouseEvent(
            target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
            Math.max(1, target.getWidth() / 2), Math.max(1, target.getHeight() / 2),
            1, false, MouseEvent.BUTTON1);
        target.dispatchEvent(event);
    }

    private static JLabel titleOf(CollapsibleSection section) {
        return (JLabel) ((Container) section.getComponent(0)).getComponent(0);
    }

    private static JEIConfig config(boolean wrap) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("wrapText".equals(name)) {
                    return wrap;
                }
                if ("fontSize".equals(name)) {
                    return 16;
                }
                if ("fontStyle".equals(name)) {
                    return FontStyleChoice.REGULAR;
                }
                if ("fontFamily".equals(name)) {
                    return FontFamilyChoice.DEFAULT;
                }
                if ("dropSort".equals(name)) {
                    return DropSort.RATE;
                }
                if ("pinSize".equals(name)) {
                    return PinSize.MEDIUM;
                }
                if ("ironmanMode".equals(name)) {
                    return IronmanMode.AUTO;
                }
                Class<?> type = method.getReturnType();
                if (type == boolean.class) {
                    return Boolean.FALSE;
                }
                if (type == int.class) {
                    return 0;
                }
                if (type.isEnum()) {
                    return type.getEnumConstants()[0];
                }
                return null;
            }
        };
        return (JEIConfig) Proxy.newProxyInstance(
            JEIConfig.class.getClassLoader(), new Class<?>[] {JEIConfig.class}, handler);
    }

    private static JLabel findLabel(Component root, String text) {
        if (root instanceof JLabel && ((JLabel) root).getText() != null
            && ((JLabel) root).getText().contains(text)) {
            return (JLabel) root;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JLabel found = findLabel(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String textOf(Component root) {
        StringBuilder builder = new StringBuilder();
        collect(root, builder);
        return builder.toString();
    }

    private static void collect(Component root, StringBuilder builder) {
        if (!root.isVisible()) {
            return;
        }
        if (root instanceof JLabel && ((JLabel) root).getText() != null) {
            builder.append(((JLabel) root).getText()).append('\n');
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                collect(child, builder);
            }
        }
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

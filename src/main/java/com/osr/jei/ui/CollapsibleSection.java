package com.osr.jei.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A section header that shows or hides an already-built body.
 * Toggling only changes visibility. It does not load anything.
 */
public final class CollapsibleSection extends JPanel {

    private static final Color HEADER = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color HEADER_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
    private static final String CHEVRON = "#64b4ff";

    private final String key;
    private final SectionState state;
    private final JPanel header;
    private final WrappingLabel titleLabel;
    private final JPanel body;
    private String title = "";
    private Runnable onToggle;

    public CollapsibleSection(String key, SectionState state, UiStyle style) {
        this.key = key;
        this.state = state == null ? new SectionState() : state;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(1, 0, 3, 0));

        header = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(0, preferred.height);
            }
        };
        header.setOpaque(true);
        header.setBackground(HEADER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        titleLabel = new WrappingLabel();
        if (style != null) {
            style.apply(titleLabel, "bodyBold");
        }
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setCursor(header.getCursor());
        header.add(titleLabel, BorderLayout.CENTER);

        MouseAdapter toggle = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                toggle();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                header.setBackground(HEADER_HOVER);
                header.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                header.setBackground(HEADER);
                header.repaint();
            }
        };
        header.addMouseListener(toggle);
        titleLabel.addMouseListener(toggle);
        titleLabel.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent event) {
                if (event.getChild() != null) {
                    event.getChild().addMouseListener(toggle);
                }
            }
        });

        body = new ScrollWidthPanel();
        body.setOpaque(false);
        body.setAlignmentX(LEFT_ALIGNMENT);
        body.setBorder(BorderFactory.createEmptyBorder(1, 8, 2, 0));

        add(header);
        add(body);
        apply();
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
        apply();
    }

    public String getTitle() {
        return title;
    }

    public JPanel body() {
        return body;
    }

    public boolean isExpanded() {
        return state.expanded(key);
    }

    /** Applies the current {@link SectionState} without notifying the toggle listener. */
    public void sync() {
        apply();
    }

    /**
     * Runs after the body is shown or hidden. The runnable must not assume
     * a wiki fetch; the section itself only changes visibility.
     */
    public void setOnToggle(Runnable onToggle) {
        this.onToggle = onToggle;
    }

    /**
     * Shows or hides {@link #body()}. Callers keep the body contents they
     * already built; this method does not replace them.
     */
    public void toggle() {
        state.toggle(key);
        apply();
        if (onToggle != null) {
            onToggle.run();
        }
        revalidate();
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
        }
        repaint();
    }

    private void apply() {
        String mark = state.expanded(key) ? "\u25be" : "\u25b8";
        titleLabel.setHtml("<font color='" + CHEVRON + "'>" + mark + "</font> " + escape(title));
        titleLabel.setToolTipText(title.isEmpty() ? null : title);
        body.setVisible(state.expanded(key));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

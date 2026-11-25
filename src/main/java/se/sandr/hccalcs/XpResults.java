package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

final class XpResults
{
    static final String RATE = "GP/XP";
    static final String RATE_HINT = "Net GP per XP. Positive means profit. Negative means cost.";
    static final int ROW_HEIGHT = 44;
    static final int LIST_HEIGHT = ROW_HEIGHT * 5 + 2;
    static final Color GOLD = new Color(241, 199, 104);
    private static final Color PROFIT = new Color(151, 225, 165);
    private static final Color COST = new Color(255, 159, 162);

    private XpResults() { }

    static String rate(double costPerXp)
    {
        if (!Double.isFinite(costPerXp)) return "—";
        if (costPerXp == 0) return "0gp";
        return (costPerXp < 0 ? "+" : "-") + UtilityCalculator.fmt(Math.abs(costPerXp)) + "gp";
    }

    static Color rateColor(double costPerXp, Color neutral)
    {
        if (!Double.isFinite(costPerXp) || costPerXp == 0) return neutral;
        return costPerXp < 0 ? PROFIT : COST;
    }

    static final class Best extends JPanel
    {
        private final JLabel caption = new JLabel();
        private final JLabel value = new JLabel();

        Best()
        {
            super(new BorderLayout(0, 4));
            setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
            JLabel title = new JLabel("Best GP/XP");
            title.setForeground(GOLD);
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            title.setToolTipText(RATE_HINT);
            caption.putClientProperty("html.disable", true);
            add(title, BorderLayout.NORTH);
            JPanel summary = new JPanel(new BorderLayout(8, 0));
            summary.setOpaque(false);
            value.setFont(value.getFont().deriveFont(Font.BOLD));
            summary.add(caption, BorderLayout.CENTER);
            summary.add(value, BorderLayout.EAST);
            add(summary, BorderLayout.CENTER);
        }

        void showRate(String name, double gpPerXp)
        {
            caption.setText(Double.isFinite(gpPerXp) ? name : "No priced results");
            caption.setToolTipText(name == null ? RATE_HINT : rate(gpPerXp) + " " + RATE + " · " + name);
            value.setText(Double.isFinite(gpPerXp) ? rate(gpPerXp) : "");
            value.setForeground(rateColor(gpPerXp, caption.getForeground()));
            value.setToolTipText(RATE_HINT);
        }
    }

    static final class Cell extends JPanel
    {
        private final JLabel name = new JLabel();
        private final JLabel subtitle = new JLabel();
        private final JLabel value = new JLabel("", SwingConstants.RIGHT);

        Cell()
        {
            super(new BorderLayout(8, 0));
            JPanel description = new JPanel(new BorderLayout());
            description.setOpaque(false);
            name.putClientProperty("html.disable", true);
            subtitle.putClientProperty("html.disable", true);
            description.add(name, BorderLayout.NORTH);
            description.add(subtitle, BorderLayout.SOUTH);
            value.setFont(value.getFont().deriveFont(Font.BOLD));
            add(description, BorderLayout.CENTER);
            add(value, BorderLayout.EAST);
        }

        Cell render(JList<?> list, String title, String meta, Double gpPerXp, boolean selected, boolean focused)
        {
            name.setText(title);
            subtitle.setText(meta);
            subtitle.setVisible(!meta.isEmpty());
            value.setText(gpPerXp == null ? "" : rate(gpPerXp));
            value.setVisible(gpPerXp != null);
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            name.setForeground(foreground);
            subtitle.setForeground(foreground);
            value.setForeground(gpPerXp == null ? foreground : rateColor(gpPerXp, foreground));
            setBorder(BorderFactory.createCompoundBorder(focused ? UIManager.getBorder("List.focusCellHighlightBorder")
                : BorderFactory.createEmptyBorder(1, 1, 1, 1), BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            String description = title + (gpPerXp == null ? "" : " · " + rate(gpPerXp) + " " + RATE);
            setToolTipText(description);
            getAccessibleContext().setAccessibleName(meta.isEmpty() ? description : description + " · " + meta);
            return this;
        }
    }
}

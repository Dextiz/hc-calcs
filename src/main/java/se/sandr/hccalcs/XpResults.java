package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
    static final int ROW_HEIGHT = 39;
    static final int LIST_HEIGHT = ROW_HEIGHT * 6 + 2;
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

    static JLabel rateHeader()
    {
        JLabel label = new JLabel(RATE, SwingConstants.RIGHT);
        label.setToolTipText(RATE_HINT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 6));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
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

        Cell render(JList<?> list, String title, String meta, Double gpPerXp, boolean best, boolean selected, boolean focused)
        {
            name.setText(title);
            subtitle.setText(best ? "Best · " + meta : meta);
            subtitle.setVisible(!meta.isEmpty());
            value.setText(gpPerXp == null ? "" : rate(gpPerXp));
            value.setVisible(gpPerXp != null);
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            name.setForeground(foreground);
            subtitle.setForeground(best ? GOLD : foreground);
            value.setForeground(gpPerXp == null ? foreground : rateColor(gpPerXp, foreground));
            setBorder(BorderFactory.createCompoundBorder(focused ? UIManager.getBorder("List.focusCellHighlightBorder")
                : BorderFactory.createEmptyBorder(1, 1, 1, 1), BorderFactory.createEmptyBorder(3, 4, 3, 4)));
            String description = title + (gpPerXp == null ? "" : " · " + rate(gpPerXp) + " " + RATE);
            setToolTipText(description);
            getAccessibleContext().setAccessibleName((best ? "Best GP/XP · " : "")
                + (meta.isEmpty() ? description : description + " · " + meta));
            return this;
        }
    }
}

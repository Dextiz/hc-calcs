package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import se.sandr.hccalcs.TrainingCalculator.Goal;

import static se.sandr.hccalcs.UtilityCalculator.fmt;
import static se.sandr.hccalcs.UtilityCalculator.gp;
import static se.sandr.hccalcs.UtilityCalculator.signedGp;

final class GoalResult extends JPanel
{
    private final JLabel heading = new JLabel();
    private final JLabel amount = new JLabel();
    private final JLabel quantity = new JLabel();
    private final JLabel buy = new JLabel();

    GoalResult()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        amount.setFont(amount.getFont().deriveFont(Font.BOLD));
        add(line(heading, amount));
        add(line(quantity, buy));
        setVisible(false);
    }

    void showEstimate(int current, int target, Goal goal, String unit, String unavailable)
    {
        if (target == 0)
        {
            setVisible(false);
            return;
        }
        heading.setText("To level " + target);
        double net = goal == null ? Double.NaN : goal.net;
        amount.setText(Double.isFinite(net) ? signedGp(-net) : goal != null ? "Missing prices" : "");
        amount.setForeground(XpResults.rateColor(net, heading.getForeground()));
        amount.setToolTipText(net < 0 ? "Estimated profit after sales" : "Estimated net cost after sales");
        if (current >= target)
            quantity.setText("Goal reached");
        else if (goal == null)
            quantity.setText(unavailable);
        else
            quantity.setText(unit + " " + fmt(goal.attempts));
        buy.setText(goal != null && Double.isFinite(goal.gross) && goal.gross > 0 && goal.gross != net
            ? "Buy " + gp(goal.gross) : "");
        buy.setToolTipText("Upfront cost before sales");
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        setVisible(true);
    }

    private static JPanel line(JLabel left, JLabel right)
    {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setOpaque(false);
        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }
}

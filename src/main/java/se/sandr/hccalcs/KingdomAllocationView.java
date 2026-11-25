package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import net.runelite.client.ui.ColorScheme;
import se.sandr.hccalcs.KingdomCalculator.Recommendation;
import se.sandr.hccalcs.KingdomCalculator.TaskAllocation;

final class KingdomAllocationView extends JPanel
{
    private final JPanel header = new JPanel(new BorderLayout(8, 0));
    private final JLabel workerCount = new JLabel();
    private final JPanel tasks = new JPanel(new GridBagLayout());
    private final JPanel forecast = new JPanel();
    private final JLabel profit = new JLabel();
    private final JLabel period = new JLabel();
    private final JLabel approval = new JLabel();
    private final JTextArea message = new JTextArea();

    KingdomAllocationView()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));

        JLabel heading = new JLabel("Best allocation");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        header.add(heading, BorderLayout.WEST);
        header.add(workerCount, BorderLayout.EAST);
        fitHeight(header);
        addLeft(header);
        tasks.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        addLeft(tasks);

        forecast.setLayout(new BoxLayout(forecast, BoxLayout.Y_AXIS));
        forecast.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        profit.setFont(profit.getFont().deriveFont(Font.BOLD, 18f));
        forecast.add(new JLabel("Estimated profit/day"));
        forecast.add(profit);
        forecast.add(period);
        forecast.add(approval);
        fitHeight(forecast);
        addLeft(forecast);

        message.setEditable(false);
        message.setOpaque(false);
        message.setFocusable(false);
        message.setFont(UIManager.getFont("Label.font"));
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        addLeft(message);
    }

    void showRecommendation(Recommendation result)
    {
        boolean available = result.error == null;
        header.setVisible(available);
        tasks.setVisible(available);
        forecast.setVisible(available);
        tasks.removeAll();
        if (available)
        {
            int assigned = result.tasks.stream().mapToInt(task -> task.workers).sum();
            workerCount.setText(assigned + "/" + result.maxWorkers + " workers");
            for (int index = 0; index < result.tasks.size(); index++)
            {
                addTask(index, result.tasks.get(index));
            }
            profit.setText(UtilityCalculator.gp(Math.round(result.profit / result.days)));
            profit.setForeground(XpResults.rateColor(-result.profit, period.getForeground()));
            period.setText(result.periodSummary());
            approval.setText(result.approval + "% starting approval");
            fitHeight(tasks);
            fitHeight(forecast);
        }
        String note = available ? (result.missingPrices ? "Some HCW sell prices are missing." : "") : result.error;
        message.setText(note);
        message.setVisible(!note.isEmpty());
        revalidate();
        repaint();
    }

    private void addTask(int index, TaskAllocation task)
    {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = index;
        cell.anchor = GridBagConstraints.LINE_START;
        cell.insets = new Insets(4, 0, 4, 0);
        JLabel name = new JLabel(task.name);
        if (task.workers > 0)
            name.setFont(name.getFont().deriveFont(Font.BOLD));
        tasks.add(name, cell);

        WorkerBar bar = new WorkerBar(task);
        name.setLabelFor(bar);
        cell.gridx = 1;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(4, 8, 4, 8);
        tasks.add(bar, cell);

        JLabel count = new JLabel(Integer.toString(task.workers), SwingConstants.RIGHT);
        count.setPreferredSize(new Dimension(count.getFontMetrics(count.getFont()).stringWidth("10"), count.getPreferredSize().height));
        cell.gridx = 2;
        cell.weightx = 0;
        cell.insets = new Insets(4, 0, 4, 0);
        tasks.add(count, cell);
    }

    private void addLeft(javax.swing.JComponent component)
    {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(component);
    }

    private static void fitHeight(JPanel panel)
    {
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
    }

    private static final class WorkerBar extends JProgressBar
    {
        private static final int GAP = 2;
        private static final int CAPACITY = KingdomCalculator.WORKERS_PER_TASK;

        WorkerBar(TaskAllocation task)
        {
            super(0, CAPACITY);
            setValue(task.workers);
            setBorderPainted(false);
            setPreferredSize(new Dimension(72, 8));
            setMinimumSize(new Dimension(36, 8));
            setToolTipText(task.name + " · " + task.workers + "/" + CAPACITY + " workers");
            getAccessibleContext().setAccessibleName(task.name + " workers");
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            int width = getWidth() - GAP * (CAPACITY - 1);
            if (width <= 0) return;
            for (int index = 0; index < CAPACITY; index++)
            {
                int left = index * width / CAPACITY;
                int right = (index + 1) * width / CAPACITY;
                graphics.setColor(index < getValue() ? XpResults.GOLD : ColorScheme.MEDIUM_GRAY_COLOR);
                graphics.fillRect(left + index * GAP, 0, right - left, getHeight());
            }
        }
    }
}

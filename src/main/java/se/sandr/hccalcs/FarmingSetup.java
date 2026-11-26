package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import se.sandr.hccalcs.UtilityCalculator.Values;

final class FarmingSetup extends JPanel
{
    private static final Set<String> FILTERS = Set.of("only", "volume", "sort", "minXp", "category");

    final JPanel quick = new JPanel(new BorderLayout());
    private final JPanel essentials = column();
    private final JPanel general = column();
    private final JPanel yieldOptions = column();
    private final JPanel herbSale = column();
    private final JPanel herbBonuses = column();
    private final JToggleButton bonuses = new JToggleButton("Herb patch bonuses");
    private JComponent patches;
    private JToggleButton secateurs;
    private JComponent magic;

    FarmingSetup()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(essentials);
        add(general);
        add(yieldOptions);
        add(herbSale);
        bonuses.setAlignmentX(LEFT_ALIGNMENT);
        add(bonuses);
        add(herbBonuses);
        bonuses.addActionListener(event -> herbBonuses.setVisible(bonuses.isSelected()));
        collapse();
    }

    static boolean isSetting(String id) { return !FILTERS.contains(id); }

    void collapse()
    {
        setVisible(false);
        quick.setVisible(false);
        bonuses.setSelected(false);
    }

    void clear()
    {
        for (JPanel group : new JPanel[]{essentials, general, yieldOptions, herbSale, herbBonuses}) group.removeAll();
    }

    void addSetting(String id, JComponent control)
    {
        switch (id)
        {
            case "patches": patches = control; essentials.add(control); break;
            case "secateurs": secateurs = (JToggleButton) control; essentials.add(control); break;
            case "compost": essentials.add(control, 0); break;
            case "protection":
            case "bucket": general.add(control); break;
            case "anima": yieldOptions.add(control); break;
            case "sell": herbSale.add(control); break;
            case "magic": magic = control; herbBonuses.add(control); break;
            default: herbBonuses.add(control); break;
        }
        control.setAlignmentX(LEFT_ALIGNMENT);
    }

    void update(Values values, boolean expanded)
    {
        String category = values.text("category");
        boolean herbs = category.equals("Herbs");
        boolean herbOptions = herbs || category.equals(UtilityCalculator.ALL);
        boolean hasYieldOptions = herbOptions || category.equals("Allotments");
        if (herbs && essentials.getParent() != quick) quick.add(essentials, BorderLayout.CENTER);
        else if (!herbs && essentials.getParent() != this) add(essentials, 0);

        patches.setVisible(herbOptions);
        secateurs.setText(values.flag("secateurs") ? "Magic secateurs · On" : "Magic secateurs · Off");
        secateurs.setVisible(hasYieldOptions);
        magic.setVisible(values.flag("resurrect"));
        yieldOptions.setVisible(hasYieldOptions);
        herbSale.setVisible(herbOptions);
        bonuses.setVisible(herbOptions);
        herbBonuses.setVisible(herbOptions && bonuses.isSelected());
        quick.setVisible(herbs);
        if (herbs) quick.setMaximumSize(new Dimension(Integer.MAX_VALUE, quick.getPreferredSize().height));
        setVisible(expanded);
    }

    private static JPanel column()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }
}

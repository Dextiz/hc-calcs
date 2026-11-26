package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;
import se.sandr.hccalcs.RecipeCatalog.Item;
import se.sandr.hccalcs.UtilityCalculator.Field;
import se.sandr.hccalcs.UtilityCalculator.Market;
import se.sandr.hccalcs.UtilityCalculator.Option;
import se.sandr.hccalcs.UtilityCalculator.Row;
import se.sandr.hccalcs.UtilityCalculator.Values;

final class UtilityPanel extends JPanel
{
    private final ConfigManager config;
    private final Runnable onChange;
    private final JPanel quickFilters = new JPanel(new BorderLayout(4, 0));
    private final JPanel quickCategory = new JPanel(new BorderLayout(5, 0));
    private final JPanel combatControls = new JPanel();
    private final JPanel combatTarget = new JPanel(new BorderLayout());
    private final JButton importCombat = new JButton("Import stats & gear");
    private final JLabel combatImportStatus = new JLabel();
    private final JPanel form = new JPanel(new GridLayout(0, 1, 0, 3));
    private final FarmingSetup farmingSetup = new FarmingSetup();
    private final JToggleButton setupButton = new JToggleButton("Setup");
    private final JToggleButton detailsButton = new JToggleButton("Details");
    private final JPanel rowActions = new JPanel(new GridLayout(1, 0, 4, 0));
    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private final JList<Row> results = new JList<>(rows);
    private final JScrollPane scroll;
    private final JLabel count = new JLabel();
    private final JLabel notice = new JLabel();
    private final JLabel rateHeader = XpResults.rateHeader();
    private final JButton optimizeKingdom = new JButton("Optimize for profits");
    private final KingdomAllocationView kingdomAllocation = new KingdomAllocationView();
    private final JTextArea details = new JTextArea();
    private final JTextArea overview = new JTextArea();
    private final GoalResult goalResult = new GoalResult();
    private final JButton sell = new JButton("Edit sell price");
    private final JButton action = new JButton();
    private final JButton marketLink = new JButton("Open HCW market page");
    private Map<Skill, Integer> levels = Map.of();
    private Map<Skill, Integer> xp = Map.of();
    private UtilityCalculator calculator;
    private Market market;
    private boolean kingdomRecommendationRequested;
    private boolean filtersOpen;
    private CombatLoadout combatLoadout;
    private Row bestRow;

    UtilityPanel(ConfigManager config, Runnable onChange, Consumer<List<Item>> editSell,
        Consumer<Consumer<CombatLoadout>> importLoadout)
    {
        this.config = config;
        this.onChange = onChange;
        HcCalcsPanel.spaceHeaderRow(quickCategory, 2);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        combatControls.setLayout(new BoxLayout(combatControls, BoxLayout.Y_AXIS));
        combatControls.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        importCombat.setAlignmentX(Component.LEFT_ALIGNMENT);
        combatTarget.setAlignmentX(Component.LEFT_ALIGNMENT);
        combatImportStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        combatControls.add(importCombat);
        combatControls.add(combatImportStatus);
        combatControls.add(combatTarget);
        combatImportStatus.setVisible(false);
        combatControls.setVisible(false);
        addLeft(combatControls);
        importCombat.addActionListener(event ->
        {
            importCombat.setEnabled(false);
            setCombatStatus("Importing…", null);
            importLoadout.accept(this::showImportedCombat);
        });
        addLeft(farmingSetup.quick);
        addLeft(quickFilters);
        addLeft(form);
        form.setVisible(false);
        addLeft(notice);
        addLeft(rateHeader);
        addLeft(count);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        XpResults.Cell resultCell = new XpResults.Cell();
        results.setCellRenderer((list, row, index, selected, focused) ->
        {
            if (row.gpPerXp == null)
                return resultCell.render(list, row.label, "", null, false, selected, focused);
            String name = row.label.startsWith("★ ") ? "★ " + row.name : row.name;
            String xpText = Double.isFinite(row.xp) ? UtilityCalculator.fmt(row.xp) + " XP" : "XP unavailable";
            return resultCell.render(list, name, "Lvl " + row.level + "  ·  " + xpText, row.gpPerXp,
                row == bestRow, selected, focused);
        });
        scroll = new JScrollPane(results, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // Avoid sizing the RuneLite sidebar from the longest row.
        results.setFixedCellWidth(165);
        scroll.setPreferredSize(new Dimension(190, XpResults.LIST_HEIGHT));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, XpResults.LIST_HEIGHT));
        addLeft(scroll);
        addLeft(goalResult);
        addLeft(optimizeKingdom);
        optimizeKingdom.setVisible(false);
        optimizeKingdom.addActionListener(event ->
        {
            kingdomRecommendationRequested = true;
            renderKingdom();
        });
        addLeft(kingdomAllocation);
        kingdomAllocation.setVisible(false);
        formatText(overview);
        addLeft(overview);
        rowActions.add(setupButton);
        rowActions.add(detailsButton);
        HcCalcsPanel.spaceHeaderRow(rowActions, 0);
        addLeft(rowActions);
        setupButton.addActionListener(event -> farmingSetup.update(values(), setupButton.isSelected()));
        detailsButton.addActionListener(event -> showRow());
        addLeft(farmingSetup);
        formatText(details);
        addLeft(details);
        addLeft(sell);
        addLeft(action);
        addLeft(marketLink);
        results.addListSelectionListener(event -> showRow());
        sell.addActionListener(event -> editSell.accept(selected().outputs));
        action.addActionListener(event -> calculator.perform(this));
        marketLink.addActionListener(event ->
        {
            Row row = selected();
            if (calculator instanceof AlchemyCalculator && row != null && row.itemId > 0)
                LinkBrowser.browse("https://prices.runescape.wiki/osrs-hc/item/" + row.itemId);
        });
    }

    void select(UtilityCalculator selected)
    {
        calculator = selected;
        boolean kingdom = calculator instanceof KingdomCalculator;
        boolean combat = calculator instanceof CombatCalculator;
        detailsButton.setSelected(false);
        setupButton.setSelected(false);
        farmingSetup.collapse();
        setFiltersOpen(filtersOpen);
        combatControls.setVisible(combat);
        scroll.setVisible(!kingdom);
        count.setVisible(!kingdom);
        optimizeKingdom.setVisible(kingdom);
        if (!kingdom)
        {
            kingdomAllocation.setVisible(false);
            buildForm();
        }
        render(false);
    }

    void setFiltersOpen(boolean open)
    {
        filtersOpen = open;
        form.setVisible(open && calculator != null && !(calculator instanceof CombatCalculator)
            && !(calculator instanceof KingdomCalculator));
        quickFilters.setVisible(open && calculator != null && calculator.showsXp(values()));
    }

    JPanel categoryControl() { return quickCategory; }

    void update(Market market, Map<Skill, Integer> levels, Map<Skill, Integer> xp, boolean keepSelection)
    {
        this.market = market;
        this.levels = levels;
        this.xp = xp;
        if (calculator != null)
        {
            render(keepSelection);
        }
    }

    void updateAges()
    {
        if (calculator instanceof AlchemyCalculator)
        {
            market = new Market(market.prices, market.volume, market.overrides, market.catalogue);
            render(true);
        }
    }

    boolean needsVolume() { return calculator != null && calculator.volumeNeeded(values()); }
    void clearCombatLoadout()
    {
        if (combatLoadout != null)
        {
            combatLoadout = null;
            setCombatStatus("", null);
        }
    }
    void setNotice(String message)
    {
        notice.setVisible(!message.isEmpty());
        notice.setText(message);
    }
    Row selected() { return results.getSelectedValue(); }

    Values values()
    {
        Map<String, Object> saved = new LinkedHashMap<>();
        for (Field field : calculator.fields)
        {
            saved.put(field.id, field.parse(config.getConfiguration("hccalcs", calculator.id + "." + field.id)));
        }
        Integer exactXp = calculator.hasTrainingProgress() ? SkillProgressInput.savedExactXp(config, calculator.id) : null;
        return new Values(saved, levels, xp, exactXp);
    }

    void store(Map<String, ?> updates)
    {
        updates.forEach((key, value) -> config.setConfiguration("hccalcs", calculator.id + "." + key, value.toString()));
        buildForm();
        onChange.run();
    }

    private void changed(Field field, Object value)
    {
        config.setConfiguration("hccalcs", calculator.id + "." + field.id, value.toString());
        onChange.run();
    }

    private void buildForm()
    {
        form.removeAll();
        quickFilters.removeAll();
        quickCategory.removeAll();
        combatTarget.removeAll();
        farmingSetup.clear();
        Values values = values();
        for (Field field : calculator.fields)
        {
            if (field.label.isEmpty())
            {
                continue;
            }
            Object current = values.input.get(field.id);
            if (current instanceof Boolean)
            {
                javax.swing.AbstractButton box = calculator instanceof FarmingCalculator && field.id.equals("secateurs")
                    ? new JToggleButton(field.label, (boolean) current) : new JCheckBox(field.label, (boolean) current);
                box.addActionListener(event -> changed(field, box.isSelected()));
                addOption(field, box, false);
            }
            else if (!field.options.isEmpty())
            {
                JComboBox<Option> options = new JComboBox<>(field.options.toArray(new Option[0]));
                options.setSelectedItem(field.options.stream().filter(option -> option.id.equals(current)).findFirst().orElseThrow());
                options.addActionListener(event -> changed(field, ((Option) options.getSelectedItem()).id));
                if (calculator instanceof CombatCalculator && field.id.equals("target"))
                    combatTarget.add(labeled(field.label, options), BorderLayout.CENTER);
                else if (field.id.equals("category") || (calculator instanceof PrayerCalculator && field.id.equals("method")))
                {
                    JLabel label = new JLabel(field.label);
                    label.setLabelFor(options);
                    quickCategory.add(label, BorderLayout.WEST);
                    quickCategory.add(options, BorderLayout.CENTER);
                }
                else
                    addOption(field, options, true);
            }
            else if (current instanceof Number)
            {
                SpinnerNumberModel model = current instanceof Integer
                    ? new SpinnerNumberModel((int) current, (int) field.min, (int) field.max, (int) field.step)
                    : new SpinnerNumberModel((double) current, field.min, field.max, field.step);
                JSpinner spinner = new JSpinner(model);
                if (current instanceof Double)
                    spinner.setEditor(new JSpinner.NumberEditor(spinner, UtilityCalculator.NUMBER_PATTERN));
                spinner.addChangeListener(event -> changed(field, spinner.getValue()));
                if (field.id.equals("magic"))
                    spinner.setToolTipText("0 uses your game level, or level 99 if unavailable.");
                if (field.id.equals("minXp"))
                {
                    spinner.setToolTipText("0 shows all XP amounts.");
                    JLabel label = new JLabel(field.label);
                    label.setLabelFor(spinner);
                    quickFilters.add(label, BorderLayout.WEST);
                    quickFilters.add(spinner, BorderLayout.CENTER);
                }
                else
                {
                    addOption(field, spinner, true);
                }
            }
            else
            {
                JTextField input = new JTextField(current.toString());
                input.getDocument().addDocumentListener(new DocumentListener()
                {
                    public void insertUpdate(DocumentEvent event) { update(); }
                    public void removeUpdate(DocumentEvent event) { update(); }
                    public void changedUpdate(DocumentEvent event) { update(); }
                    private void update() { changed(field, input.getText()); }
                });
                addOption(field, input, true);
            }
        }
        quickFilters.setVisible(filtersOpen && calculator.showsXp(values));
        quickCategory.setVisible(quickCategory.getComponentCount() > 0);
        quickFilters.revalidate();
        quickCategory.revalidate();
        combatTarget.revalidate();
        form.revalidate();
        form.repaint();
    }

    private void addOption(Field field, JComponent control, boolean labeled)
    {
        if (calculator instanceof FarmingCalculator && FarmingSetup.isSetting(field.id))
            farmingSetup.addSetting(field.id, labeled ? HcCalcsPanel.labeled(field.label, control) : control);
        else
            form.add(labeled ? labeled(field.label, control) : control);
    }

    private void render(boolean keepSelection)
    {
        if (calculator instanceof KingdomCalculator)
        {
            renderKingdom();
            return;
        }
        if (market == null)
        {
            return;
        }
        Row previous = keepSelection ? selected() : null;
        Values values = values();
        if (calculator instanceof FarmingCalculator) farmingSetup.update(values, setupButton.isSelected());
        boolean showXp = calculator.showsXp(values);
        quickFilters.setVisible(filtersOpen && showXp);
        rateHeader.setVisible(showXp);
        results.setFixedCellHeight(showXp ? XpResults.ROW_HEIGHT : 26);
        List<Row> available = calculator instanceof CombatCalculator && combatLoadout != null
            ? ((CombatCalculator) calculator).calculate(values, market, combatLoadout) : calculator.calculate(values, market);
        boolean assumedCombat = calculator instanceof CombatCalculator && combatLoadout == null
            && (!values.levels.containsKey(Skill.MAGIC) || !values.levels.containsKey(Skill.RANGED));
        count.setVisible(available.isEmpty() || assumedCombat);
        count.setText(available.isEmpty() ? "No matching results" : "Auto uses 99");
        bestRow = showXp ? available.stream().filter(row -> row.gpPerXp != null && Double.isFinite(row.gpPerXp))
            .min(UtilityCalculator.bestXpOrder()).orElse(null) : null;
        rows.clear();
        rows.addAll(available);
        for (int index = 0; index < available.size(); index++)
        {
            if (previous != null && available.get(index).name.equals(previous.name))
            {
                results.setSelectedIndex(index);
                return;
            }
        }
        if (!available.isEmpty())
        {
            results.setSelectedIndex(0);
            results.ensureIndexIsVisible(0);
        }
        showRow();
    }

    private void renderKingdom()
    {
        quickFilters.setVisible(false);
        rateHeader.setVisible(false);
        rowActions.setVisible(false);
        overview.setVisible(false);
        goalResult.setVisible(false);
        sell.setVisible(false);
        action.setVisible(false);
        marketLink.setVisible(false);
        boolean pricesReady = market != null && market.prices != MarketSnapshot.EMPTY;
        optimizeKingdom.setEnabled(pricesReady);
        boolean showRecommendation = pricesReady && kingdomRecommendationRequested;
        details.setVisible(false);
        kingdomAllocation.setVisible(showRecommendation);
        if (showRecommendation)
        {
            kingdomAllocation.showRecommendation(((KingdomCalculator) calculator).recommend(values(), market));
        }
        revalidate();
        repaint();
    }

    private void showImportedCombat(CombatLoadout loadout)
    {
        importCombat.setEnabled(true);
        combatLoadout = loadout;
        if (loadout == null)
        {
            setCombatStatus("Log in to import stats.", null);
        }
        else
        {
            setCombatStatus("Magic " + loadout.magic + " · Ranged " + loadout.ranged
                + (loadout.missingGear ? " · Partial gear" : ""),
                "Magic attack " + loadout.magicAttack + ", damage " + UtilityCalculator.fmt(loadout.magicDamage)
                    + "% · Ranged attack " + loadout.rangedAttack + ", strength " + loadout.rangedStrength);
        }
        if (calculator instanceof CombatCalculator) render(true);
    }

    private void setCombatStatus(String message, String tooltip)
    {
        combatImportStatus.setText(message);
        combatImportStatus.setToolTipText(tooltip);
        combatImportStatus.setVisible(!message.isEmpty());
    }

    private void showRow()
    {
        Row row = selected();
        boolean show = row != null && !(calculator instanceof KingdomCalculator);
        boolean expanded = show && detailsButton.isSelected();
        boolean farming = calculator instanceof FarmingCalculator;
        Values values = calculator == null ? null : values();
        if (show && calculator.hasTrainingProgress())
        {
            int current = values.trainingProgress(calculator.skill).level;
            goalResult.showEstimate(current, values.integer("goal"), row.goal, farming ? "Patches" : "Actions",
                current < row.level ? "Requires level " + row.level : "Estimate unavailable");
        }
        else goalResult.setVisible(false);
        rowActions.setVisible(show || farming);
        setupButton.setVisible(farming);
        detailsButton.setVisible(show);
        String summary = row == null ? "" : row.summary;
        overview.setText(summary);
        overview.setCaretPosition(0);
        overview.setVisible(!summary.isEmpty());
        if (!summary.isEmpty())
            overview.setMaximumSize(new Dimension(Integer.MAX_VALUE, overview.getPreferredSize().height));
        details.setVisible(expanded);
        details.setText(row == null ? "" : row.detail);
        details.setCaretPosition(0);
        sell.setVisible(expanded && row.outputs.stream().anyMatch(item -> item.id() != null));
        String label = calculator == null ? null : calculator.actionLabel(values, row, market);
        action.setVisible(expanded && label != null);
        action.setText(label);
        marketLink.setVisible(expanded && calculator instanceof AlchemyCalculator && row.itemId > 0);
    }

    private static void formatText(JTextArea text)
    {
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFocusable(false);
        text.setFont(javax.swing.UIManager.getFont("Label.font"));
        text.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
    }

    private void addLeft(javax.swing.JComponent component)
    {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(component);
    }

    private static JPanel labeled(String name, javax.swing.JComponent component)
    {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        JLabel label = new JLabel(name);
        label.setLabelFor(component);
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }
}

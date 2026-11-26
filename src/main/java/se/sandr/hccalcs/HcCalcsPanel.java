package se.sandr.hccalcs;

import static se.sandr.hccalcs.UtilityCalculator.fmt;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.PluginPanel;
import okhttp3.Call;
import se.sandr.hccalcs.AlchemyCalculator.AlchItem;
import se.sandr.hccalcs.RecipeCatalog.Item;
import se.sandr.hccalcs.RecipeCatalog.Recipe;
import se.sandr.hccalcs.RunecraftRecipes.EssencePouch;
import se.sandr.hccalcs.RunecraftRecipes.PouchLoad;
import se.sandr.hccalcs.TrainingCalculator.Amulet;
import se.sandr.hccalcs.TrainingCalculator.Goal;
import se.sandr.hccalcs.TrainingCalculator.Kitchen;
import se.sandr.hccalcs.TrainingCalculator.Result;
import se.sandr.hccalcs.TrainingCalculator.RunecraftHourly;
import se.sandr.hccalcs.TrainingCalculator.Settings;

final class HcCalcsPanel extends PluginPanel
{
    private static final String CONFIG_GROUP = "hccalcs";
    private static final String SEEN_INTRO = "seenIntroVersion";
    private static final String ALL = "All categories";
    private static final Color GOLD = new Color(241, 199, 104);
    private static final DateTimeFormatter PRICE_TIME =
        DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_PRICE_TIME =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter VOLUME_DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private final Map<String, RecipeCatalog> catalogs;
    private final MarketClient marketClient;
    private final ConfigManager config;
    private final JPanel calculatorPanel = new JPanel();
    private final HcCalcsIntro intro;
    private final JComboBox<Object> skill = new JComboBox<>();
    private final SkillProgressInput progressInput;
    private final JPanel trainingControls = new JPanel();
    private final JPanel trainingResults = new JPanel();
    private final UtilityPanel utilities;
    private final JComboBox<String> category = new JComboBox<>();
    private final JComboBox<String> sort = new JComboBox<>(new String[]{"Level", "Best GP/XP", "Highest XP/action"});
    private final JCheckBox onlyAvailable = new JCheckBox("Only show recipes I can use");
    private final JSpinner goal = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
    private final JSpinner minimumXp = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 1.0));
    private final JSpinner minimumVolume = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 100));
    private final JComboBox<Amulet> amulet = new JComboBox<>(Amulet.values());
    private final JCheckBox goggles = new JCheckBox("Prescription goggles");
    private final JComboBox<Kitchen> kitchen = new JComboBox<>(Kitchen.values());
    private final JCheckBox gauntlets = new JCheckBox("Cooking gauntlets");
    private final JSpinner essencePerAltarUse = new JSpinner(new SpinnerNumberModel(
        RunecraftRecipes.DEFAULT_ESSENCE_PER_USE, 1, 28, 1));
    private final JSpinner runsPerHour = new JSpinner(new SpinnerNumberModel(50.0, 0.1, 3600.0, 1.0));
    private final JSpinner essencePerRun = new JSpinner(new SpinnerNumberModel(
        RunecraftRecipes.DEFAULT_ESSENCE_PER_USE, 1, 1000, 1));
    private final JButton resetRunecraftPace = new JButton("Reset");
    private final JButton autoEssencePerRun = new JButton("Auto");
    private final Map<EssencePouch, JCheckBox> essencePouches = new EnumMap<>(EssencePouch.class);
    private final JPanel runecraftPace = new JPanel(new GridLayout(0, 1, 0, 3));
    private final JPanel herbloreOptions = new JPanel();
    private final JPanel cookingOptions = new JPanel();
    private final JPanel runecraftOptions = new JPanel();
    private final JLabel status = new JLabel("HCW market prices");
    private final JLabel resultsStatus = new JLabel();
    private final XpResults.Best bestRate = new XpResults.Best();
    private final JButton refresh = new JButton("Refresh");
    private final JButton sell = new JButton("Edit sell price");
    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private final JList<Row> results = new JList<>(rows);
    private final JTextArea details = new JTextArea();
    private final Timer timer = new Timer(1000, event -> refreshWhenDue());
    private final Map<Integer, Long> overrides = new HashMap<>();
    private Map<Skill, Integer> levels = Map.of();
    private Map<Skill, Integer> experience = Map.of();
    private MarketSnapshot market = MarketSnapshot.EMPTY;
    private MarketSnapshot volume = MarketSnapshot.EMPTY;
    private Call priceCall;
    private Call volumeCall;
    private Call catalogueCall;
    private List<AlchItem> catalogue = List.of();
    private Instant lastPriceAttempt;
    private Instant lastVolumeAttempt;
    private Instant lastCatalogueAttempt;
    private Instant lastAgeRefresh;
    private boolean priceFailed;
    private boolean volumeFailed;
    private boolean catalogueFailed;
    private boolean restoring;
    private boolean restoringRunecraftPace;
    private boolean active;
    private boolean disposed;

    HcCalcsPanel(Map<String, RecipeCatalog> catalogs, List<UtilityCalculator> extra, MarketClient marketClient,
        ConfigManager config, SkillIconManager icons, Consumer<Consumer<CombatLoadout>> importCombatLoadout)
    {
        this.catalogs = catalogs;
        this.marketClient = marketClient;
        this.config = config;
        intro = HcCalcsIntro.VERSION.equals(config.getConfiguration(CONFIG_GROUP, SEEN_INTRO))
            ? null : new HcCalcsIntro(this::openCalculators);
        utilities = new UtilityPanel(config, this::utilityChanged, this::editSale, importCombatLoadout);
        progressInput = new SkillProgressInput(config, this::renderRows);
        setLayout(new BorderLayout());
        calculatorPanel.setLayout(new BoxLayout(calculatorPanel, BoxLayout.Y_AXIS));
        trainingControls.setLayout(new BoxLayout(trainingControls, BoxLayout.Y_AXIS));
        trainingResults.setLayout(new BoxLayout(trainingResults, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel("HCW Calcs");
        heading.setFont(heading.getFont().deriveFont(18f));
        heading.setForeground(GOLD);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        calculatorPanel.add(heading);
        for (Skill item : HcCalcsPlugin.SKILLS) skill.addItem(item);
        extra.forEach(skill::addItem);
        skill.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focused)
            {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                Skill iconSkill = value instanceof Skill ? (Skill) value
                    : value instanceof UtilityCalculator ? ((UtilityCalculator) value).skill : null;
                if (value instanceof Skill)
                {
                    setText(value == Skill.RUNECRAFT ? "Runecrafting" : ((Skill) value).getName());
                }
                if (iconSkill != null) setIcon(new ImageIcon(icons.getSkillImage(iconSkill, true)));
                return this;
            }
        });
        addLeft(skill);

        JPanel filters = new JPanel();
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        filters.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        JToggleButton showFilters = new JToggleButton("Options and filters");
        showFilters.addActionListener(event -> filters.setVisible(showFilters.isSelected()));
        addLeft(trainingControls, labeled("Minimum XP/action", minimumXp));
        minimumXp.setEditor(new JSpinner.NumberEditor(minimumXp, UtilityCalculator.NUMBER_PATTERN));
        addLeft(trainingControls, showFilters);
        addLeft(filters, labeled("Category", category));
        addLeft(filters, labeled("Sort by", sort));
        addLeft(filters, onlyAvailable);
        addLeft(filters, labeled("Goal level (0 = off)", goal));
        addLeft(filters, labeled("Min. 24h volume", minimumVolume));
        minimumVolume.setToolTipText("Minimum HCW buys for every ingredient and sells for every output.");
        addLeft(trainingControls, filters);
        filters.setVisible(false);

        herbloreOptions.setLayout(new GridLayout(0, 1, 0, 3));
        herbloreOptions.add(labeled("Amulet", amulet));
        herbloreOptions.add(goggles);
        addLeft(filters, herbloreOptions);

        cookingOptions.setLayout(new GridLayout(0, 1, 0, 3));
        cookingOptions.add(labeled("Cooking method", kitchen));
        cookingOptions.add(gauntlets);
        addLeft(filters, cookingOptions);
        runecraftOptions.setLayout(new BoxLayout(runecraftOptions, BoxLayout.Y_AXIS));
        essencePerAltarUse.setToolTipText("Loose essence slots before packing pouches. Each pouch takes one slot.");
        addLeft(runecraftOptions, labeled("Base essence slots", essencePerAltarUse));
        addLeft(runecraftOptions, new JLabel("Essence pouches"));
        JPanel pouchOptions = new JPanel(new GridLayout(0, 2, 3, 0));
        for (EssencePouch pouch : EssencePouch.values())
        {
            JCheckBox box = new JCheckBox(pouch.label);
            box.setToolTipText(pouch == EssencePouch.COLOSSAL ? "Replaces other pouches. Capacity scales with level."
                : pouch.capacityAt(99) + " essence. Requires level " + pouch.level + ".");
            box.addActionListener(event ->
            {
                refreshPouchOptions();
                changed(pouchSetting(pouch), box.isSelected());
            });
            essencePouches.put(pouch, box);
            pouchOptions.add(box);
        }
        pouchOptions.setMaximumSize(new Dimension(Integer.MAX_VALUE, pouchOptions.getPreferredSize().height));
        addLeft(runecraftOptions, pouchOptions);
        addLeft(filters, runecraftOptions);

        runsPerHour.setEditor(new JSpinner.NumberEditor(runsPerHour, UtilityCalculator.NUMBER_PATTERN));
        JPanel paceControl = new JPanel(new BorderLayout(3, 0));
        paceControl.add(runsPerHour, BorderLayout.CENTER);
        paceControl.add(resetRunecraftPace, BorderLayout.EAST);
        resetRunecraftPace.setToolTipText("Restore this method's estimated runs and essence per run.");
        JPanel essenceControl = new JPanel(new BorderLayout(3, 0));
        essenceControl.add(essencePerRun, BorderLayout.CENTER);
        essenceControl.add(autoEssencePerRun, BorderLayout.EAST);
        autoEssencePerRun.setToolTipText("Use the estimated capacity for this method.");
        runecraftPace.add(labeled("Runs per hour", paceControl));
        runecraftPace.add(labeled("Essence per run", essenceControl));
        runecraftPace.setMaximumSize(new Dimension(Integer.MAX_VALUE, runecraftPace.getPreferredSize().height));
        addLeft(trainingControls, runecraftPace);

        JPanel priceControls = new JPanel(new BorderLayout(4, 0));
        priceControls.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        priceControls.add(status, BorderLayout.CENTER);
        priceControls.add(refresh, BorderLayout.EAST);
        priceControls.setMaximumSize(new Dimension(Integer.MAX_VALUE, priceControls.getPreferredSize().height));
        addLeft(priceControls);

        addLeft(progressInput);
        addLeft(trainingControls);
        addLeft(trainingResults);
        addLeft(trainingResults, bestRate);
        addLeft(trainingResults, resultsStatus);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        XpResults.Cell resultCell = new XpResults.Cell();
        results.setCellRenderer((list, row, index, selected, focused) -> resultCell.render(list, row.recipe.name,
            "Lvl " + row.recipe.level + "  ·  " + fmt(row.recipe.xp)
                + (row.recipe.runecraft != null ? " XP/essence" : row.recipe.range == null ? " XP" : " XP/cooked"),
            -row.result.gpPerXp, selected, focused));
        JScrollPane scroll = new JScrollPane(results, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        results.setFixedCellHeight(XpResults.ROW_HEIGHT);
        results.setFixedCellWidth(165);
        scroll.setPreferredSize(new Dimension(190, XpResults.LIST_HEIGHT));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, XpResults.LIST_HEIGHT));
        addLeft(trainingResults, scroll);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFocusable(false);
        details.setFont(javax.swing.UIManager.getFont("Label.font"));
        details.setBorder(BorderFactory.createEmptyBorder(6, 3, 6, 3));
        addLeft(trainingResults, details);
        addLeft(trainingResults, sell);
        addLeft(utilities);

        String selected = config.getConfiguration(CONFIG_GROUP, "calculator");
        for (int index = 0; index < skill.getItemCount(); index++)
        {
            if (calculatorId(skill.getItemAt(index)).equalsIgnoreCase(selected))
            {
                skill.setSelectedIndex(index);
                break;
            }
        }
        configureSelection();

        skill.addActionListener(event ->
        {
            remember("calculator", calculatorId(skill.getSelectedItem()));
            configureSelection();
        });
        category.addActionListener(event -> changed("category", category.getSelectedItem()));
        sort.addActionListener(event -> changed("sort", sort.getSelectedIndex()));
        onlyAvailable.addActionListener(event -> changed("availableOnly", onlyAvailable.isSelected()));
        goal.addChangeListener(event -> changed("goal", goal.getValue()));
        minimumXp.addChangeListener(event -> changed("minXp", minimumXp.getValue()));
        minimumVolume.addChangeListener(event -> changed("minVolume", minimumVolume.getValue()));
        amulet.addActionListener(event -> changed("amulet", amulet.getSelectedItem()));
        goggles.addActionListener(event -> changed("goggles", goggles.isSelected()));
        kitchen.addActionListener(event -> changed("kitchen", kitchen.getSelectedItem()));
        gauntlets.addActionListener(event -> changed("gauntlets", gauntlets.isSelected()));
        essencePerAltarUse.addChangeListener(event -> changed("essencePerUse", essencePerAltarUse.getValue()));
        runsPerHour.addChangeListener(event -> rememberRunecraftPace("runsPerHour", runsPerHour.getValue()));
        essencePerRun.addChangeListener(event -> rememberRunecraftPace("essencePerRun", essencePerRun.getValue()));
        autoEssencePerRun.addActionListener(event ->
        {
            Row row = results.getSelectedValue();
            if (row == null || row.recipe.runecraft == null) return;
            config.unsetConfiguration(CONFIG_GROUP, runecraftPaceKey(row.recipe) + "essencePerRun");
            showDetails();
        });
        resetRunecraftPace.addActionListener(event ->
        {
            Row row = results.getSelectedValue();
            if (row == null || row.recipe.runecraft == null) return;
            String prefix = runecraftPaceKey(row.recipe);
            config.unsetConfiguration(CONFIG_GROUP, prefix + "runsPerHour");
            config.unsetConfiguration(CONFIG_GROUP, prefix + "essencePerRun");
            showDetails();
        });
        results.addListSelectionListener(event -> showDetails());
        refresh.addActionListener(event -> fetchPrices(true));
        sell.addActionListener(event ->
        {
            Row row = results.getSelectedValue();
            if (row != null) editSale(row.result.outputs);
        });
        add(intro == null ? calculatorPanel : intro, BorderLayout.CENTER);
    }

    @Override
    public void onActivate()
    {
        active = true;
        if (calculatorPanel.getParent() == this)
        {
            startMarket();
        }
    }

    @Override
    public void onDeactivate()
    {
        active = false;
        timer.stop();
    }

    private void openCalculators()
    {
        remember(SEEN_INTRO, HcCalcsIntro.VERSION);
        remove(intro);
        add(calculatorPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        if (active)
        {
            startMarket();
        }
        skill.requestFocusInWindow();
    }

    private void startMarket()
    {
        refreshWhenDue();
        timer.start();
    }

    void dispose()
    {
        disposed = true;
        timer.stop();
        if (priceCall != null)
        {
            priceCall.cancel();
        }
        if (volumeCall != null)
        {
            volumeCall.cancel();
        }
        if (catalogueCall != null)
        {
            catalogueCall.cancel();
        }
    }

    void setStats(Map<Skill, Integer> newLevels, Map<Skill, Integer> newXp)
    {
        UtilityCalculator utility = selectedUtility();
        Skill relevant = utility == null ? selectedSkill() : utility.skill;
        boolean changed = !levels.equals(newLevels) || relevant != null
            && !java.util.Objects.equals(experience.get(relevant), newXp.get(relevant));
        levels = newLevels;
        experience = newXp;
        if (newLevels.isEmpty()) utilities.clearCombatLoadout();
        if (changed)
        {
            renderRows();
        }
    }

    private void configureSelection()
    {
        UtilityCalculator utility = selectedUtility();
        boolean showsProgress = utility == null || utility.hasTrainingProgress();
        progressInput.setVisible(showsProgress);
        if (showsProgress)
        {
            Skill selected = utility == null ? selectedSkill() : utility.skill;
            progressInput.configure(utility == null ? key(selected) : utility.id, utility == null ? "current" : "level",
                () -> resolveProgress(selected, null).xp);
        }
        trainingControls.setVisible(utility == null);
        trainingResults.setVisible(utility == null);
        utilities.setVisible(utility != null);
        if (utility == null)
        {
            configureSkill();
        }
        else
        {
            utilities.select(utility);
            utilityChanged();
        }
    }

    private void utilityChanged()
    {
        renderRows();
        if (timer.isRunning())
        {
            fetchVolume(false);
            fetchCatalogue(false);
        }
    }

    private void configureSkill()
    {
        restoring = true;
        category.removeAllItems();
        category.addItem(ALL);
        catalog().rows.stream().map(recipe -> recipe.category).distinct().forEach(category::addItem);
        category.setSelectedItem(setting("category", String.class, ALL));
        sort.setSelectedIndex(Math.max(0, Math.min(sort.getItemCount() - 1, setting("sort", int.class, 1))));
        onlyAvailable.setSelected(setting("availableOnly", boolean.class, true));
        goal.setValue(setting("goal", int.class, 0));
        minimumXp.setValue(setting("minXp", double.class, 0.0));
        minimumVolume.setValue(setting("minVolume", int.class, 0));
        amulet.setSelectedItem(setting("amulet", Amulet.class, Amulet.NONE));
        goggles.setSelected(setting("goggles", boolean.class, false));
        kitchen.setSelectedItem(setting("kitchen", Kitchen.class, Kitchen.RANGE));
        gauntlets.setSelected(setting("gauntlets", boolean.class, false));
        herbloreOptions.setVisible(selectedSkill() == Skill.HERBLORE);
        cookingOptions.setVisible(selectedSkill() == Skill.COOKING);
        runecraftOptions.setVisible(selectedSkill() == Skill.RUNECRAFT);
        runecraftPace.setVisible(selectedSkill() == Skill.RUNECRAFT);
        if (selectedSkill() == Skill.RUNECRAFT)
        {
            int batch = setting("essencePerUse", int.class, RunecraftRecipes.DEFAULT_ESSENCE_PER_USE);
            essencePerAltarUse.setValue(Math.max(1, Math.min(28, batch)));
            essencePouches.forEach((pouch, box) -> box.setSelected(setting(pouchSetting(pouch), boolean.class, false)));
            refreshPouchOptions();
        }
        minimumXp.setToolTipText(selectedSkill() == Skill.RUNECRAFT ? "Filters by XP per essence or fragment."
            : "0 shows every recipe. Cooking uses XP per successful cook.");
        restoring = false;
        if (timer.isRunning())
        {
            fetchVolume(false);
        }
        renderRows(false);
    }

    private void changed(String name, Object value)
    {
        if (restoring || value == null)
        {
            return;
        }
        remember(preference(name), value);
        if (timer.isRunning())
        {
            fetchVolume(false);
        }
        renderRows();
    }

    private <T> T setting(String name, Class<T> type, T fallback)
    {
        T value = config.getConfiguration(CONFIG_GROUP, preference(name), type);
        return value == null ? fallback : value;
    }

    private void remember(String name, Object value)
    {
        config.setConfiguration(CONFIG_GROUP, name, value);
    }

    private String preference(String name) { return key(selectedSkill()) + "." + name; }
    private Skill selectedSkill() { return (Skill) skill.getSelectedItem(); }
    private UtilityCalculator selectedUtility()
    {
        Object selected = skill.getSelectedItem();
        return selected instanceof UtilityCalculator ? (UtilityCalculator) selected : null;
    }
    private static String calculatorId(Object calculator)
    {
        return calculator instanceof Skill ? ((Skill) calculator).name() : ((UtilityCalculator) calculator).id;
    }
    private RecipeCatalog catalog() { return catalogs.get(key(selectedSkill())); }
    private Settings trainingSettings()
    {
        int essencePerUse = selectedSkill() == Skill.RUNECRAFT ? runecraftLoad().loose : integer(essencePerAltarUse);
        return new Settings((Amulet) amulet.getSelectedItem(), goggles.isSelected(),
            (Kitchen) kitchen.getSelectedItem(), gauntlets.isSelected(), essencePerUse);
    }

    private PouchLoad runecraftLoad()
    {
        EnumSet<EssencePouch> selected = EnumSet.noneOf(EssencePouch.class);
        essencePouches.forEach((pouch, box) -> { if (box.isSelected()) selected.add(pouch); });
        return RunecraftRecipes.pouchLoad(currentLevel(), integer(essencePerAltarUse), selected);
    }

    private void refreshPouchOptions()
    {
        boolean colossal = essencePouches.get(EssencePouch.COLOSSAL).isSelected();
        essencePouches.forEach((pouch, box) -> box.setEnabled(pouch == EssencePouch.COLOSSAL || !colossal));
    }

    private static String pouchSetting(EssencePouch pouch)
    {
        return "pouch." + pouch.name().toLowerCase(Locale.ROOT);
    }

    private static String key(Skill skill) { return skill.name().toLowerCase(Locale.ROOT); }
    private static int integer(JSpinner input) { return ((Number) input.getValue()).intValue(); }

    private int currentLevel()
    {
        return currentProgress().level;
    }

    private SkillProgress currentProgress()
    {
        return resolveProgress(selectedSkill(), progressInput.exactXp());
    }

    private SkillProgress resolveProgress(Skill skill, Integer exactXp)
    {
        return SkillProgress.resolve(progressInput.level(), exactXp, levels.get(skill), experience.get(skill));
    }

    private void renderRows()
    {
        renderRows(true);
    }

    private void renderRows(boolean keepSelection)
    {
        if (selectedUtility() != null)
        {
            utilities.update(new UtilityCalculator.Market(market, volume, overrides, catalogue), levels, experience, keepSelection);
            String notice = "";
            if (selectedUtility() instanceof AlchemyCalculator && catalogueFailed)
                notice = catalogue.isEmpty() ? "Item catalogue unavailable" : "Using last item catalogue";
            else if (utilities.needsVolume() && volumeFailed)
                notice = volume == MarketSnapshot.EMPTY ? "HCW volume unavailable" : "Using last HCW volume";
            utilities.setNotice(notice);
            return;
        }
        Row selected = keepSelection ? results.getSelectedValue() : null;
        SkillProgress progress = currentProgress();
        int level = progress.level;
        int magic = levels.getOrDefault(Skill.MAGIC, UtilityCalculator.DEFAULT_LEVEL);
        Settings settings = trainingSettings();
        double xp = ((Number) minimumXp.getValue()).doubleValue();
        int tradeVolume = integer(minimumVolume);
        List<Row> eligible = new ArrayList<>();
        for (Recipe recipe : catalog().rows)
        {
            boolean levelLocked = recipe.level > level || recipe.runecraft != null && recipe.runecraft.magicLevel > magic;
            if ((onlyAvailable.isSelected() && levelLocked) || recipe.xp < xp ||
                (!ALL.equals(category.getSelectedItem()) && !recipe.category.equals(category.getSelectedItem())))
            {
                continue;
            }
            Result result = TrainingCalculator.calculate(recipe, catalog().taxExempt, settings, level, market, overrides);
            if (TrainingCalculator.meetsVolume(result, recipe, volume, tradeVolume))
            {
                eligible.add(new Row(recipe, result));
            }
        }
        Comparator<Row> order = Comparator.comparingInt(row -> row.recipe.level);
        if (sort.getSelectedIndex() == 1)
        {
            order = Comparator.<Row>comparingDouble(row ->
                Double.isFinite(row.result.gpPerXp) ? -row.result.gpPerXp : Double.POSITIVE_INFINITY).thenComparing(order);
        }
        else if (sort.getSelectedIndex() == 2)
        {
            order = Comparator.<Row>comparingDouble(row -> -row.recipe.xp).thenComparing(order);
        }
        eligible.sort(order);
        Row best = eligible.stream().filter(row -> Double.isFinite(row.result.gpPerXp))
            .max(Comparator.comparingDouble(row -> row.result.gpPerXp)).orElse(null);
        bestRate.showRate(best == null ? null : best.recipe.name, best == null ? Double.NaN : -best.result.gpPerXp);
        Row keep = selected == null ? null : eligible.stream().filter(row -> row.recipe == selected.recipe)
            .findFirst().orElse(null);
        rows.clear();
        rows.addAll(eligible);
        if (keep != null)
        {
            results.setSelectedValue(keep, true);
        }
        else if (!eligible.isEmpty())
        {
            results.setSelectedIndex(0);
            results.ensureIndexIsVisible(0);
        }
        resultsStatus.setText(progress.levelLabel + " · " + eligible.size() + " recipes");
        if (tradeVolume > 0)
        {
            resultsStatus.setToolTipText(volume == MarketSnapshot.EMPTY
                ? (volumeFailed ? "HCW volume unavailable" : "Waiting for HCW volume")
                : "Volume for " + VOLUME_DATE.format(volume.asOf) + " UTC" +
                    (volumeFailed ? " · update failed" : ""));
        }
        else
        {
            resultsStatus.setToolTipText(selectedSkill() == Skill.RUNECRAFT
                ? "Base rune yields per essence. Outfit bonuses and travel costs are excluded." : null);
        }
        showDetails();
    }

    private void showDetails()
    {
        Row row = results.getSelectedValue();
        configureRunecraftPace(row == null ? null : row.recipe);
        if (row == null)
        {
            sell.setEnabled(false);
            String reason = resultsStatus.getToolTipText();
            details.setText(integer(minimumVolume) > 0 && volume == MarketSnapshot.EMPTY
                ? (reason == null ? "Waiting for HCW volume" : reason) : "No recipes match your filters.");
            return;
        }
        Recipe recipe = row.recipe;
        Result result = row.result;
        boolean runecraft = recipe.runecraft != null;
        StringBuilder summary = new StringBuilder(recipe.name).append("\n")
            .append(fmt(recipe.xp)).append(runecraft ? " XP/essence\n" : " XP/action\n");
        if (runecraft)
        {
            summary.append(fmt(result.outputs.get(0).quantity())).append(" runes made per essence\n");
        }
        if (recipe.range != null)
        {
            summary.append("Burn rate ")
                .append(Double.isFinite(result.success) ? fmt(100 * (1 - result.success)) + "%" : "—")
                .append("\n");
        }
        summary.append("Buy cost ").append(gp(result.cost)).append("\n")
            .append("Net sale ").append(gp(result.netSale)).append("\n")
            .append(runecraft ? "Profit/essence " : "Profit/action ")
            .append(gp(result.netSale * result.success - result.cost)).append("\n");
        if (runecraft && !recipe.runecraft.note.isEmpty())
        {
            summary.append(recipe.runecraft.note).append("\n");
        }
        if (runecraft)
        {
            RunecraftHourly hourly = TrainingCalculator.runecraftHourly(recipe, result, runecraftLoad().loose,
                integer(essencePerRun), ((Number) runsPerHour.getValue()).doubleValue());
            summary.append("\nPer hour\n").append(fmt(hourly.xp)).append(" XP · ")
                .append(fmt(hourly.runes)).append(" runes\nNet profit ").append(gp(hourly.profit)).append("\n");
        }
        if (selectedSkill() == Skill.SMITHING && recipe.category.equals("Blast Furnace"))
        {
            summary.append("Excludes Blast Furnace fees.\n");
        }
        int target = integer(goal);
        if (target > 0)
        {
            SkillProgress progress = currentProgress();
            Goal plan = TrainingCalculator.goal(recipe, result, trainingSettings(), progress.level, progress.xp, target);
            summary.append("\nTo level ").append(target).append("\n");
            if (plan != null)
            {
                summary.append(fmt(plan.attempts)).append(runecraft ? " essence\n" : " actions\n")
                    .append("Buy ").append(gp(plan.gross)).append("\n")
                    .append("Net cost ").append(gp(plan.net)).append("\n");
            }
            else
            {
                summary.append("Goal unavailable at this level\n");
            }
        }
        summary.append(runecraft ? "\nIngredients per essence\n" : "\nIngredients\n");
        for (Item item : runecraft ? recipe.inputs : result.inputs)
        {
            summary.append(fmt(item.quantity())).append(" × ").append(item.name()).append(" · ")
                .append(item.id() == null ? "Self-gathered" : gp(market.buy(item.id())) + " each").append("\n");
        }
        if (runecraft && !recipe.runecraft.perUse.isEmpty())
        {
            summary.append("\nPer altar use of ").append(runecraftLoad().loose).append(" essence\n");
            for (Item item : recipe.runecraft.perUse)
            {
                summary.append(item.quantity() < 1 ? "1/16" : fmt(item.quantity())).append(" × ").append(item.name())
                    .append(" · ").append(gp(market.buy(item.id()))).append(" each\n");
            }
        }
        summary.append("\nOutputs\n");
        boolean canEdit = false;
        for (Item item : result.outputs)
        {
            canEdit |= item.id() != null;
            summary.append(fmt(item.quantity())).append(" × ").append(item.name()).append(" · ")
                .append(gp(TrainingCalculator.salePrice(item.id(), market, overrides))).append(" each\n");
        }
        details.setText(summary.toString());
        details.setCaretPosition(0);
        sell.setEnabled(canEdit);
    }

    private void configureRunecraftPace(Recipe recipe)
    {
        boolean selected = recipe != null && recipe.runecraft != null;
        runsPerHour.setEnabled(selected);
        essencePerRun.setEnabled(selected);
        autoEssencePerRun.setEnabled(false);
        resetRunecraftPace.setEnabled(selected);
        if (!selected) return;

        String prefix = runecraftPaceKey(recipe);
        Double savedRate = config.getConfiguration(CONFIG_GROUP, prefix + "runsPerHour", double.class);
        Integer savedEssence = config.getConfiguration(CONFIG_GROUP, prefix + "essencePerRun", int.class);
        double defaultRate = recipe.runecraft.defaultRunsPerHour;
        int defaultEssence = recipe.runecraft.defaultEssencePerRun(runecraftLoad().total);
        boolean manualEssence = savedEssence != null && savedEssence >= 1 && savedEssence <= 1000;
        restoringRunecraftPace = true;
        runsPerHour.setValue(savedRate != null && savedRate >= 0.1 && savedRate <= 3600 ? savedRate : defaultRate);
        essencePerRun.setValue(manualEssence ? savedEssence : defaultEssence);
        restoringRunecraftPace = false;
        autoEssencePerRun.setEnabled(manualEssence);
        essencePerRun.setToolTipText((manualEssence ? "Manual value. Auto estimates " : "Estimated capacity is ")
            + defaultEssence + " essence per trip.");
        runsPerHour.setToolTipText("Estimated starting pace: " + fmt(defaultRate) + " full trips per hour.");
    }

    private void rememberRunecraftPace(String setting, Object value)
    {
        Row row = results.getSelectedValue();
        if (restoringRunecraftPace || row == null || row.recipe.runecraft == null) return;
        remember(runecraftPaceKey(row.recipe) + setting, value);
        showDetails();
    }

    private static String runecraftPaceKey(Recipe recipe)
    {
        return "runecraft." + recipe.outputs.get(0).id() + (recipe.selfGatheredFree ? ".arceuus." : ".");
    }

    private void editSale(List<Item> outputs)
    {
        List<Item> items = new ArrayList<>();
        for (Item item : outputs)
        {
            if (item.id() != null)
            {
                items.add(item);
            }
        }
        if (items.isEmpty())
        {
            return;
        }
        JComboBox<String> choices = new JComboBox<>(items.stream().map(Item::name).toArray(String[]::new));
        JSpinner input = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        Runnable showPrice = () ->
        {
            double quote = TrainingCalculator.salePrice(items.get(choices.getSelectedIndex()).id(), market, overrides);
            input.setValue(Double.isFinite(quote) && quote <= Integer.MAX_VALUE ? (int) quote : 0);
        };
        showPrice.run();
        choices.addActionListener(event -> showPrice.run());
        int answer = JOptionPane.showConfirmDialog(this, new Object[]{choices, "Sell price (0 = HCW market)", input},
            "Edit sell price", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer == JOptionPane.OK_OPTION)
        {
            try
            {
                input.commitEdit();
            }
            catch (java.text.ParseException ex)
            {
                return;
            }
            Integer id = items.get(choices.getSelectedIndex()).id();
            long value = integer(input);
            if (value == 0)
            {
                overrides.remove(id);
            }
            else
            {
                overrides.put(id, value);
            }
            renderRows();
        }
    }

    private void refreshWhenDue()
    {
        fetchPrices(false);
        fetchVolume(false);
        fetchCatalogue(false);
        if (selectedUtility() != null && secondsSince(lastAgeRefresh) >= 30)
        {
            lastAgeRefresh = Instant.now();
            utilities.updateAges();
        }
        updateMarketStatus();
    }

    private void fetchPrices(boolean manual)
    {
        long interval = manual ? MarketClient.MANUAL_COOLDOWN_SECONDS : MarketClient.REFRESH_SECONDS;
        if (disposed || priceCall != null || secondsSince(lastPriceAttempt) < interval)
        {
            return;
        }
        lastPriceAttempt = Instant.now();
        priceCall = marketClient.fetch(false, snapshot ->
        {
            if (disposed)
            {
                return;
            }
            priceCall = null;
            priceFailed = snapshot == null;
            if (snapshot != null)
            {
                boolean hadPrices = market != MarketSnapshot.EMPTY;
                market = snapshot;
                overrides.clear();
                renderRows(hadPrices);
            }
            updateMarketStatus();
        });
        if (manual)
        {
            fetchVolume(true);
            fetchCatalogue(true);
        }
        updateMarketStatus();
    }

    private void fetchVolume(boolean manual)
    {
        long interval = manual ? MarketClient.MANUAL_COOLDOWN_SECONDS : MarketClient.REFRESH_SECONDS;
        boolean needed = selectedUtility() == null ? integer(minimumVolume) > 0 : utilities.needsVolume();
        if (disposed || !needed || volumeCall != null || secondsSince(lastVolumeAttempt) < interval)
        {
            return;
        }
        lastVolumeAttempt = Instant.now();
        volumeCall = marketClient.fetch(true, snapshot ->
        {
            if (disposed)
            {
                return;
            }
            volumeCall = null;
            volumeFailed = snapshot == null;
            if (snapshot != null)
            {
                volume = snapshot;
            }
            renderRows();
        });
    }

    private void fetchCatalogue(boolean manual)
    {
        long interval = manual ? MarketClient.MANUAL_COOLDOWN_SECONDS : MarketClient.REFRESH_SECONDS;
        if (disposed || !(selectedUtility() instanceof AlchemyCalculator)
            || catalogueCall != null || secondsSince(lastCatalogueAttempt) < interval) return;
        lastCatalogueAttempt = Instant.now();
        catalogueCall = marketClient.fetchItems(items ->
        {
            if (disposed) return;
            catalogueCall = null;
            catalogueFailed = items == null;
            if (items != null) catalogue = items;
            if (selectedUtility() instanceof AlchemyCalculator) renderRows();
        });
    }

    private void updateMarketStatus()
    {
        String updated = market == MarketSnapshot.EMPTY ? "" : PRICE_TIME.format(market.asOf);
        if (priceCall != null)
        {
            status.setText("Updating HCW…");
        }
        else if (priceFailed)
        {
            status.setText(updated.isEmpty() ? "No HCW prices" : "HCW · last quote");
        }
        else
        {
            status.setText(updated.isEmpty() ? "HCW prices" : "HCW · " + SHORT_PRICE_TIME.format(market.asOf));
        }
        status.setToolTipText(updated.isEmpty() ? null : "HCW snapshot " + updated + (priceFailed ? " · update failed" : ""));
        long wait = MarketClient.MANUAL_COOLDOWN_SECONDS - secondsSince(lastPriceAttempt);
        refresh.setEnabled(priceCall == null && wait <= 0);
        refresh.setText(wait > 0 ? "Refresh " + wait + "s" : "Refresh");
    }

    private static long secondsSince(Instant last)
    {
        return last == null ? Long.MAX_VALUE : Math.max(0, Duration.between(last, Instant.now()).getSeconds());
    }

    private String gp(double value) { return Double.isFinite(value) ? UtilityCalculator.gp(value) : "—"; }

    private void addLeft(javax.swing.JComponent component)
    {
        addLeft(calculatorPanel, component);
    }

    private static void addLeft(JPanel parent, javax.swing.JComponent component)
    {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(component);
    }

    private static JPanel labeled(String name, Component control)
    {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        JLabel label = new JLabel(name);
        label.setLabelFor(control);
        panel.add(label, BorderLayout.WEST);
        panel.add(control, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private static final class Row
    {
        final Recipe recipe;
        final Result result;

        Row(Recipe recipe, Result result)
        {
            this.recipe = recipe;
            this.result = result;
        }
    }
}

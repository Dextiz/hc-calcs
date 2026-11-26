package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import net.runelite.api.Experience;
import net.runelite.client.config.ConfigManager;

final class SkillProgressInput extends JPanel
{
    private static final String GROUP = "hccalcs";
    private static final String EXACT_MODE = ".exactXpMode";
    private static final String EXACT_XP = ".exactXp";
    private final JToggleButton levelButton = new JToggleButton("Level");
    private final JToggleButton xpButton = new JToggleButton("Exact XP");
    private final JSpinner level = new JSpinner(new SpinnerNumberModel(0, 0, Experience.MAX_REAL_LEVEL, 1));
    private final JSpinner xp = new JSpinner(new SpinnerNumberModel(0, 0, Experience.MAX_SKILL_XP, 1));
    private final CardLayout fields = new CardLayout();
    private final JPanel input = new JPanel(fields);
    private final ConfigManager config;
    private final Runnable onChange;
    private String scope;
    private String levelKey;
    private IntSupplier seedXp;
    private boolean restoring;

    SkillProgressInput(ConfigManager config, Runnable onChange)
    {
        super(new BorderLayout(0, 4));
        this.config = config;
        this.onChange = onChange;

        ButtonGroup modes = new ButtonGroup();
        modes.add(levelButton);
        modes.add(xpButton);
        JPanel toggle = new JPanel(new GridLayout(1, 2, 3, 0));
        toggle.add(levelButton);
        toggle.add(xpButton);
        add(toggle, BorderLayout.NORTH);

        level.setToolTipText("0 uses your game level, or level 99 if unavailable.");
        xp.setToolTipText("Enter the skill's total XP. The calculator uses it for unlocks and level goals.");
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(xp, "#,##0");
        editor.getFormat().setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.UK));
        xp.setEditor(editor);
        input.add(labeled("Level (0 = auto)", level), "level");
        input.add(labeled("Current XP", xp), "xp");
        add(input, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

        levelButton.addActionListener(event -> selectMode(false));
        xpButton.addActionListener(event -> selectMode(true));
        level.addChangeListener(event -> save(levelKey, level.getValue()));
        xp.addChangeListener(event -> save(scope + EXACT_XP, xp.getValue()));
    }

    void configure(String scope, String levelField, IntSupplier seedXp)
    {
        this.scope = scope;
        this.levelKey = scope + "." + levelField;
        this.seedXp = seedXp;
        restoring = true;
        Integer savedLevel = config.getConfiguration(GROUP, levelKey, int.class);
        level.setValue(savedLevel == null ? 0 : Math.max(0, Math.min(Experience.MAX_REAL_LEVEL, savedLevel)));
        Integer saved = savedExactXp(config, scope);
        xp.setValue(saved == null ? 0 : saved);
        showMode(saved != null);
        restoring = false;
    }

    int level() { return (int) level.getValue(); }
    Integer exactXp() { return xpButton.isSelected() ? (int) xp.getValue() : null; }

    static Integer savedExactXp(ConfigManager config, String scope)
    {
        if (!Boolean.TRUE.equals(config.getConfiguration(GROUP, scope + EXACT_MODE, boolean.class))) return null;
        Integer saved = config.getConfiguration(GROUP, scope + EXACT_XP, int.class);
        return SkillProgress.validXp(saved) ? saved : null;
    }

    private void selectMode(boolean exact)
    {
        if (exact)
        {
            Integer saved = config.getConfiguration(GROUP, scope + EXACT_XP, int.class);
            if (!SkillProgress.validXp(saved))
            {
                saved = seedXp.getAsInt();
                config.setConfiguration(GROUP, scope + EXACT_XP, saved);
            }
            restoring = true;
            xp.setValue(saved);
            restoring = false;
        }
        config.setConfiguration(GROUP, scope + EXACT_MODE, exact);
        showMode(exact);
        onChange.run();
    }

    private void showMode(boolean exact)
    {
        levelButton.setSelected(!exact);
        xpButton.setSelected(exact);
        levelButton.setForeground(exact ? null : XpResults.GOLD);
        xpButton.setForeground(exact ? XpResults.GOLD : null);
        fields.show(input, exact ? "xp" : "level");
    }

    private void save(String key, Object value)
    {
        if (restoring || key == null) return;
        config.setConfiguration(GROUP, key, value);
        onChange.run();
    }

    private static JPanel labeled(String label, JSpinner spinner)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        JLabel text = new JLabel(label);
        text.setLabelFor(spinner);
        row.add(text, BorderLayout.WEST);
        row.add(spinner, BorderLayout.CENTER);
        return row;
    }
}

package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import net.runelite.api.Experience;
import net.runelite.client.config.ConfigManager;

final class SkillProgressInput extends JPanel
{
    private static final String GROUP = "hccalcs";
    private static final String EXACT_MODE = ".exactXpMode";
    private static final String EXACT_XP = ".exactXp";
    private final JToggleButton levelButton = new JToggleButton("Level");
    private final JToggleButton xpButton = new JToggleButton("XP");
    private final InlineNumberInput level = new InlineNumberInput("Lvl", "Current level",
        "Clear the field to use your game level, or level 99 if unavailable.", 2, Experience.MAX_REAL_LEVEL, this::saveLevel);
    private final InlineNumberInput xp = new InlineNumberInput("XP", "Current exact XP",
        "Enter total XP from 0 to 200,000,000.", 9, Experience.MAX_SKILL_XP, this::saveXp);
    private final InlineNumberInput goal = new InlineNumberInput("Goal", "Goal level",
        "Enter a target level from 1 to 99. Clear the field to remove the goal.", 2, Experience.MAX_REAL_LEVEL, this::saveGoal);
    private final CardLayout fields = new CardLayout();
    private final JPanel input = new JPanel(fields);
    private final JPanel modeControl = new JPanel(new BorderLayout());
    private final JLabel modeStatus = new JLabel();
    private final ConfigManager config;
    private final Runnable onChange;
    private String scope;
    private String levelKey;
    private IntSupplier seedXp;
    private int manualLevel;
    private int resolvedLevel;
    private int exactXp;
    private int target;

    SkillProgressInput(ConfigManager config, Runnable onChange)
    {
        super(new BorderLayout(6, 0));
        this.config = config;
        this.onChange = onChange;

        ButtonGroup modes = new ButtonGroup();
        modes.add(levelButton);
        modes.add(xpButton);
        JPanel toggle = new JPanel(new GridLayout(1, 2, 3, 0));
        toggle.add(levelButton);
        toggle.add(xpButton);
        modeControl.add(toggle, BorderLayout.WEST);
        modeStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        modeControl.add(modeStatus, BorderLayout.EAST);
        HcCalcsPanel.spaceHeaderRow(modeControl, 4);

        input.add(level, "level");
        input.add(xp, "xp");
        add(input, BorderLayout.CENTER);
        add(goal, BorderLayout.EAST);

        levelButton.addActionListener(event -> selectMode(false));
        xpButton.addActionListener(event -> selectMode(true));
    }

    void configure(String scope, String levelField, IntSupplier seedXp)
    {
        this.scope = scope;
        this.levelKey = scope + "." + levelField;
        this.seedXp = seedXp;
        Integer savedLevel = config.getConfiguration(GROUP, levelKey, int.class);
        manualLevel = savedLevel == null ? 0 : Math.max(0, Math.min(Experience.MAX_REAL_LEVEL, savedLevel));
        level.showValue(manualLevel);
        Integer saved = savedExactXp(config, scope);
        exactXp = saved == null ? 0 : saved;
        xp.showValue(exactXp);
        Integer savedGoal = config.getConfiguration(GROUP, scope + ".goal", int.class);
        target = savedGoal != null && savedGoal > 0 && savedGoal <= Experience.MAX_REAL_LEVEL ? savedGoal : 0;
        showGoal();
        showMode(saved != null);
    }

    JPanel modeControl() { return modeControl; }
    int level() { return manualLevel; }
    Integer exactXp() { return xpButton.isSelected() ? exactXp : null; }
    int target() { return target; }

    void showProgress(SkillProgress progress)
    {
        resolvedLevel = progress.level;
        boolean exact = xpButton.isSelected();
        if (!exact && manualLevel == 0) level.showWhenIdle(resolvedLevel);
        modeStatus.setText(exact ? "Lvl " + resolvedLevel : "Auto");
        modeStatus.setToolTipText(progress.levelLabel);
        modeStatus.setVisible(exact || manualLevel == 0);
    }

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
            exactXp = saved;
            xp.showValue(saved);
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

    private void saveLevel(int value)
    {
        manualLevel = value;
        config.setConfiguration(GROUP, levelKey, value);
        onChange.run();
        if (value == 0) level.showValue(resolvedLevel);
    }

    private void saveXp(int value)
    {
        exactXp = value;
        config.setConfiguration(GROUP, scope + EXACT_XP, value);
        onChange.run();
    }

    private void saveGoal(int value)
    {
        target = value;
        String key = scope + ".goal";
        if (value == 0) config.unsetConfiguration(GROUP, key);
        else config.setConfiguration(GROUP, key, value);
        showGoal();
        onChange.run();
    }

    private void showGoal()
    {
        goal.showValue(target == 0 ? null : target);
        goal.highlight(target > 0);
    }
}

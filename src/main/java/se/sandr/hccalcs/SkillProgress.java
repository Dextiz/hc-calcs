package se.sandr.hccalcs;

import net.runelite.api.Experience;

final class SkillProgress
{
    final int level;
    final int xp;
    final String levelLabel;

    private SkillProgress(int level, int xp, String levelLabel)
    {
        this.level = level;
        this.xp = xp;
        this.levelLabel = levelLabel;
    }

    static SkillProgress resolve(int manualLevel, Integer exactXp, Integer gameLevel, Integer gameXp)
    {
        if (validXp(exactXp))
        {
            int level = Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp(exactXp));
            return new SkillProgress(level, exactXp, "Level " + level + " from XP");
        }
        if (manualLevel > 0)
            return new SkillProgress(manualLevel, Experience.getXpForLevel(manualLevel), "Level " + manualLevel);

        int level = gameLevel == null ? UtilityCalculator.DEFAULT_LEVEL : gameLevel;
        int xp = validXp(gameXp) ? gameXp : Experience.getXpForLevel(level);
        return new SkillProgress(level, xp, (gameLevel == null ? "Assumed level " : "Game level ") + level);
    }

    static boolean validXp(Integer xp)
    {
        return xp != null && xp >= 0 && xp <= Experience.MAX_SKILL_XP;
    }
}

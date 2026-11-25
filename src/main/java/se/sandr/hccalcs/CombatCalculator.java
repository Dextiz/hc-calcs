package se.sandr.hccalcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import se.sandr.hccalcs.RecipeCatalog.Item;

final class CombatCalculator extends UtilityCalculator
{
    private static final int STACK = 6;
    private final JsonObject data;

    CombatCalculator(JsonObject data)
    {
        super("combat", "Combat XP", null);
        this.data = data;
        xpFilter();
        choice("target", "Target", "zombie", "zombie=Armoured zombie", "fort-zombie=Fortis armoured zombie",
            "monkey=Maniacal monkey", "defenseless=Defenseless");
    }

    @Override
    List<Row> calculate(Values values, Market market)
    {
        return calculate(values, market, CombatLoadout.baseline(values.level(Skill.MAGIC), values.level(Skill.RANGED)));
    }

    List<Row> calculate(Values values, Market market, CombatLoadout loadout)
    {
        JsonObject target = data.getAsJsonObject("targets").getAsJsonObject(values.text("target"));
        JsonObject salve = data.getAsJsonObject("salveBonuses")
            .getAsJsonObject(b(target, "undead") ? loadout.salve : "none");
        List<Row> rows = new ArrayList<>();
        addSpells(rows, target, salve, loadout, market);
        addChins(rows, target, salve, loadout, market);
        rows.removeIf(row -> !allowsXp(values, row.xp));
        rows.sort(bestXpOrder());
        return rows;
    }

    private void addSpells(List<Row> rows, JsonObject target, JsonObject salve, CombatLoadout loadout, Market market)
    {
        double hp = n(target, "hp", Double.POSITIVE_INFINITY);
        JsonObject prayer = data.getAsJsonObject("magicPrayers").getAsJsonObject(loadout.magicPrayer);
        double roll = (Math.floor(loadout.magic * i(prayer, "accuracy") / 100.0) + 9) * (loadout.magicAttack + 64);
        double defence = i(target, "magicDefenceRoll");
        double mainAccuracy = accuracy(Math.floor(roll * (100 + i(salve, "magic")) / 100), defence, b(target, "guaranteed"));
        double splashAccuracy = accuracy(roll, defence, b(target, "guaranteed"));
        double bonus = Math.round(loadout.magicDamage * 10) + 10 * i(prayer, "damage");
        for (JsonElement entry : array(data, "spells"))
        {
            JsonObject spell = object(entry);
            List<Item> runes = new ArrayList<>();
            for (Map.Entry<String, JsonElement> rune : spell.getAsJsonObject("runes").entrySet())
                runes.add(item(i(data.getAsJsonObject("runeIds"), rune.getKey()), rune.getKey() + " rune", rune.getValue().getAsInt()));
            int mainMax = (int) Math.floor(i(spell, "maxHit") * (1000 + bonus + 10 * i(salve, "magic")) / 1000);
            int splashMax = (int) Math.floor(i(spell, "maxHit") * (1000 + bonus) / 1000);
            double damage = damage(mainMax, mainAccuracy, hp, 0) + (STACK - 1) * damage(splashMax, splashAccuracy, hp, 0);
            int base = i(spell, "baseXp");
            rows.add(result(spell, "cast", loadout.magic, loadout.magic, base + 2 * damage, damage,
                Math.min(hp, mainMax), mainAccuracy, runes, market, base + " base Magic XP\n"));
        }
    }

    private void addChins(List<Row> rows, JsonObject target, JsonObject salve, CombatLoadout loadout, Market market)
    {
        double hp = n(target, "hp", Double.POSITIVE_INFINITY);
        JsonObject prayer = data.getAsJsonObject("rangedPrayers").getAsJsonObject(loadout.rangedPrayer);
        double attack = Math.floor(loadout.ranged * i(prayer, "accuracy") / 100.0) + 8;
        double strength = Math.floor(loadout.ranged * i(prayer, "strength") / 100.0) + 8;
        int salveNumerator = i(salve, "rangedNumerator");
        int salveDenominator = i(salve, "rangedDenominator");
        int armour = i(target, "armour");
        for (JsonElement entry : array(data, "chins"))
        {
            JsonObject chin = object(entry);
            double baseRoll = attack * (loadout.rangedAttack + i(chin, "attack") + 64);
            double roll = Math.floor(baseRoll * salveNumerator / salveDenominator);
            double chance = accuracy(roll, i(target, "rangedDefenceRoll"), b(target, "guaranteed"));
            int splashMax = (int) Math.floor((320 + strength * (loadout.rangedStrength + i(chin, "strength") + 64)) / 640);
            int mainMax = splashMax * salveNumerator / salveDenominator;
            double damage = damage(mainMax, chance, hp, armour) + (STACK - 1) * damage(splashMax, chance, hp, armour);
            rows.add(result(chin, "throw", loadout.ranged, loadout.baseRanged, 4 * damage, damage,
                Math.min(hp, Math.max(0, mainMax - armour)), chance,
                List.of(item(i(chin, "id"), s(chin, "name"), 1)), market, ""));
        }
    }

    private static Row result(JsonObject method, String action, int level, int unlockLevel, double xp, double damage,
        double max, double accuracy, List<Item> items, Market market, String note)
    {
        String name = s(method, "name");
        int required = i(method, "level");
        double cost = basket(items, market::buy);
        if (unlockLevel < required)
        {
            xp = damage = max = accuracy = UNKNOWN;
        }
        return new Row(name, name + " · " + fmt(xp) + " XP", name + "\nLevel " + level + " · Requires " + required
            + "\n" + note + "\n" + fmt(xp) + " XP/" + action + "\n" + gp(cost) + "/" + action
            + "\n\nAccuracy " + percent(accuracy) + "\nMax hit " + fmt(max)
            + "\nAverage damage across " + STACK + " targets " + fmt(damage)
            + "\n\n" + itemLines(items, market::buy), List.of())
            .training(required, xp, -cost / xp, cost).rate(xp > 0 ? cost / xp : UNKNOWN);
    }

    private static double accuracy(double attack, double defence, boolean guaranteed)
    {
        if (guaranteed) return 1;
        return attack <= defence ? Math.max(0, attack) / (2 * (defence + 1))
            : 1 - (defence + 2) / (2 * (attack + 1));
    }

    private static double damage(int max, double accuracy, double hp, int armour)
    {
        if (max < 1) return 0;
        double total = 0;
        for (int roll = 0; roll <= max; roll++)
            total += Math.min(hp, Math.max(0, Math.max(1, roll) - armour));
        return accuracy * total / (max + 1);
    }
}

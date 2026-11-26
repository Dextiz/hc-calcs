package se.sandr.hccalcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import se.sandr.hccalcs.RecipeCatalog.Item;

final class PrayerCalculator extends UtilityCalculator
{
    private final JsonObject data;

    PrayerCalculator(JsonObject data)
    {
        super("prayer", "Prayer", Skill.PRAYER);
        this.data = data;
        choice("method", "Method", "gilded", "gilded=Gilded altar", "chaos=Chaos altar", "ecto=Ectofuntus",
            "wine=Blessed wine", "sunfire=Sunfire wine", "bury=Bury / scatter", "sinister=Sinister Offering",
            "demonic=Demonic Offering", "ensouled=Ensouled heads");
        number("magic", "Magic (0 = auto)", 0, 0, 99);
        flag("onlyMagic", "Only show heads I can reanimate", true);
        trainingFields();
    }

    @Override
    boolean volumeNeeded(Values values) { return values.integer("volume") > 0; }

    @Override
    List<Row> calculate(Values values, Market market)
    {
        List<Row> rows = new ArrayList<>();
        String method = values.text("method");
        int magic = values.level(Skill.MAGIC, "magic");
        if (method.equals("ensouled"))
        {
            for (JsonElement element : array(data, "reanimations"))
            {
                JsonObject spell = object(element);
                int requirement = i(spell, "level");
                if (values.flag("onlyMagic") && magic < requirement) continue;
                List<Item> runes = new ArrayList<>();
                for (JsonElement rune : array(spell, "runes"))
                {
                    JsonArray entry = array(rune);
                    int id = (int) n(entry, 0);
                    runes.add(item(id, s(data.getAsJsonObject("runeNames"), Integer.toString(id)) + " rune", n(entry, 1)));
                }
                for (JsonElement head : array(spell, "heads"))
                {
                    JsonArray entry = array(head);
                    String name = "Ensouled " + s(entry, 1) + " head";
                    add(rows, values, market, item((int) n(entry, 0), name, 1), 1, n(entry, 2), runes,
                        method, 0, s(spell, "name") + " · " + requirement + " Magic\n");
                }
            }
        }
        else
        {
            if (method.equals("bury") || method.equals("demonic"))
            {
                for (JsonElement element : array(data, "ashes"))
                {
                    JsonArray ash = array(element);
                    boolean demonic = method.equals("demonic");
                    add(rows, values, market, item((int) n(ash, 0), s(ash, 1), 1), 1,
                        n(ash, 2) * (demonic ? 3 : 1), demonic ? offering(566, "Soul rune") : List.of(),
                        method, 0, demonic ? "Requires 84 Magic and A Kingdom Divided\n" : "");
                }
            }
            if (!method.equals("demonic"))
            {
                for (JsonElement element : array(data, "bones"))
                {
                    JsonArray bone = array(element);
                    List<Item> extra = new ArrayList<>();
                    int level = (int) n(bone, 2);
                    int shards = 0;
                    double xp;
                    String note = "";
                    switch (method)
                    {
                        case "gilded": xp = n(bone, 4); note = "Both incense burners lit\n"; break;
                        case "chaos": xp = n(bone, 6); note = "Includes free re-offers of saved bones\n"; break;
                        case "ecto": xp = n(bone, 5); note = "Collect slime yourself\n"; break;
                        case "sinister":
                            xp = 3 * n(bone, 3);
                            extra = offering(565, "Blood rune");
                            note = "Requires 92 Magic and A Kingdom Divided\n";
                            break;
                        case "wine":
                        case "sunfire":
                            boolean sunfire = method.equals("sunfire");
                            shards = (int) n(bone, 7);
                            xp = shards * (sunfire ? 6 : 5);
                            level = Math.max(30, level);
                            extra.add(item(1993, "Jug of wine", shards / 400.0));
                            if (sunfire) extra.add(item(28924, "Sunfire splinters", shards / 200.0));
                            break;
                        default: xp = n(bone, 3);
                    }
                    if (xp > 0)
                        add(rows, values, market, item((int) n(bone, 0), s(bone, 1), 1), level, xp, extra, method, shards, note);
                }
            }
        }
        rows.sort(trainingOrder(values));
        return rows;
    }

    private void add(List<Row> rows, Values values, Market market, Item remains, int level,
        double xp, List<Item> extra, String method, int shards, String note)
    {
        List<Item> supplies = new ArrayList<>(extra);
        supplies.add(0, remains);
        if (!trainingAllowed(values, market, level, xp, supplies, List.of())) return;
        double cost = basket(supplies, market::buy);
        String detail = remains.name() + "\nPrayer level " + level + " · " + fmt(xp) + " XP/action\n" + note
            + "\nCost " + gp(cost) + "\n"
            + (extra.isEmpty() ? "" : "Supplies " + gp(basket(extra, market::buy)) + "\n")
            + "\n" + itemLines(supplies, market::buy);
        Row row = new Row(remains.name(), remains.name(), detail, List.of())
            .training(level, xp, -cost / xp, cost).rate(cost / xp);
        long actions = goalActions(values, level, xp);
        if (actions > 0)
        {
            double total = market.buy(remains.id()) * actions;
            double jugs = Math.ceil(shards * actions / 400.0);
            for (Item supply : extra)
            {
                double quantity;
                if (shards > 0)
                    quantity = supply.id() == 28924 ? 2 * jugs : jugs;
                else if (method.equals("sinister") || method.equals("demonic"))
                    quantity = Math.ceil(actions / 3.0);
                else
                    quantity = supply.quantity() * actions;
                total += quantity * market.buy(supply.id());
            }
            row.goal = new TrainingCalculator.Goal(actions, total, total);
        }
        rows.add(row);
    }

    private static List<Item> offering(int id, String rune)
    {
        return List.of(item(id, rune, 1.0 / 3), item(21880, "Wrath rune", 1.0 / 3));
    }
}

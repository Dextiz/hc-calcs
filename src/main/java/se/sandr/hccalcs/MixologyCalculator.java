package se.sandr.hccalcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

final class MixologyCalculator extends UtilityCalculator
{
    private final JsonObject data;

    MixologyCalculator(JsonObject data)
    {
        super("mixology", "Mastering Mixology", Skill.HERBLORE);
        this.data = data;
        xpFilter();
        choice("potionSort", "Potion order", BEST_GP_XP, BEST_GP_XP, "Highest XP", "Level");
        choice("view", "Results", "all", "all=All results", "paste=Paste sources", "potion=Potion orders");
        number("target", "Paste target", 1000, 1, 1_000_000);
        choice("source", "Supply type", "all", "all=Any source", "clean=Clean herbs", "grimy=Grimy herbs",
            "unfinished=Unfinished potions");
        choice("paste", "Paste", "all", "all=All pastes", "Mox", "Aga", "Lye");
        choice("sort", "GP/paste", "low", "low=Lowest first", "high=Highest first");
    }

    @Override
    boolean showsXp(Values values) { return !values.text("view").equals("paste"); }

    @Override
    List<Row> calculate(Values values, Market market)
    {
        List<Row> rows = new ArrayList<>();
        Map<String, Double> rates = new HashMap<>();
        List<Row> supplies = new ArrayList<>();
        for (JsonElement element : array(data, "pasteTypes"))
        {
            String paste = element.getAsString();
            double bestRate = UNKNOWN;
            double bestCost = UNKNOWN;
            double batchRate = UNKNOWN;
            String bestSupply = "", bestBatch = "";
            for (JsonElement source : array(data, "supplies"))
            {
                JsonObject herb = object(source);
                if (!paste.equals(s(herb, "paste")) || (!values.text("source").equals("all")
                    && !values.text("source").equals(s(herb, "source")))) continue;
                double price = market.buy(i(herb, "id"));
                int yield = i(herb, "yieldPerItem");
                int count = (int) Math.ceil(values.number("target") / yield);
                double rate = price / yield;
                double cost = price * count;
                String name = s(herb, "name");
                int level = i(herb, "level");
                String note = level > 0 ? "\nClean at level " + level : "";
                if (Double.isFinite(rate) && (!Double.isFinite(bestRate) || rate < bestRate))
                {
                    bestRate = rate;
                    bestSupply = name + note;
                }
                if (Double.isFinite(cost) && (!Double.isFinite(bestCost) || cost < bestCost
                    || cost == bestCost && rate < batchRate))
                {
                    bestCost = cost;
                    batchRate = rate;
                    bestBatch = "Buy " + fmt(count) + " × " + name + " for " + gp(cost) + "\nProduces "
                        + fmt((double) count * yield) + " paste" + note;
                }
                if (values.text("paste").equals("all") || values.text("paste").equals(paste))
                    supplies.add(new Row(name, name + " · " + fmt(rate) + " GP/paste", name + "\n" + yield + " " + paste
                        + " paste each\nBuy " + gp(price) + " · " + fmt(rate) + " GP/paste\nTarget costs " + gp(cost)
                        + " (" + fmt(count) + " items)" + note, List.of()).training(level, UNKNOWN, rate, cost));
            }
            rates.put(paste, bestRate);
            if (!values.text("view").equals("potion"))
                rows.add(info(paste + " paste summary", paste + " paste · " + fmt(bestRate) + " GP/paste\n"
                    + bestSupply + "\n\n" + bestBatch));
        }
        if (!values.text("view").equals("potion"))
        {
            supplies.sort(metric(row -> row.score, values.text("sort").equals("high")));
            rows.addAll(supplies);
        }
        if (!values.text("view").equals("paste"))
        {
            List<Row> potions = new ArrayList<>();
            for (JsonElement entry : array(data, "orders"))
            {
                JsonArray order = array(entry);
                String name = s(order, 0);
                double xp = n(order, 2);
                if (!allowsXp(values, xp)) continue;
                StringBuilder recipe = new StringBuilder();
                double cost = 0;
                for (Map.Entry<String, JsonElement> ingredient : object(order.get(3)).entrySet())
                {
                    int quantity = ingredient.getValue().getAsInt();
                    recipe.append(quantity).append(" ").append(ingredient.getKey()).append(" paste\n");
                    cost += quantity * rates.get(ingredient.getKey());
                }
                int level = (int) n(order, 1);
                potions.add(new Row(name, name, name + "\nHerblore level "
                    + level + " · " + fmt(xp) + " XP\nCost " + gp(cost)
                    + "\n\n" + recipe, List.of()).training(level, xp, cost / xp, cost).rate(cost / xp));
            }
            switch (values.text("potionSort"))
            {
                case BEST_GP_XP: potions.sort(bestXpOrder()); break;
                case "Highest XP": potions.sort(metric(row -> row.xp, true)); break;
                default: potions.sort(java.util.Comparator.comparingInt(row -> row.level));
            }
            rows.addAll(0, potions);
        }
        return rows;
    }
}

package se.sandr.hccalcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import se.sandr.hccalcs.RecipeCatalog.Item;
import se.sandr.hccalcs.RecipeCatalog.Recipe;

final class TrainingCalculator
{
    enum Amulet
    {
        NONE("None", 0, 1),
        CHEMISTRY("Chemistry", 0.05, 5),
        ALCHEMIST("Alchemist's", 0.15, 10);

        final String label;
        final double chance;
        final int charges;

        Amulet(String label, double chance, int charges)
        {
            this.label = label;
            this.chance = chance;
            this.charges = charges;
        }

        @Override
        public String toString() { return label; }
    }

    enum Kitchen
    {
        RANGE("Range", 0), FIRE("Fire", 0),
        HOSIDIUS("Hosidius", 12), HOSIDIUS_ELITE("Hosidius (Elite)", 25);

        final String label;
        final int bonus;

        Kitchen(String label, int bonus)
        {
            this.label = label;
            this.bonus = bonus;
        }

        @Override
        public String toString() { return label; }
    }

    static final class Settings
    {
        final Amulet amulet;
        final boolean goggles;
        final Kitchen kitchen;
        final boolean gauntlets;
        final int essencePerAltarUse;

        Settings(Amulet amulet, boolean goggles, Kitchen kitchen, boolean gauntlets)
        {
            this(amulet, goggles, kitchen, gauntlets, RunecraftRecipes.DEFAULT_ESSENCE_PER_USE);
        }

        Settings(Amulet amulet, boolean goggles, Kitchen kitchen, boolean gauntlets, int essencePerAltarUse)
        {
            this.amulet = amulet;
            this.goggles = goggles;
            this.kitchen = kitchen;
            this.gauntlets = gauntlets;
            this.essencePerAltarUse = essencePerAltarUse;
        }
    }

    static final class Result
    {
        final List<Item> inputs;
        final List<Item> outputs;
        final double cost;
        final double netSale;
        final double success;
        final double gpPerXp;
        final double altarUseCost;

        Result(List<Item> inputs, List<Item> outputs, double cost, double netSale, double success, double gpPerXp,
            double altarUseCost)
        {
            this.inputs = inputs;
            this.outputs = outputs;
            this.cost = cost;
            this.netSale = netSale;
            this.success = success;
            this.gpPerXp = gpPerXp;
            this.altarUseCost = altarUseCost;
        }
    }

    static final class Goal
    {
        final long attempts;
        final double gross;
        final double net;

        Goal(long attempts, double gross, double net)
        {
            this.attempts = attempts;
            this.gross = gross;
            this.net = net;
        }
    }

    static final class RunecraftHourly
    {
        final double xp;
        final double runes;
        final double profit;

        RunecraftHourly(double xp, double runes, double profit)
        {
            this.xp = xp;
            this.runes = runes;
            this.profit = profit;
        }
    }

    static Result calculate(Recipe recipe, Set<Integer> taxExempt, Settings settings, Integer level,
        MarketSnapshot market, Map<Integer, Long> sellOverrides)
    {
        List<Item> inputs = new ArrayList<>();
        double cost = 0;
        for (Item item : recipe.inputs)
        {
            double count = item.quantity() * (item.goggles() && settings.goggles ? 0.9 : 1);
            inputs.add(new Item(item.id(), item.name(), count));
            if (item.id() != null || !recipe.selfGatheredFree)
            {
                cost += market.buy(item.id()) * count;
            }
        }

        List<Item> outputs = recipe.outputs;
        double altarUseCost = 0;
        if (recipe.runecraft != null)
        {
            for (Item item : recipe.runecraft.perUse)
            {
                inputs.add(new Item(item.id(), item.name(), item.quantity() / settings.essencePerAltarUse));
                altarUseCost += market.buy(item.id()) * item.quantity();
            }
            cost += altarUseCost / settings.essencePerAltarUse;
            Item output = outputs.get(0);
            int outputLevel = level == null ? recipe.level : level;
            outputs = List.of(new Item(output.id(), output.name(), recipe.runecraft.runesPerEssence(outputLevel)));
        }
        Amulet amulet = settings.amulet;
        if (recipe.amulet && amulet != Amulet.NONE)
        {
            Item charge = new Item(21163, "Amulet of chemistry", amulet.chance / amulet.charges);
            inputs.add(charge);
            cost += market.buy(charge.id()) * charge.quantity();
            Item output = outputs.get(0);
            outputs = List.of(new Item(output.id(), output.name(), output.quantity() + amulet.chance / 4));
        }

        double netSale = 0;
        for (Item output : outputs)
        {
            double sell = salePrice(output.id(), market, sellOverrides);
            double tax = output.id() != null && taxExempt.contains(output.id()) ? 0 : Math.min(Math.floor(sell / 50), 5_000_000);
            netSale += (sell - tax) * output.quantity();
        }

        double success = success(recipe, settings, level);
        double gpPerXp = recipe.xp > 0 && success > 0
            ? (netSale * success - cost) / (recipe.xp * success) : Double.NaN;
        return new Result(inputs, outputs, cost, netSale, success, gpPerXp, altarUseCost);
    }

    static double salePrice(Integer id, MarketSnapshot market, Map<Integer, Long> overrides)
    {
        Long override = id == null ? null : overrides.get(id);
        return override == null ? market.sell(id) : override;
    }

    static boolean meetsVolume(Result result, Recipe recipe, MarketSnapshot volume, int threshold)
    {
        if (threshold <= 0)
        {
            return true;
        }
        return result.inputs.stream().allMatch(item -> item.id() == null && recipe.selfGatheredFree ||
            volume.buy(item.id()) >= threshold) &&
            result.outputs.stream().allMatch(item -> volume.sell(item.id()) >= threshold);
    }

    static RunecraftHourly runecraftHourly(Recipe recipe, Result result, int essencePerUse,
        int essencePerRun, double runsPerHour)
    {
        double essencePerHour = essencePerRun * runsPerHour;
        double profitPerRun = result.netSale * essencePerRun - runecraftCost(result, essencePerUse, essencePerRun);
        return new RunecraftHourly(recipe.xp * essencePerHour, result.outputs.get(0).quantity() * essencePerHour,
            profitPerRun * runsPerHour);
    }

    private static double runecraftCost(Result result, int essencePerUse, long essence)
    {
        long altarUses = (essence + essencePerUse - 1) / essencePerUse;
        return essence * (result.cost - result.altarUseCost / essencePerUse) + altarUses * result.altarUseCost;
    }

    static Goal goal(Recipe recipe, Result result, Settings settings, Integer level, Integer currentXp, int target)
    {
        if (level == null || target <= level || level < recipe.level || recipe.xp <= 0)
        {
            return null;
        }
        double xp = currentXp == null ? Experience.getXpForLevel(level) : currentXp;
        double remaining = Experience.getXpForLevel(target) - xp;
        if (remaining <= 0)
        {
            return null;
        }
        long products = (long) Math.ceil(remaining / recipe.xp);
        if (recipe.runecraft != null)
        {
            int essencePerUse = settings.essencePerAltarUse;
            long runes = recipe.runecraft.goalRunes(level, xp, recipe.xp, products, essencePerUse);
            double gross = runecraftCost(result, essencePerUse, products);
            return new Goal(products, gross, gross - runes * result.netSale / result.outputs.get(0).quantity());
        }
        long attempts = products;
        if (recipe.range != null)
        {
            products = 0;
            double expected = 0;
            for (int stage = level; stage < target; stage++)
            {
                double toNext = Experience.getXpForLevel(stage + 1) - xp;
                if (toNext <= 0)
                {
                    continue;
                }
                double success = success(recipe, settings, stage);
                if (success <= 0)
                {
                    return null;
                }
                long actions = (long) Math.ceil(toNext / recipe.xp);
                products += actions;
                xp += actions * recipe.xp;
                expected += actions / success;
            }
            attempts = (long) Math.ceil(expected);
        }
        double gross = attempts * result.cost;
        return new Goal(attempts, gross, gross - products * result.netSale);
    }

    private static double success(Recipe recipe, Settings settings, Integer level)
    {
        if (recipe.range == null)
        {
            return 1;
        }
        if (level == null || level < recipe.level)
        {
            return Double.NaN;
        }
        int[] roll = recipe.range;
        if (settings.gauntlets && recipe.gauntlets != null)
        {
            roll = recipe.gauntlets;
        }
        else if (settings.kitchen == Kitchen.FIRE && recipe.fire != null)
        {
            roll = recipe.fire;
        }
        int bonus = recipe.outputs.get(0).id() == 1993 ? 0 : settings.kitchen.bonus;
        long value = Math.round((roll[0] * (99 - level) + roll[1] * (level - 1)) / 98.0) + bonus + 1;
        return Math.max(0, Math.min(256, value)) / 256.0;
    }
}

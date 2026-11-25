package se.sandr.hccalcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import se.sandr.hccalcs.RecipeCatalog.Item;

final class KingdomCalculator extends UtilityCalculator
{
    static final int WORKERS_PER_TASK = 10;
    private final JsonObject data;
    private final FarmingCalculator farming;
    private final List<JsonObject> departments = new ArrayList<>();

    KingdomCalculator(JsonObject data, FarmingCalculator farming)
    {
        super("kingdom", "Kingdom", null);
        this.data = data;
        this.farming = farming;
        number("coffers", "Starting coffers", 1_500_000, 0, 7_500_000);
        number("days", "Days", 7, 1, 30);
        number("approval", "Approval %", 100, 25, 100);
        flag("royal", "Royal Trouble completed", true);
        flag("constant", "Maintain approval daily", true);
        for (JsonElement element : array(data, "departments"))
        {
            departments.add(object(element));
        }
    }

    @Override
    List<Row> calculate(Values values, Market market)
    {
        Recommendation result = recommend(values, market);
        if (result.error != null)
            return List.of(info("Allocation unavailable", result.error));

        StringBuilder text = new StringBuilder("Best worker allocation\n\n");
        for (TaskAllocation task : result.tasks)
        {
            if (task.workers > 0)
                text.append(task.workers).append(task.workers == 1 ? " worker · " : " workers · ")
                    .append(task.name).append("\n");
        }
        text.append("\nEstimated profit ").append(gp(Math.round(result.profit / result.days))).append("/day\n")
            .append(result.periodSummary()).append("\n").append(result.approval).append("% starting approval");
        if (result.missingPrices)
            text.append("\n\nSome HCW sell prices are missing.");
        return List.of(info("Best worker allocation", text.toString()));
    }

    Recommendation recommend(Values values, Market market)
    {
        Production production = production(values);
        if (production.points == 0)
            return Recommendation.unavailable("The current coffer balance is too low to produce loot.");
        Allocation best = optimize(values, market);
        if (best.value <= 0)
            return Recommendation.unavailable("No sellable loot could be valued with the available HCW prices.");

        List<TaskAllocation> tasks = new ArrayList<>();
        for (JsonObject department : departments)
        {
            if (!active(department, values)) continue;
            String id = s(department, "id");
            int count = (int) best.settings.getOrDefault(id + "Workers", 0);
            JsonArray options = array(department, "options");
            Object selected = best.settings.getOrDefault(id + "Task", s(array(options.get(0)), 0));
            for (JsonElement entry : options)
            {
                JsonArray option = array(entry);
                tasks.add(new TaskAllocation(s(option, 1), s(option, 0).equals(selected) ? count : 0));
            }
        }
        return new Recommendation(List.copyOf(tasks), workers(values), values.integer("days"), values.integer("approval"),
            best.value - production.wages, best.missingPrices, null);
    }

    Allocation optimize(Values values, Market market)
    {
        int maximum = workers(values);
        int points = production(values).points;
        boolean missingPrices = false;
        Allocation[] best = new Allocation[maximum + 1];
        best[0] = new Allocation(0, Map.of(), false);
        for (JsonObject department : departments)
        {
            if (!active(department, values)) continue;
            String id = s(department, "id");
            Allocation[] next = new Allocation[maximum + 1];
            List<Choice> choices = new ArrayList<>();
            choices.add(new Choice(null, 0, 0));
            for (JsonElement option : array(department, "options"))
            {
                String task = s(array(option), 0);
                for (int count = 1; count <= WORKERS_PER_TASK; count++)
                {
                    double value = 0;
                    for (Item item : loot(task, count, points))
                    {
                        if (item.id() == null) continue;
                        double net = market.net(item.id(), farming.taxExempt);
                        if (Double.isFinite(net)) value += net * item.quantity();
                        else missingPrices = true;
                    }
                    choices.add(new Choice(task, count, value));
                }
            }
            for (int used = 0; used <= maximum; used++)
            {
                Allocation prior = best[used];
                if (prior == null) continue;
                for (Choice choice : choices)
                {
                    int count = used + choice.workers;
                    double value = prior.value + choice.value;
                    if (count > maximum || next[count] != null && next[count].value >= value) continue;
                    Map<String, Object> settings = new LinkedHashMap<>(prior.settings);
                    settings.put(id + "Workers", choice.workers);
                    if (array(department, "options").size() > 1 && choice.task != null)
                    {
                        settings.put(id + "Task", choice.task);
                    }
                    next[count] = new Allocation(value, settings, false);
                }
            }
            best = next;
        }
        return new Allocation(best[maximum].value, best[maximum].settings, missingPrices);
    }

    private static boolean active(JsonObject department, Values values)
    {
        return values.flag("royal") || !b(department, "royal");
    }

    private static int workers(Values values) { return values.flag("royal") ? 15 : 10; }
    static Production production(Values values)
    {
        int maxWage = values.flag("royal") ? 75_000 : 50_000;
        int decayBase = values.flag("royal") ? 131 : 160;
        int startingBalance = Math.min(values.integer("coffers"), values.flag("royal") ? 7_500_000 : 5_000_000);
        int balance = startingBalance;
        int favour = (int) Math.round(values.number("approval") * 127 / 100);
        int points = 0, fullDays = 0;
        for (int day = 0; day < values.integer("days"); day++)
        {
            int wage = Math.min(balance, Math.min(maxWage, balance / 10 + 5));
            balance -= wage;
            if (wage == maxWage) fullDays++;
            points += (wage * 100 / 8333) * favour / 100;
            if (!values.flag("constant")) favour = Math.max(32, favour - (int) Math.ceil((decayBase - favour) / 15.0));
        }
        return new Production(startingBalance - balance, fullDays, points);
    }

    private List<Item> loot(String task, int workers, int points)
    {
        int amount = (int) Math.floor(workers * n(data.getAsJsonObject("rates"), task, 0) * points / 2048);
        List<Item> result = new ArrayList<>();
        if (amount <= 0) return result;
        switch (task)
        {
            case "coal":
                result.add(item(453, "Coal", amount));
                weighted(result, "gems", 58, (amount + 100) / 200, 0);
                break;
            case "herbs":
                weighted(result, "herbs", 46, amount, 0);
                weighted(result, "herbSeeds", 1000, amount / 100.0, 0);
                break;
            case "flax":
                result.add(item(1779, "Flax", amount));
                weighted(result, "flaxSeeds", 1000, amount / 600.0, 0);
                break;
            case "maple":
                result.add(item(1517, "Maple logs", amount));
                nests(result, amount / 100.0);
                break;
            case "raw":
            case "cooked":
                boolean raw = task.equals("raw");
                result.add(item(raw ? 359 : 361, raw ? "Raw tuna" : "Tuna", amount / 2));
                result.add(item(raw ? 371 : 373, raw ? "Raw swordfish" : "Swordfish", Math.floor(amount * 0.15)));
                weighted(result, "fishing", 104, amount / 200.0, 0);
                break;
            case "teak":
            case "mahogany":
            case "mixed":
                if (!task.equals("mahogany")) result.add(item(6333, "Teak logs", task.equals("mixed") ? amount / 2 : amount));
                if (!task.equals("teak")) result.add(item(6332, "Mahogany logs", task.equals("mixed") ? amount / 2 : amount));
                nests(result, amount / 350.0);
                break;
            case "seeds":
                weighted(result, "farmSeeds", 8_858_315, amount, 0);
                weighted(result, "treeSeeds", 1011, amount / 200.0, 4);
                break;
            default: throw new IllegalArgumentException("Unknown kingdom task " + task);
        }
        result.removeIf(item -> item.quantity() <= 0);
        return result;
    }

    private void nests(List<Item> items, double rolls)
    {
        items.add(item(5075, "Bird nest (empty)", rolls));
        weighted(items, "treeSeeds", 1011, rolls * 0.65, 0);
        weighted(items, "rings", 100, rolls * 0.32, 0);
        items.add(item(null, "Bird eggs (any colour)", rolls * 0.03));
    }

    private void weighted(List<Item> items, String key, double total, double rolls, int defaultCap)
    {
        if (rolls <= 0) return;
        for (JsonElement element : array(data, key))
        {
            JsonArray entry = array(element);
            Item type = tuple(entry);
            double probability = type.quantity() / total;
            int cap = entry.size() > 3 ? (int) n(entry, 3) : defaultCap;
            int count = (int) Math.floor(rolls);
            double fraction = rolls - count;
            double quantity = cap == 0 ? rolls * probability
                : capped(count, probability, cap) * (1 - fraction) + capped(count + 1, probability, cap) * fraction;
            items.add(item(type.id(), type.name(), quantity));
        }
    }

    private static double capped(int count, double probability, int cap)
    {
        double chance = Math.pow(1 - probability, count);
        double shortfall = 0;
        for (int hits = 0; hits < cap && hits <= count; hits++)
        {
            shortfall += (cap - hits) * chance;
            chance *= (count - hits) * probability / ((hits + 1) * (1 - probability));
        }
        return Math.max(0, cap - shortfall);
    }

    static final class Production
    {
        final int wages, fullDays, points;
        Production(int wages, int fullDays, int points) { this.wages = wages; this.fullDays = fullDays; this.points = points; }
    }

    static final class TaskAllocation
    {
        final String name;
        final int workers;

        TaskAllocation(String name, int workers)
        {
            this.name = name;
            this.workers = workers;
        }
    }

    static final class Recommendation
    {
        final List<TaskAllocation> tasks;
        final int maxWorkers, days, approval;
        final double profit;
        final boolean missingPrices;
        final String error;

        Recommendation(List<TaskAllocation> tasks, int maxWorkers, int days, int approval, double profit,
            boolean missingPrices, String error)
        {
            this.tasks = tasks;
            this.maxWorkers = maxWorkers;
            this.days = days;
            this.approval = approval;
            this.profit = profit;
            this.missingPrices = missingPrices;
            this.error = error;
        }

        static Recommendation unavailable(String error)
        {
            return new Recommendation(List.of(), 0, 0, 0, UNKNOWN, false, error);
        }

        String periodSummary()
        {
            return gp(Math.round(profit)) + " over " + days + (days == 1 ? " day" : " days");
        }
    }

    static final class Allocation
    {
        final double value;
        final Map<String, Object> settings;
        final boolean missingPrices;
        Allocation(double value, Map<String, Object> settings, boolean missingPrices)
        {
            this.value = value;
            this.settings = settings;
            this.missingPrices = missingPrices;
        }
    }

    private static final class Choice
    {
        final String task;
        final int workers;
        final double value;

        Choice(String task, int workers, double value)
        {
            this.task = task;
            this.workers = workers;
            this.value = value;
        }
    }
}

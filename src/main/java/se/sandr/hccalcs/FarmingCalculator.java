package se.sandr.hccalcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;
import net.runelite.api.Skill;
import se.sandr.hccalcs.RecipeCatalog.Item;

final class FarmingCalculator extends UtilityCalculator
{
    private static final String[] PROTECTED = {"fortis", "harmony", "hosidius", "trollheim", "weiss"};
    private static final String[] RUNE_NAMES = {"Soul rune", "Nature rune", "Blood rune", "Earth rune"};
    private final JsonObject data;
    final Set<Integer> taxExempt;

    FarmingCalculator(JsonObject data)
    {
        super("farming", "Farming", Skill.FARMING);
        this.data = data;
        taxExempt = ids(array(data, "taxExempt"));
        Set<String> categories = new LinkedHashSet<>();
        categories.add(ALL);
        array(data, "crops").forEach(crop -> categories.add(s(object(crop), "category")));
        choice("category", "Category", ALL, categories.toArray(new String[0]));
        choice("compost", "Compost", "ultra", "none=None", "compost=Compost", "super=Supercompost", "ultra=Ultracompost");
        flag("protection", "Pay for protection", false);
        trainingFields();
        number("patches", "Herb patches", 1, 1, 10);
        choice("kandarin", "Kandarin Diary", "0", "0=None", "10=Medium", "17=Hard", "25=Elite");
        choice("sell", "Herb output", "grimy", "grimy=Grimy", "clean=Clean", "unfinished=Unfinished potions");
        flag("fortis", "Civitas illa Fortis", false);
        flag("harmony", "Harmony Island patch", false);
        flag("hosidius", "Hosidius patch", false);
        flag("trollheim", "Trollheim patch", false);
        flag("weiss", "Weiss patch", false);
        flag("kourend", "Hard Kourend Diary", false);
        flag("secateurs", "Magic secateurs", false);
        choice("anima", "Anima plant", "none", "none=None", "attas=Attas", "iasor=Iasor");
        flag("cape", "Farming / Max cape", false);
        flag("bucket", "Bottomless bucket", false);
        flag("resurrect", "Resurrect Crops", false);
        number("magic", "Resurrect Magic (0 = auto)", 0, 0, 99);
        hidden("harvest");
    }

    @Override
    boolean volumeNeeded(Values values) { return values.integer("volume") > 0; }

    @Override
    List<Row> calculate(Values values, Market market)
    {
        List<Row> rows = new ArrayList<>();
        String category = values.text("category");
        int level = values.trainingProgress(Skill.FARMING).level;
        Map<Integer, Double> custom = harvestOverrides(values);
        for (JsonElement element : array(data, "crops"))
        {
            JsonObject crop = object(element);
            String group = s(crop, "category");
            if (!category.equals(ALL) && !category.equals(group)) continue;
            boolean herb = group.equals("Herbs");
            Item seed = tuple(crop.get("input"));
            CropPlan plan = plan(crop, values, level, custom.getOrDefault(seed.id(), UNKNOWN));
            int required = i(crop, "level");
            List<Item> requiredSales = plan.outputs;
            if (!Double.isFinite(plan.quantity) && !plan.outputs.isEmpty())
            {
                Item plannedProduct = plan.outputs.get(0);
                // The volume requirement applies to the selected product even before harvest can be estimated.
                requiredSales = List.of(item(plannedProduct.id(), plannedProduct.name(), 1));
            }
            if (!trainingAllowed(values, market, required, plan.xp, plan.inputs, requiredSales)) continue;
            double cost = basket(plan.inputs, market::buy);
            if (!Double.isFinite(plan.quantity) && herb && values.text("sell").equals("unfinished")) cost = UNKNOWN;
            double loot = Double.isFinite(plan.quantity) ? basket(plan.outputs, id -> market.net(id, taxExempt)) : UNKNOWN;
            double profit = loot - cost;
            double gpPerXp = profit / plan.xp;
            String name = s(crop, "name");
            String detail = "Buy " + gp(cost) + " · Loot " + gp(loot) + "\n"
                + (Double.isFinite(plan.average) && plan.average != plan.quantity ? "Default harvest " + fmt(plan.average) + "\n" : "")
                + (herb && Double.isFinite(plan.quantity)
                    ? "Herbs/run " + fmt(plan.quantity * plan.patches) + " · " + percent(plan.loss) + " crop loss\n" : "")
                + "\n\nInputs\n" + itemLines(plan.inputs, market::buy)
                + (plan.outputs.isEmpty() ? "" : "\nHarvest sale\n" + itemLines(plan.outputs, market::sell));
            Row row = new Row(name, required + "  " + name + " · " + gp(profit), detail, plan.outputs)
                .training(required, plan.xp, gpPerXp, cost).rate(-gpPerXp);
            long actions = goalActions(values, required, plan.xp);
            if (actions > 0) row.goal = new TrainingCalculator.Goal(actions, cost * actions, -profit * actions);
            row.summary = farmSummary(plan, herb, profit);
            if (!herb && !crop.get("product").isJsonNull()) row.itemId = seed.id();
            rows.add(row);
        }
        rows.sort(trainingOrder(values));
        return rows;
    }

    private static String farmSummary(CropPlan plan, boolean herb, double profit)
    {
        if (herb && !Double.isFinite(plan.quantity)) return "Check herb patches and Resurrect Crops in Setup.";
        StringBuilder summary = new StringBuilder(signedGp(profit)).append("/patch");
        if (!plan.outputs.isEmpty() && Double.isFinite(plan.quantity))
            summary.append(" · ").append(fmt(plan.quantity)).append(herb ? " herbs" : " harvest");
        if (herb && plan.patches > 1)
            summary.append("\n").append(signedGp(profit * plan.patches)).append("/run · ").append(plan.patches).append(" patches");
        return summary.toString();
    }

    @Override
    String actionLabel(Values values, Row row, Market market)
    {
        return row != null && row.itemId != 0 ? "Edit harvest quantity" : null;
    }

    @Override
    void perform(UtilityPanel panel)
    {
        int seed = panel.selected().itemId;
        Map<Integer, Double> harvest = harvestOverrides(panel.values());
        Double previous = harvest.get(seed);
        String initial = previous != null && Double.isFinite(previous)
            ? BigDecimal.valueOf(previous).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() : "";
        Object input = JOptionPane.showInputDialog(panel, "Expected harvest per patch (blank = default)", "Edit harvest",
            JOptionPane.PLAIN_MESSAGE, null, null, initial);
        if (input == null) return;
        try
        {
            String raw = input.toString().strip();
            if (raw.equals(initial)) return;
            if (raw.isEmpty()) harvest.remove(seed);
            else
            {
                double quantity = Double.parseDouble(raw);
                if (!(quantity >= 0 && quantity <= 1_000_000)) throw new NumberFormatException();
                harvest.put(seed, quantity);
            }
        }
        catch (NumberFormatException ex)
        {
            JOptionPane.showMessageDialog(panel, "Enter a quantity from 0 to 1,000,000.");
            return;
        }
        StringBuilder saved = new StringBuilder();
        harvest.forEach((id, quantity) -> saved.append(id).append("=").append(quantity).append(";"));
        panel.store(Map.of("harvest", saved.toString()));
    }

    private static Map<Integer, Double> harvestOverrides(Values values)
    {
        Map<Integer, Double> saved = new LinkedHashMap<>();
        for (String entry : values.text("harvest").split(";"))
        {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            try { saved.put(Integer.parseInt(parts[0]), Double.parseDouble(parts[1])); }
            catch (NumberFormatException ex) { /* Skip invalid overrides. */ }
        }
        return saved;
    }

    CropPlan plan(JsonObject crop, Values values, int level, double custom)
    {
        String kind = s(crop, "category");
        boolean herb = kind.equals("Herbs");
        String compostKey = values.text("compost");
        JsonObject compost = data.getAsJsonObject("composts").getAsJsonObject(compostKey);
        int lives = i(compost, "harvestLives");
        double quantity = n(crop, "quantity", 0);
        double average = UNKNOWN, loss = UNKNOWN, casts = 0;
        List<Item> inputs = new ArrayList<>();
        inputs.add(tuple(crop.get("input")));
        int compostId = i(compost, "id");
        if (compostId > 0)
            inputs.add(item(compostId, s(compost, "name"), values.flag("bucket") ? 0.5 : 1));
        if (values.flag("protection")) inputs.addAll(items(array(crop, "protection")));
        Item output = crop.get("product").isJsonNull() ? null : tuple(crop.get("product"));
        if (herb)
        {
            JsonArray dataRow = data.getAsJsonObject("herbs").getAsJsonArray(Integer.toString(output.id()));
            double[] harvest = estimateHerb(values, level, lives, (int) n(dataRow, 0));
            quantity = harvest[0];
            loss = harvest[1];
            casts = harvest[2];
            if (values.text("sell").equals("clean"))
                output = item((int) n(dataRow, 1), "Clean " + s(crop, "name").toLowerCase(java.util.Locale.ROOT), quantity);
            if (values.text("sell").equals("unfinished"))
            {
                output = item((int) n(dataRow, 2), s(crop, "name") + " potion (unf)", quantity);
                if (quantity > 0) inputs.add(item(227, "Vial of water", quantity));
            }
            if (casts > 0)
            {
                JsonArray runes = array(data, "runes");
                for (int index = 0; index < runes.size(); index++)
                {
                    JsonArray rune = array(runes.get(index));
                    inputs.add(item((int) n(rune, 0), RUNE_NAMES[index], casts * n(rune, 1)));
                }
            }
        }
        else
        {
            if (crop.has("harvestRoll"))
            {
                JsonArray roll = array(crop, "harvestRoll");
                int itemBoost = values.flag("secateurs") ? 110 : 100;
                int attas = values.text("anima").equals("attas") ? 105 : 100;
                double save = roll(boost(n(roll, 0), itemBoost, 0, attas), boost(n(roll, 1), itemBoost, 0, attas), level);
                average = lives / (1 - save);
                quantity = average;
            }
            if (Double.isFinite(custom)) quantity = custom;
        }
        double harvestXp = b(crop, "fixedHarvest") ? (quantity > 0 ? n(crop, "harvest", 0) : 0) : n(crop, "harvest", 0) * quantity;
        double xp = n(crop, "plant", 0) + n(crop, "check", 0) + harvestXp + i(compost, "xp");
        List<Item> outputs = output == null ? List.of() : List.of(item(output.id(), output.name(), quantity));
        return new CropPlan(inputs, outputs, quantity, average, loss, xp, herb ? values.integer("patches") : 1);
    }

    private static double[] estimateHerb(Values values, int level, int lives, int low)
    {
        int magic = values.level(Skill.MAGIC, "magic");
        boolean resurrect = values.flag("resurrect");
        int patches = values.integer("patches");
        List<Integer> protectedBonuses = new ArrayList<>();
        boolean kourend = values.flag("kourend");
        for (String patch : PROTECTED)
            if (values.flag(patch)) protectedBonuses.add(kourend && patch.equals("hosidius") ? 10 : 0);
        List<Integer> ordinaryBonuses = new ArrayList<>();
        int kandarin = Integer.parseInt(values.text("kandarin"));
        if (kandarin > 0) ordinaryBonuses.add(kandarin);
        if (kourend && level >= 65) ordinaryBonuses.add(10);
        if (level < 1 || protectedBonuses.size() + ordinaryBonuses.size() > patches || resurrect && magic < 78)
            return new double[]{UNKNOWN, UNKNOWN, 0};
        while (protectedBonuses.size() + ordinaryBonuses.size() < patches) ordinaryBonuses.add(0);
        int divisor = new int[]{1, 2, 5, 10}[lives - 3];
        double disease = (Math.floor((26 / divisor) * (values.text("anima").equals("iasor") ? 0.2 : 1)) + 1) / 128;
        double healthy = 1 - disease;
        double natural = Math.pow(healthy, 3);
        double survival = natural + (resurrect ? 3 * disease * healthy * healthy * roll(-106, 192, magic) : 0);
        int itemBoost = 100 + (values.flag("secateurs") ? 10 : 0) + (values.flag("cape") ? 5 : 0);
        int attas = values.text("anima").equals("attas") ? 105 : 100;
        double total = 0;
        for (int bonus : protectedBonuses) total += herbs(low, lives, level, itemBoost, attas, bonus);
        for (int bonus : ordinaryBonuses) total += survival * herbs(low, lives, level, itemBoost, attas, bonus);
        double ordinary = ordinaryBonuses.size() / (double) patches;
        return new double[]{total / patches, ordinary * (1 - survival), resurrect ? ordinary * (1 - natural) : 0};
    }

    private static double herbs(int low, int lives, int level, int items, int attas, int diary)
    {
        double save = Math.floor(boost(low, items, diary, attas) * (99 - level) / 98)
            + Math.floor(boost(80, items, diary, attas) * (level - 1) / 98) + 1;
        return lives / (1 - save / 256);
    }

    private static double boost(double roll, int items, int diary, int attas)
    {
        return Math.floor((Math.floor(roll * items / 100) + diary) * attas / 100);
    }

    private static double roll(double low, double high, int level)
    {
        return (Math.round((low * (99 - level) + high * (level - 1)) / 98) + 1) / 256.0;
    }

    static final class CropPlan
    {
        final List<Item> inputs, outputs;
        final double quantity, average, loss, xp;
        final int patches;

        CropPlan(List<Item> inputs, List<Item> outputs, double quantity, double average, double loss, double xp, int patches)
        {
            this.inputs = inputs;
            this.outputs = outputs;
            this.quantity = quantity;
            this.average = average;
            this.loss = loss;
            this.xp = xp;
            this.patches = patches;
        }
    }
}

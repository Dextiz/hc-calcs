package se.sandr.hccalcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import se.sandr.hccalcs.AlchemyCalculator.AlchItem;
import se.sandr.hccalcs.RecipeCatalog.Item;

abstract class UtilityCalculator
{
    static final double UNKNOWN = Double.NaN;
    static final int DEFAULT_LEVEL = 99;
    static final String NUMBER_PATTERN = "#,##0.#";
    static final String ALL = "All";
    static final String BEST_GP_XP = "Best GP/XP";
    static final String[] TRAINING_SORTS = {BEST_GP_XP, "Level", "Highest XP", "Lowest cost"};
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() ->
        new DecimalFormat(NUMBER_PATTERN, DecimalFormatSymbols.getInstance(Locale.UK)));

    final String id;
    final String title;
    final Skill skill;
    final List<Field> fields = new ArrayList<>();

    UtilityCalculator(String id, String title, Skill skill)
    {
        this.id = id;
        this.title = title;
        this.skill = skill;
    }

    static List<UtilityCalculator> load() throws IOException
    {
        JsonObject data;
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
            UtilityCalculator.class.getResourceAsStream("/hc-calcs-utilities.json")), StandardCharsets.UTF_8))
        {
            data = new JsonParser().parse(reader).getAsJsonObject();
        }
        FarmingCalculator farming = new FarmingCalculator(data.getAsJsonObject("farming"));
        return List.of(new PrayerCalculator(data.getAsJsonObject("prayer")), farming,
            new MixologyCalculator(data.getAsJsonObject("mixology")), new CombatCalculator(data.getAsJsonObject("combat")),
            new AlchemyCalculator(), new NmzCalculator(data.getAsJsonObject("nmz")),
            new KingdomCalculator(data.getAsJsonObject("kingdom"), farming));
    }

    @Override
    public final String toString() { return title; }

    abstract List<Row> calculate(Values values, Market market);
    boolean volumeNeeded(Values values) { return false; }
    boolean showsXp(Values values) { return values.input.containsKey("minXp"); }
    String actionLabel(Values values, Row row, Market market) { return null; }
    void perform(UtilityPanel panel) { }

    void number(String id, String label, int initial, int min, int max)
    {
        fields.add(new Field(id, label, initial, min, max, 1, List.of()));
    }

    void decimal(String id, String label, double initial, double min, double max)
    {
        fields.add(new Field(id, label, initial, min, max, 0.1, List.of()));
    }

    void flag(String id, String label, boolean initial)
    {
        fields.add(new Field(id, label, initial, 0, 0, 0, List.of()));
    }

    void search(String id, String label)
    {
        fields.add(new Field(id, label, "", 0, 0, 0, List.of()));
    }

    void hidden(String id)
    {
        search(id, "");
    }

    void choice(String id, String label, String initial, String... options)
    {
        List<Option> choices = new ArrayList<>();
        for (String text : options)
        {
            String[] parts = text.split("=", 2);
            choices.add(new Option(parts[0], parts[parts.length - 1]));
        }
        fields.add(new Field(id, label, initial, 0, 0, 0, choices));
    }

    void trainingFields()
    {
        number("level", skill.getName() + " (0 = auto)", 0, 0, 99);
        flag("only", "Only show unlocked", true);
        number("goal", "Goal level (0 = off)", 0, 0, 99);
        xpFilter();
        number("volume", "Min. 24h volume", 0, 0, Integer.MAX_VALUE);
        choice("sort", "Sort by", BEST_GP_XP, TRAINING_SORTS);
    }

    void xpFilter()
    {
        decimal("minXp", "Minimum XP/action", 0, 0, 100_000);
    }

    static boolean allowsXp(Values values, double xp)
    {
        double minimum = values.number("minXp");
        return minimum == 0 || xp >= minimum;
    }

    boolean trainingAllowed(Values values, Market market, int level, double xp, List<Item> inputs, List<Item> outputs)
    {
        int current = values.level(skill, "level");
        int volume = values.integer("volume");
        return (!values.flag("only") || current >= level)
            && allowsXp(values, xp)
            && (volume == 0 || inputs.stream().filter(item -> item.quantity() > 0)
                .allMatch(item -> market.volume.tradeCount(item.id(), true) >= volume)
                && outputs.stream().filter(item -> item.quantity() > 0)
                    .allMatch(item -> market.volume.tradeCount(item.id(), false) >= volume));
    }

    long goalActions(Values values, int required, double xp)
    {
        int level = values.level(skill, "level");
        int target = values.integer("goal");
        if (level < required || target <= level || !(xp > 0))
        {
            return 0;
        }
        int start = values.integer("level") == 0
            ? values.xp.getOrDefault(skill, Experience.getXpForLevel(level)) : Experience.getXpForLevel(level);
        return Math.max(0, (long) Math.ceil((Experience.getXpForLevel(target) - start) / xp));
    }

    static Comparator<Row> metric(ToDoubleFunction<Row> score, boolean descending)
    {
        return Comparator.<Row>comparingDouble(row ->
        {
            double value = score.applyAsDouble(row);
            return Double.isFinite(value) ? (descending ? -value : value) : Double.POSITIVE_INFINITY;
        }).thenComparing(row -> row.name);
    }

    static Comparator<Row> trainingOrder(Values values)
    {
        switch (values.text("sort"))
        {
            case "Highest XP": return metric(row -> row.xp, true);
            case BEST_GP_XP: return bestXpOrder();
            case "Lowest cost": return metric(row -> row.cost, false);
            default: return Comparator.comparingInt(row -> row.level);
        }
    }

    static Comparator<Row> bestXpOrder()
    {
        return metric(row -> row.gpPerXp == null ? UNKNOWN : row.gpPerXp, false);
    }

    static String fmt(double value)
    {
        if (!Double.isFinite(value)) return "—";
        if (value == 0) return "0";
        if (Math.abs(value) < 0.1) return value < 0 ? "-<0.1" : "<0.1";
        return FORMAT.get().format(value);
    }

    static String gp(double value) { return fmt(value) + " GP"; }
    static String percent(double value) { return fmt(value * 100) + "%"; }

    static String age(long timestamp, long now)
    {
        if (timestamp <= 0 || timestamp > now)
        {
            return "unknown age";
        }
        long mins = (now - timestamp) / 60;
        if (mins >= 1440) return mins / 1440 + "d " + mins / 60 % 24 + "h ago";
        if (mins >= 60) return mins / 60 + "h " + mins % 60 + "m ago";
        return (mins == 0 ? "<1" : mins) + "m ago";
    }

    static Row info(String name, String details) { return new Row(name, name, details, List.of()); }
    static Item item(Integer id, String name, double amount) { return new Item(id, name, amount); }
    static JsonArray array(JsonElement element) { return element.getAsJsonArray(); }
    static JsonArray array(JsonObject object, String key) { return object.getAsJsonArray(key); }
    static JsonObject object(JsonElement element) { return element.getAsJsonObject(); }
    static double n(JsonArray a, int i) { return a.get(i).isJsonNull() ? UNKNOWN : a.get(i).getAsDouble(); }
    static String s(JsonArray a, int i) { return a.get(i).getAsString(); }
    static String s(JsonObject o, String key) { return o.has(key) ? o.get(key).getAsString() : ""; }
    static double n(JsonObject o, String key, double fallback) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : fallback; }
    static int i(JsonObject o, String key) { return (int) n(o, key, 0); }
    static boolean b(JsonObject o, String key) { return o.has(key) && o.get(key).getAsBoolean(); }

    static Item tuple(JsonElement element)
    {
        JsonArray a = array(element);
        return item(a.get(0).isJsonNull() ? null : (int) n(a, 0), s(a, 1), a.size() >= 3 ? n(a, 2) : 1);
    }

    static List<Item> items(JsonArray source)
    {
        List<Item> items = new ArrayList<>();
        if (source != null)
        {
            source.forEach(entry -> items.add(tuple(entry)));
        }
        return items;
    }

    static Set<Integer> ids(JsonArray source)
    {
        Set<Integer> ids = new HashSet<>();
        source.forEach(entry -> ids.add(entry.getAsInt()));
        return ids;
    }

    static double basket(List<Item> items, ToDoubleFunction<Integer> quote)
    {
        return items.stream().filter(item -> item.quantity() > 0)
            .mapToDouble(item -> quote.applyAsDouble(item.id()) * item.quantity()).sum();
    }

    static String itemLines(List<Item> items, ToDoubleFunction<Integer> quote)
    {
        StringBuilder detail = new StringBuilder();
        for (Item item : items)
        {
            if (item.quantity() > 0)
            {
                detail.append(fmt(item.quantity())).append(" × ").append(item.name()).append(" · ")
                    .append(gp(quote.applyAsDouble(item.id()))).append(" each\n");
            }
        }
        return detail.toString();
    }

    static final class Row
    {
        final String name;
        final String label;
        final String detail;
        final List<Item> outputs;
        int level;
        double xp = UNKNOWN;
        double score = UNKNOWN;
        double cost = UNKNOWN;
        Double gpPerXp;
        int itemId;

        Row(String name, String label, String detail, List<Item> outputs)
        {
            this.name = name;
            this.label = label;
            this.detail = detail;
            this.outputs = outputs;
        }

        Row training(int level, double xp, double score, double cost)
        {
            this.level = level;
            this.xp = xp;
            this.score = score;
            this.cost = cost;
            return this;
        }

        Row rate(double costPerXp)
        {
            gpPerXp = costPerXp;
            return this;
        }
    }

    static final class Option
    {
        final String id;
        final String label;

        Option(String id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    static final class Field
    {
        final String id, label;
        final Object initial;
        final double min, max, step;
        final List<Option> options;

        Field(String id, String label, Object initial, double min, double max, double step, List<Option> options)
        {
            this.id = id;
            this.label = label;
            this.initial = initial;
            this.min = min;
            this.max = max;
            this.step = step;
            this.options = options;
        }

        Object parse(String saved)
        {
            if (saved == null) return initial;
            if (initial instanceof Number)
            {
                try
                {
                    double value = Double.parseDouble(saved);
                    if (value >= min && value <= max)
                    {
                        if (initial instanceof Integer) return (int) value;
                        return value;
                    }
                }
                catch (NumberFormatException ex) { /* Use the default. */ }
                return initial;
            }
            if (initial instanceof Boolean) return Boolean.parseBoolean(saved);
            return options.isEmpty() || options.stream().anyMatch(option -> option.id.equals(saved)) ? saved : initial;
        }
    }

    static final class Values
    {
        final Map<String, Object> input;
        final Map<Skill, Integer> levels, xp;

        Values(Map<String, Object> input, Map<Skill, Integer> levels, Map<Skill, Integer> xp)
        {
            this.input = new LinkedHashMap<>(input);
            this.levels = levels;
            this.xp = xp;
        }

        int integer(String field) { return ((Number) input.get(field)).intValue(); }
        double number(String field) { return ((Number) input.get(field)).doubleValue(); }
        String text(String field) { return input.get(field).toString(); }
        boolean flag(String field) { return (boolean) input.get(field); }
        int level(Skill skill)
        {
            return levels.getOrDefault(skill, DEFAULT_LEVEL);
        }

        int level(Skill skill, String override)
        {
            int level = integer(override);
            return level > 0 ? level : level(skill);
        }
    }

    static final class Market
    {
        final MarketSnapshot prices, volume;
        final Map<Integer, Long> overrides;
        final List<AlchItem> catalogue;
        final long now;

        Market(MarketSnapshot prices, MarketSnapshot volume, Map<Integer, Long> overrides, List<AlchItem> catalogue)
        {
            this.prices = prices;
            this.volume = volume;
            this.overrides = overrides;
            this.catalogue = catalogue;
            now = Instant.now().getEpochSecond();
        }

        double buy(Integer id) { return prices.buy(id); }
        double sell(Integer id) { return TrainingCalculator.salePrice(id, prices, overrides); }
        double net(Integer id, Set<Integer> exempt)
        {
            double sell = sell(id);
            return id != null && exempt.contains(id) ? sell : sell - Math.min(Math.floor(sell / 50), 5_000_000);
        }
    }
}

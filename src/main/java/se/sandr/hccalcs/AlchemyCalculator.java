package se.sandr.hccalcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AlchemyCalculator extends UtilityCalculator
{
    private static final int MIN_BUYS = 100;
    private static final int XP_PER_CAST = 65;
    private static final int MAGIC_LEVEL = 55;
    private static final String[] RANGES = {"Margin", "Buy price", "High alch", "Buy limit", "Buys / 24h", "Daily volume"};
    private static final String[] SORTS = {"Margin", "Buy price", "High alch", "Buy limit", "Buys / 24h", "Daily volume",
        "Last buy", "Access", "Name", BEST_GP_XP};

    AlchemyCalculator()
    {
        super("alch", "High Alchemy", null);
        xpFilter();
        search("search", "Item name");
        flag("free", "Free-to-play only", false);
        flag("onlySaved", "Favourites only", false);
        hidden("favourites");
        choice("sort", "Sort by", BEST_GP_XP, SORTS);
        flag("descending", "Descending", true);
        for (int index = 0; index < RANGES.length; index++)
        {
            search("min" + index, RANGES[index] + " min");
            search("max" + index, RANGES[index] + " max");
        }
    }

    @Override
    boolean volumeNeeded(Values values) { return true; }

    @Override
    List<Row> calculate(Values values, Market market)
    {
        if (!allowsXp(values, XP_PER_CAST)) return List.of();
        if (market.catalogue.isEmpty() || market.volume == MarketSnapshot.EMPTY)
            return List.of(info("Waiting for HCW catalogue / volume", "Alchemy requires the item catalogue and a completed UTC day of volume."));
        double nature = market.buy(561);
        String query = values.text("search").toLowerCase(Locale.ROOT).trim();
        Set<String> favourites = favourites(values);
        double[] lower = new double[RANGES.length], upper = new double[RANGES.length];
        for (int index = 0; index < RANGES.length; index++)
        {
            long minimum = index == 0 ? Long.MIN_VALUE : 0;
            lower[index] = bound(values.text("min" + index), minimum, Double.NEGATIVE_INFINITY);
            upper[index] = bound(values.text("max" + index), minimum, Double.POSITIVE_INFINITY);
        }
        boolean bestRate = values.text("sort").equals(BEST_GP_XP);
        int column = bestRate ? 0 : Arrays.asList(SORTS).indexOf(values.text("sort"));
        List<Row> rows = new ArrayList<>();
        for (AlchItem item : market.catalogue)
        {
            double buys = market.volume.tradeCount(item.id, true);
            boolean saved = favourites.contains(Integer.toString(item.id));
            if (!(buys >= MIN_BUYS) || values.flag("free") && item.members || values.flag("onlySaved") && !saved
                || !item.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            double buy = market.buy(item.id);
            long time = Double.isFinite(buy) ? market.prices.tradeTime(item.id, true) : -1;
            double daily = buys + market.volume.tradeCount(item.id, false);
            double margin = item.alch - buy - nature;
            double[] metrics = {margin, buy, item.alch, item.limit, buys, daily,
                time > 0 && time <= market.now ? time : UNKNOWN, item.members ? 1 : 0, UNKNOWN};
            if (!inRange(metrics, lower, upper)) continue;
            String detail = item.name + (item.members ? " · Members" : " · F2P") + "\n"
                + XP_PER_CAST + " XP/cast · Requires " + MAGIC_LEVEL + " Magic\n"
                + gp(margin) + " profit/cast\nHigh alch " + gp(item.alch) + "\nItem buy " + gp(buy)
                + "\nNature rune " + gp(nature) + "\nLast buy " + age(time, market.now) + "\n\n"
                + "Buy limit " + fmt(item.limit) + "\nBuys / 24h " + fmt(buys)
                + "\nDaily volume " + fmt(daily) + "\nVolume window " + market.volume.asOf.toString().substring(0, 10) + " UTC";
            Row row = new Row(item.name, (saved ? "★ " : "") + item.name + " · " + gp(margin), detail, List.of());
            row.itemId = item.id;
            row.score = metrics[Math.max(0, column)];
            row.cost = buy;
            row.level = MAGIC_LEVEL;
            row.xp = XP_PER_CAST;
            row.rate(-margin / XP_PER_CAST);
            rows.add(row);
        }
        Comparator<Row> byName = Comparator.comparing(row -> row.name, String.CASE_INSENSITIVE_ORDER);
        if (bestRate)
            rows.sort(bestXpOrder());
        else
            rows.sort(values.text("sort").equals("Name") ? (values.flag("descending") ? byName.reversed() : byName)
                : metric(row -> row.score, values.flag("descending")));
        return rows;
    }

    @Override
    String actionLabel(Values values, Row row, Market market)
    {
        if (row == null || row.itemId <= 0) return null;
        return favourites(values).contains(Integer.toString(row.itemId)) ? "Remove favourite" : "Save favourite";
    }

    @Override
    void perform(UtilityPanel panel)
    {
        String id = Integer.toString(panel.selected().itemId);
        Set<String> favourites = favourites(panel.values());
        if (!favourites.remove(id)) favourites.add(id);
        panel.store(Map.of("favourites", String.join(",", favourites)));
    }

    private static Set<String> favourites(Values values)
    {
        Set<String> saved = new LinkedHashSet<>(Arrays.asList(values.text("favourites").split(",")));
        saved.remove("");
        return saved;
    }

    private static double bound(String text, long minimum, double fallback)
    {
        try
        {
            long value = Long.parseLong(text.trim());
            return value >= minimum ? value : fallback;
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    private static boolean inRange(double[] values, double[] lower, double[] upper)
    {
        for (int index = 0; index < lower.length; index++)
        {
            if ((Double.isFinite(lower[index]) || Double.isFinite(upper[index])) &&
                !(values[index] >= lower[index] && values[index] <= upper[index])) return false;
        }
        return true;
    }

    static List<AlchItem> readItems(Reader reader)
    {
        List<AlchItem> items = new ArrayList<>();
        for (JsonElement element : array(new JsonParser().parse(reader)))
        {
            if (!element.isJsonObject()) continue;
            JsonObject entry = object(element);
            int id = wholeNumber(entry.get("id")), high = wholeNumber(entry.get("highalch"));
            JsonElement nameElement = entry.get("name"), members = entry.get("members");
            if (id <= 0 || high <= 0 || nameElement == null || !nameElement.isJsonPrimitive()
                || !nameElement.getAsJsonPrimitive().isString() || members == null || !members.isJsonPrimitive()
                || !members.getAsJsonPrimitive().isBoolean()) continue;
            String name = nameElement.getAsString();
            if (name.length() > 128 || name.isBlank() || name.indexOf('<') >= 0 || name.indexOf('>') >= 0 ||
                name.codePoints().anyMatch(character -> Character.isISOControl(character) || character == 0x2028 || character == 0x2029))
                continue;
            int limit = wholeNumber(entry.get("limit"));
            items.add(new AlchItem(id, name, high, members.getAsBoolean(), limit > 0 ? limit : UNKNOWN));
        }
        if (items.isEmpty()) throw new IllegalArgumentException("Empty item catalogue");
        return List.copyOf(items);
    }

    private static int wholeNumber(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return 0;
        double value = element.getAsDouble();
        return value > 0 && value <= Integer.MAX_VALUE && value == Math.floor(value) ? (int) value : 0;
    }

    static final class AlchItem
    {
        final int id, alch;
        final String name;
        final boolean members;
        final double limit;

        AlchItem(int id, String name, int alch, boolean members, double limit)
        {
            this.id = id;
            this.name = name;
            this.alch = alch;
            this.members = members;
            this.limit = limit;
        }
    }
}

package se.sandr.hccalcs;

import com.google.gson.Gson;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Experience;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import se.sandr.hccalcs.RecipeCatalog.Recipe;
import se.sandr.hccalcs.RunecraftRecipes.EssencePouch;
import se.sandr.hccalcs.TrainingCalculator.Amulet;
import se.sandr.hccalcs.TrainingCalculator.Goal;
import se.sandr.hccalcs.TrainingCalculator.Kitchen;
import se.sandr.hccalcs.TrainingCalculator.Result;
import se.sandr.hccalcs.TrainingCalculator.RunecraftHourly;
import se.sandr.hccalcs.TrainingCalculator.Settings;

public final class CalculatorChecks
{
    public static void main(String[] args) throws Exception
    {
        Map<String, RecipeCatalog> data = RecipeCatalog.load(new Gson());
        require(data.size() == HcCalcsPlugin.SKILLS.length, "Calculator catalog");
        int recipes = data.values().stream().mapToInt(catalog -> catalog.rows.size()).sum();
        require(recipes > 500, "Website recipe export");

        Instant now = Instant.parse("2026-11-25T12:00:00Z");
        MarketSnapshot prices = snapshot("{\"227\":{\"high\":100},\"249\":{\"high\":1000}," +
            "\"221\":{\"high\":200},\"21163\":{\"high\":10000},\"2428\":{\"low\":1600}," +
            "\"377\":{\"high\":200},\"379\":{\"low\":100}}", now);
        RecipeCatalog herbs = data.get("herblore");
        Recipe attack = find(herbs, "Attack potion(4)");
        Settings alchemist = new Settings(Amulet.ALCHEMIST, true, Kitchen.RANGE, false);
        Result potion = TrainingCalculator.calculate(attack, herbs.taxExempt, alchemist, 5, prices, Map.of());
        close(potion.cost, 100 + 1000 + 200 * .9 + 150, "Herblore equipment cost");
        close(potion.netSale, (1600 - 32) * .7875, "Extra doses and GE tax");
        require(potion.inputs.size() == 4, "Amulet charge appears as an ingredient");
        Result override = TrainingCalculator.calculate(attack, herbs.taxExempt, alchemist, 5, prices, Map.of(2428, 2000L));
        close(override.netSale, 1960 * .7875, "Sell override");
        require(Double.isNaN(TrainingCalculator.calculate(attack, herbs.taxExempt, alchemist,
            5, MarketSnapshot.EMPTY, Map.of()).gpPerXp), "Missing quotes stay unknown");
        Goal goal = TrainingCalculator.goal(attack, potion, alchemist, 5, Experience.getXpForLevel(6) - 26, 6);
        require(goal.attempts == 2, "Goal uses actual XP");
        close(goal.net, 2 * (potion.cost - potion.netSale), "Goal net cost");
        SkillProgress exact = SkillProgress.resolve(20, Experience.getXpForLevel(6) - 26, 99, null);
        require(exact.level == 5 && TrainingCalculator.goal(attack, potion, alchemist, exact.level, exact.xp, 6).attempts == 2,
            "Exact XP overrides the saved level and determines the actions to goal");
        require(SkillProgress.resolve(99, 0, 99, null).level == 1, "Zero exact XP is a level 1 start");
        require(SkillProgress.resolve(0, Experience.MAX_SKILL_XP, null, null).level == 99,
            "XP above level 99 keeps the real level capped at 99");
        require(SkillProgress.resolve(0, null, 5, exact.xp).xp == exact.xp, "Auto uses live XP");
        require(SkillProgress.resolve(5, null, 5, exact.xp).xp == Experience.getXpForLevel(5),
            "Manual levels use the start of the level");

        RecipeCatalog cooking = data.get("cooking");
        Recipe lobster = find(cooking, "Lobster");
        Settings range = new Settings(Amulet.NONE, false, Kitchen.RANGE, false);
        Settings hosidius = new Settings(Amulet.NONE, false, Kitchen.HOSIDIUS_ELITE, true);
        Result burn = TrainingCalculator.calculate(lobster, cooking.taxExempt, range, 40, prices, Map.of());
        Result equipped = TrainingCalculator.calculate(lobster, cooking.taxExempt, hosidius, 40, prices, Map.of());
        close(burn.success, 156 / 256.0, "Range success chance");
        close(equipped.success, 206 / 256.0, "Hosidius and gauntlet success chance");
        close(burn.gpPerXp, (100 * burn.success - 200) / (120 * burn.success), "True GP/XP");
        Goal cookingGoal = TrainingCalculator.goal(lobster, burn, range, 40, null, 45);
        require(cookingGoal.attempts > (Experience.getXpForLevel(45) - Experience.getXpForLevel(40)) / 120.0,
            "Cooking goal counts failed attempts");

        verifyRunecraft(data.get("runecraft"), now);

        long day = Instant.parse("2026-11-24T00:00:00Z").getEpochSecond();
        MarketSnapshot volume = MarketSnapshot.read(new StringReader("{\"timestamp\":" + day +
            ",\"data\":{\"377\":{\"highPriceVolume\":20},\"379\":{\"lowPriceVolume\":12}}}"), now, true);
        require(TrainingCalculator.meetsVolume(burn, lobster, volume, 12), "Threshold uses buys and sells");
        require(!TrainingCalculator.meetsVolume(burn, lobster, volume, 13), "Sell volume below threshold");
        try
        {
            MarketSnapshot.read(new StringReader("{\"timestamp\":" + (day + 86400) + ",\"data\":{}}"), now, true);
            throw new AssertionError("Open UTC day was accepted");
        }
        catch (IllegalArgumentException expected)
        {
            // The unfinished UTC day must never pass the volume filter.
        }

        if (args.length == 2)
        {
            try (Reader livePrices = Files.newBufferedReader(Path.of(args[0]));
                Reader liveVolume = Files.newBufferedReader(Path.of(args[1])))
            {
                MarketSnapshot hcPrices = MarketSnapshot.read(livePrices, Instant.now(), false);
                MarketSnapshot hcVolume = MarketSnapshot.read(liveVolume, Instant.now(), true);
                require(Double.isFinite(hcPrices.buy(227)), "Live vial price");
                require(Double.isFinite(hcVolume.buy(227)), "Live vial buy volume");
                System.out.println("Live HCW price and volume payloads verified");
            }
        }
        System.out.println("HCW Calcs checks passed for " + recipes + " training recipes");
        UtilityChecks.verify(args);
        verifyMarketRequests();
    }

    private static void verifyRunecraft(RecipeCatalog catalog, Instant now)
    {
        require(catalog.rows.size() == 24, "Runecraft methods");
        Set<EssencePouch> regularPouches = Set.of(EssencePouch.SMALL, EssencePouch.MEDIUM,
            EssencePouch.LARGE, EssencePouch.GIANT);
        close(RunecraftRecipes.pouchLoad(49, 25, regularPouches).total, 32, "Only level-unlocked pouches count");
        RunecraftRecipes.PouchLoad filledPouches = RunecraftRecipes.pouchLoad(75, 25, regularPouches);
        close(filledPouches.loose, 21, "Regular pouches occupy one slot each");
        close(filledPouches.total, 51, "Regular pouch contents extend the trip");
        Set<EssencePouch> colossal = Set.of(EssencePouch.COLOSSAL, EssencePouch.GIANT);
        close(RunecraftRecipes.pouchLoad(25, 25, colossal).total, 32, "Level 25 colossal capacity");
        close(RunecraftRecipes.pouchLoad(50, 25, colossal).total, 40, "Level 50 colossal capacity");
        close(RunecraftRecipes.pouchLoad(84, 25, colossal).total, 51, "Colossal uses the level 75 tier until 85");
        close(RunecraftRecipes.pouchLoad(85, 25, colossal).total, 64, "Colossal replaces regular pouches");
        MarketSnapshot quotes = snapshot("{\"7936\":{\"high\":4},\"561\":{\"low\":100}," +
            "\"565\":{\"low\":250},\"4699\":{\"low\":24},\"557\":{\"high\":6}," +
            "\"1440\":{\"high\":375},\"5521\":{\"high\":1600}}", now);
        Settings settings = new Settings(Amulet.NONE, false, Kitchen.RANGE, false, 25);
        Recipe nature = find(catalog, "Nature rune");
        Result single = TrainingCalculator.calculate(nature, catalog.taxExempt, settings, 90, quotes, Map.of());
        Result doubled = TrainingCalculator.calculate(nature, catalog.taxExempt, settings, 91, quotes, Map.of());
        close(single.outputs.get(0).quantity(), 1, "Single nature rune at level 90");
        close(doubled.outputs.get(0).quantity(), 2, "Double nature runes at level 91");
        close(doubled.gpPerXp, (196 - 4) / 9.0, "Extra runes increase sale revenue without adding XP");
        Result override = TrainingCalculator.calculate(nature, catalog.taxExempt, settings, 91, quotes, Map.of(561, 150L));
        close(override.netSale, 294, "Runecraft respects output sell overrides");
        close(nature.runecraft.goalRunes(90, Experience.getXpForLevel(91) - 9, 9, 26, 25), 27,
            "A new multiplier starts after the altar use that earned the level");
        RunecraftHourly natureRate = TrainingCalculator.runecraftHourly(nature, doubled, 25,
            nature.runecraft.defaultEssencePerRun(25), nature.runecraft.defaultRunsPerHour);
        close(natureRate.xp, 12_375, "Estimated hourly XP uses essence per trip and trips per hour");
        close(natureRate.runes, 2_750, "Hourly runes include the level 91 multiplier");
        close(natureRate.profit, 264_000, "Hourly revenue accounts for GE tax and buy costs");

        Recipe lava = find(catalog, "Lava rune");
        Result combined = TrainingCalculator.calculate(lava, catalog.taxExempt, settings, 23, quotes, Map.of());
        close(combined.cost, 29, "Lava includes one talisman and necklace charge per 25 essence");
        close(combined.gpPerXp, -5 / 10.5, "Lava is a loss per XP when inputs exceed the sale value");
        Goal partial = TrainingCalculator.goal(lava, combined, settings, 23, Experience.getXpForLevel(24) - 10, 24);
        close(partial.gross, 485, "A partial altar use still consumes a full talisman and necklace charge");
        RunecraftHourly lavaRate = TrainingCalculator.runecraftHourly(lava, combined, 25, 40, 30.5);
        close(lavaRate.xp, 12_810, "Custom fractional runs per hour");
        close(lavaRate.runes, 1_220, "Custom essence per run");
        close(lavaRate.profit, -11_895, "Two altar uses billed per trip, including the partial use");

        Recipe blood = find(catalog, "Blood rune (Arceuus)");
        Result gathered = TrainingCalculator.calculate(blood, catalog.taxExempt, settings, 77, quotes, Map.of());
        close(gathered.cost, 0, "Dark essence has no market purchase cost");
        close(gathered.gpPerXp, 245 / 23.8, "Arceuus blood rune profit per XP");
        RunecraftHourly arceuusRate = TrainingCalculator.runecraftHourly(blood, gathered, 25,
            blood.runecraft.defaultEssencePerRun(filledPouches.total), blood.runecraft.defaultRunsPerHour);
        close(arceuusRate.runes, 1_470, "Arceuus dark essence trips include both inventories");
    }

    private static void verifyMarketRequests() throws Exception
    {
        Instant priceTime = Instant.parse("2026-11-25T12:00:00Z");
        long previousDay = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS).getEpochSecond();
        Map<String, String> payloads = Map.of(
            "/api/v2/osrs-hc/latest", "{\"data\":{\"227\":{\"high\":100}}}",
            "/api/v2/osrs-hc/24h", "{\"timestamp\":" + previousDay +
                ",\"data\":{\"227\":{\"highPriceVolume\":100}}}",
            "/api/v2/osrs-hc/mapping", "[{\"id\":227,\"name\":\"Vial of water\",\"highalch\":2,\"members\":false}]");
        ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
        {
            Request request = chain.request();
            requests.add(request);
            String payload = payloads.getOrDefault(request.url().encodedPath(), "");
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Date", "Wed, 25 Nov 2026 12:00:00 GMT")
                .body(ResponseBody.create(MediaType.parse("application/json"), payload)).build();
        }).build();
        try
        {
            MarketClient marketClient = new MarketClient(http);
            CompletableFuture<MarketSnapshot> prices = new CompletableFuture<>();
            CompletableFuture<MarketSnapshot> volume = new CompletableFuture<>();
            CompletableFuture<List<AlchemyCalculator.AlchItem>> items = new CompletableFuture<>();
            marketClient.fetch(false, prices::complete);
            marketClient.fetch(true, volume::complete);
            marketClient.fetchItems(items::complete);
            MarketSnapshot snapshot = prices.get(5, TimeUnit.SECONDS);
            require(snapshot != null && snapshot.asOf.equals(priceTime) && snapshot.buy(227) == 100,
                "Price fetch uses the direct API's Date header");
            require(volume.get(5, TimeUnit.SECONDS).tradeCount(227, true) == 100, "HCW volume response");
            require(items.get(5, TimeUnit.SECONDS).size() == 1, "HCW mapping response");
            require(requests.size() == 3 && requests.stream().allMatch(request ->
                request.url().isHttps() && request.url().host().equals("prices.runescape.wiki")
                    && payloads.containsKey(request.url().encodedPath()) && request.url().query() == null
                    && request.method().equals("GET") && request.body() == null
                    && request.header("User-Agent").startsWith("HCW Calcs")),
                "Public HCW requests go directly to the Wiki and carry the plugin's User-Agent");
        }
        finally
        {
            http.dispatcher().executorService().shutdown();
        }
    }

    private static MarketSnapshot snapshot(String items, Instant now)
    {
        return MarketSnapshot.read(new StringReader("{\"data\":" + items + "}"), now, false);
    }

    private static Recipe find(RecipeCatalog catalog, String name)
    {
        return catalog.rows.stream().filter(row -> row.name.equals(name)).findFirst().orElseThrow();
    }

    private static void close(double actual, double expected, String label)
    {
        require(Math.abs(actual - expected) < 0.00001, label + " expected " + expected + " but was " + actual);
    }

    private static void require(boolean condition, String label)
    {
        if (!condition)
        {
            throw new AssertionError(label);
        }
    }
}

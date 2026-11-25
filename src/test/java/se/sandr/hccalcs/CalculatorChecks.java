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
import se.sandr.hccalcs.TrainingCalculator.Amulet;
import se.sandr.hccalcs.TrainingCalculator.Goal;
import se.sandr.hccalcs.TrainingCalculator.Kitchen;
import se.sandr.hccalcs.TrainingCalculator.Result;
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
        System.out.println("HC Calcs checks passed for " + recipes + " training recipes");
        UtilityChecks.verify(args);
        verifyMarketRequests();
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
                    && request.header("User-Agent").startsWith("HC Calcs")),
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

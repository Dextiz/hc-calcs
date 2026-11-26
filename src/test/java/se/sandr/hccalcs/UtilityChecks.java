package se.sandr.hccalcs;

import java.awt.Color;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import se.sandr.hccalcs.UtilityCalculator.Market;
import se.sandr.hccalcs.UtilityCalculator.Row;
import se.sandr.hccalcs.UtilityCalculator.Values;

final class UtilityChecks
{
    static void verify(String[] args) throws Exception
    {
        Map<String, UtilityCalculator> calculators = new LinkedHashMap<>();
        for (UtilityCalculator calculator : UtilityCalculator.load()) calculators.put(calculator.id, calculator);
        require(calculators.keySet().equals(java.util.Set.of("prayer", "farming", "mixology", "combat", "alch", "nmz", "kingdom")),
            "RuneLite calculator catalogue");
        require(XpResults.rate(-2.5).equals("+2.5gp") && XpResults.rate(2.5).equals("-2.5gp")
            && XpResults.rate(0).equals("0gp") && XpResults.rate(Double.NaN).equals("—"), "Signed GP/XP formatting");
        require(XpResults.rate(-12.3456).equals("+12.3gp") && XpResults.rate(0.004).equals("-<0.1gp"),
            "GP/XP display has at most one decimal");
        require(UtilityCalculator.fmt(1234.567).equals("1,234.6") && UtilityCalculator.fmt(32).equals("32")
            && UtilityCalculator.percent(0.123456).equals("12.3%") && UtilityCalculator.fmt(0.02).equals("<0.1"),
            "Amounts and percentages share one-decimal display precision");
        Color profitColor = XpResults.rateColor(-2.5, Color.WHITE), costColor = XpResults.rateColor(2.5, Color.WHITE);
        require(profitColor.getGreen() > profitColor.getRed() && costColor.getRed() > costColor.getGreen(),
            "Profit is green and cost is red");

        Instant now = Instant.now();
        long fresh = now.getEpochSecond() - 120;
        long day = now.truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS).getEpochSecond();
        String quotes = "{\"data\":{\"13469\":{\"high\":800},\"559\":{\"high\":10},\"561\":{\"high\":100}," +
            "\"566\":{\"high\":200},\"565\":{\"high\":250},\"557\":{\"high\":40},\"536\":{\"high\":3000}," +
            "\"1993\":{\"high\":30},\"28924\":{\"high\":500},\"5295\":{\"high\":400},\"21483\":{\"high\":500}," +
            "\"227\":{\"high\":10},\"99\":{\"low\":2000},\"5324\":{\"high\":10},\"5458\":{\"high\":100},\"1965\":{\"low\":100}," +
            "\"249\":{\"high\":100},\"453\":{\"low\":200},\"10034\":{\"high\":450}," +
            "\"1\":{\"high\":500,\"highTime\":" + fresh + "},\"2\":{\"high\":100},\"3\":{\"high\":100}}}";
        MarketSnapshot prices = MarketSnapshot.read(new StringReader(quotes), now, false);
        MarketSnapshot volume = MarketSnapshot.read(new StringReader("{\"timestamp\":" + day + ",\"data\":{" +
            "\"1\":{\"highPriceVolume\":100,\"lowPriceVolume\":0},\"2\":{\"highPriceVolume\":99,\"lowPriceVolume\":1}," +
            "\"3\":{\"highPriceVolume\":120,\"lowPriceVolume\":1}}}"), now, true);
        List<AlchemyCalculator.AlchItem> items = AlchemyCalculator.readItems(new StringReader("[" +
            "{\"id\":1,\"name\":\"Tradeable\",\"highalch\":700,\"members\":false,\"limit\":80}," +
            "{\"id\":2,\"name\":\"Too thin\",\"highalch\":300,\"members\":false}," +
            "{\"id\":3,\"name\":\"Members' item\",\"highalch\":300,\"members\":true}]"));
        Market market = new Market(prices, volume, Map.of(), items);

        UtilityCalculator prayer = calculators.get("prayer");
        Values heads = settings(prayer, Map.of("method", "ensouled"), Map.of(Skill.MAGIC, 41, Skill.PRAYER, 55), Map.of());
        List<Row> unlocked = prayer.calculate(heads, market);
        require(unlocked.size() == 14, "Magic 41 unlocks Basic and Adept heads");
        Values assumedMagic = settings(prayer, Map.of("method", "ensouled"), Map.of(Skill.PRAYER, 55), Map.of());
        require(prayer.calculate(assumedMagic, market).size() == 23, "Missing Magic defaults to 99");
        Values manualMagic = settings(prayer, Map.of("method", "ensouled", "magic", 16),
            Map.of(Skill.MAGIC, 99), Map.of());
        require(prayer.calculate(manualMagic, market).size() == 7, "A manual level takes priority over the game level");
        close(find(unlocked, "Ensouled dog head").cost, 1340, "Adept reanimation rune cost");
        close(find(unlocked, "Ensouled dog head").gpPerXp, 1340.0 / 520, "Prayer displays cost per XP");
        heads.input.put("minXp", 810.0);
        require(prayer.calculate(heads, market).stream().allMatch(row -> row.xp >= 810), "Prayer minimum XP");
        Values sunfire = settings(prayer, Map.of("method", "sunfire", "goal", 60),
            Map.of(Skill.PRAYER, 59), Map.of(Skill.PRAYER, Experience.getXpForLevel(60) - 5000));
        Row dragon = find(prayer.calculate(sunfire, market), "Dragon bones");
        close(dragon.xp, 348, "Sunfire XP");
        close(dragon.cost, 3149.35, "Average sunfire supplies");
        require(dragon.detail.contains("48,090 GP"), "Whole jugs and splinters for the goal");
        Values exactPrayer = new Values(sunfire.input, Map.of(Skill.PRAYER, 99), Map.of(),
            Experience.getXpForLevel(60) - 5000);
        require(find(prayer.calculate(exactPrayer, market), "Dragon bones").detail.contains("48,090 GP"),
            "Exact Prayer XP overrides level and determines the goal cost");

        UtilityCalculator farming = calculators.get("farming");
        Map<String, Object> farmOptions = Map.ofEntries(Map.entry("patches", 6), Map.entry("hosidius", true),
            Map.entry("weiss", true), Map.entry("kandarin", "17"), Map.entry("kourend", true), Map.entry("anima", "attas"),
            Map.entry("secateurs", true), Map.entry("cape", true), Map.entry("resurrect", true), Map.entry("bucket", true),
            Map.entry("sell", "unfinished"), Map.entry("protection", true));
        Values farm = settings(farming, farmOptions, Map.of(Skill.FARMING, 75, Skill.MAGIC, 85), Map.of());
        List<Row> crops = farming.calculate(farm, market);
        Row ranarr = find(crops, "Ranarr"), cabbage = find(crops, "Cabbage");
        close(ranarr.xp, 340.2079525723587, "Farming herb run XP");
        close(ranarr.cost, 1006.6044992154586, "Farming herb run costs");
        close(ranarr.outputs.get(0).quantity(), 9.10517877286422, "Expected herb harvest and resurrection");
        Values exactFarm = new Values(farm.input, Map.of(Skill.FARMING, 1, Skill.MAGIC, 85), Map.of(),
            Experience.getXpForLevel(75));
        close(find(farming.calculate(exactFarm, market), "Ranarr").outputs.get(0).quantity(),
            ranarr.outputs.get(0).quantity(), "Exact Farming XP controls unlocks and harvest yield");
        close(cabbage.outputs.get(0).quantity(), 22.26086956521739, "Average allotment harvest");
        close(cabbage.cost, 380, "Allotment protection and bottomless compost");
        Values assumedFarm = settings(farming, Map.of("resurrect", true), Map.of(), Map.of());
        Values maxedFarm = settings(farming, Map.of("resurrect", true), Map.of(Skill.FARMING, 99, Skill.MAGIC, 99), Map.of());
        close(find(farming.calculate(assumedFarm, market), "Ranarr").outputs.get(0).quantity(),
            find(farming.calculate(maxedFarm, market), "Ranarr").outputs.get(0).quantity(), "Farming and resurrection default to 99");
        close(ranarr.gpPerXp, (1006.6044992154586 - 17846.15039481387) / ranarr.xp, "Profitable Farming has negative GP/XP");
        farm.input.put("minXp", 400.0);
        require(farming.calculate(farm, market).stream().noneMatch(row -> row.name.equals("Ranarr")), "Farming minimum XP");

        UtilityCalculator mixology = calculators.get("mixology");
        List<Row> mixes = mixology.calculate(settings(mixology, Map.of("source", "clean", "target", 11), Map.of(), Map.of()), market);
        require(find(mixes, "Mox paste summary").detail.contains("200 GP"), "Mixology rounds target supply to whole herbs");
        close(find(mixes, "Guam leaf").score, 10, "Mixology GP/paste");
        List<Row> potions = mixology.calculate(settings(mixology,
            Map.of("view", "potion", "minXp", 350.0), Map.of(), Map.of()), market);
        require(potions.size() == 1 && potions.get(0).name.equals("Mixalot"), "Mixology minimum XP");

        UtilityCalculator combat = calculators.get("combat");
        List<Row> attacks = combat.calculate(settings(combat, Map.of(), Map.of(Skill.MAGIC, 94, Skill.RANGED, 65), Map.of()), market);
        close(find(attacks, "Ice Barrage").xp, 223.60440936868525, "Ice Barrage against zombies");
        close(find(attacks, "Red chinchompa").xp, 61.22980680772769, "Red chin damage and armour");
        require(find(attacks, "Red chinchompa").cost == 450, "Chinchompa purchase price");
        close(find(attacks, "Red chinchompa").gpPerXp, 450 / 61.22980680772769, "Chinchompa cost per expected XP");
        List<Row> assumedCombat = combat.calculate(settings(combat, Map.of(), Map.of(), Map.of()), market);
        List<Row> maxedCombat = combat.calculate(settings(combat, Map.of(), Map.of(Skill.MAGIC, 99, Skill.RANGED, 99), Map.of()), market);
        close(find(assumedCombat, "Ice Barrage").xp, find(maxedCombat, "Ice Barrage").xp, "Missing Magic defaults to 99 for Combat");
        close(find(assumedCombat, "Red chinchompa").xp, find(maxedCombat, "Red chinchompa").xp, "Missing Ranged defaults to 99");
        List<Row> filteredAttacks = combat.calculate(settings(combat,
            Map.of("minXp", 200.0), Map.of(), Map.of()), market);
        require(!filteredAttacks.isEmpty() && filteredAttacks.stream().allMatch(row -> row.xp >= 200),
            "Combat minimum expected XP across casts and throws");
        Values smoke = settings(combat, Map.of("target", "defenseless"), Map.of(), Map.of());
        close(find(combat.calculate(smoke, market), "Smoke Burst").xp, 138.66666666666666, "Six full-HP targets");
        require(combat.fields.size() == 2, "Combat only offers a target and minimum XP");
        CombatLoadout imported = testLoadout();
        List<Row> equipped = ((CombatCalculator) combat).calculate(smoke, market, imported);
        require(find(equipped, "Smoke Burst").xp > find(combat.calculate(smoke, market), "Smoke Burst").xp,
            "Imported magic damage increases XP");
        require(Double.isNaN(find(equipped, "Red chinchompa").xp),
            "Boosted Ranged does not unlock a chinchompa above the base Ranged level");

        UtilityCalculator alch = calculators.get("alch");
        List<Row> alchemy = alch.calculate(settings(alch, Map.of("free", true), Map.of(), Map.of()), market);
        require(alchemy.size() == 1 && alchemy.get(0).itemId == 1, "Alch minimum of 100 buys and F2P filter");
        close(alchemy.get(0).score, 100, "Alch payout less item and Nature rune");
        close(alchemy.get(0).gpPerXp, -100.0 / 65, "Alch profit per XP");
        require(alch.calculate(settings(alch, Map.of("max4", "99"), Map.of(), Map.of()), market).isEmpty(),
            "An alchemy buy-volume cap below 100 leaves no eligible items");
        require(alch.calculate(settings(alch, Map.of("minXp", 66.0), Map.of(), Map.of()), market).isEmpty(),
            "Alchemy casts award exactly 65 XP");

        UtilityCalculator nmz = calculators.get("nmz");
        List<Row> rewards = nmz.calculate(settings(nmz, Map.of("boxes", 13, "points", 1000000), Map.of(), Map.of()), market);
        require(find(rewards, "Herb box").detail.contains("You can buy 2"), "NMZ daily herb box cap");
        require(rewards.stream().noneMatch(row -> row.name.endsWith(" (i)") || row.name.contains("amulet(ei)"))
            && nmz.fields.stream().noneMatch(field -> field.id.equals("category")), "NMZ imbues and category removed");

        KingdomCalculator kingdom = (KingdomCalculator) calculators.get("kingdom");
        Values plan = settings(kingdom, Map.of(), Map.of(), Map.of());
        KingdomCalculator.Production production = KingdomCalculator.production(plan);
        require(production.wages == 525000 && production.fullDays == 7 && production.points == 8001, "Kingdom wage and production forecast");
        require(kingdom.optimize(plan, market).settings.get("miningWorkers").equals(10), "Kingdom optimizer allocates maximum to priced coal");
        KingdomCalculator.Recommendation allocation = kingdom.recommend(plan, market);
        require(allocation.tasks.size() == 10 && allocation.tasks.stream().mapToInt(task -> task.workers).sum() == 15
            && allocation.tasks.stream().anyMatch(task -> task.name.equals("Coal") && task.workers == 10)
            && allocation.tasks.stream().anyMatch(task -> task.name.equals("Flax") && task.workers == 0),
            "Kingdom bar data includes each task and matches the recommended allocation");
        List<Row> best = kingdom.calculate(plan, market);
        require(best.size() == 1 && best.get(0).detail.contains("10 workers · Coal")
            && best.get(0).detail.contains("Estimated profit") && best.get(0).detail.contains("Some HCW sell prices are missing"),
            "Kingdom produces one recommendation with the allocation, profit and quote coverage");
        Values basicKingdom = settings(kingdom, Map.of("royal", false), Map.of(), Map.of());
        require(kingdom.optimize(basicKingdom, market).settings.get("miningWorkers").equals(10)
            && !kingdom.optimize(basicKingdom, market).settings.containsKey("hardwoodWorkers"),
            "Kingdom optimizer respects saved Royal Trouble setting");
        require(kingdom.recommend(basicKingdom, market).tasks.size() == 6, "Kingdom displays unlocked tasks");

        if (args.length == 3)
        {
            try (Reader livePrices = Files.newBufferedReader(Path.of(args[0]));
                Reader liveVolume = Files.newBufferedReader(Path.of(args[1]));
                Reader liveMapping = Files.newBufferedReader(Path.of(args[2])))
            {
                Market live = new Market(MarketSnapshot.read(livePrices, now, false), MarketSnapshot.read(liveVolume, now, true),
                    Map.of(), AlchemyCalculator.readItems(liveMapping));
                require(alch.calculate(settings(alch, Map.of(), Map.of(), Map.of()), live).size() > 10, "Live alchemy catalogue and volume");
                for (UtilityCalculator calculator : calculators.values())
                    require(!calculator.calculate(settings(calculator, Map.of(), Map.of(), Map.of()), live).isEmpty(),
                        calculator.title + " renders using live prices");
                System.out.println("All seven utility calculators verified with live HCW payloads");
            }
        }
        System.out.println("Seven utility calculator checks passed");
    }

    private static CombatLoadout testLoadout()
    {
        ItemContainer gear = (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
            new Class<?>[]{ItemContainer.class}, (proxy, method, args) ->
            {
                if (method.getName().equals("getItem"))
                {
                    int slot = (int) args[0];
                    if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx()) return new Item(1, 1);
                    if (slot == EquipmentInventorySlot.BODY.getSlotIdx()) return new Item(2, 1);
                    if (slot == EquipmentInventorySlot.AMMO.getSlotIdx()) return new Item(3, 150);
                    if (slot == EquipmentInventorySlot.AMULET.getSlotIdx()) return new Item(ItemID.NZONE_SALVE_AMULET_E, 1);
                    if (slot == EquipmentInventorySlot.BOOTS.getSlotIdx()) return new Item(4, 1);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "getGameState": return GameState.LOGGED_IN;
                    case "getItemContainer": return gear;
                    case "getBoostedSkillLevel": return args[0] == Skill.MAGIC ? 99 : 61;
                    case "getRealSkillLevel": return 52;
                    case "isPrayerActive": return args[0] == Prayer.AUGURY || args[0] == Prayer.RIGOUR;
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        Map<Integer, ItemEquipmentStats> itemBonuses = Map.of(
            1, ItemEquipmentStats.builder().amagic(80).mdmg(5).arange(90).rstr(75).build(),
            2, ItemEquipmentStats.builder().amagic(20).mdmg(2.5f).arange(40).rstr(4).build(),
            3, ItemEquipmentStats.builder().arange(15).rstr(30).build(),
            ItemID.NZONE_SALVE_AMULET_E, ItemEquipmentStats.builder().build());
        CombatLoadout loadout = CombatLoadout.read(client, id ->
            itemBonuses.containsKey(id) ? new ItemStats(true, 0, 0, itemBonuses.get(id)) : null);
        require(loadout.magic == 99 && loadout.ranged == 61 && loadout.baseRanged == 52 && loadout.magicAttack == 100,
            "Import reads current skill levels and equipped Magic accuracy");
        close(loadout.magicDamage, 7.5, "Magic damage import is percentage points");
        require(loadout.rangedAttack == 40 && loadout.rangedStrength == 4 && loadout.missingGear,
            "Chins use non-weapon and non-ammo bonuses and acknowledge missing gear stats");
        require(loadout.magicPrayer.equals("augury") && loadout.rangedPrayer.equals("rigour") && loadout.salve.equals("ei"),
            "Import reads active prayers and worn Salve");
        return loadout;
    }

    private static Values settings(UtilityCalculator calculator, Map<String, ?> overrides,
        Map<Skill, Integer> levels, Map<Skill, Integer> xp)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        calculator.fields.forEach(field -> values.put(field.id, field.initial));
        values.putAll(overrides);
        return new Values(values, levels, xp);
    }

    private static Row find(List<Row> rows, String name)
    {
        return rows.stream().filter(row -> row.name.equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("Expected row " + name));
    }

    private static void close(double actual, double expected, String message)
    {
        require(Math.abs(actual - expected) < 0.00001, message + " expected " + expected + " got " + actual);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}

package se.sandr.hccalcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import se.sandr.hccalcs.RecipeCatalog.Item;
import se.sandr.hccalcs.RecipeCatalog.Recipe;

final class RunecraftRecipes
{
    static final int DEFAULT_ESSENCE_PER_USE = 25;
    private static final double DEFAULT_RUNS_PER_HOUR = 50;
    private static final Map<Integer, Double> ROUTE_RATES = Map.of(561, 55.0, 9075, 73.0, 21880, 90.0);
    private static final Item PURE_ESSENCE = new Item(7936, "Pure essence", 1);
    private static final Item DARK_ESSENCE = new Item(null, "Dark essence fragment", 1);
    private static final Item NECKLACE_CHARGE = new Item(5521, "Binding necklace", 1.0 / 16);

    static RecipeCatalog load()
    {
        RecipeCatalog catalog = new RecipeCatalog();
        catalog.taxExempt = Set.of(558); // Mind rune
        catalog.rows = List.of(
            standard(1, 556, "Air rune", 5, 11, 22, 33, 44, 55, 66, 77, 88, 99),
            standard(2, 558, "Mind rune", 5.5, 14, 28, 42, 56, 70, 84, 98),
            standard(5, 555, "Water rune", 6, 19, 38, 57, 76, 95),
            standard(9, 557, "Earth rune", 6.5, 26, 52, 78),
            standard(14, 554, "Fire rune", 7, 35, 70),
            standard(20, 559, "Body rune", 7.5, 46, 92),
            standard(27, 564, "Cosmic rune", 8, 59),
            recipe(33, 28929, "Sunfire rune", "Standard runes", 9,
                List.of(PURE_ESSENCE, new Item(554, "Fire rune", 1), new Item(28924, "Sunfire splinters", 1)),
                new Method(new int[]{49, 98}, List.of(), 0, "Crafted at a Shrine of Ralos.")),
            standard(35, 562, "Chaos rune", 8.5, 74),
            standard(40, 9075, "Astral rune", 8.7, 82),
            standard(44, 561, "Nature rune", 9, 91),
            standard(54, 563, "Law rune", 9.5, 95),
            standard(65, 560, "Death rune", 10, 99),
            recipe(77, 565, "Blood rune (True Altar)", "Standard runes", 10.5, List.of(PURE_ESSENCE),
                new Method(new int[0], List.of(), 0, "Requires access to the True Blood Altar.", 60, 0)),
            standard(95, 21880, "Wrath rune", 8),
            combo(6, 4695, "Mist rune", 8.5, 556, "Air", 1438, "Water"),
            combo(10, 4696, "Dust rune", 9, 556, "Air", 1438, "Earth"),
            combo(13, 4698, "Mud rune", 9.5, 555, "Water", 1444, "Earth"),
            combo(15, 4697, "Smoke rune", 9.5, 556, "Air", 1438, "Fire"),
            combo(19, 4694, "Steam rune", 10, 555, "Water", 1444, "Fire"),
            combo(23, 4699, "Lava rune", 10.5, 557, "Earth", 1440, "Fire"),
            recipe(90, 30843, "Aether rune", "Combination runes", 20,
                List.of(PURE_ESSENCE, new Item(566, "Soul rune", 1), new Item(30771, "Aether catalyst", 1)),
                new Method(new int[0], List.of(NECKLACE_CHARGE, new Item(554, "Fire rune", 7),
                    new Item(555, "Water rune", 7), new Item(9075, "Astral rune", 2)), 82,
                    "Cosmic Altar. Requires Lunar Diplomacy and 82 Magic. Includes one full Magic Imbue cast per altar use.")),
            dark(77, 565, "Blood rune (Arceuus)", 23.8, 7.5, 196),
            dark(90, 566, "Soul rune", 29.7, 8, 204));
        return catalog;
    }

    private static Recipe standard(int level, int id, String name, double xp, int... multiplierLevels)
    {
        return recipe(level, id, name, "Standard runes", xp, List.of(PURE_ESSENCE),
            new Method(multiplierLevels, List.of(), 0, "", ROUTE_RATES.getOrDefault(id, DEFAULT_RUNS_PER_HOUR), 0));
    }

    private static Recipe combo(int level, int id, String name, double xp, int runeId, String element,
        int talismanId, String altar)
    {
        return recipe(level, id, name, "Combination runes", xp,
            List.of(PURE_ESSENCE, new Item(runeId, element + " rune", 1)),
            new Method(new int[0], List.of(new Item(talismanId, element + " talisman", 1), NECKLACE_CHARGE), 0,
                altar + " Altar with a binding necklace and the " + element + " talisman."));
    }

    private static Recipe dark(int level, int id, String name, double xp, double runsPerHour, int fragmentsPerRun)
    {
        return recipe(level, id, name, "Arceuus runes", xp, List.of(DARK_ESSENCE),
            new Method(new int[0], List.of(), 0, "XP from crafting the rune. Dark essence is self-gathered.",
                runsPerHour, fragmentsPerRun));
    }

    private static Recipe recipe(int level, int id, String name, String category, double xp, List<Item> inputs,
        Method method)
    {
        Recipe recipe = new Recipe();
        recipe.level = level;
        recipe.name = name;
        recipe.category = category;
        recipe.xp = xp;
        recipe.quantity = 1;
        recipe.inputs = inputs;
        recipe.outputs = List.of(new Item(id, name.startsWith("Blood rune") ? "Blood rune" : name, 1));
        recipe.selfGatheredFree = inputs.contains(DARK_ESSENCE);
        recipe.runecraft = method;
        return recipe;
    }

    enum EssencePouch
    {
        SMALL("Small", 1, 3), MEDIUM("Medium", 25, 6), LARGE("Large", 50, 9),
        GIANT("Giant", 75, 12), COLOSSAL("Colossal", 25, 8);

        final String label;
        final int level;
        private final int capacity;

        EssencePouch(String label, int level, int capacity)
        {
            this.label = label;
            this.level = level;
            this.capacity = capacity;
        }

        int capacityAt(int runecraftLevel)
        {
            if (runecraftLevel < level) return 0;
            if (this != COLOSSAL) return capacity;
            if (runecraftLevel >= 85) return 40;
            if (runecraftLevel >= 75) return 27;
            if (runecraftLevel >= 50) return 16;
            return capacity;
        }
    }

    static PouchLoad pouchLoad(int level, int baseSlots, Set<EssencePouch> pouches)
    {
        Set<EssencePouch> selected = pouches.contains(EssencePouch.COLOSSAL) ? Set.of(EssencePouch.COLOSSAL) : pouches;
        int occupied = 0;
        int stored = 0;
        for (EssencePouch pouch : selected)
        {
            int capacity = pouch.capacityAt(level);
            if (capacity == 0) continue;
            occupied++;
            stored += capacity;
        }
        int loose = Math.max(1, baseSlots - occupied);
        return new PouchLoad(loose, loose + stored);
    }

    static final class PouchLoad
    {
        final int loose;
        final int total;

        PouchLoad(int loose, int total)
        {
            this.loose = loose;
            this.total = total;
        }
    }

    static final class Method
    {
        final int[] multiplierLevels;
        final List<Item> perUse;
        final int magicLevel;
        final String note;
        final double defaultRunsPerHour;
        private final int fragmentsPerRun;

        Method(int[] multiplierLevels, List<Item> perUse, int magicLevel, String note)
        {
            this(multiplierLevels, perUse, magicLevel, note, DEFAULT_RUNS_PER_HOUR, 0);
        }

        Method(int[] multiplierLevels, List<Item> perUse, int magicLevel, String note,
            double defaultRunsPerHour, int fragmentsPerRun)
        {
            this.multiplierLevels = multiplierLevels;
            this.perUse = perUse;
            this.magicLevel = magicLevel;
            this.note = note;
            this.defaultRunsPerHour = defaultRunsPerHour;
            this.fragmentsPerRun = fragmentsPerRun;
        }

        int defaultEssencePerRun(int pureEssencePerRun)
        {
            return fragmentsPerRun == 0 ? pureEssencePerRun : fragmentsPerRun;
        }

        int runesPerEssence(int level)
        {
            int runes = 1;
            for (int threshold : multiplierLevels)
            {
                if (level < threshold) break;
                runes++;
            }
            return runes;
        }

        long goalRunes(int level, double currentXp, double xpPerEssence, long essence, int essencePerUse)
        {
            long runes = essence * runesPerEssence(level);
            for (int threshold : multiplierLevels)
            {
                if (threshold <= level) continue;
                // An altar use keeps its starting multiplier even if it gains a level.
                long usesBefore = (long) Math.ceil(Math.max(0, Experience.getXpForLevel(threshold) - currentXp)
                    / (xpPerEssence * essencePerUse));
                runes += Math.max(0, essence - usesBefore * essencePerUse);
            }
            return runes;
        }
    }
}

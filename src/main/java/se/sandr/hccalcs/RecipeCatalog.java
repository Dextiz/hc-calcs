package se.sandr.hccalcs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class RecipeCatalog
{
    static Map<String, RecipeCatalog> load(Gson gson) throws IOException
    {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
            RecipeCatalog.class.getResourceAsStream("/hc-calcs-recipes.json")), StandardCharsets.UTF_8))
        {
            return gson.fromJson(reader, new TypeToken<Map<String, RecipeCatalog>>() {}.getType());
        }
    }

    List<Recipe> rows;
    Set<Integer> taxExempt;

    static final class Recipe
    {
        int level;
        String name;
        String category;
        double xp;
        double quantity;
        boolean amulet;
        boolean selfGatheredFree;
        List<Item> inputs;
        List<Item> outputs;
        int[] range;
        int[] fire;
        int[] gauntlets;
    }

    static final class Item
    {
        private Integer id;
        private String name;
        private double quantity;
        private boolean goggles;

        Item(Integer id, String name, double quantity)
        {
            this.id = id;
            this.name = name;
            this.quantity = quantity;
        }

        Integer id() { return id; }
        String name() { return name; }
        double quantity() { return quantity; }
        boolean goggles() { return goggles; }
    }
}

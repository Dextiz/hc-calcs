package se.sandr.hccalcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import se.sandr.hccalcs.RecipeCatalog.Item;

final class NmzCalculator extends UtilityCalculator
{
    final List<Item> herbs;
    final int boxLimit;
    private final JsonObject data;

    NmzCalculator(JsonObject data)
    {
        super("nmz", "NMZ Shop", null);
        this.data = data;
        herbs = items(array(data, "herbs"));
        boxLimit = i(data, "boxLimit");
        search("points", "NMZ points");
        number("boxes", "Herb boxes bought today", 0, 0, boxLimit);
        choice("sort", "GP/point", "default", "default=Shop order", "high=Highest first", "low=Lowest first");
    }

    @Override
    List<Row> calculate(Values values, Market market)
    {
        List<Row> rows = new ArrayList<>();
        String enteredPoints = values.text("points").strip();
        double budget = pointBudget(enteredPoints);
        for (JsonElement entry : array(data, "rewards"))
        {
            JsonObject reward = object(entry);
            String name = s(reward, "name");
            String id = s(reward, "itemId");
            boolean box = id.equals("herb-box");
            List<Item> outputs = box ? herbs : id.isEmpty() ? List.of() : List.of(item(Integer.parseInt(id), name, 1));
            double points = i(reward, "points");
            double net = outputs.isEmpty() ? UNKNOWN : basket(outputs, itemId -> market.net(itemId, Set.of()));
            double limit = box ? boxLimit - values.integer("boxes") : n(reward, "maxQuantity", Double.POSITIVE_INFINITY);
            double quantity = Math.min(limit, Math.floor(budget / points));
            String detail = name + "\n" + fmt(points) + " points each\n" + fmt(net / points)
                + " GP/point · " + gp(net) + " each\n\nYou can buy " + fmt(quantity) + "\nTotal sale " + gp(net * quantity)
                + "\nPoints left " + fmt(budget - quantity * points) + "\n" + s(reward, "note")
                + (!enteredPoints.isEmpty() && !Double.isFinite(budget) ? "\nEnter a whole number from 0 to 2,147,483,647." : "")
                + (box ? "\n\nExpected herbs per box\n" + itemLines(outputs, market::sell)
                    : outputs.isEmpty() ? "" : "\nSell price " + gp(market.sell(outputs.get(0).id())));
            rows.add(new Row(name, name + " · " + fmt(net / points) + " GP/point", detail, outputs)
                .training(0, UNKNOWN, net / points, points));
        }
        if (!values.text("sort").equals("default"))
            rows.sort(metric(row -> row.score, values.text("sort").equals("high")));
        return rows;
    }

    private static double pointBudget(String value)
    {
        try
        {
            double number = Double.parseDouble(value);
            return number >= 0 && number <= Integer.MAX_VALUE && number == Math.floor(number) ? number : UNKNOWN;
        }
        catch (NumberFormatException ex)
        {
            return UNKNOWN;
        }
    }
}

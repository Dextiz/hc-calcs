package se.sandr.hccalcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

final class MarketSnapshot
{
    private static final long DAY_SECONDS = 86_400;
    static final MarketSnapshot EMPTY = new MarketSnapshot(Map.of(), Instant.EPOCH);

    private final Map<Integer, Quote> quotes;
    final Instant asOf;

    private MarketSnapshot(Map<Integer, Quote> quotes, Instant asOf)
    {
        this.quotes = Map.copyOf(quotes);
        this.asOf = asOf;
    }

    static MarketSnapshot read(Reader reader, Instant asOf, boolean volume)
    {
        JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
        if (volume)
        {
            long timestamp = number(root.get("timestamp"));
            if (timestamp <= 0 || timestamp % DAY_SECONDS != 0 ||
                timestamp > asOf.getEpochSecond() - DAY_SECONDS)
            {
                throw new IllegalArgumentException("Expected a completed UTC volume window");
            }
            asOf = Instant.ofEpochSecond(timestamp);
        }

        Map<Integer, Quote> quotes = new HashMap<>();
        String buyKey = volume ? "highPriceVolume" : "high";
        String sellKey = volume ? "lowPriceVolume" : "low";
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("data").entrySet())
        {
            int id = Integer.parseInt(entry.getKey());
            JsonObject item = entry.getValue().getAsJsonObject();
            quotes.put(id, new Quote(number(item.get(buyKey)), number(item.get(sellKey)),
                number(item.get("highTime")), number(item.get("lowTime"))));
        }
        if (quotes.isEmpty())
        {
            throw new IllegalArgumentException("Empty HCW snapshot");
        }
        return new MarketSnapshot(quotes, asOf);
    }

    double buy(Integer id)
    {
        Quote quote = id == null ? null : quotes.get(id);
        return quote == null || quote.buy <= 0 ? Double.NaN : quote.buy;
    }

    double sell(Integer id)
    {
        Quote quote = id == null ? null : quotes.get(id);
        return quote == null || quote.sell <= 0 ? Double.NaN : quote.sell;
    }

    double tradeCount(Integer id, boolean buying)
    {
        Quote quote = id == null ? null : quotes.get(id);
        long count = quote == null ? -1 : buying ? quote.buy : quote.sell;
        return count < 0 ? Double.NaN : count;
    }

    long tradeTime(Integer id, boolean buying)
    {
        Quote quote = id == null ? null : quotes.get(id);
        return quote == null ? -1 : buying ? quote.boughtAt : quote.soldAt;
    }

    private static long number(JsonElement value)
    {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            return -1;
        }
        try
        {
            return Long.parseLong(value.getAsString());
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    private static final class Quote
    {
        final long buy;
        final long sell;
        final long boughtAt;
        final long soldAt;

        Quote(long buy, long sell, long boughtAt, long soldAt)
        {
            this.buy = buy;
            this.sell = sell;
            this.boughtAt = boughtAt;
            this.soldAt = soldAt;
        }
    }
}

package se.sandr.hccalcs;

import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class MarketClient
{
    private static final String WIKI_API = "https://prices.runescape.wiki/api/v2/osrs-hc";
    private static final String USER_AGENT = "HCW Calcs (RuneLite Hardcore Worlds calculator)";
    static final long REFRESH_SECONDS = 30 * 60;
    static final long MANUAL_COOLDOWN_SECONDS = 60;
    private final OkHttpClient http;

    MarketClient(OkHttpClient http)
    {
        this.http = http.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
            .callTimeout(15, TimeUnit.SECONDS).build();
    }

    Call fetch(boolean volume, Consumer<MarketSnapshot> result)
    {
        return fetch(volume ? "/24h" : "/latest",
            (resource, reader) -> MarketSnapshot.read(reader, sourceTime(resource, volume), volume), result);
    }

    Call fetchItems(Consumer<List<AlchemyCalculator.AlchItem>> result)
    {
        return fetch("/mapping", (response, reader) -> AlchemyCalculator.readItems(reader), result);
    }

    private <T> Call fetch(String route, BiFunction<Response, Reader, T> parser, Consumer<T> result)
    {
        Request request = new Request.Builder()
            .url(WIKI_API + route)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build();
        Call call = http.newCall(request);
        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call failedCall, IOException ex)
            {
                complete(null, result);
            }

            @Override
            public void onResponse(Call finishedCall, Response response)
            {
                T snapshot = null;
                try (Response resource = response)
                {
                    ResponseBody body = resource.body();
                    if (resource.isSuccessful() && body != null && body.contentLength() <= 2_000_000 &&
                        !body.source().request(2_000_001))
                    {
                        try (Reader reader = body.charStream())
                        {
                            snapshot = parser.apply(resource, reader);
                        }
                    }
                }
                catch (IOException | RuntimeException ex)
                {
                    // Retain the last valid snapshot and let the panel show the update failure.
                }
                complete(snapshot, result);
            }
        });
        return call;
    }

    private static Instant sourceTime(Response response, boolean volume)
    {
        String date = response.header("Date");
        if (!volume && date != null)
        {
            try
            {
                return ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            }
            catch (DateTimeParseException ex)
            {
                // The header is optional.
            }
        }
        return Instant.now();
    }

    private static <T> void complete(T snapshot, Consumer<T> result)
    {
        SwingUtilities.invokeLater(() -> result.accept(snapshot));
    }
}

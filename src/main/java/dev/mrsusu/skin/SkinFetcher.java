package dev.mrsusu.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mrsusu.MrSusuPlugin;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Fetches skin texture + signature from Mojang's API asynchronously.
 *
 * Flow:
 *  1. GET https://api.mojang.com/users/profiles/minecraft/{name}  → UUID
 *  2. GET https://sessionserver.mojang.com/session/minecraft/profile/{uuid}?unsigned=false → properties
 */
public final class SkinFetcher {

    private static final String MOJANG_PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL         = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final MrSusuPlugin plugin;
    private final HttpClient    http;

    /** Cached skin + the instant it was fetched. */
    private SkinData cachedSkin;
    private Instant  cacheTime = Instant.EPOCH;

    public SkinFetcher(MrSusuPlugin plugin) {
        this.plugin = plugin;
        this.http   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    // -------------------------------------------------------------------------

    /**
     * Fetches the skin for the configured skin-name, calling {@code callback}
     * on the main thread with the result (or FALLBACK on error).
     */
    public void fetchSkin(Consumer<SkinData> callback) {
        String skinName = plugin.getConfig().getString("skin-name", "Herobrine");
        long   cacheMin = plugin.getConfig().getLong("skin-cache-minutes", 60);

        // Return cached if still fresh
        if (cachedSkin != null && Duration.between(cacheTime, Instant.now()).toMinutes() < cacheMin) {
            plugin.debug("Using cached skin for " + skinName);
            callback.accept(cachedSkin);
            return;
        }

        CompletableFuture.supplyAsync(() -> fetchBlocking(skinName))
                .thenAccept(skin -> Bukkit.getScheduler().runTask(plugin, () -> {
                    cachedSkin = skin;
                    cacheTime  = Instant.now();
                    callback.accept(skin);
                }));
    }

    // -------------------------------------------------------------------------

    private SkinData fetchBlocking(String name) {
        try {
            // Step 1 – resolve UUID
            plugin.debug("Fetching UUID for skin name: " + name);
            HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(URI.create(MOJANG_PROFILE_URL + name))
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();

            HttpResponse<String> resp1 = http.send(req1, HttpResponse.BodyHandlers.ofString());
            if (resp1.statusCode() != 200) {
                plugin.getLogger().warning("[MrSusu] Mojang UUID lookup failed (" + resp1.statusCode() + ") – using fallback skin.");
                return SkinData.FALLBACK;
            }

            JsonObject profileJson = JsonParser.parseString(resp1.body()).getAsJsonObject();
            String uuid = profileJson.get("id").getAsString();
            plugin.debug("Resolved UUID: " + uuid + " for " + name);

            // Step 2 – fetch texture property (signed)
            HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(URI.create(SESSION_URL + uuid + "?unsigned=false"))
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();

            HttpResponse<String> resp2 = http.send(req2, HttpResponse.BodyHandlers.ofString());
            if (resp2.statusCode() != 200) {
                plugin.getLogger().warning("[MrSusu] Mojang session lookup failed (" + resp2.statusCode() + ") – using fallback skin.");
                return SkinData.FALLBACK;
            }

            JsonObject sessionJson = JsonParser.parseString(resp2.body()).getAsJsonObject();
            var properties = sessionJson.getAsJsonArray("properties");

            for (var elem : properties) {
                JsonObject prop = elem.getAsJsonObject();
                if ("textures".equals(prop.get("name").getAsString())) {
                    String value     = prop.get("value").getAsString();
                    String signature = prop.has("signature") ? prop.get("signature").getAsString() : "";
                    plugin.debug("Skin fetched successfully for " + name);
                    return new SkinData(value, signature);
                }
            }

            plugin.getLogger().warning("[MrSusu] No texture property found for " + name + " – using fallback skin.");
            return SkinData.FALLBACK;

        } catch (Exception e) {
            plugin.getLogger().warning("[MrSusu] Skin fetch error: " + e.getMessage() + " – using fallback skin.");
            return SkinData.FALLBACK;
        }
    }

    /** Force-invalidates the cache so the next call re-fetches. */
    public void invalidateCache() {
        cachedSkin = null;
        cacheTime  = Instant.EPOCH;
    }
}

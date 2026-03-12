package com.stsaiadvisor.util;

import com.stsaiadvisor.model.BattleContext;
import com.stsaiadvisor.model.CardState;
import com.stsaiadvisor.model.EnemyState;
import com.stsaiadvisor.model.Recommendation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Simple cache manager to avoid redundant API calls.
 */
public class CacheManager {

    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(30);

    private final Map<String, CacheEntry> cache = new HashMap<>();
    private long lastCleanup = System.currentTimeMillis();

    /**
     * Get a cached recommendation if available and not expired.
     */
    public Recommendation get(BattleContext context) {
        if (context == null) {
            return null;
        }

        String key = generateKey(context);
        CacheEntry entry = cache.get(key);

        if (entry != null && !entry.isExpired()) {
            return entry.recommendation;
        }

        return null;
    }

    /**
     * Store a recommendation in the cache.
     */
    public void put(BattleContext context, Recommendation recommendation) {
        if (context == null || recommendation == null) {
            return;
        }

        String key = generateKey(context);
        cache.put(key, new CacheEntry(recommendation));

        // Periodic cleanup
        if (System.currentTimeMillis() - lastCleanup > TimeUnit.MINUTES.toMillis(1)) {
            cleanup();
        }
    }

    /**
     * Generate a cache key from the battle context.
     */
    private String generateKey(BattleContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append(context.getTurn()).append(":");
        sb.append(context.getPlayer().getCurrentHealth()).append(":");
        sb.append(context.getPlayer().getEnergy()).append(":");

        // Hand cards (simplified)
        if (context.getHand() != null) {
            for (CardState card : context.getHand()) {
                sb.append(card.getId()).append(card.getCost());
            }
        }

        sb.append(":");

        // Enemy states (simplified)
        if (context.getEnemies() != null) {
            for (EnemyState enemy : context.getEnemies()) {
                sb.append(enemy.getId()).append(enemy.getCurrentHealth());
            }
        }

        return sb.toString();
    }

    /**
     * Remove expired entries from the cache.
     */
    private void cleanup() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        lastCleanup = System.currentTimeMillis();
    }

    /**
     * Clear the entire cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Get cache statistics.
     */
    public String getStats() {
        return "Cache size: " + cache.size() + " entries";
    }

    /**
     * Internal cache entry class.
     */
    private static class CacheEntry {
        final Recommendation recommendation;
        final long timestamp;

        CacheEntry(Recommendation recommendation) {
            this.recommendation = recommendation;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
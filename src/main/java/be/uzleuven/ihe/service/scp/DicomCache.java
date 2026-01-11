package be.uzleuven.ihe.service.scp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache for DICOM instance data retrieved from WADO-RS.
 * Reduces repeated downloads of the same instances.
 */
@Component
public class DicomCache {

    private static final Logger LOG = LoggerFactory.getLogger(DicomCache.class);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    private long maxSizeBytes = 500 * 1024 * 1024; // 500MB default
    private long ttlMillis = 300_000; // 5 minutes default
    private long currentSizeBytes = 0;
    private boolean enabled = true;

    /**
     * Get cached DICOM data by SOP Instance UID.
     *
     * @param sopInstanceUID SOP Instance UID
     * @return Cached DICOM data or null if not in cache
     */
    public byte[] get(String sopInstanceUID) {
        if (!enabled) {
            return null;
        }

        CacheEntry entry = cache.get(sopInstanceUID);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }

        // Check if expired
        if (isExpired(entry)) {
            remove(sopInstanceUID);
            misses.incrementAndGet();
            return null;
        }

        hits.incrementAndGet();
        entry.lastAccessed = Instant.now();
        return entry.data;
    }

    /**
     * Put DICOM data into cache.
     *
     * @param sopInstanceUID SOP Instance UID
     * @param data DICOM data
     */
    public void put(String sopInstanceUID, byte[] data) {
        if (!enabled || data == null) {
            return;
        }

        // Check if we need to evict entries to make room
        while (currentSizeBytes + data.length > maxSizeBytes && !cache.isEmpty()) {
            evictOldest();
        }

        CacheEntry entry = new CacheEntry(data);
        CacheEntry old = cache.put(sopInstanceUID, entry);

        if (old != null) {
            currentSizeBytes -= old.data.length;
        }
        currentSizeBytes += data.length;

        LOG.debug("Cached instance {} ({} bytes). Cache size: {}/{} MB",
                sopInstanceUID, data.length,
                currentSizeBytes / (1024 * 1024),
                maxSizeBytes / (1024 * 1024));
    }

    /**
     * Remove an entry from cache.
     */
    public void remove(String sopInstanceUID) {
        CacheEntry entry = cache.remove(sopInstanceUID);
        if (entry != null) {
            currentSizeBytes -= entry.data.length;
        }
    }

    /**
     * Clear all cache entries.
     */
    public void clear() {
        cache.clear();
        currentSizeBytes = 0;
        LOG.info("Cache cleared");
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        return new CacheStats(
                cache.size(),
                currentSizeBytes,
                maxSizeBytes,
                hits.get(),
                misses.get(),
                evictions.get()
        );
    }

    /**
     * Configure cache settings.
     */
    public void configure(long maxSizeMB, long ttlMinutes, boolean enabled) {
        this.maxSizeBytes = maxSizeMB * 1024 * 1024;
        this.ttlMillis = ttlMinutes * 60 * 1000;
        this.enabled = enabled;

        LOG.info("Cache configured: enabled={}, maxSize={}MB, ttl={}min",
                enabled, maxSizeMB, ttlMinutes);

        // Evict if over new limit
        while (currentSizeBytes > maxSizeBytes && !cache.isEmpty()) {
            evictOldest();
        }
    }

    private boolean isExpired(CacheEntry entry) {
        return System.currentTimeMillis() - entry.created.toEpochMilli() > ttlMillis;
    }

    private void evictOldest() {
        String oldestKey = null;
        Instant oldestTime = Instant.now();

        for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
            if (e.getValue().lastAccessed.isBefore(oldestTime)) {
                oldestTime = e.getValue().lastAccessed;
                oldestKey = e.getKey();
            }
        }

        if (oldestKey != null) {
            remove(oldestKey);
            evictions.incrementAndGet();
            LOG.debug("Evicted oldest entry: {}", oldestKey);
        }
    }

    private static class CacheEntry {
        final byte[] data;
        final Instant created;
        Instant lastAccessed;

        CacheEntry(byte[] data) {
            this.data = data;
            this.created = Instant.now();
            this.lastAccessed = this.created;
        }
    }

    public static class CacheStats {
        public final int entries;
        public final long currentSizeBytes;
        public final long maxSizeBytes;
        public final long hits;
        public final long misses;
        public final long evictions;

        public CacheStats(int entries, long currentSizeBytes, long maxSizeBytes,
                         long hits, long misses, long evictions) {
            this.entries = entries;
            this.currentSizeBytes = currentSizeBytes;
            this.maxSizeBytes = maxSizeBytes;
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
        }

        public double getHitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{entries=%d, size=%d/%d MB, hits=%d, misses=%d, evictions=%d, hitRate=%.2f%%}",
                    entries,
                    currentSizeBytes / (1024 * 1024),
                    maxSizeBytes / (1024 * 1024),
                    hits, misses, evictions,
                    getHitRate() * 100);
        }
    }
}


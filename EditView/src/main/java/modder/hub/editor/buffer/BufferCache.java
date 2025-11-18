/**
 * A LRU cache that stores the last seek line and its corresponding index so that future lookups can
 * start from the cached position instead of the beginning of the file
 *
 * <p>_cache.Pair.First = line index _cache.Pair.Second = character offset of first character in
 * that line
 *
 * <p>TextBufferCache always has one valid entry (0,0) signifying that in line 0, the first
 * character is at offset 0. This is true even for an "empty" file, which is not really empty
 * because TextBuffer inserts a EOF character in it.
 *
 * <p>Therefore, _cache[0] is always occupied by the entry (0,0). It is not affected by
 * invalidateCache, cache miss, etc. operations
 */
package modder.hub.editor.buffer;

import modder.hub.editor.utils.Pair;

public class BufferCache {
    private final int CACHE_SIZE = 4; // minimum = 1
    private Pair<Integer, Integer>[] _cache;

    public BufferCache() {
        _cache = new Pair[CACHE_SIZE];
        _cache[0] = new Pair<Integer, Integer>(0, 0); // invariant lineIndex and charOffset relation
        for (int i = 1; i < CACHE_SIZE; ++i) {
            _cache[i] = new Pair<Integer, Integer>(-1, -1);
            // -1 line index is used implicitly in calculations in getNearestMatch
        }
    }

    // TODO consider extracting common parts with getNearestCharOffset(int)
    public Pair<Integer, Integer> getNearestLine(int lineIndex) {
        int nearestMatch = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < CACHE_SIZE; ++i) {
            int distance = Math.abs(lineIndex - _cache[i].first);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestMatch = i;
            }
        }

        Pair<Integer, Integer> nearestEntry = _cache[nearestMatch];
        makeHead(nearestMatch);
        return nearestEntry;
    }

    public Pair<Integer, Integer> getNearestCharOffset(int charOffset) {
        int nearestMatch = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < CACHE_SIZE; ++i) {
            int distance = Math.abs(charOffset - _cache[i].second);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestMatch = i;
            }
        }

        Pair<Integer, Integer> nearestEntry = _cache[nearestMatch];
        makeHead(nearestMatch);
        return nearestEntry;
    }

    /** Place _cache[newHead] at the top of the list */
    private void makeHead(int newHead) {
        if (newHead == 0) {
            return; // nothing to do for _cache[0]
        }

        Pair<Integer, Integer> temp = _cache[newHead];
        for (int i = newHead; i > 1; --i) {
            _cache[i] = _cache[i - 1];
        }
        _cache[1] = temp; // _cache[0] is always occupied by (0,0)
    }

    public void updateEntry(int lineIndex, int charOffset) {
        if (lineIndex <= 0) {
            // lineIndex 0 always has 0 charOffset; ignore. Also ignore negative lineIndex
            return;
        }

        if (!replaceEntry(lineIndex, charOffset)) {
            insertEntry(lineIndex, charOffset);
        }
    }

    private boolean replaceEntry(int lineIndex, int charOffset) {
        for (int i = 1; i < CACHE_SIZE; ++i) {
            if (_cache[i].first == lineIndex) {
                _cache[i].second = charOffset;
                return true;
            }
        }
        return false;
    }

    private void insertEntry(int lineIndex, int charOffset) {
        makeHead(CACHE_SIZE - 1); // rotate right list of entries
        // replace head (most recently used entry) with new entry
        _cache[1] = new Pair<Integer, Integer>(lineIndex, charOffset);
    }

    /** Invalidate all cache entries that have char offset >= fromCharOffset */
    public void invalidateCache(int fromCharOffset) {
        for (int i = 1; i < CACHE_SIZE; ++i) {
            if (_cache[i].second >= fromCharOffset) {
                _cache[i] = new Pair<Integer, Integer>(-1, -1);
            }
        }
    }
}

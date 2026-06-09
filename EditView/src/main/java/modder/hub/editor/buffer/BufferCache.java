package modder.hub.editor.buffer;

import java.util.Arrays;
import modder.hub.editor.utils.Pair;

/**
 * An optimized line/offset cache using sorted arrays and binary search.
 * This provides O(log N) lookup and is much more efficient than an LRU cache for 2M+ lines.
 */
public class BufferCache {
    private int[] lineIndices;
    private int[] charOffsets;
    private int size;
    private static final int INITIAL_CAPACITY = 1024;

    public BufferCache() {
        lineIndices = new int[INITIAL_CAPACITY];
        charOffsets = new int[INITIAL_CAPACITY];
        lineIndices[0] = 0;
        charOffsets[0] = 0;
        size = 1;
    }

    public synchronized Pair<Integer, Integer> getNearestLine(int lineIndex) {
        int idx = Arrays.binarySearch(lineIndices, 0, size, lineIndex);
        if (idx >= 0) {
            return new Pair<>(lineIndices[idx], charOffsets[idx]);
        } else {
            int insertionPoint = -(idx + 1);
            int nearestIdx = Math.max(0, insertionPoint - 1);
            return new Pair<>(lineIndices[nearestIdx], charOffsets[nearestIdx]);
        }
    }

    public synchronized Pair<Integer, Integer> getNearestCharOffset(int charOffset) {
        int idx = Arrays.binarySearch(charOffsets, 0, size, charOffset);
        if (idx >= 0) {
            return new Pair<>(lineIndices[idx], charOffsets[idx]);
        } else {
            int insertionPoint = -(idx + 1);
            int nearestIdx = Math.max(0, insertionPoint - 1);
            return new Pair<>(lineIndices[nearestIdx], charOffsets[nearestIdx]);
        }
    }

    public synchronized void updateEntry(int lineIndex, int charOffset) {
        if (lineIndex <= 0) return;

        int idx = Arrays.binarySearch(lineIndices, 0, size, lineIndex);
        if (idx >= 0) {
            charOffsets[idx] = charOffset;
        } else {
            int insertionPoint = -(idx + 1);
            
            // To prevent the cache from growing too large and slowing down updates (O(N) shift),
            // we only insert if the distance to the nearest cached line is significant.
            int prevIdx = insertionPoint - 1;
            if (prevIdx >= 0 && lineIndex - lineIndices[prevIdx] < 100) {
                // Already have a nearby entry, don't bloat the cache
                return;
            }

            ensureCapacity(size + 1);
            System.arraycopy(lineIndices, insertionPoint, lineIndices, insertionPoint + 1, size - insertionPoint);
            System.arraycopy(charOffsets, insertionPoint, charOffsets, insertionPoint + 1, size - insertionPoint);
            lineIndices[insertionPoint] = lineIndex;
            charOffsets[insertionPoint] = charOffset;
            size++;
        }
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > lineIndices.length) {
            int newCapacity = lineIndices.length * 2;
            if (newCapacity < minCapacity) newCapacity = minCapacity;
            lineIndices = Arrays.copyOf(lineIndices, newCapacity);
            charOffsets = Arrays.copyOf(charOffsets, newCapacity);
        }
    }

    /** Invalidate all cache entries that have char offset >= fromCharOffset */
    public synchronized void invalidateCache(int fromCharOffset) {
        int idx = Arrays.binarySearch(charOffsets, 0, size, fromCharOffset);
        if (idx < 0) {
            idx = -(idx + 1);
        }
        // idx is the first entry to invalidate.
        // We always keep entry 0 (0,0).
        if (idx == 0) idx = 1;
        if (idx < size) {
            size = idx;
        }
    }
}

package modder.hub.editor.utils;


/**
 * Author - @developer-krushna
 * Uses a Fenwick Tree for O(log N) prefix sums (line tops) and search.
 * Idea and fix by AI
 */
public class LineHeightManager {
    private int[] heights;
    private int[] tree;
    public int count;
    private java.util.BitSet measured;

    public void init(int lineCount, int defaultHeight) {
        this.count = lineCount;
        this.heights = new int[lineCount + 1];
        this.tree = new int[lineCount + 1];
        this.measured = new java.util.BitSet(lineCount + 1);
        // Batch initialization: O(N) math is instant even for 3M lines.
        for (int i = 1; i <= lineCount; i++) {
            heights[i] = defaultHeight;
        }
        // Build BIT in O(N)
        for (int i = 1; i <= lineCount; i++) {
            int j = i + (i & -i);
            tree[i] += heights[i];
            if (j <= lineCount) tree[j] += tree[i];
        }
    }

    public void scaleHeights(float ratio, int newDefaultHeight) {
        if (heights == null || tree == null) return;
        // Scale all heights and rebuild tree.
        // This maintains approximate folded positions during zoom.
        for (int i = 1; i <= count; i++) {
            if (measured.get(i)) {
                heights[i] = Math.max(1, Math.round(heights[i] * ratio));
            } else {
                heights[i] = newDefaultHeight;
            }
        }
        // Rebuild tree
        java.util.Arrays.fill(tree, 0);
        for (int i = 1; i <= count; i++) {
            int j = i + (i & -i);
            tree[i] += heights[i];
            if (j <= count) tree[j] += tree[i];
        }
        // After scaling, we consider them unmeasured because the exact layout changed
        measured.clear();
    }

    public boolean isMeasured(int line) {
        return measured.get(line);
    }

    public void adjustLineCount(int changeLine, int oldCount, int newCount, int defaultHeight) {
        if (this.heights == null || this.tree == null || oldCount <= 0) {
            init(newCount, defaultHeight);
            return;
        }
        if (oldCount == newCount) {
            if (newCount > count) {
                init(newCount, defaultHeight);
            }
            return;
        }

        int[] newHeights = new int[newCount + 1];
        java.util.BitSet newMeasured = new java.util.BitSet(newCount + 1);
        int diff = newCount - oldCount;

        // Copy elements before the change line
        int copyBefore = Math.min(changeLine, Math.min(oldCount, newCount));
        for (int i = 1; i <= copyBefore; i++) {
            newHeights[i] = (i <= count) ? heights[i] : defaultHeight;
            if (i <= count) newMeasured.set(i, measured.get(i));
        }

        // For newly inserted lines, initialize with defaultHeight
        if (diff > 0) {
            for (int i = changeLine + 1; i <= changeLine + diff; i++) {
                if (i <= newCount) {
                    newHeights[i] = defaultHeight;
                }
            }
        }

        // Copy elements after the change line with shift
        int destStart = changeLine + (diff > 0 ? diff : 0) + 1;
        for (int i = destStart; i <= newCount; i++) {
            int src = i - diff;
            if (src >= 1 && src <= count) {
                newHeights[i] = heights[src];
                newMeasured.set(i, measured.get(src));
            } else {
                newHeights[i] = defaultHeight;
            }
        }

        this.count = newCount;
        this.heights = newHeights;
        this.measured = newMeasured;
        this.tree = new int[newCount + 1];
        // Rebuild BIT in O(N)
        for (int i = 1; i <= newCount; i++) {
            int j = i + (i & -i);
            tree[i] += heights[i];
            if (j <= newCount) tree[j] += tree[i];
        }
    }

    public void updateHeight(int line, int newHeight) {
        if (line < 1 || line > count) return;
        int delta = newHeight - heights[line];
        measured.set(line);
        if (delta == 0) return;
        heights[line] = newHeight;
        for (int i = line; i <= count; i += i & -i) {
            tree[i] += delta;
        }
    }

    public int getTop(int line) {
        if (line <= 1 || tree == null) return 0;
        int sum = 0;
        int limit = Math.min(line - 1, tree.length - 1);
        for (int i = limit; i > 0; i -= i & -i) {
            sum += tree[i];
        }
        return sum;
    }

    public int getBottom(int line) {
        if (line < 1 || tree == null) return 0;
        int sum = 0;
        int limit = Math.min(line, tree.length - 1);
        for (int i = limit; i > 0; i -= i & -i) {
            sum += tree[i];
        }
        return sum;
    }

    public int getTotalHeight() {
        return getBottom(count);
    }

    public int getLineAtY(int y) {
        if (y <= 0) return 1;
        int idx = 0;
        int currentY = 0;
        // Binary lifting on BIT search in O(log N)
        for (int i = Integer.highestOneBit(count); i > 0; i >>= 1) {
            int nextIdx = idx + i;
            if (nextIdx <= count && currentY + tree[nextIdx] <= y) {
                idx = nextIdx;
                currentY += tree[idx];
            }
        }
        return Math.min(idx + 1, count);
    }

    public int getHeight(int line) {
        return (line >= 1 && line <= count) ? heights[line] : 0;
    }
}
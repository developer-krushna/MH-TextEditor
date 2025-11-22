package modder.hub.editor.buffer;

import java.util.LinkedList;
import modder.hub.editor.utils.Pair;

/**
 * GapBuffer is a threadsafe EditBuffer that is optimized for editing with a cursor which tends to
 * make a sequence of inserts and deletes at the same place in the buffer
 *
 * <p>have all methods work with charOffsets and move all gap handling to getRealIndex()
 */

/** Re modification done by @developer-krushna Optimized some code config Known bugs are fixed */
public class GapBuffer implements CharSequence {

    private char[] _contents;
    private int _gapStartIndex;
    private int _gapEndIndex;
    private int _lineCount;
    private BufferCache _cache;
    private UndoStack _undoStack;

    // Selection snapshots produced by last undo/redo
    private int _lastUndoSelStart = -1;
    private int _lastUndoSelEnd = -1;
    private boolean _lastUndoSelMode = false;

    private int _lastRedoSelStart = -1;
    private int _lastRedoSelEnd = -1;
    private boolean _lastRedoSelMode = false;

    private final int EOF = '\uFFFF';
    private final int NEWLINE = '\n';

    public GapBuffer() {
        _contents = new char[16]; // init size 16
        _lineCount = 1;
        _gapStartIndex = 0;
        _gapEndIndex = _contents.length;
        _cache = new BufferCache();
        _undoStack = new UndoStack();
    }

    public GapBuffer(String buffer) {
        this();
        insert(0, buffer, false);
    }

    public GapBuffer(char[] buffer) {
        _contents = buffer;
        _lineCount = 1;
        _cache = new BufferCache();
        _undoStack = new UndoStack();
        for (char c : _contents) {
            if (c == NEWLINE)
                _lineCount++;
        }
    }

    /**
     * Returns a string of text corresponding to the line with index lineNumber.
     *
     * @param lineNumber The index of the line of interest
     * @return The text on lineNumber, or an empty string if the line does not exist
     */
    public synchronized String getLine(int lineNumber) {
        int startIndex = getLineOffset(lineNumber);
        int length = getLineLength(lineNumber);

        return substring(startIndex, startIndex + length);
    }

    /**
     * Get the offset of the first character of the line with index lineNumber. The offset is
     * counted from the beginning of the text.
     *
     * @param lineNumber The index of the line of interest
     * @return The character offset of lineNumber, or -1 if the line does not exist
     */
    public synchronized int getLineOffset(int lineNumber) {
        if (lineNumber <= 0 || lineNumber > getLineCount()) {
            throw new IllegalArgumentException("line index is invalid");
        }

        int lineIndex = --lineNumber;
        // start search from nearest known lineIndex~charOffset pair
        Pair<Integer, Integer> cacheEntry = _cache.getNearestLine(lineIndex);
        int cacheLine = cacheEntry.first;
        int cacheOffset = cacheEntry.second;

        int offset = 0;
        if (lineIndex > cacheLine) {
            offset = findCharOffset(lineIndex, cacheLine, cacheOffset);
        } else if (lineIndex < cacheLine) {
            offset = findCharOffsetBackward(lineIndex, cacheLine, cacheOffset);
        } else {
            offset = cacheOffset;
        }

        if (offset >= 0) {
            // seek successful
            _cache.updateEntry(lineIndex, offset);
        }
        return offset;
    }

    /*
     * Precondition: startOffset is the offset of startLine
     */
    private int findCharOffset(int targetLine, int startLine, int startOffset) {
        assert isValid(startOffset);

        int currLine = startLine;
        int offset = getRealIndex(startOffset);

        while ((currLine < targetLine) && (offset < _contents.length)) {
            if (_contents[offset] == NEWLINE) {
                ++currLine;
            }
            ++offset;

            // skip the gap
            if (offset == _gapStartIndex) {
                offset = _gapEndIndex;
            }
        }

        if (currLine != targetLine) {
            return -1;
        }
        return getLogicalIndex(offset);
    }

    /*
     * Precondition: startOffset is the offset of startLine
     */
    private int findCharOffsetBackward(int targetLine, int startLine, int startOffset) {
        assert isValid(startOffset);

        if (targetLine == 0) {
            return 0; // line index 0 always has 0 offset
        }

        int currLine = startLine;
        int offset = getRealIndex(startOffset);
        while (currLine > (targetLine - 1) && offset >= 0) {
            // skip behind the gap
            if (offset == _gapEndIndex) {
                offset = _gapStartIndex;
            }
            --offset;

            if (_contents[offset] == NEWLINE) {
                --currLine;
            }
        }

        int charOffset;
        if (offset >= 0) {
            // now at the '\n' of the line before targetLine
            charOffset = getLogicalIndex(offset);
            ++charOffset;
        } else {
            // assert isValid(offset);
            charOffset = -1;
        }
        return charOffset;
    }

    /**
     * Get the line number that charOffset is on
     *
     * @return The line number that charOffset is on, or -1 if charOffset is invalid
     */
    public synchronized int findLineNumber(int charOffset) {
        // Add bounds checking
        if (charOffset < 0) return 1;
        if (charOffset >= length()) return getLineCount();

        Pair<Integer, Integer> cachedEntry = _cache.getNearestCharOffset(charOffset);
        int line = cachedEntry.first;
        int offset = getRealIndex(cachedEntry.second);
        int targetOffset = getRealIndex(charOffset);
        int lastKnownLine = -1;
        int lastKnownCharOffset = -1;

        if (targetOffset > offset) {
            // search forward
            while ((offset < targetOffset) && (offset < _contents.length)) {
                if (_contents[offset] == NEWLINE) {
                    ++line;
                    lastKnownLine = line;
                    lastKnownCharOffset = getLogicalIndex(offset) + 1;
                }

                ++offset;
                // skip the gap
                if (offset == _gapStartIndex) {
                    offset = _gapEndIndex;
                }
            }
        } else if (targetOffset < offset) {
            // search backward
            while ((offset > targetOffset) && (offset > 0)) {
                // skip behind the gap
                if (offset == _gapEndIndex) {
                    offset = _gapStartIndex;
                }
                --offset;

                if (_contents[offset] == NEWLINE) {
                    lastKnownLine = line;
                    lastKnownCharOffset = getLogicalIndex(offset) + 1;
                    --line;
                }
            }
        }

        if (offset == targetOffset) {
            if (lastKnownLine != -1) {
                // cache the lookup entry
                _cache.updateEntry(lastKnownLine, lastKnownCharOffset);
            }
            return line + 1;
        } else {
            return 1; // Return 1 instead of 0 for invalid cases
        }
    }

    /**
     * Finds the number of char on the specified line. All valid lines contain at least one char,
     * which may be a non-printable one like \n, \t or EOF.
     *
     * @return The number of chars in lineNumber, or 0 if the line does not exist.
     */
    public synchronized int getLineLength(int lineNumber) {
        int lineLength = 0;
        int pos = getLineOffset(lineNumber);
        pos = getRealIndex(pos);

        // TODO consider adding check for (pos < _contents.length) in case EOF is not properly set
        while (pos < _contents.length &&
                _contents[pos] != NEWLINE &&
                _contents[pos] != EOF) {
            ++lineLength;
            ++pos;
            // skip the gap
            if (pos == _gapStartIndex) {
                pos = _gapEndIndex;
            }
        }
        return lineLength;
    }

    /**
     * Gets the char at charOffset Does not do bounds-checking.
     *
     * @return The char at charOffset. If charOffset is invalid, the result is undefined.
     */

    // In GapBuffer class - make charAt completely safe
    public synchronized char charAt(int charOffset) {
        // Comprehensive bounds checking
        if (charOffset < 0 || charOffset >= length()) {
            return '\0'; // Return null character for invalid indices
        }

        int realIndex = getRealIndex(charOffset);

        // Double-check real index bounds
        if (realIndex < 0 || realIndex >= _contents.length) {
            return '\0';
        }

        return _contents[realIndex];
    }

    /**
     * Gets up to maxChars number of chars starting at charOffset
     *
     * @return The chars starting from charOffset, up to a maximum of maxChars. An empty array is
     *     returned if charOffset is invalid or maxChars is non-positive.
     */
    public synchronized CharSequence subSequence(int start, int end) {
        // Add proper bounds checking
        if (start < 0) start = 0;
        if (end > length()) end = length();
        if (start >= end) return "";

        int count = end - start;
        int realIndex = getRealIndex(start);
        char[] chars = new char[count];

        for (int i = 0; i < count; ++i) {
            if (realIndex >= _contents.length) break; // Safety check
            chars[i] = _contents[realIndex];
            // skip the gap
            if (++realIndex == _gapStartIndex) {
                realIndex = _gapEndIndex;
            }
        }
        return new String(chars);
    }

    public synchronized String substring(int start, int end) {
        if (start < 0) start = 0;
        if (end > length()) end = length();
        if (start >= end) return "";
        return subSequence(start, end).toString();
    }

    /**
     * Insert all characters in c into position charOffset.
     *
     * <p>No error checking is done
     */
    public synchronized GapBuffer insert(int offset, String str, boolean capture) {
        return insert(offset, str, capture, System.nanoTime());
    }

    public synchronized GapBuffer insert(int offset, String str,
            boolean capture, long timestamp) {
        int length = str.length();
        if (capture && length > 0) {
            _undoStack.captureInsert(offset, offset + length, timestamp);
        }

        int insertIndex = getRealIndex(offset);

        // shift gap to insertion point
        if (insertIndex != _gapEndIndex) {
            if (isBeforeGap(insertIndex)) {
                shiftGapLeft(insertIndex);
            } else {
                shiftGapRight(insertIndex);
            }
        }

        if (length >= gapSize()) {
            expandBuffer(length - gapSize());
        }

        for (int i = 0; i < length; ++i) {
            char c = str.charAt(i);
            if (c == NEWLINE) {
                ++_lineCount;
            }
            _contents[_gapStartIndex] = c;
            ++_gapStartIndex;
        }

        _cache.invalidateCache(offset);
        return GapBuffer.this;
    }

    public synchronized GapBuffer append(String str, boolean capture) {
        insert(length(), str, capture);
        return GapBuffer.this;
    }

    public synchronized GapBuffer append(String str) {
        insert(length(), str, false);
        return GapBuffer.this;
    }

    /**
     * Deletes up to totalChars number of char starting from position charOffset, inclusive.
     *
     * <p>No error checking is done
     */
    public synchronized GapBuffer delete(int start, int end, boolean capture) {
        return delete(start, end, capture, System.nanoTime());
    }

    public synchronized GapBuffer delete(int start, int end,
            boolean capture, long timestamp) {
        if (capture && start < end) {
            _undoStack.captureDelete(start, end, timestamp);
        }

        int newGapStart = end;

        // shift gap to deletion point
        if (newGapStart != _gapStartIndex) {
            if (isBeforeGap(newGapStart)) {
                shiftGapLeft(newGapStart);
            } else {
                shiftGapRight(newGapStart + gapSize());
            }
        }

        // increase gap size
        int len = end - start;
        for (int i = 0; i < len; ++i) {
            --_gapStartIndex;
            if (_contents[_gapStartIndex] == NEWLINE) {
                --_lineCount;
            }
        }

        _cache.invalidateCache(start);
        return GapBuffer.this;
    }

    public synchronized GapBuffer replace(int start, int end, String str, boolean capture) {
        delete(start, end, capture);
        insert(start, str, capture);
        return GapBuffer.this;
    }

    /**
     * Gets charCount number of consecutive characters starting from _gapStartIndex.
     *
     * <p>Only UndoStack should use this method. No error checking is done.
     */
    private char[] gapSubSequence(int charCount) {
        char[] chars = new char[charCount];

        for (int i = 0; i < charCount; ++i) {
            chars[i] = _contents[_gapStartIndex + i];
        }
        return chars;
    }

    /**
     * Moves _gapStartIndex by displacement units. Note that displacement can be negative and will
     * move _gapStartIndex to the left.
     *
     * <p>Only UndoStack should use this method to carry out a simple undo/redo of
     * insertions/deletions. No error checking is done.
     */
    private synchronized void shiftGapStart(int displacement) {
        if (displacement >= 0)
            _lineCount += countNewlines(_gapStartIndex, displacement);
        else
            _lineCount -= countNewlines(_gapStartIndex + displacement, -displacement);

        _gapStartIndex += displacement;
        _cache.invalidateCache(getLogicalIndex(_gapStartIndex - 1) + 1);
    }

    // does NOT skip the gap when examining consecutive positions
    private int countNewlines(int start, int totalChars) {
        int newlines = 0;
        for (int i = start; i < (start + totalChars); ++i) {
            if (_contents[i] == NEWLINE) {
                ++newlines;
            }
        }

        return newlines;
    }

    /** Adjusts gap so that _gapStartIndex is at newGapStart */
    private void shiftGapLeft(int newGapStart) {
        while (_gapStartIndex > newGapStart) {
            _gapEndIndex--;
            _gapStartIndex--;
            _contents[_gapEndIndex] = _contents[_gapStartIndex];
        }
    }

    /** Adjusts gap so that _gapEndIndex is at newGapEnd */
    private void shiftGapRight(int newGapEnd) {
        while (_gapEndIndex < newGapEnd) {
            _contents[_gapStartIndex] = _contents[_gapEndIndex];
            _gapStartIndex++;
            _gapEndIndex++;
        }
    }

    /**
     * Copies _contents into a buffer that is larger by Math.max(minIncrement, _contents.length * 2
     * + 2) bytes.
     *
     * <p>_allocMultiplier doubles on every call to this method, to avoid the overhead of repeated
     * allocations.
     */
    private void expandBuffer(int minIncrement) {
        // TODO handle new size > MAX_INT or allocation failure
        int incrSize = Math.max(minIncrement, _contents.length * 2 + 2);
        char[] temp = new char[_contents.length + incrSize];
        // check the maxiunm size
        assert temp.length <= Integer.MAX_VALUE;

        int i = 0;
        while (i < _gapStartIndex) {
            temp[i] = _contents[i];
            ++i;
        }

        i = _gapEndIndex;
        while (i < _contents.length) {
            temp[i + incrSize] = _contents[i];
            ++i;
        }

        _gapEndIndex += incrSize;
        _contents = temp;
    }

    private boolean isValid(int charOffset) {
        return (charOffset >= 0 && charOffset <= this.length());
    }

    private int gapSize() {
        return _gapEndIndex - _gapStartIndex;
    }

    private int getRealIndex(int index) {
        // Handle all edge cases
        if (index < 0) return 0;
        if (index >= length()) return _contents.length - 1;

        if (isBeforeGap(index)) {
            return index;
        } else {
            int realIndex = index + gapSize();
            // Ensure we don't go beyond array bounds
            return Math.min(realIndex, _contents.length - 1);
        }
    }

    private int getLogicalIndex(int index) {
        if (isBeforeGap(index))
            return index;
        else
            return index - gapSize();
    }

    private boolean isBeforeGap(int index) {
        return index < _gapStartIndex;
    }

    public synchronized int getLineCount() {
        return _lineCount;
    }

    @Override
    public synchronized int length() {
        // TODO: Implement this method
        return _contents.length - gapSize();
    }

    @Override
    public synchronized String toString() {
        // TODO: Implement this method
        StringBuffer buf = new StringBuffer();
        int len = this.length();
        for (int i = 0; i < len; i++) {
            buf.append(charAt(i));
        }
        return new String(buf);
    }

    public boolean canUndo() {
        return _undoStack.canUndo();
    }

    public boolean canRedo() {
        return _undoStack.canRedo();
    }

    public int undo() {
        // clear last redo snapshot (avoid stale)
        _lastUndoSelStart = _lastUndoSelEnd = -1;
        _lastUndoSelMode = false;
        int pos = _undoStack.undo();
        // _undoStack will fill _lastUndo* fields (via inner class)
        return pos;
    }

    public int redo() {
        _lastRedoSelStart = _lastRedoSelEnd = -1;
        _lastRedoSelMode = false;
        int pos = _undoStack.redo();
        return pos;
    }

    /**
     * Editor should call this BEFORE starting an operation (or batch). This tells the undo stack
     * what the selection was before the edit.
     */
    public void markSelectionBefore(int selStart, int selEnd, boolean selMode) {
        _undoStack.setPendingSelectionBefore(selStart, selEnd, selMode);
    }

    /**
     * Editor should call this AFTER finishing an operation (or batch). This lets the undo stack
     * record the selection state after edit.
     */
    public void markSelectionAfter(int selStart, int selEnd, boolean selMode) {
        _undoStack.setPendingSelectionAfter(selStart, selEnd, selMode);
    }

    // getters for editor to read the selection restored by last undo/redo
    public int getLastUndoSelectionStart() {
        return _lastUndoSelStart;
    }

    public int getLastUndoSelectionEnd() {
        return _lastUndoSelEnd;
    }

    public boolean getLastUndoSelectionMode() {
        return _lastUndoSelMode;
    }

    public int getLastRedoSelectionStart() {
        return _lastRedoSelStart;
    }

    public int getLastRedoSelectionEnd() {
        return _lastRedoSelEnd;
    }

    public boolean getLastRedoSelectionMode() {
        return _lastRedoSelMode;
    }

    public void beginBatchEdit() {
        _undoStack.beginBatchEdit();
    }

    public void endBatchEdit() {
        _undoStack.endBatchEdit();
    }

    public boolean isBatchEdit() {
        return _undoStack.isBatchEdit();
    }

    class UndoStack {
        private boolean _isBatchEdit;
        /* for grouping batch operations */
        private int _groupId;
        /* where new entries should go */
        private int _top;
        /* timestamp for the previous edit operation */
        private long _lastEditTime = -1L; // Initialize to -1 to distinguish from valid timestamps

        private LinkedList<Action> _stack = new LinkedList<>();

        private static final int MAX_UNDO_SIZE = 50000; // Max chars per undo action (~100KB);
        // adjust as needed

        public final long MERGE_TIME = 1000000000;

        // Pending selection snapshots (set by GapBuffer.markSelectionBefore/After)
        private int _pendingSelBeforeStart = -1;
        private int _pendingSelBeforeEnd = -1;
        private boolean _pendingSelBeforeMode = false;

        private int _pendingSelAfterStart = -1;
        private int _pendingSelAfterEnd = -1;
        private boolean _pendingSelAfterMode = false;

        /**
         * Undo the previous insert/delete operation
         *
         * @return The suggested position of the caret after the undo, or -1 if there is nothing to
         *     undo
         */
        public int undo() {
            if (canUndo()) {
                Action lastUndo = _stack.get(_top - 1);
                int group = lastUndo._group;
                do {
                    Action action = _stack.get(_top - 1);
                    if (action._group != group) {
                        break;
                    }

                    lastUndo = action;
                    action.undo();
                    _top--;
                } while (canUndo());

                // After undoing the group, tell outer GapBuffer what selection to restore:
                // Use the 'before' snapshot of the last undone action (represents state prior to
                // the group)
                _lastUndoSelStart = lastUndo._selBeforeStart;
                _lastUndoSelEnd = lastUndo._selBeforeEnd;
                _lastUndoSelMode = lastUndo._selBeforeMode;

                return lastUndo.findUndoPosition();
            }
            return -1;
        }

        /**
         * Redo the previous insert/delete operation
         *
         * @return The suggested position of the caret after the redo, or -1 if there is nothing to
         *     redo
         */
        public int redo() {
            if (canRedo()) {
                Action lastRedo = _stack.get(_top);
                int group = lastRedo._group;
                do {
                    Action action = _stack.get(_top);
                    if (action._group != group) {
                        break;
                    }

                    lastRedo = action;
                    action.redo();
                    _top++;
                } while (canRedo());

                // After redoing the group, use the 'after' snapshot of the last redone action
                _lastRedoSelStart = lastRedo._selAfterStart;
                _lastRedoSelEnd = lastRedo._selAfterEnd;
                _lastRedoSelMode = lastRedo._selAfterMode;

                return lastRedo.findRedoPosition();
            }
            return -1;
        }

        // setters used by GapBuffer.markSelectionBefore/After
        public void setPendingSelectionBefore(int s, int e, boolean mode) {
            _pendingSelBeforeStart = s;
            _pendingSelBeforeEnd = e;
            _pendingSelBeforeMode = mode;
        }

        public void setPendingSelectionAfter(int s, int e, boolean mode) {
            _pendingSelAfterStart = s;
            _pendingSelAfterEnd = e;
            _pendingSelAfterMode = mode;
        }

        /**
         * extract common parts of captureInsert and captureDelete
         *
         * <p>Records an insert operation. Should be called before the insertion is actually done.
         */
        public void captureInsert(int start, int end, long time) {
            int len = end - start;
            boolean mergeSuccess = false;

            if (canUndo()) {
                Action action = _stack.get(_top - 1);
                if (action instanceof InsertAction &&
                        (time - _lastEditTime) < MERGE_TIME &&
                        start == action._end &&
                        (action._end - action._start + len <= MAX_UNDO_SIZE)) {
                    action._end += len;
                    mergeSuccess = true;
                } else {
                    action.recordData();
                }
            }

            if (!mergeSuccess && len <= MAX_UNDO_SIZE) {
                InsertAction a = new InsertAction(start, end, _groupId);
                // copy pending selection-before into action
                a._selBeforeStart = _pendingSelBeforeStart;
                a._selBeforeEnd = _pendingSelBeforeEnd;
                a._selBeforeMode = _pendingSelBeforeMode;
                // also copy pending selection-after (if editor already set it)
                a._selAfterStart = _pendingSelAfterStart;
                a._selAfterEnd = _pendingSelAfterEnd;
                a._selAfterMode = _pendingSelAfterMode;

                push(a);

                if (!_isBatchEdit) {
                    _groupId++;
                }
            }
            _lastEditTime = time;
        }

        /** Records a delete operation. Should be called before the deletion is actually done. */
        public void captureDelete(int start, int end, long time) {
            int len = end - start;
            boolean mergeSuccess = false;

            if (canUndo()) {
                Action action = _stack.get(_top - 1);
                if (action instanceof DeleteAction &&
                        (time - _lastEditTime) < MERGE_TIME &&
                        end == action._start &&
                        (action._end - start <= MAX_UNDO_SIZE)) {
                    action._start = start;
                    mergeSuccess = true;
                } else {
                    action.recordData();
                }
            }

            if (!mergeSuccess && len <= MAX_UNDO_SIZE) {
                DeleteAction a = new DeleteAction(start, end, _groupId);
                a._selBeforeStart = _pendingSelBeforeStart;
                a._selBeforeEnd = _pendingSelBeforeEnd;
                a._selBeforeMode = _pendingSelBeforeMode;

                a._selAfterStart = _pendingSelAfterStart;
                a._selAfterEnd = _pendingSelAfterEnd;
                a._selAfterMode = _pendingSelAfterMode;

                push(a);

                if (!_isBatchEdit) {
                    _groupId++;
                }
            }
            _lastEditTime = time;
        }

        private void push(Action action) {
            trimStack();
            _top++;
            _stack.add(action);
        }

        private void trimStack() {
            while (_stack.size() > _top) {
                _stack.removeLast();
            }
        }

        public final boolean canUndo() {
            return _top > 0;
        }

        public final boolean canRedo() {
            return _top < _stack.size();
        }

        public boolean isBatchEdit() {
            return _isBatchEdit;
        }

        public void beginBatchEdit() {
            _isBatchEdit = true;
        }

        public void endBatchEdit() {
            _isBatchEdit = false;
            _groupId++;
        }

        private abstract class Action {
            /* Start position of the edit */
            public int _start;
            /* End position of the edit */
            public int _end;
            /* Contents of the affected segment */
            public String _data;
            /* Group ID. Commands of the same group are undo/redo as a unit */
            public int _group;
            /* 750ms in nanoseconds */
            public final long MERGE_TIME = 750000000L; // Fixed to 750ms as per comment

            // Selection snapshot BEFORE this action (or group)
            public int _selBeforeStart = -1;
            public int _selBeforeEnd = -1;
            public boolean _selBeforeMode = false;

            // Selection snapshot AFTER this action
            public int _selAfterStart = -1;
            public int _selAfterEnd = -1;
            public boolean _selAfterMode = false;

            public abstract void undo();

            public abstract void redo();

            /* Populates _data with the affected text */
            public abstract void recordData();

            public abstract int findUndoPosition();

            public abstract int findRedoPosition();

            /**
             * Attempts to merge in an edit. This will only be successful if the new edit is
             * continuous. See {@link UndoStack} for the requirements of a continuous edit.
             *
             * @param start Start position of the new edit
             * @param length Length of the newly edited segment
             * @param time Timestamp when the new edit was made. There are no restrictions on the
             *     units used, as long as it is consistently used in the whole program
             * @return Whether the merge was successful
             */
            public abstract boolean merge(int start, int end, long time);
        }

        private class InsertAction extends Action {
            /** Corresponds to an insertion of text of size length just before start position. */
            public InsertAction(int start, int end, int group) {
                this._start = start;
                this._end = end;
                this._group = group;
            }

            @Override
            public boolean merge(int start, int end, long time) {
                if (_lastEditTime < 0) {
                    return false;
                }

                if ((time - _lastEditTime) < MERGE_TIME
                        && start == _end) {
                    _end += end - start;
                    trimStack();
                    return true;
                }
                return false;
            }

            @Override
            public void recordData() {
                // TODO handle memory allocation failure
                _data = substring(_start, _end);
            }

            @Override
            public void undo() {
                if (_data == null) {
                    recordData();
                    shiftGapStart(-(_end - _start));
                } else {
                    // dummy timestamp of 0
                    delete(_start, _end, false, 0);
                }
            }

            @Override
            public void redo() {
                // dummy timestamp of 0
                insert(_start, _data, false, 0);
            }

            @Override
            public int findRedoPosition() {
                return _end;
            }

            @Override
            public int findUndoPosition() {
                return _start;
            }
        }

        private class DeleteAction extends Action {
            /**
             * Corresponds to an deletion of text of size length starting from start position,
             * inclusive.
             */
            public DeleteAction(int start, int end, int group) {
                this._start = start;
                this._end = end;
                this._group = group;
            }

            @Override
            public boolean merge(int start, int end, long time) {
                if (_lastEditTime < 0) {
                    return false;
                }

                if ((time - _lastEditTime) < MERGE_TIME
                        && end == _start) {
                    _start = start;
                    trimStack();
                    return true;
                }
                return false;
            }

            @Override
            public void recordData() {
                // TODO handle memory allocation failure
                _data = new String(gapSubSequence(_end - _start));
            }

            @Override
            public void undo() {
                if (_data == null) {
                    recordData();
                    shiftGapStart(_end - _start);
                } else {
                    // dummy timestamp of 0
                    insert(_start, _data, false, 0);
                }
            }

            @Override
            public void redo() {
                // dummy timestamp of 0
                delete(_start, _end, false, 0);
            }

            @Override
            public int findRedoPosition() {
                return _start;
            }

            @Override
            public int findUndoPosition() {
                return _end;
            }
        } // end inner class
    }
}

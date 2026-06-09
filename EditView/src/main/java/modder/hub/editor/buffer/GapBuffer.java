package modder.hub.editor.buffer;

import android.text.Editable;
import android.text.GetChars;
import android.text.InputFilter;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.List;
import modder.hub.editor.utils.Pair;

/**
 * GapBuffer is a threadsafe EditBuffer that is optimized for editing with a cursor which tends to
 * make a sequence of inserts and deletes at the same place in the buffer
 *
 * <p>have all methods work with charOffsets and move all gap handling to getRealIndex()
 */

/** Re modification done by @developer-krushna Optimized some code config Known bugs are fixed */
public class GapBuffer implements CharSequence, Editable, GetChars {

    private char[] _contents;
    private volatile int _gapStartIndex;
    private volatile int _gapEndIndex;
    private volatile int _lineCount;
    private BufferCache _cache;
    private UndoStack _undoStack;

    // Span management
    private final List<Object> mSpans = new ArrayList<>();
    private final List<Integer> mSpanStarts = new ArrayList<>();
    private final List<Integer> mSpanEnds = new ArrayList<>();
    private final List<Integer> mSpanFlags = new ArrayList<>();
    private InputFilter[] mFilters = new InputFilter[0];
    private final List<TextWatcher> mWatchers = new ArrayList<>();

    // Batch edit tracking
    private int mBatchStart = -1;
    private int mBatchBefore = 0;
    private int mBatchAfter = 0;

    // Selection snapshots produced by last undo/redo
    private int _lastUndoSelStart = -1;
    private int _lastUndoSelEnd = -1;
    private boolean _lastUndoSelMode = false;

    private int _lastRedoSelStart = -1;
    private int _lastRedoSelEnd = -1;
    private boolean _lastRedoSelMode = false;

    private final int EOF = '\uFFFF';
    private final int NEWLINE = '\n';

    private boolean mSuppressUndoCapture = false;

    public void setSuppressUndoCapture(boolean suppress) {
        mSuppressUndoCapture = suppress;
    }

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
        return insert(offset, str, capture, System.currentTimeMillis());
    }

    public synchronized GapBuffer insert(int offset, String str,
            boolean capture, long timestamp) {
        int bufLen = length();
        if (offset < 0) offset = 0;
        if (offset > bufLen) offset = bufLen;
        
        int length = str != null ? str.length() : 0;
        if (length == 0) return this;
        
        if (isBatchEdit()) {
            if (mBatchStart == -1 || offset < mBatchStart) mBatchStart = offset;
            mBatchAfter += length;
        } else {
            sendBeforeTextChanged(offset, 0, length);
        }
        
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
                // Optimization for 1M+ lines: index the file during load/large inserts
                if (_lineCount % 1000 == 0) {
                    _cache.updateEntry(_lineCount - 1, offset + i + 1);
                }
            }
            _contents[_gapStartIndex] = c;
            ++_gapStartIndex;
        }

        updateSpansForInsert(offset, length);
        _cache.invalidateCache(offset);
        
        if (!isBatchEdit()) {
            sendOnTextChanged(offset, 0, length);
            sendAfterTextChanged();
        }
        
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
        return delete(start, end, capture, System.currentTimeMillis());
    }

    public synchronized GapBuffer delete(int start, int end,
            boolean capture, long timestamp) {
        int bufLen = length();
        if (start < 0) start = 0;
        if (start > bufLen) start = bufLen;
        if (end < start) end = start;
        if (end > bufLen) end = bufLen;

        int len = end - start;
        if (len <= 0) return this;
        
        if (isBatchEdit()) {
            if (mBatchStart == -1 || start < mBatchStart) mBatchStart = start;
            mBatchBefore += len;
        } else {
            sendBeforeTextChanged(start, len, 0);
        }

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
        for (int i = 0; i < len; ++i) {
            --_gapStartIndex;
            if (_contents[_gapStartIndex] == NEWLINE) {
                --_lineCount;
            }
        }

        updateSpansForDelete(start, end);
        _cache.invalidateCache(start);
        
        if (!isBatchEdit()) {
            sendOnTextChanged(start, len, 0);
            sendAfterTextChanged();
        }
        
        return GapBuffer.this;
    }

    public synchronized GapBuffer replace(int start, int end, String str, boolean capture) {
        beginBatchEdit();
        try {
            delete(start, end, capture);
            insert(start, str, capture);
        } finally {
            endBatchEdit();
        }
        return GapBuffer.this;
    }

    // --- Editable and Spannable implementations ---

    private void sendBeforeTextChanged(int start, int before, int after) {
        if (isBatchEdit()) return;
        for (TextWatcher watcher : mWatchers) {
            watcher.beforeTextChanged(this, start, before, after);
        }
    }

    private void sendOnTextChanged(int start, int before, int after) {
        if (isBatchEdit()) return;
        for (TextWatcher watcher : mWatchers) {
            watcher.onTextChanged(this, start, before, after);
        }
    }

    private void sendAfterTextChanged() {
        if (isBatchEdit()) return;
        for (TextWatcher watcher : mWatchers) {
            watcher.afterTextChanged(this);
        }
    }

    public void addTextChangedListener(TextWatcher watcher) {
        if (watcher != null && !mWatchers.contains(watcher)) {
            mWatchers.add(watcher);
        }
    }

    public void removeTextChangedListener(TextWatcher watcher) {
        mWatchers.remove(watcher);
    }

    @Override
    public Editable replace(int st, int en, CharSequence source, int start, int end) {
        replace(st, en, source != null ? source.subSequence(start, end).toString() : "",
                !mSuppressUndoCapture);
        return this;
    }

    @Override
    public Editable replace(int st, int en, CharSequence source) {
        replace(st, en, source != null ? source.toString() : "",
                !mSuppressUndoCapture);
        return this;
    }

    @Override
    public Editable insert(int where, CharSequence text, int start, int end) {
        insert(where, text != null ? text.subSequence(start, end).toString() : "",
                !mSuppressUndoCapture);
        return this;
    }

    @Override
    public Editable insert(int where, CharSequence text) {
        insert(where, text != null ? text.toString() : "",
                !mSuppressUndoCapture);
        return this;
    }

    @Override
    public Editable delete(int st, int en) {
        delete(st, en, !mSuppressUndoCapture);
        return this;
    }

    @Override
    public Editable append(CharSequence text) {
        append(text.toString(), true);
        return this;
    }

    @Override
    public Editable append(CharSequence text, int start, int end) {
        append(text.subSequence(start, end).toString(), true);
        return this;
    }

    @Override
    public Editable append(char text) {
        append(String.valueOf(text), true);
        return this;
    }

    @Override
    public void clear() {
        delete(0, length(), true);
    }

    @Override
    public synchronized void getChars(int start, int end, char[] dest, int destoff) {
        if (start < 0 || end > length() || start > end) {
            throw new IndexOutOfBoundsException();
        }
        int count = end - start;
        if (count == 0) return;

        int realStart = getRealIndex(start);
        int realEnd = getRealIndex(end - 1) + 1;

        if (realStart < _gapStartIndex && realEnd > _gapEndIndex) {
            // Segment spans across the gap
            int beforeGap = _gapStartIndex - realStart;
            System.arraycopy(_contents, realStart, dest, destoff, beforeGap);
            System.arraycopy(_contents, _gapEndIndex, dest, destoff + beforeGap, count - beforeGap);
        } else {
            // Segment is entirely on one side of the gap
            System.arraycopy(_contents, realStart, dest, destoff, count);
        }
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        mFilters = filters;
    }

    @Override
    public InputFilter[] getFilters() {
        return mFilters;
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        for (int i = 0; i < mSpans.size(); i++) {
            if (mSpans.get(i) == what) {
                mSpanStarts.set(i, start);
                mSpanEnds.set(i, end);
                mSpanFlags.set(i, flags);
                return;
            }
        }
        mSpans.add(what);
        mSpanStarts.add(start);
        mSpanEnds.add(end);
        mSpanFlags.add(flags);
    }

    @Override
    public void removeSpan(Object what) {
        for (int i = 0; i < mSpans.size(); i++) {
            if (mSpans.get(i) == what) {
                mSpans.remove(i);
                mSpanStarts.remove(i);
                mSpanEnds.remove(i);
                mSpanFlags.remove(i);
                return;
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < mSpans.size(); i++) {
            Object span = mSpans.get(i);
            if (type.isInstance(span)) {
                int s = mSpanStarts.get(i);
                int e = mSpanEnds.get(i);
                if (s <= end && e >= start) {
                    result.add((T) span);
                }
            }
        }
        return result.toArray((T[]) java.lang.reflect.Array.newInstance(type, result.size()));
    }

    @Override
    public int getSpanStart(Object what) {
        for (int i = 0; i < mSpans.size(); i++) {
            if (mSpans.get(i) == what) return mSpanStarts.get(i);
        }
        return -1;
    }

    @Override
    public int getSpanEnd(Object what) {
        for (int i = 0; i < mSpans.size(); i++) {
            if (mSpans.get(i) == what) return mSpanEnds.get(i);
        }
        return -1;
    }

    @Override
    public int getSpanFlags(Object what) {
        for (int i = 0; i < mSpans.size(); i++) {
            if (mSpans.get(i) == what) return mSpanFlags.get(i);
        }
        return 0;
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class type) {
        int best = limit;
        for (int i = 0; i < mSpans.size(); i++) {
            if (type == null || type.isInstance(mSpans.get(i))) {
                int s = mSpanStarts.get(i);
                int e = mSpanEnds.get(i);
                if (s > start && s < best) best = s;
                if (e > start && e < best) best = e;
            }
        }
        return best;
    }

    public void clearSpans() {
        mSpans.clear();
        mSpanStarts.clear();
        mSpanEnds.clear();
        mSpanFlags.clear();
    }

    private void updateSpansForInsert(int offset, int length) {
        for (int i = 0; i < mSpans.size(); i++) {
            int start = mSpanStarts.get(i);
            int end = mSpanEnds.get(i);
            if (start >= offset) mSpanStarts.set(i, start + length);
            if (end >= offset) mSpanEnds.set(i, end + length);
        }
    }

    private void updateSpansForDelete(int start, int end) {
        int length = end - start;
        for (int i = 0; i < mSpans.size(); i++) {
            int s = mSpanStarts.get(i);
            int e = mSpanEnds.get(i);
            if (s >= end) mSpanStarts.set(i, s - length);
            else if (s > start) mSpanStarts.set(i, start);

            if (e >= end) mSpanEnds.set(i, e - length);
            else if (e > start) mSpanEnds.set(i, start);
        }
    }

    // --- End Editable ---

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
        if (index < _gapStartIndex) {
            return index;
        } else {
            return index + gapSize();
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

    public int getLineCount() {
        return _lineCount;
    }

    @Override
    public int length() {
        return _contents.length - (_gapEndIndex - _gapStartIndex);
    }

    @Override
    public synchronized String toString() {
        int len = length();
        if (len <= 0) return "";
        return substring(0, len);
    }

    public boolean canUndo() {
        return _undoStack.canUndo();
    }

    public boolean canRedo() {
        return _undoStack.canRedo();
    }

    public synchronized int undo() {
        // clear last redo snapshot (avoid stale)
        _lastUndoSelStart = _lastUndoSelEnd = -1;
        _lastUndoSelMode = false;

        beginBatchEdit();
        int pos = -1;
        try {
            pos = _undoStack.undo();
            // Restore selection spans in the buffer so they are visible to the View
            if (_lastUndoSelStart >= 0 && _lastUndoSelEnd >= 0) {
                Selection.setSelection(this, _lastUndoSelStart, _lastUndoSelEnd);
            } else if (pos >= 0) {
                Selection.setSelection(this, pos);
            }
        } finally {
            endBatchEdit();
        }
        return pos;
    }

    public synchronized int redo() {
        _lastRedoSelStart = _lastRedoSelEnd = -1;
        _lastRedoSelMode = false;

        beginBatchEdit();
        int pos = -1;
        try {
            pos = _undoStack.redo();
            // Restore selection spans in the buffer
            if (_lastRedoSelStart >= 0 && _lastRedoSelEnd >= 0) {
                Selection.setSelection(this, _lastRedoSelStart, _lastRedoSelEnd);
            } else if (pos >= 0) {
                Selection.setSelection(this, pos);
            }
        } finally {
            endBatchEdit();
        }
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
        if (!isBatchEdit()) {
            mBatchStart = -1;
            mBatchBefore = 0;
            mBatchAfter = 0;
        }
        _undoStack.beginBatchEdit();
    }

    public void endBatchEdit() {
        _undoStack.endBatchEdit();
        if (!isBatchEdit()) {
            if (mBatchStart != -1) {
                for (TextWatcher watcher : mWatchers) {
                    watcher.beforeTextChanged(this, mBatchStart, mBatchBefore, mBatchAfter);
                    watcher.onTextChanged(this, mBatchStart, mBatchBefore, mBatchAfter);
                    watcher.afterTextChanged(this);
                }
            }
            mBatchStart = -1;
            mBatchBefore = 0;
            mBatchAfter = 0;
        }
    }

    public boolean isBatchEdit() {
        return _undoStack.isBatchEdit();
    }

    class UndoStack {
        private int _batchEditCount = 0;
        /* for grouping batch operations */
        private int _groupId;
        /* where new entries should go */
        private int _top;
        /* timestamp for the previous edit operation */
        private long _lastEditTime = -1L;

        private List<Action> _stack = new ArrayList<>();

        private static final int MAX_UNDO_SIZE = 50000;

        // Remove the char limit — cap by number of operations instead
        private static final int MAX_UNDO_OPERATIONS = 100000;

        /** Merge window for continuous edits (e.g., typing). */
        public static final long MERGE_TIME = 1000L;

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
            
            // Immediately update the last action in the stack if it belongs to the current group.
            // This ensures that 'redo' can restore the selection to what it was right after the edit.
            if (_top > 0) {
                Action lastAction = _stack.get(_top - 1);
                // Check if it's the current group (or the one that just 'ended' by _groupId++ but is still relevant)
                if (lastAction._group == _groupId || lastAction._group == _groupId - 1) {
                    lastAction._selAfterStart = s;
                    lastAction._selAfterEnd = e;
                    lastAction._selAfterMode = mode;
                }
            }
        }

        /**
         * extract common parts of captureInsert and captureDelete
         *
         * <p>Records an insert operation. Should be called before the insertion is actually done.
         */
        public void captureInsert(int start, int end, long time) {
            int len = end - start;
            if (len <= 0) return;
            boolean mergeSuccess = false;

            if (canUndo()) {
                Action action = _stack.get(_top - 1);
                if (action instanceof InsertAction &&
                        (time - _lastEditTime) < MERGE_TIME &&
                        start == action._end) {

                    InsertAction ia = (InsertAction) action;
                    // Extend end — data will be re-read fresh on undo via recordData
                    // but safer to build it incrementally
                    if (ia._data == null) {
                        ia._data = substring(ia._start, ia._end);
                    }
                    ia._end += len;
                    // Append new chars — they are about to be inserted so read BEFORE insert
                    // Actually at captureInsert time the chars are not yet in buffer,
                    // so we cannot read them. Leave _data to be refreshed via recordData on undo.
                    ia._data = null; // mark dirty — recordData will re-read correctly at undo time
                    ia._selAfterStart = _pendingSelAfterStart;
                    ia._selAfterEnd = _pendingSelAfterEnd;
                    ia._selAfterMode = _pendingSelAfterMode;
                    mergeSuccess = true;
                }
            }

            if (!mergeSuccess) {
                while (_stack.size() >= MAX_UNDO_OPERATIONS) {
                    _stack.remove(0);
                    if (_top > 0) _top--;
                }

                InsertAction a = new InsertAction(start, end, _groupId);
                a._selBeforeStart = _pendingSelBeforeStart;
                a._selBeforeEnd = _pendingSelBeforeEnd;
                a._selBeforeMode = _pendingSelBeforeMode;
                a._selAfterStart = _pendingSelAfterStart;
                a._selAfterEnd = _pendingSelAfterEnd;
                a._selAfterMode = _pendingSelAfterMode;
                // _data intentionally null — recordData reads it fresh at undo time
                // which is CORRECT for inserts because the text IS in the buffer at that point
                push(a);

                if (_batchEditCount <= 0) {
                    _groupId++;
                }
            }
            _lastEditTime = time;
        }
        /** Records a delete operation. Should be called before the deletion is actually done. */
        public void captureDelete(int start, int end, long time) {
            int len = end - start;
            if (len <= 0) return;
            boolean mergeSuccess = false;

            if (canUndo()) {
                Action action = _stack.get(_top - 1);
                if (action instanceof DeleteAction &&
                        (time - _lastEditTime) < MERGE_TIME) {

                    DeleteAction da = (DeleteAction) action;

                    if (end == da._start) {
                        // Backspace: new deletion is immediately before existing
                        // Capture the new fragment NOW before gap moves
                        String newFragment = substring(start, end);
                        da._data = newFragment + (da._data != null ? da._data : "");
                        da._start = start;
                        da._selAfterStart = _pendingSelAfterStart;
                        da._selAfterEnd = _pendingSelAfterEnd;
                        da._selAfterMode = _pendingSelAfterMode;
                        mergeSuccess = true;

                    } else if (start == da._start) {
                        // Forward delete: new deletion extends end forward
                        String newFragment = substring(start, end);
                        da._data = (da._data != null ? da._data : "") + newFragment;
                        da._end = da._start + da._data.length();
                        da._selAfterStart = _pendingSelAfterStart;
                        da._selAfterEnd = _pendingSelAfterEnd;
                        da._selAfterMode = _pendingSelAfterMode;
                        mergeSuccess = true;
                    }
                }
            }

            if (!mergeSuccess) {
                while (_stack.size() >= MAX_UNDO_OPERATIONS) {
                    _stack.remove(0);
                    if (_top > 0) _top--;
                }

                DeleteAction a = new DeleteAction(start, end, _groupId);
                // Always capture eagerly before gap moves
                a._data = substring(start, end);
                a._selBeforeStart = _pendingSelBeforeStart;
                a._selBeforeEnd = _pendingSelBeforeEnd;
                a._selBeforeMode = _pendingSelBeforeMode;
                a._selAfterStart = _pendingSelAfterStart;
                a._selAfterEnd = _pendingSelAfterEnd;
                a._selAfterMode = _pendingSelAfterMode;

                push(a);

                if (_batchEditCount <= 0) {
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
                _stack.remove(_stack.size() - 1);
            }
        }

        public final boolean canUndo() {
            return _top > 0;
        }

        public final boolean canRedo() {
            return _top < _stack.size();
        }

        public boolean isBatchEdit() {
            return _batchEditCount > 0;
        }

        public void beginBatchEdit() {
            _batchEditCount++;
        }

        public void endBatchEdit() {
            _batchEditCount--;
            if (_batchEditCount <= 0) {
                _batchEditCount = 0;
                _groupId++;
            }
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

                if ((time - _lastEditTime) < UndoStack.MERGE_TIME
                        && start == _end) {
                    _end += end - start;
                    trimStack();
                    return true;
                }
                return false;
            }

            @Override
            public void undo() {
                // For inserts: text IS still in buffer at undo time, safe to read now
                recordData();
                delete(_start, _end, false, 0);
            }

            @Override
            public void recordData() {
                _data = substring(_start, Math.min(_end, length()));
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

                if ((time - _lastEditTime) < UndoStack.MERGE_TIME
                        && end == _start) {
                    _start = start;
                    trimStack();
                    return true;
                }
                return false;
            }

            @Override
            public void recordData() {
                // Safe substring read — never use gapSubSequence
                _data = substring(_start, _end);
            }

            @Override
            public void undo() {
                // _data always captured eagerly at captureDelete time
                if (_data != null) {
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

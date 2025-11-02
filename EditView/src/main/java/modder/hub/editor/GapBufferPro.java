/*
* MH-TextEditor - An Advanced and optimized TextEditor for android
* Copyright 2025, developer-krushna
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*
*     * Redistributions of source code must retain the above copyright
* notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
* copyright notice, this list of conditions and the following disclaimer
* in the documentation and/or other materials provided with the
* distribution.
*     * Neither the name of developer-krushna nor the names of its
* contributors may be used to endorse or promote products derived from
* this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


*     Please contact Krushna by email modder-hub@zohomail.in if you need
*     additional information or have any questions
*/

package modder.hub.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.GetChars;
import android.text.InputFilter;
import android.text.NoCopySpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import java.lang.reflect.Array;

// Not ready yet
/* This is an another method to use spananable text as Syntex highligter
as well as Gap Buffer functionalities that */

/* This class was actually used by priorier versions of  MT Manager Text Editor*/
public class GapBufferPro implements Editable, GetChars, Spannable, Appendable, CharSequence {
    private static final InputFilter[] NO_FILTERS = new InputFilter[0];

    private boolean mLocked;
    private char[] mDrawBuffer;
    private InputFilter[] mFilters;
    private char[] mBuffer;
    private int mGapStart;
    private int mGapLength;
    private Object[] mSpans;
    private int[] mSpanStarts;
    private int[] mSpanEnds;
    private int[] mSpanFlags;
    private int mSpanCount;

    public GapBufferPro() {
        this("");
    }

    public GapBufferPro(CharSequence source) {
        this(source, 0, source.length());
    }

    public GapBufferPro(CharSequence source, int start, int end) {
        mLocked = false;
        mDrawBuffer = new char[50];
        mFilters = NO_FILTERS;
        int sourceLength = end - start;
        int bufferCapacity = nextPowerOfTwo(sourceLength + 1);
        mBuffer = new char[bufferCapacity];
        mGapStart = sourceLength;
        mGapLength = bufferCapacity - sourceLength;
        TextUtils.getChars(source, start, end, mBuffer, 0);
        mSpanCount = 0;
        int initialSpanCapacity = nextSpanCapacity(0);
        mSpans = new Object[initialSpanCapacity];
        mSpanStarts = new int[initialSpanCapacity];
        mSpanEnds = new int[initialSpanCapacity];
        mSpanFlags = new int[initialSpanCapacity];
        if (source instanceof Spanned) {
            Spanned spannedSource = (Spanned) source;
            Object[] sourceSpans = spannedSource.getSpans(start, end, Object.class);
            for (int spanIndex = 0; spanIndex < sourceSpans.length; spanIndex++) {
                if (!(sourceSpans[spanIndex] instanceof NoCopySpan)) {
                    int spanStart = Math.max(0, Math.min(sourceLength, spannedSource.getSpanStart(sourceSpans[
                            spanIndex]) - start));
                    int spanEnd = Math.max(0, Math.min(sourceLength, spannedSource.getSpanEnd(sourceSpans[
                            spanIndex]) - start));
                    int spanFlags = spannedSource.getSpanFlags(sourceSpans[spanIndex]);
                    setSpan(sourceSpans[spanIndex], spanStart, spanEnd, spanFlags);
                }
            }
        }
    }

    private int replaceText(boolean notifyWatchers, int replaceStart, int replaceEnd, CharSequence replacement, int replacementStart, int replacementEnd) {
        int replacementLength;
        Object[] addedSpans;
        int spanIndex;
        int adjustedStart;
        int adjustedEnd;
        int insertedLength = replacementEnd - replacementStart;
        if (!isValidRange(replaceStart, replaceEnd)) {
            return insertedLength;
        }
        TextWatcher
                [] watchers = notifyWatchers ? getWatchers(replaceStart, replaceEnd - replaceStart, insertedLength) : null;
        // Handle paragraph spans that cross the replacement boundary
        for (int spanIdx = mSpanCount - 1; spanIdx >= 0; spanIdx--) {
            if ((mSpanFlags[spanIdx] & 51) == 51) { // SPAN_PARAGRAPH flags
                int spanStartRaw = mSpanStarts[spanIdx];
                if (spanStartRaw > mGapStart) {
                    spanStartRaw -= mGapLength;
                }
                int spanEndRaw = mSpanEnds[spanIdx];
                if (spanEndRaw > mGapStart) {
                    spanEndRaw -= mGapLength;
                }
                int textLength = length();
                int newSpanStart = (spanStartRaw <= replaceStart || spanStartRaw > replaceEnd) ? spanStartRaw : adjustToParagraphEnd(replaceEnd, textLength);
                int newSpanEnd = (spanEndRaw <= replaceStart || spanEndRaw > replaceEnd) ? spanEndRaw : adjustToParagraphStart(replaceEnd, textLength);
                if (newSpanStart != spanStartRaw || newSpanEnd != spanEndRaw) {
                    setSpan(mSpans[spanIdx], newSpanStart, newSpanEnd, mSpanFlags[spanIdx]);
                }
            }
        }
        moveGapTo(replaceEnd);
        int deletedLength = replaceEnd - replaceStart;
        if (insertedLength >= mGapLength + deletedLength) {
            growBuffer((mBuffer.length - mGapLength + replacementEnd) - replacementStart - deletedLength);
        }
        int netInsertion = insertedLength - deletedLength;
        mGapStart += netInsertion;
        mGapLength -= netInsertion;
        if (mGapLength < 1) {
            throw new IllegalStateException("Gap length must be at least 1");
        }
        TextUtils.getChars(replacement, replacementStart, replacementEnd, mBuffer, replaceStart);
        if (replacement instanceof Spanned) {
            Spanned spannedReplacement = (Spanned) replacement;
            Object
                    [] replacementSpans = spannedReplacement.getSpans(replacementStart, replacementEnd, Object.class);
            int repSpanIndex = 0;
            while (repSpanIndex < replacementSpans.length) {
                int repSpanStart = spannedReplacement.getSpanStart(replacementSpans[repSpanIndex]);
                int repSpanEnd = spannedReplacement.getSpanEnd(replacementSpans[repSpanIndex]);
                repSpanStart = Math.max(replacementStart, repSpanStart);
                repSpanEnd = Math.min(replacementEnd, repSpanEnd);
                if (getSpanStart(replacementSpans[repSpanIndex]) < 0) {
                    replacementLength = repSpanIndex;
                    addedSpans = replacementSpans;
                    addSpan(false, replacementSpans[
                    repSpanIndex], (repSpanStart - replacementStart) + replaceStart, (repSpanEnd - replacementStart) + replaceStart, spannedReplacement.getSpanFlags(replacementSpans[
                    repSpanIndex]));
                } else {
                    replacementLength = repSpanIndex;
                    addedSpans = replacementSpans;
                }
                repSpanIndex = replacementLength + 1;
                replacementSpans = addedSpans;
            }
        }
        if (replacementEnd > replacementStart && deletedLength == 0) {
            if (notifyWatchers) {
                notifyTextChanged(watchers, replaceStart, deletedLength, insertedLength);
                notifyAfterTextChanged(watchers);
            }
            return insertedLength;
        }
        boolean atEnd = mGapStart + mGapLength == mBuffer.length;
        for (int spanIdx2 = mSpanCount - 1; spanIdx2 >= 0; spanIdx2--) {
            if (mSpanStarts[spanIdx2] >= replaceStart && mSpanStarts[
                            spanIdx2] < mGapStart + mGapLength) {
                int startFlag = (mSpanFlags[spanIdx2] & 240) >> 4;
                if (startFlag == 2 || (startFlag == 3 && atEnd)) {
                    mSpanStarts[spanIdx2] = mGapStart + mGapLength;
                } else {
                    mSpanStarts[spanIdx2] = replaceStart;
                }
            }
            if (mSpanEnds[spanIdx2] >= replaceStart && mSpanEnds[
                            spanIdx2] < mGapStart + mGapLength) {
                int endFlag = mSpanFlags[spanIdx2] & 15;
                if (endFlag == 2 || (endFlag == 3 && atEnd)) {
                    mSpanEnds[spanIdx2] = mGapStart + mGapLength;
                } else {
                    mSpanEnds[spanIdx2] = replaceStart;
                }
            }
            if (mSpanEnds[spanIdx2] < mSpanStarts[spanIdx2]) {
                int removeIdx = spanIdx2 + 1;
                System.arraycopy(mSpans, removeIdx, mSpans, spanIdx2, mSpanCount - removeIdx);
                System.arraycopy(mSpanStarts, removeIdx, mSpanStarts, spanIdx2, mSpanCount - removeIdx);
                System.arraycopy(mSpanEnds, removeIdx, mSpanEnds, spanIdx2, mSpanCount - removeIdx);
                System.arraycopy(mSpanFlags, removeIdx, mSpanFlags, spanIdx2, mSpanCount - removeIdx);
                mSpanCount--;
            }
        }
        if (notifyWatchers) {
            notifyTextChanged(watchers, replaceStart, deletedLength, insertedLength);
            notifyAfterTextChanged(watchers);
        }
        return insertedLength;
    }

    public static GapBufferPro valueOf(CharSequence source) {
        return (source instanceof GapBufferPro) ? (GapBufferPro) source : new GapBufferPro(source);
    }

    private void growBuffer(int requiredLength) {
        int newCapacity = nextPowerOfTwo(requiredLength + 1);
        if (requiredLength > 524288) {
            newCapacity = 131072 + requiredLength;
        }
        char[] newBuffer = new char[newCapacity];
        int trailingLength = mBuffer.length - (mGapStart + mGapLength);
        System.arraycopy(mBuffer, 0, newBuffer, 0, mGapStart);
        System.arraycopy(mBuffer, mBuffer.length - trailingLength, newBuffer, newCapacity - trailingLength, trailingLength);
        for (int spanIdx = 0; spanIdx < mSpanCount; spanIdx++) {
            if (mSpanStarts[spanIdx] > mGapStart) {
                mSpanStarts[spanIdx] += (newCapacity - mBuffer.length);
            }
            if (mSpanEnds[spanIdx] > mGapStart) {
                mSpanEnds[spanIdx] += (newCapacity - mBuffer.length);
            }
        }
        int oldLength = mBuffer.length;
        mBuffer = newBuffer;
        mGapLength += mBuffer.length - oldLength;
        if (mGapLength < 1) {
            throw new IllegalStateException("Gap length must be at least 1");
        }
    }

    private void notifySpanAdded(Object span, int start, int end) {
        for (SpanWatcher watcher : getSpans(start, end, SpanWatcher.class)) {
            watcher.onSpanAdded(this, span, start, end);
        }
    }

    private void notifySpanChanged(Object span, int oldStart, int oldEnd, int newStart, int newEnd) {
        for (SpanWatcher watcher : getSpans(Math.min(oldStart, newStart), Math.max(oldEnd, newEnd), SpanWatcher.class)) {
            watcher.onSpanChanged(this, span, oldStart, oldEnd, newStart, newEnd);
        }
    }

    private void addSpan(boolean notify, Object span, int start, int end, int flags) {
        int startInGap = (start <= mGapStart && !(start == mGapStart && ((flags & 240) >> 4 == 2 || (flags >> 4 == 3 && start == length())))) ? start : start + mGapLength;
        int endInGap = (end <= mGapStart && !(end == mGapStart && ((flags & 15) == 2 || ((flags & 15) == 3 && end == length())))) ? end : end + mGapLength;
        if (isValidRange(start, end)) {
            int startFlag = flags & 240;
            if (startFlag == 48 && start != 0 && start != length() && charAt(start - 1) != '\n') {
                throw new RuntimeException("PARAGRAPH span must start at paragraph boundary");
            }
            int endFlag = flags & 15;
            if (endFlag == 3 && end != 0 && end != length() && charAt(end - 1) != '\n') {
                throw new RuntimeException("PARAGRAPH span must end at paragraph boundary");
            }
            for (int existingIdx = 0; existingIdx < mSpanCount; existingIdx++) {
                if (mSpans[existingIdx] == span) {
                    int oldStartRaw = mSpanStarts[existingIdx];
                    int oldEndRaw = mSpanEnds[existingIdx];
                    if (oldStartRaw > mGapStart) {
                        oldStartRaw -= mGapLength;
                    }
                    if (oldEndRaw > mGapStart) {
                        oldEndRaw -= mGapLength;
                    }
                    mSpanStarts[existingIdx] = startInGap;
                    mSpanEnds[existingIdx] = endInGap;
                    mSpanFlags[existingIdx] = flags;
                    if (notify) {
                        notifySpanChanged(span, oldStartRaw, oldEndRaw, start, end);
                    }
                    return;
                }
            }
            if (mSpanCount + 1 >= mSpans.length) {
                int newSpanCapacity = nextSpanCapacity(mSpanCount + 1);
                Object[] newSpans = new Object[newSpanCapacity];
                int[] newStarts = new int[newSpanCapacity];
                int[] newEnds = new int[newSpanCapacity];
                int[] newFlags = new int[newSpanCapacity];
                System.arraycopy(mSpans, 0, newSpans, 0, mSpanCount);
                System.arraycopy(mSpanStarts, 0, newStarts, 0, mSpanCount);
                System.arraycopy(mSpanEnds, 0, newEnds, 0, mSpanCount);
                System.arraycopy(mSpanFlags, 0, newFlags, 0, mSpanCount);
                mSpans = newSpans;
                mSpanStarts = newStarts;
                mSpanEnds = newEnds;
                mSpanFlags = newFlags;
            }
            mSpans[mSpanCount] = span;
            mSpanStarts[mSpanCount] = startInGap;
            mSpanEnds[mSpanCount] = endInGap;
            mSpanFlags[mSpanCount] = flags;
            mSpanCount++;
            if (notify) {
                notifySpanAdded(span, start, end);
            }
        }
    }

    private void notifyAfterTextChanged(TextWatcher[] watchers) {
        for (TextWatcher watcher : watchers) {
            watcher.afterTextChanged(this);
        }
    }

    private void notifyTextChanged(TextWatcher[] watchers, int start, int before, int count) {
        for (TextWatcher watcher : watchers) {
            watcher.onTextChanged(this, start, before, count);
        }
    }

    private TextWatcher[] getWatchers(int start, int before, int count) {
        TextWatcher[] watchers = getSpans(start, start + before, TextWatcher.class);
        for (TextWatcher watcher : watchers) {
            watcher.beforeTextChanged(this, start, before, count);
        }
        return watchers;
    }

    private int insertText(int insertPos, int insertEnd, CharSequence source, int sourceStart, int sourceEnd) {
        return replaceText(true, insertPos, insertEnd, source, sourceStart, sourceEnd);
    }

    private void moveGapTo(int newGapPos) {
        int gapMoveAmount;
        int endFlag;
        if (newGapPos == mGapStart) {
            return;
        }
        boolean atTextEnd = newGapPos == length();
        if (newGapPos < mGapStart) {
            gapMoveAmount = mGapStart - newGapPos;
            System.arraycopy(mBuffer, newGapPos, mBuffer, (mGapStart + mGapLength) - gapMoveAmount, gapMoveAmount);
        } else {
            gapMoveAmount = newGapPos - mGapStart;
            System.arraycopy(mBuffer, (mGapStart + mGapLength) - gapMoveAmount, mBuffer, mGapStart, gapMoveAmount);
        }
        for (int spanIdx = 0; spanIdx < mSpanCount; spanIdx++) {
            int startRaw = mSpanStarts[spanIdx];
            int endRaw = mSpanEnds[spanIdx];
            if (startRaw > mGapStart) {
                startRaw -= mGapLength;
            }
            if (startRaw > newGapPos || (startRaw == newGapPos && (((mSpanFlags[
                                                            spanIdx] & 240) >> 4) == 2 || (atTextEnd && ((mSpanFlags[
                                                                    spanIdx] & 240) >> 4 == 3))))) {
                startRaw += mGapLength;
            }
            if (endRaw > mGapStart) {
                endRaw -= mGapLength;
            }
            if (endRaw > newGapPos || (endRaw == newGapPos && ((endFlag = mSpanFlags[
                                                    spanIdx] & 15) == 2 || (atTextEnd && endFlag == 3)))) {
                endRaw += mGapLength;
            }
            mSpanStarts[spanIdx] = startRaw;
            mSpanEnds[spanIdx] = endRaw;
        }
        mGapStart = newGapPos;
    }

    private void notifySpanRemoved(Object span, int start, int end) {
        for (SpanWatcher watcher : getSpans(start, end, SpanWatcher.class)) {
            watcher.onSpanRemoved(this, span, start, end);
        }
    }

    private boolean isValidRange(int start, int end) {
        int textLen = length();
        return end >= start && start <= textLen && end <= textLen && start >= 0 && end >= 0;
    }

    @Override
    public GapBufferPro append(char ch) {
        return append(String.valueOf(ch));
    }

    @Override
    public GapBufferPro delete(int start, int end) {
        if (mLocked) {
            return this;
        }
        GapBufferPro result = replace(start, end, "", 0, 0);
        if (mGapLength > 2 * length()) {
            growBuffer(length());
        }
        return result;
    }

    @Override
    public GapBufferPro replace(int start, int end, CharSequence replacement) {
        return replace(start, end, replacement, 0, replacement.length());
    }

    @Override
    public GapBufferPro replace(int start, int end, CharSequence replacement, int repStart, int repEnd) {
        if (mLocked) {
            return this;
        }
        int filterCount = mFilters.length;
        CharSequence filteredRep = replacement;
        int filteredRepStart = repStart;
        int filteredRepEnd = repEnd;
        for (int filterIdx = 0; filterIdx < filterCount; filterIdx++) {
            CharSequence filtered = mFilters[
            filterIdx].filter(filteredRep, filteredRepStart, filteredRepEnd, this, start, end);
            if (filtered != null) {
                filteredRep = filtered;
                filteredRepEnd = filtered.length();
                filteredRepStart = 0;
            }
        }
        if (end == start && filteredRepStart == filteredRepEnd) {
            return this;
        }
        if (end == start || filteredRepStart == filteredRepEnd) {
            insertText(start, end, filteredRep, filteredRepStart, filteredRepEnd);
            return this;
        }
        int selStart = Selection.getSelectionStart(this);
        int selEnd = Selection.getSelectionEnd(this);
        if (!isValidRange(start, end)) {
            return this;
        }
        moveGapTo(end);
        int deletedLen = end - start;
        TextWatcher[] watchers = getWatchers(start, deletedLen, filteredRepEnd - filteredRepStart);
        if (mGapLength < 2) {
            growBuffer(length() + 1);
        }
        for (int spanIdx = mSpanCount - 1; spanIdx >= 0; spanIdx--) {
            if (mSpanStarts[spanIdx] == mGapStart) {
                mSpanStarts[spanIdx]++;
            }
            if (mSpanEnds[spanIdx] == mGapStart) {
                mSpanEnds[spanIdx]++;
            }
        }
        mBuffer[mGapStart] = ' ';
        mGapStart++;
        mGapLength--;
        if (mGapLength < 1) {
            throw new IllegalStateException("Gap length must be at least 1");
        }
        int insertedLen = replaceText(false, start + 1, start + 1, filteredRep, filteredRepStart, filteredRepEnd);
        replaceText(false, start, start + 1, "", 0, 0);
        replaceText(false, start + insertedLen, (end + 1 - start) - 1, "", 0, 0);
        if (selStart > start && selStart < end) {
            int newSelStart = start + (((selStart - start) * insertedLen) / deletedLen);
            addSpan(false, Selection.SELECTION_START, newSelStart, newSelStart, 34);
        }
        if (selEnd > start && selEnd < end) {
            int newSelEnd = start + (((selEnd - start) * insertedLen) / deletedLen);
            addSpan(false, Selection.SELECTION_END, newSelEnd, newSelEnd, 34);
        }
        notifyTextChanged(watchers, start, deletedLen, insertedLen);
        notifyAfterTextChanged(watchers);
        return this;
    }

    @Override
    public GapBufferPro insert(int where, CharSequence chars) {
        return replace(where, where, chars, 0, chars.length());
    }

    @Override
    public GapBufferPro insert(int where, CharSequence chars, int start, int end) {
        return replace(where, where, chars, start, end);
    }

    @Override
    public GapBufferPro append(CharSequence chars, int start, int end) {
        int len = length();
        return replace(len, len, chars, start, end);
    }

    public void drawText(Canvas canvas, int start, int end, float x, float y, Paint paint) {
        char[] textToDraw;
        int offset;
        Canvas drawCanvas;
        int drawLen = end - start;
        if (drawLen > 0) {
            if (end <= mGapStart) {
                textToDraw = mBuffer;
                drawCanvas = canvas;
                offset = start;
            } else {
                if (start >= mGapStart) {
                    textToDraw = mBuffer;
                    offset = start + mGapLength;
                } else {
                    if (mDrawBuffer.length < drawLen) {
                        mDrawBuffer = new char[drawLen];
                    }
                    System.arraycopy(mBuffer, start, mDrawBuffer, 0, mGapStart - start);
                    System.arraycopy(mBuffer, mGapStart + mGapLength, mDrawBuffer, mGapStart - start, end - mGapStart);
                    textToDraw = mDrawBuffer;
                    offset = 0;
                }
                drawCanvas = canvas;
            }
            drawCanvas.drawText(textToDraw, offset, drawLen, x, y, paint);
        }
    }

    void setLocked(boolean locked) {
        mLocked = locked;
    }

    @Override
    public GapBufferPro append(CharSequence chars) {
        int len = length();
        return replace(len, len, chars, 0, chars.length());
    }

    public String substring(int start, int end) {
        char[] chars = new char[end - start];
        getChars(start, end, chars, 0);
        return new String(chars);
    }

    @Override
    public char charAt(int index) {
        int len = length();
        if (index < 0) {
            throw new IndexOutOfBoundsException("charAt: " + index + " < 0");
        }
        if (index < len) {
            return index >= mGapStart ? mBuffer[index + mGapLength] : mBuffer[index];
        }
        throw new IndexOutOfBoundsException("charAt: " + index + " >= length " + len);
    }

    @Override
    public void clear() {
        if (mLocked) {
            return;
        }
        replace(0, length(), "", 0, 0);
    }

    @Override
    public void clearSpans() {
        for (int spanIdx = mSpanCount - 1; spanIdx >= 0; spanIdx--) {
            Object span = mSpans[spanIdx];
            int spanStartRaw = mSpanStarts[spanIdx];
            int spanEndRaw = mSpanEnds[spanIdx];
            if (spanStartRaw > mGapStart) {
                spanStartRaw -= mGapLength;
            }
            if (spanEndRaw > mGapStart) {
                spanEndRaw -= mGapLength;
            }
            mSpanCount = spanIdx;
            mSpans[spanIdx] = null;
            notifySpanRemoved(span, spanStartRaw, spanEndRaw);
        }
    }

    @Override
    public void getChars(int start, int end, char[] dest, int destStart) {
        char[] src;
        int srcOffset;
        if (isValidRange(start, end)) {
            if (end <= mGapStart) {
                System.arraycopy(mBuffer, start, dest, destStart, end - start);
                return;
            }
            if (start >= mGapStart) {
                src = mBuffer;
                srcOffset = mGapLength + start;
            } else {
                System.arraycopy(mBuffer, start, dest, destStart, mGapStart - start);
                src = mBuffer;
                srcOffset = mGapStart + mGapLength;
                destStart += mGapStart - start;
                start = mGapStart;
            }
            System.arraycopy(src, srcOffset, dest, destStart, end - start);
        }
    }

    @Override
    public InputFilter[] getFilters() {
        return mFilters;
    }

    @Override
    public int getSpanEnd(Object span) {
        int count = mSpanCount;
        Object[] spansArray = mSpans;
        for (int idx = count - 1; idx >= 0; idx--) {
            if (spansArray[idx] == span) {
                int endRaw = mSpanEnds[idx];
                return endRaw > mGapStart ? endRaw - mGapLength : endRaw;
            }
        }
        return -1;
    }

    @Override
    public int getSpanFlags(Object span) {
        int count = mSpanCount;
        Object[] spansArray = mSpans;
        for (int idx = count - 1; idx >= 0; idx--) {
            if (spansArray[idx] == span) {
                return mSpanFlags[idx];
            }
        }
        return 0;
    }

    @Override
    public int getSpanStart(Object span) {
        int count = mSpanCount;
        Object[] spansArray = mSpans;
        for (int idx = count - 1; idx >= 0; idx--) {
            if (spansArray[idx] == span) {
                int startRaw = mSpanStarts[idx];
                return startRaw > mGapStart ? startRaw - mGapLength : startRaw;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        int queryStart = start;
        int count = mSpanCount;
        Object[] spansArray = mSpans;
        int[] startsArray = mSpanStarts;
        int[] endsArray = mSpanEnds;
        int[] flagsArray = mSpanFlags;
        int gapStartLocal = mGapStart;
        int gapLengthLocal = mGapLength;
        T firstMatch = null;
        Object[] matches = null;
        int matchCount = 0;
        int idx = 0;
        while (idx < count) {
            int spanStartRaw = startsArray[idx];
            int[] startsLocal = startsArray;
            int spanEndRaw = endsArray[idx];
            if (spanStartRaw > gapStartLocal) {
                spanStartRaw -= gapLengthLocal;
            }
            if (spanEndRaw > gapStartLocal) {
                spanEndRaw -= gapLengthLocal;
            }
            if (spanStartRaw <= end && spanEndRaw >= queryStart &&
                    (spanStartRaw == spanEndRaw || queryStart == end ||
                            (spanStartRaw != end && spanEndRaw != queryStart)) &&
                    (type == null || type.isInstance(spansArray[idx]))) {
                if (matchCount == 0) {
                    matchCount++;
                    firstMatch = (T) spansArray[idx];
                } else {
                    if (matchCount == 1) {
                        matches = (Object[]) Array.newInstance(type, (count - idx) + 1);
                        matches[0] = firstMatch;
                    }
                    int priority = flagsArray[idx] & 16711680;
                    if (priority != 0) {
                        int insertPos = 0;
                        while (insertPos < matchCount && priority <= (getSpanFlags(matches[
                                                insertPos]) & 16711680)) {
                            insertPos++;
                        }
                        System.arraycopy(matches, insertPos, matches, insertPos + 1, matchCount - insertPos);
                        matches[insertPos] = spansArray[idx];
                        matchCount++;
                    } else {
                        matches[matchCount] = spansArray[idx];
                        matchCount++;
                    }
                }
            }
            idx++;
            startsArray = startsLocal;
            queryStart = start;
        }
        if (matchCount == 0) {
            return (T[]) Array.newInstance(type, 0);
        }
        T[] result;
        if (matchCount == 1) {
            result = (T[]) Array.newInstance(type, 1);
            result[0] = firstMatch;
        } else {
            if (matchCount == matches.length) {
                return (T[]) matches;
            }
            result = (T[]) Array.newInstance(type, matchCount);
            System.arraycopy(matches, 0, result, 0, matchCount);
        }
        return result;
    }

    @Override
    public int length() {
        return Math.max(mBuffer.length - mGapLength, 0);
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class kind) {
        int count = mSpanCount;
        Object[] spansArray = mSpans;
        int[] startsArray = mSpanStarts;
        int[] endsArray = mSpanEnds;
        int gapStartLocal = mGapStart;
        int gapLengthLocal = mGapLength;
        if (kind == null) {
            kind = Object.class;
        }
        int nextTransition = limit;
        for (int idx = 0; idx < count; idx++) {
            int spanStartRaw = startsArray[idx];
            int spanEndRaw = endsArray[idx];
            if (spanStartRaw > gapStartLocal) {
                spanStartRaw -= gapLengthLocal;
            }
            if (spanEndRaw > gapStartLocal) {
                spanEndRaw -= gapLengthLocal;
            }
            if (spanStartRaw > start && spanStartRaw < nextTransition && kind.isInstance(spansArray[
                    idx])) {
                nextTransition = spanStartRaw;
            }
            if (spanEndRaw > start && spanEndRaw < nextTransition && kind.isInstance(spansArray[
                    idx])) {
                nextTransition = spanEndRaw;
            }
        }
        return nextTransition;
    }

    @Override
    public void removeSpan(Object span) {
        for (int idx = mSpanCount - 1; idx >= 0; idx--) {
            if (mSpans[idx] == span) {
                int spanStartRaw = mSpanStarts[idx];
                int spanEndRaw = mSpanEnds[idx];
                if (spanStartRaw > mGapStart) {
                    spanStartRaw -= mGapLength;
                }
                if (spanEndRaw > mGapStart) {
                    spanEndRaw -= mGapLength;
                }
                int removeIdx = idx + 1;
                int copyLen = mSpanCount - removeIdx;
                System.arraycopy(mSpans, removeIdx, mSpans, idx, copyLen);
                System.arraycopy(mSpanStarts, removeIdx, mSpanStarts, idx, copyLen);
                System.arraycopy(mSpanEnds, removeIdx, mSpanEnds, idx, copyLen);
                System.arraycopy(mSpanFlags, removeIdx, mSpanFlags, idx, copyLen);
                mSpanCount--;
                mSpans[mSpanCount] = null;
                notifySpanRemoved(span, spanStartRaw, spanEndRaw);
                return;
            }
        }
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        if (filters == null) {
            throw new IllegalArgumentException();
        }
        mFilters = filters;
    }

    @Override
    public void setSpan(Object span, int start, int end, int flags) {
        addSpan(true, span, start, end, flags);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new GapBufferPro(this, start, end);
    }

    @Override
    public String toString() {
        int len = length();
        char[] chars = new char[len];
        getChars(0, len, chars, 0);
        return new String(chars);
    }

    // Helper methods (inferred from decompiler; implement as needed for full functionality)
    private int nextPowerOfTwo(int n) {
        if (n <= 0) return 1;
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return (n + 1);
    }

    private int nextSpanCapacity(int current) {
        return nextPowerOfTwo(current + 1);
    }

    private int adjustToParagraphEnd(int pos, int textLen) {
        int adjusted = pos;
        while (adjusted < textLen && (adjusted <= pos || charAt(adjusted - 1) != '\n')) {
            adjusted++;
        }
        return adjusted;
    }

    private int adjustToParagraphStart(int pos, int textLen) {
        int adjusted = pos;
        while (adjusted < textLen) {
            if (adjusted > pos) {
                textLen = textLen;
                if (charAt(adjusted - 1) == '\n') {
                    break;
                }
            } else {
                textLen = textLen;
            }
            adjusted++;
            textLen = textLen;
        }
        return adjusted;
    }
}

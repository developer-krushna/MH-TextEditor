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

import android.util.Log;
import modder.hub.editor.EditView;
import modder.hub.editor.GapBuffer;
import java.util.ArrayList;
import java.util.List;

// Not ready yet
public class WordWrapLayout {
    private static final String TAG = "WordWrapLayout";

    private EditView mEditView;
    private GapBuffer mGapBuffer;
    private List<RowRegion> mRowTable;
    private int mEditorWidth;
    private boolean mEnabled = false;

    public WordWrapLayout(EditView editView) {
        this.mEditView = editView;
        this.mGapBuffer = editView.getBuffer();
        this.mRowTable = new ArrayList<>();
        this.mEditorWidth = editView.getWidth();
    }

    public void setEnabled(boolean enabled) {
        this.mEnabled = enabled;
        if (enabled) {
            breakAllLines();
        } else {
            mRowTable.clear();
        }
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEditorWidth(int width) {
        this.mEditorWidth = width;
        if (mEnabled) {
            breakAllLines();
        }
    }

    private void breakAllLines() {
        if (!mEnabled || mGapBuffer == null) return;

        mRowTable.clear();

        for (int line = 1; line <= mGapBuffer.getLineCount(); line++) {
            breakLine(line);
        }

        Log.d(TAG, "Word wrap completed. Total rows: " + mRowTable.size());
    }

    private void breakLine(int lineNumber) {
        if (!mEnabled) return;

        String lineText = mGapBuffer.getLine(lineNumber);
        if (lineText == null || lineText.isEmpty()) {
            // Empty line still needs one row
            mRowTable.add(new RowRegion(lineNumber, 0, 0));
            return;
        }

        int lineStart = mGapBuffer.getLineOffset(lineNumber);
        int currentPos = 0;
        int lineLength = lineText.length();

        while (currentPos < lineLength) {
            int breakPoint = findBreakPoint(lineText, currentPos, lineLength);

            if (breakPoint == currentPos) {
                // Force break if no space found
                breakPoint = Math.min(currentPos + 1, lineLength);
            }

            mRowTable.add(new RowRegion(lineNumber, lineStart + currentPos, lineStart + breakPoint));
            currentPos = breakPoint;
        }

        // If we didn't add any rows (shouldn't happen), add at least one
        if (mRowTable.isEmpty() || mRowTable.get(mRowTable.size() - 1).line != lineNumber) {
            mRowTable.add(new RowRegion(lineNumber, lineStart, lineStart + lineLength));
        }
    }

    private int findBreakPoint(String text, int start, int maxLength) {
        if (start >= maxLength) return maxLength;

        int availableWidth = mEditorWidth - mEditView.getLeftSpace() - 20; // Some padding
        int currentWidth = 0;
        int lastBreakable = start;

        for (int i = start; i < maxLength; i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            int charWidth = mEditView.measureText(charStr);

            // Check if adding this character would exceed available width
            if (currentWidth + charWidth > availableWidth) {
                if (lastBreakable > start) {
                    // Break at last breakable position
                    return lastBreakable;
                } else {
                    // No breakable position found, break here
                    return i;
                }
            }

            currentWidth += charWidth;

            // Mark breakable positions (spaces, punctuation)
            if (isBreakableChar(c)) {
                lastBreakable = i + 1; // Break after the space/punctuation
            }

            // Break at newlines
            if (c == '\n') {
                return i + 1;
            }
        }

        return maxLength;
    }

    private boolean isBreakableChar(char c) {
        return c == ' ' || c == '\t' || c == '.' || c == ',' || c == ';' || c == ':' ||
                c == '!' || c == '?' || c == ')' || c == ']' || c == '}';
    }

    public void onTextChanged(int startLine, int endLine) {
        if (!mEnabled) return;

        // Remove affected rows
        for (int i = mRowTable.size() - 1; i >= 0; i--) {
            RowRegion region = mRowTable.get(i);
            if (region.line >= startLine && region.line <= endLine) {
                mRowTable.remove(i);
            }
        }

        // Re-break affected lines
        for (int line = startLine; line <= endLine; line++) {
            breakLine(line);
        }

        // Update line numbers for subsequent lines
        int delta = endLine - startLine;
        if (delta != 0) {
            for (int i = 0; i < mRowTable.size(); i++) {
                RowRegion region = mRowTable.get(i);
                if (region.line > endLine) {
                    region.line += delta;
                }
            }
        }
    }

    public int getRowCount() {
        if (!mEnabled) {
            return mGapBuffer.getLineCount();
        }
        return mRowTable.size();
    }

    public int getLineForRow(int row) {
        if (!mEnabled || row < 0 || row >= mRowTable.size()) {
            return Math.max(1, Math.min(row + 1, mGapBuffer.getLineCount()));
        }
        return mRowTable.get(row).line;
    }

    public RowRegion getRowRegion(int row) {
        if (!mEnabled || row < 0 || row >= mRowTable.size()) {
            int line = Math.max(1, Math.min(row + 1, mGapBuffer.getLineCount()));
            int lineStart = mGapBuffer.getLineOffset(line);
            int lineEnd = lineStart + mGapBuffer.getLine(line).length();
            return new RowRegion(line, lineStart, lineEnd);
        }
        return mRowTable.get(row);
    }

    public int findRowForPosition(int line, int column) {
        if (!mEnabled) {
            return line - 1; // Convert to 0-based
        }

        for (int row = 0; row < mRowTable.size(); row++) {
            RowRegion region = mRowTable.get(row);
            if (region.line == line && column >= region.start && column < region.end) {
                return row;
            }
        }

        // Fallback: find the first row for this line
        for (int row = 0; row < mRowTable.size(); row++) {
            if (mRowTable.get(row).line == line) {
                return row;
            }
        }

        return Math.max(0, line - 1);
    }

    public int getRowHeight() {
        return mEditView.getLineHeight();
    }

    public int getTotalHeight() {
        return getRowCount() * getRowHeight();
    }

    public class RowRegion {
        public int line;
        public int start;
        public int end;

        public RowRegion(int line, int start, int end) {
            this.line = line;
            this.start = start;
            this.end = end;
        }

        public boolean isFirstRow() {
            return start == mGapBuffer.getLineOffset(line);
        }

        public int getLength() {
            return end - start;
        }

        @Override
        public String toString() {
            return "RowRegion{line=" + line + ", start=" + start + ", end=" + end + "}";
        }
    }
}

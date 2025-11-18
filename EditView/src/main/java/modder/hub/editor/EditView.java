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

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;
import android.widget.OverScroller;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import modder.hub.editor.R;
import modder.hub.editor.buffer.GapBuffer;
import modder.hub.editor.component.ClipboardPanel;
import modder.hub.editor.component.Magnifier;
import modder.hub.editor.highlight.MHSyntaxHighlightEngine;
import modder.hub.editor.listener.OnTextChangedListener;
import modder.hub.editor.utils.ScreenUtils;

/* Author : Krushna Chandra Maharna(@developer-krushna)
   This project was actually started by some one using gap buffer
   But i forgot his name and his repository link because i was started
   working on this project in 2024 .. During that time due to some personal problem
   I closed this project and saved it in sdcard for future task .
   But unfortunately i unable to save the original author name. I am really sorry.
   But if you are the creator then please let me know so that i can update this
   part. Thank You

   Optmization , code refactorinh and comments are made by ChatGPT
*/

/*
* I have not included many useful helper methods as i was working for something
* Feel free to include them
* But basic fetures are already introduced so no need to worry about it Lol

*/

public class EditView extends View {

    private static final String COPYRIGHT = "MH-TextEditor\nCopyright (C) Krushna Chandra modder-hub@zohomail.in\nThis project is distributed under the LGPL v2.1 license";

    private final String TAG = this.getClass().getSimpleName();

    // ---------- Fields (state, resources, helpers) ----------
    private Paint mPaint;
    private TextPaint mTextPaint;
    private GapBuffer mGapBuffer;

    // cursor and select handle drawable resources
    private Drawable mDrawableCursorRes;
    private Drawable mTextSelectHandleLeftRes;
    private Drawable mTextSelectHandleRightRes;
    private Drawable mTextSelectHandleMiddleRes;

    private int mCursorPosX, mCursorPosY;
    private int mCursorLine, mCursorIndex;
    private int mCursorWidth, mCursorHeight;
    private int screenWidth, screenHeight;
    private int lineWidth, spaceWidth;
    private int handleMiddleWidth, handleMiddleHeight;
    private int selectionStart, selectionEnd;
    private int selectHandleWidth, selectHandleHeight;
    private int selectHandleLeftX, selectHandleLeftY;
    private int selectHandleRightX, selectHandleRightY;

    private int mMetaState = 0;

    private OnTextChangedListener mTextListener;
    private OverScroller mScroller;
    private GestureDetector mGestureDetector;
    private GestureListener mGestureListener;
    private ScaleGestureDetector mScaleGestureDetector;
    private ClipboardManager mClipboard;
    private ArrayList<Pair<Integer, Integer>> mReplaceList;

    private boolean mCursorVisiable = true;
    private boolean mHandleMiddleVisable = false;
    private boolean isEditedMode = true;
    private boolean isSelectMode = false;

    private long mLastScroll;
    // record last single tap time
    private long mLastTapTime;
    // left margin for draw text
    private final int SPACEING = 2;
    // animation duration 250ms
    private final int DEFAULT_DURATION = 250;
    // cursor blink BLINK_TIMEOUT 500ms
    private final int BLINK_TIMEOUT = 500;

    // Magnifier
    private Magnifier mMagnifier;
    private boolean mMagnifierEnabled = true;
    private float mMagnifierX, mMagnifierY;
    private boolean mIsMagnifierShowing = false;

    private ClipboardPanel mClipboardPanel;

    private MHSyntaxHighlightEngine mHighlighter;
    private boolean isSyntaxDarkMode = false;

    // Auto-complete
    private Set<String> mWordSet = new HashSet<>();
    private ListPopupWindow mAutoCompletePopup;
    private ArrayAdapter<String> mAutoCompleteAdapter;

    private static final Pattern WORD_PATTERN = Pattern.compile("\\w+");
    private static final int MIN_WORD_LEN = 2; // Filter short words
    private static final int WORD_UPDATE_DELAY = 200; // ms throttle
    private Runnable mWordUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateWordSet();
        }
    };
    private String mCurrentPrefix = "";
    private long mLastInputTime = 0;
    private String mLastCommittedText = "";
    private boolean mProcessingInput = false;
    private final long INPUT_DEBOUNCE_DELAY = 50; // ms

    private boolean mAutoIndentEnabled = true; // Default on

    private int mFirstSelectedLine = -1;
    private int mSecondSelectedLine = -1;
    private boolean mWaitingForSecondSelection = false;
    private int mStartSelectionLine = -1;
    private int mEndSelectionLine = -1;
    private boolean mIsLineSelectionMode = false;
    private Runnable mClearSelectionRunnable = new Runnable() {
        @Override
        public void run() {
            // If user doesn't select second line within timeout, clear selection
            mWaitingForSecondSelection = false;
            mFirstSelectedLine = -1;
        }
    };

    // ---------- Blink / auto-hide ----------
    // cursor blink runnable toggling visibility
    private Runnable blinkAction = new Runnable() {
        @Override
        public void run() {
            // TODO: Implement this method
            mCursorVisiable = !mCursorVisiable;
            postDelayed(blinkAction, BLINK_TIMEOUT);

            if (System.currentTimeMillis() - mLastTapTime >= 5 * BLINK_TIMEOUT) {
                mHandleMiddleVisable = false;
            }
            postInvalidate();
        }
    };

    private Handler mSelectionHandler = new Handler();
    private Runnable mUpdateSelectionPosition = new Runnable() {
        @Override
        public void run() {
            if (mClipboardPanel != null && (isSelectMode || mHandleMiddleVisable)) {
                mClipboardPanel.updatePosition();
            }
        }
    };

    private Runnable mAutoHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSelectMode && !mHandleMiddleVisable) {
                hideTextSelectionWindow();
            }
        }
    };

    // ---------- Constructors ----------
    // Constructor (Context)
    public EditView(Context context) {
        super(context);
        initView(context);
    }

    // Constructor (Context, AttributeSet)
    public EditView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    // Constructor (Context, AttributeSet, defStyle)
    public EditView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    private void initView(Context context) {
        Log.v(TAG, COPYRIGHT);

        // Initialize Gapbuffer
        mGapBuffer = new GapBuffer();
        mCursorLine = getLineCount();
        setBackgroundColor(Color.WHITE);

        screenWidth = ScreenUtils.getScreenWidth(context);
        screenHeight = ScreenUtils.getScreenHeight(context);

        mDrawableCursorRes = context.getDrawable(R.drawable.abc_text_cursor_material);
        mDrawableCursorRes.setTint(Color.BLACK);

        mCursorWidth = mDrawableCursorRes.getIntrinsicWidth();
        mCursorHeight = mDrawableCursorRes.getIntrinsicHeight();

        mClipboardPanel = new ClipboardPanel(this);

        // Initialize magnifier
        mMagnifier = new Magnifier(this);

        // Reduce cursor width and make it responsive
        int density = (int) getResources().getDisplayMetrics().density;
        mCursorWidth = Math.max(2, density); // Minimum 2px, scales with density
        if (mCursorWidth > 5) mCursorWidth = 5; // Max 4px

        // handle left - scale down selection handles
        mTextSelectHandleLeftRes = context.getDrawable(R.drawable.abc_text_select_handle_left_mtrl);
        mTextSelectHandleLeftRes.setTint(Color.parseColor("#63B5F7"));

        // Scale down selection handles based on screen density
        selectHandleWidth = (int) (mTextSelectHandleLeftRes.getIntrinsicWidth() * 0.3f);
        selectHandleHeight = (int) (mTextSelectHandleLeftRes.getIntrinsicHeight() * 0.3f);

        // handle right
        mTextSelectHandleRightRes = context.getDrawable(R.drawable.abc_text_select_handle_right_mtrl);
        mTextSelectHandleRightRes.setTint(Color.parseColor("#63B5F7"));

        // handle middle - scale down
        mTextSelectHandleMiddleRes = context.getDrawable(R.drawable.abc_text_select_handle_middle_mtrl);
        mTextSelectHandleMiddleRes.setTint(Color.parseColor("#63B5F7"));
        handleMiddleWidth = (int) (mTextSelectHandleMiddleRes.getIntrinsicWidth() * 0.5f);
        handleMiddleHeight = (int) (mTextSelectHandleMiddleRes.getIntrinsicHeight() * 0.5f);

        mGestureListener = new GestureListener();
        mGestureDetector = new GestureDetector(context, mGestureListener);
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureListener());

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.parseColor("#B0B0B0"));

        setTextSize(ScreenUtils.dip2px(context, 18));
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.parseColor("#FFFAE3"));
        mPaint.setStrokeWidth(0);

        mScroller = new OverScroller(context);
        mClipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        mReplaceList = new ArrayList<>();

        spaceWidth = (int) mTextPaint.measureText("  ");

        // Explicitly set initial scroll position to (0, 0)
        scrollTo(0, 0);

        requestFocus();
        setFocusable(true);
        postDelayed(blinkAction, BLINK_TIMEOUT);

        mAutoCompletePopup = new ListPopupWindow(getContext());
        mAutoCompleteAdapter = new ArrayAdapter<String>(
        getContext(),
        R.layout.item_autocomplete,
        R.id.text1, // 👈 explicitly tell it which TextView to use
        new ArrayList<String>()
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(R.id.text1);
                String item = getItem(position);
                if (item != null && !mCurrentPrefix.isEmpty()) {
                    int index = item.toLowerCase().indexOf(mCurrentPrefix.toLowerCase());
                    if (index >= 0) {
                        SpannableString spannable = new SpannableString(item);
                        spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#2196F3")),
                                index, index + mCurrentPrefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        textView.setText(spannable);
                    } else {
                        textView.setText(item);
                    }
                } else {
                    textView.setText(item);
                }
                return view;
            }
        };
        mAutoCompletePopup.setAdapter(mAutoCompleteAdapter);
        mAutoCompletePopup.setHeight(ScreenUtils.dip2px(context, 150)); // fits about 3-4 rows
        mAutoCompletePopup.setModal(false); // allow typing while shown
        mAutoCompletePopup.setAnchorView(this);

        mAutoCompletePopup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                if (selected != null) {
                    replacePrefixWithWord(selected);
                    dismissAutoComplete();
                }
            }
        });
        mAutoCompletePopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                mCurrentPrefix = ""; // Reset on dismiss
            }
        });
        // Initial word set
        post(mWordUpdateRunnable);
    }

    // ---------- Lifecycle ----------
    // Called when view detached from window
    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mWordUpdateRunnable);
        dismissAutoComplete();
        super.onDetachedFromWindow();
    }

    // Called when view size/layout changes
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            // Only adjust scroll if cursor is significantly out of view
            adjustCursorPosition();
            if (isSelectMode) {
                adjustSelectRange(selectionStart, selectionEnd);
            }
            // Check if initial scroll is needed
            /*  if (getScrollY() > 0 || getScrollX() > 0) {
                scrollToVisable();
            } else {
                // Ensure we're at the top-left initially
                scrollTo(0, 0);
            }*/
        }
    }

    // ---------- Rendering / Drawing ----------
    // Top-level draw method
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.clipRect(getScrollX(),
                getScrollY(),
                getScrollX() + getWidth() - getPaddingRight(),
                getScrollY() + getHeight() - getPaddingBottom());

        canvas.translate(getPaddingLeft(), getPaddingTop());

        Drawable background = getBackground();
        if (background != null) {
            background.draw(canvas);
        }

        drawMatchText(canvas);
        drawLineBackground(canvas);
        drawEditableText(canvas); // Remove the background drawing from this method
        drawSelectHandle(canvas);
        drawCursor(canvas);

        canvas.restore();
    }

    // Draw the editor content (helper)
    public void drawEditorContent(Canvas canvas, int captureTop, int captureBottom) {
        int saveCount = canvas.save();
        canvas.translate(getScrollX(), getScrollY());
        drawMatchText(canvas);
        drawLineBackground(canvas);
        drawEditableText(canvas);
        drawCursor(canvas);
        drawSelectHandle(canvas);
        canvas.restoreToCount(saveCount);
    }

    // Draw current line background or selection highlight
    public void drawLineBackground(Canvas canvas) {
        if (mIsLineSelectionMode && mStartSelectionLine > 0 && mEndSelectionLine > 0) {
            mPaint.setColor(Color.parseColor("#E3F2FD")); // Light blue background
            int lineNumberWidth = getLineNumberWidth() + SPACEING * 2;

            for (int i = mStartSelectionLine; i <= mEndSelectionLine; i++) {
                int top = (i - 1) * getLineHeight();
                int bottom = i * getLineHeight();

                canvas.drawRect(getPaddingLeft(), top,
                        getPaddingLeft() + lineNumberWidth, bottom, mPaint);
            }
        }

        if (!isSelectMode) {
            // draw current line background
            mPaint.setColor(Color.parseColor("#FFFAE3"));
            int left = getPaddingLeft() + getLineNumberWidth() + SPACEING;
            canvas.drawRect(left,
                    getPaddingTop() + mCursorPosY,
                    getScrollX() + getWidth(),
                    mCursorPosY + getLineHeight(),
                    mPaint);
        } else {
            // draw select text background - BLOCK STYLE (like before)
            mPaint.setColor(Color.parseColor("#B3DBFB"));

            int left = getLeftSpace();
            int lineHeight = getLineHeight();

            int startLine = getOffsetLine(selectionStart);
            int endLine = getOffsetLine(selectionEnd);

            // Only draw visible selection ranges to reduce lag
            int visibleStartLine = Math.max(startLine, canvas.getClipBounds().top / lineHeight);
            int visibleEndLine = Math.min(endLine, canvas.getClipBounds().bottom / lineHeight + 1);

            // start line < end line
            if (startLine != endLine) {
                for (int i = visibleStartLine; i <= visibleEndLine; i++) {
                    int lineWidth = getLineWidth(i) + spaceWidth;
                    if (i == startLine) {
                        // First line - from selection start to end of line
                        int lineStart = getLineStart(startLine);
                        String beforeText = mGapBuffer.substring(lineStart, selectionStart);
                        int selectStartX = left + (int) mTextPaint.measureText(beforeText);

                        canvas.drawRect(selectStartX,
                                (startLine - 1) * lineHeight,
                                left + lineWidth,
                                startLine * lineHeight,
                                mPaint);
                    } else if (i == endLine) {
                        // Last line - from line start to selection end
                        int lineStart = getLineStart(endLine);
                        String beforeText = mGapBuffer.substring(lineStart, selectionEnd);
                        int selectEndX = left + (int) mTextPaint.measureText(beforeText);

                        canvas.drawRect(left,
                                (endLine - 1) * lineHeight,
                                selectEndX,
                                endLine * lineHeight,
                                mPaint);
                    } else {
                        // Middle lines - full line width
                        canvas.drawRect(left,
                                (i - 1) * lineHeight,
                                left + lineWidth,
                                i * lineHeight,
                                mPaint);
                    }
                }
            } else {
                // start line = end line - single line selection
                int lineStart = getLineStart(startLine);
                String beforeStartText = mGapBuffer.substring(lineStart, selectionStart);
                String selectedText = mGapBuffer.substring(selectionStart, selectionEnd);

                int selectStartX = left + (int) mTextPaint.measureText(beforeStartText);
                int selectEndX = selectStartX + (int) mTextPaint.measureText(selectedText);

                canvas.drawRect(selectStartX,
                        (startLine - 1) * lineHeight,
                        selectEndX,
                        startLine * lineHeight,
                        mPaint);
            }
        }
    }

    // Draw select handles (left/right)
    public void drawSelectHandle(Canvas canvas) {
        if (isSelectMode) {
            mTextSelectHandleLeftRes.setBounds(selectHandleLeftX - selectHandleWidth + selectHandleWidth / 4,
                    selectHandleLeftY,
                    selectHandleLeftX + selectHandleWidth / 4,
                    selectHandleLeftY + selectHandleHeight
            );

            mTextSelectHandleLeftRes.draw(canvas);

            // select handle right
            mTextSelectHandleRightRes.setBounds(selectHandleRightX - selectHandleWidth / 4,
                    selectHandleRightY,
                    selectHandleRightX + selectHandleWidth - selectHandleWidth / 4,
                    selectHandleRightY + selectHandleHeight
            );
            mTextSelectHandleRightRes.draw(canvas);
        }
    }

    // Draw match/replace highlights
    public void drawMatchText(Canvas canvas) {
        if (isSelectMode) {
            int size = mReplaceList.size();
            int left = getLeftSpace();

            for (int i = 0; i < size; ++i) {
                int start = mReplaceList.get(i).first;
                int end = mReplaceList.get(i).second;

                if (start == selectionStart && end == selectionEnd)
                    mPaint.setColor(Color.YELLOW);
                else
                    mPaint.setColor(Color.parseColor("#FFFD54"));

                int line = mGapBuffer.findLineNumber(start);
                int lineStart = getLineStart(line);

                canvas.drawRect(left + measureText(mGapBuffer.substring(lineStart, start)),
                        (line - 1) * getLineHeight(),
                        left + measureText(mGapBuffer.substring(lineStart, end)),
                        line * getLineHeight(),
                        mPaint
                );
            }
        }
    }

    // Draw the cursor and middle handle
    public void drawCursor(Canvas canvas) {
        if (mCursorVisiable) {
            int left = getLeftSpace();
            int half = 0;
            if (mCursorPosX >= left) {
                half = mCursorWidth / 2;
            } else {
                mCursorPosX = left;
            }

            // draw text cursor
            mDrawableCursorRes.setBounds(mCursorPosX - half,
                    getPaddingTop() + mCursorPosY,
                    mCursorPosX - half + mCursorWidth,
                    mCursorPosY + getLineHeight()
            );
            mDrawableCursorRes.draw(canvas);
        }

        if (mHandleMiddleVisable) {
            // draw text select handle middle
            mTextSelectHandleMiddleRes.setBounds(mCursorPosX - handleMiddleWidth / 2,
                    mCursorPosY + getLineHeight(),
                    mCursorPosX + handleMiddleWidth / 2,
                    mCursorPosY + getLineHeight() + handleMiddleHeight
            );
            mTextSelectHandleMiddleRes.draw(canvas);
        }
    }

    // Draw editable text lines with numbers and syntax highlighting

    public void drawEditableText(Canvas canvas) {
        int startLine = Math.max(canvas.getClipBounds().top / getLineHeight(), 1);
        int endLine = Math.min(canvas.getClipBounds().bottom / getLineHeight() + 1, getLineCount());

        int lineNumberWidth = getLineNumberWidth();
        lineWidth = getWidth() - lineNumberWidth;

        int totalContentHeight = getLineCount() * getLineHeight();

        // Draw full-height line number bar background
        mPaint.setColor(Color.parseColor("#F8F8F8"));
        canvas.drawRect(
                getPaddingLeft(),
                0,
                getPaddingLeft() + lineNumberWidth + SPACEING * 2,
                Math.max(getHeight(), totalContentHeight),
                mPaint
        );

        // Draw separator line
        int separatorWidth = 2;
        int separatorX = getPaddingLeft() + lineNumberWidth + SPACEING * 2 - separatorWidth;
        mPaint.setColor(Color.parseColor("#E4E4E4"));
        mPaint.setStrokeWidth(separatorWidth);
        canvas.drawLine(
                separatorX,
                0,
                separatorX,
                Math.max(getHeight(), totalContentHeight),
                mPaint
        );

        // Margins
        int leftMargin = 10; // ✅ Space from left edge for line numbers
        int rightMargin = 13; // Space between line numbers and separator

        for (int i = startLine; i <= endLine; i++) {
            int paintY = i * getLineHeight() - (int) mTextPaint.descent();
            mTextPaint.setColor(Color.parseColor("#B0B0B0"));

            String lineNumberText = String.valueOf(i);
            int textWidth = (int) mTextPaint.measureText(lineNumberText);

            // RIGHT aligned, with left and right margins respected
            int lineNumberX = getPaddingLeft() + leftMargin
                    + (lineNumberWidth - rightMargin - textWidth);

            canvas.drawText(lineNumberText, lineNumberX, paintY, mTextPaint);

            // Draw text content
            int contentStartX = separatorX + separatorWidth + 10;
            String text = getLine(i);
            lineWidth = Math.max(measureText(text), lineWidth);

            if (mHighlighter != null && text != null && !text.isEmpty()) {
                int lineHeight = getLineHeight();
                int top = (i - 1) * lineHeight;
                int bottom = i * lineHeight;
                int left = getPaddingLeft() + getLineNumberWidth() + SPACEING;
                int right = getScrollX() + getWidth();
                // Special Cases, like for smali print method line bg
                // Bugs : You cant see the selected visual when you select method line
                mHighlighter.drawLineBackground(canvas, text, i, left, top, right, bottom);
                // Draw line text
                mHighlighter.drawLineText(canvas, text, i, contentStartX, paintY);
            } else {
                mTextPaint.setColor(Color.BLACK);
                canvas.drawText(text, contentStartX, paintY, mTextPaint);
            }
        }

        mPaint.setColor(Color.parseColor("#FFFAE3"));
        mPaint.setStrokeWidth(0);
    }

    // ---------- Input Handling (touch/keyboard/IME) ----------
    // Handle touch events (dispatch gesture detectors)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Reset auto-hide timer on any touch
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            resetAutoHideTimer();
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mScroller.abortAnimation();
                break;
            case MotionEvent.ACTION_UP:
                mGestureListener.onUp(event);
                break;
        }

        mGestureDetector.onTouchEvent(event);
        mScaleGestureDetector.onTouchEvent(event);
        return true;
    }

    // Reset clipboard auto-hide timer helper
    private void resetAutoHideTimer() {
        if (mClipboardPanel != null && (isSelectMode || mHandleMiddleVisable)) {
            scheduleAutoHide();
        }
    }

    // Handle key down events for editing keys
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEditedMode) return super.onKeyDown(keyCode, event);

        // Skip if we're already processing input to avoid duplicates
        if (mProcessingInput) {
            Log.d(TAG, "Skipping onKeyDown - processing input");
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            mMetaState |= KeyEvent.META_SHIFT_ON;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            mMetaState |= KeyEvent.META_CTRL_ON;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            mMetaState |= KeyEvent.META_ALT_ON;
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "onKeyDown: keyCode=" + keyCode + ", unicode=" + event.getUnicodeChar());

            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    mProcessingInput = true;
                    insert("\n");
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mProcessingInput = false;
                        }
                    }, INPUT_DEBOUNCE_DELAY);
                    return true;

                case KeyEvent.KEYCODE_DEL:
                    mProcessingInput = true;
                    delete();
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mProcessingInput = false;
                        }
                    }, INPUT_DEBOUNCE_DELAY);
                    return true;

                case KeyEvent.KEYCODE_FORWARD_DEL:
                    mProcessingInput = true;
                    handleForwardDelete();
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mProcessingInput = false;
                        }
                    }, INPUT_DEBOUNCE_DELAY);
                    return true;

                case KeyEvent.KEYCODE_SPACE:
                    mProcessingInput = true;
                    insert(" ");
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mProcessingInput = false;
                        }
                    }, INPUT_DEBOUNCE_DELAY);
                    return true;

                case KeyEvent.KEYCODE_TAB:
                    mProcessingInput = true;
                    insert("\t");
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mProcessingInput = false;
                        }
                    }, INPUT_DEBOUNCE_DELAY);
                    return true;

                // Let the input connection handle these to avoid duplicates
                case KeyEvent.KEYCODE_0:
                case KeyEvent.KEYCODE_1:
                case KeyEvent.KEYCODE_2:
                case KeyEvent.KEYCODE_3:
                case KeyEvent.KEYCODE_4:
                case KeyEvent.KEYCODE_5:
                case KeyEvent.KEYCODE_6:
                case KeyEvent.KEYCODE_7:
                case KeyEvent.KEYCODE_8:
                case KeyEvent.KEYCODE_9:
                case KeyEvent.KEYCODE_NUMPAD_0:
                case KeyEvent.KEYCODE_NUMPAD_1:
                case KeyEvent.KEYCODE_NUMPAD_2:
                case KeyEvent.KEYCODE_NUMPAD_3:
                case KeyEvent.KEYCODE_NUMPAD_4:
                case KeyEvent.KEYCODE_NUMPAD_5:
                case KeyEvent.KEYCODE_NUMPAD_6:
                case KeyEvent.KEYCODE_NUMPAD_7:
                case KeyEvent.KEYCODE_NUMPAD_8:
                case KeyEvent.KEYCODE_NUMPAD_9:
                    return false; // Let input connection handle via commitText

                default:
                    // For other keys, let the input connection handle them
                    return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // Handle key up events for modifier keys
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            mMetaState &= ~KeyEvent.META_SHIFT_ON;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            mMetaState &= ~KeyEvent.META_CTRL_ON;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            mMetaState &= ~KeyEvent.META_ALT_ON;
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    // Create input connection for IME
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // TODO: Implement this method
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
                | EditorInfo.IME_ACTION_DONE
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI;

        return new TextInputConnection(this, true);
    }

    // Toggle software keyboard
    public void showSoftInput(boolean show) {
        if (isEditedMode) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (show)
                imm.showSoftInput(this, 0);
            else
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    // ---------- Text and Buffer Operations ----------
    // Set an external buffer
    public void setBuffer(GapBuffer buffer) {
        mGapBuffer = buffer;
        clearSyntaxCache();
        dismissAutoComplete();
        invalidate();
    }

    // Get current buffer
    public GapBuffer getBuffer() {
        return this.mGapBuffer;
    }

    // Set text directly
    public void setText(String text) {
        mGapBuffer = new GapBuffer(text);
        clearSyntaxCache();
        dismissAutoComplete();
        invalidate();
    }

    // Set font size in px with bounds and adjust scroll to keep relative position
    public void setTextSize(float px) {
        float min = ScreenUtils.dip2px(getContext(), 10);
        float max = ScreenUtils.dip2px(getContext(), 30);

        if (px < min) px = min;
        if (px > max) px = max;

        if (px == mTextPaint.getTextSize()) return;

        int currentScrollY = getScrollY();
        int currentLine = Math.max(1, currentScrollY / getLineHeight());
        float lineFraction = (float) (currentScrollY % getLineHeight()) / getLineHeight();

        mTextPaint.setTextSize(px);

        adjustCursorPosition();
        if (isSelectMode) {
            adjustSelectRange(selectionStart, selectionEnd);
        }

        // Update magnifier position if active
        if (mIsMagnifierShowing && mMagnifierEnabled) {
            // updateMagnifier(mCursorPosX, mCursorPosY + getLineHeight());
        }

        int newLineHeight = getLineHeight();
        int newScrollY = (int) (currentLine * newLineHeight + lineFraction * newLineHeight);
        newScrollY = Math.max(0, Math.min(newScrollY, getMaxScrollY()));
        int newScrollX = getScrollX();

        scrollTo(newScrollX, newScrollY);
        postInvalidate();
    }

    // Toggle edit mode
    public void setEditedMode(boolean editMode) {
        isEditedMode = editMode;
    }

    // Get edit mode
    public boolean getEditedMode() {
        return isEditedMode;
    }

    // Set typeface
    public void setTypeface(Typeface typeface) {
        mTextPaint.setTypeface(typeface);
    }

    // Set listener for text change
    public void setOnTextChangedListener(OnTextChangedListener listener) {
        mTextListener = listener;
    }

    // Return left padding + line number width

    public int getLeftSpace() {
        int lineNumberWidth = getLineNumberWidth();
        int separatorWidth = 2;
        int contentPadding = 10;

        return getPaddingLeft() + lineNumberWidth + SPACEING * 2 + separatorWidth + contentPadding;
    }

    // Measure text width
    public int measureText(String text) {
        return (int) Math.ceil(mTextPaint.measureText(text));
    }

    // Get single line height in px
    public int getLineHeight() {
        TextPaint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        return metrics.bottom - metrics.top;
    }

    // Get the start offset of a line
    private int getLineStart(int lineNumber) {
        return mGapBuffer.getLineOffset(lineNumber);
    }

    // Find which line an offset is on
    private int getOffsetLine(int offset) {
        return mGapBuffer.findLineNumber(offset);
    }

    // Get text size px
    public float getTextSize() {
        return mTextPaint.getTextSize();
    }

    // Get number of lines
    public int getLineCount() {
        return mGapBuffer.getLineCount();
    }

    // Width needed to draw line numbers
    private int getLineNumberWidth() {
        return measureText(Integer.toString(getLineCount()));
    }

    // Helper used by clipboard panel to show selection
    public void selectText(boolean enable) {
        if (!enable) clearSelectionMenu();
    }

    // Get bounding box for caret at index (for popup positioning)
    public Rect getBoundingBox(int index) {
        int left = getLeftSpace();
        int line = getOffsetLine(index);
        int lineStart = getLineStart(line);
        String text = mGapBuffer.substring(lineStart, Math.min(index, mGapBuffer.length()));
        int x = left + measureText(text);
        int y = (line - 1) * getLineHeight();

        // Translate for scroll and padding
        int viewX = x - getScrollX() + getPaddingLeft();
        int viewY = y - getScrollY() + getPaddingTop();

        // Make sure panel appears above caret line
        return new Rect(viewX, viewY - getLineHeight() * 2, viewX + getLineHeight(), viewY);
    }

    // Get caret index
    public int getCaretPosition() {
        return mCursorIndex;
    }

    // Get selected text string
    public String getSelectedText() {
        if (isSelectMode && selectionStart < selectionEnd) {
            return mGapBuffer.substring(selectionStart, selectionEnd);
        }
        return "";
    }

    // Insert text at caret, with auto-indent, autocomplete, and blinking handling
    public void insertText(String text) {
        if (text != null) {
            mGapBuffer.insert(mCursorIndex, text, true);
            mCursorIndex += text.length();
            adjustCursorPosition();
            scrollToVisable();
            postInvalidate();
        }
    }

    // Selection getters
    public int getSelectionStart() {
        return selectionStart;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    // Delete selected text if in selection mode
    public void deleteSelectedText() {
        if (isSelectMode && selectionStart < selectionEnd) {
            mGapBuffer.delete(selectionStart, selectionEnd, true);
            mCursorIndex = selectionStart;
            isSelectMode = false;
            adjustCursorPosition();
            scrollToVisable();
            dismissAutoComplete();
            postDelayed(mWordUpdateRunnable, WORD_UPDATE_DELAY);
            postInvalidate();
        }
    }

    // Select word at caret
    public void selectWordAtCursor() {
        if (mGapBuffer.length() == 0) return;

        int start = mCursorIndex;
        int end = mCursorIndex;

        // Find word start
        while (start > 0 && Character.isJavaIdentifierPart(mGapBuffer.charAt(start - 1))) {
            start--;
        }

        // Find word end
        while (end < mGapBuffer.length() && Character.isJavaIdentifierPart(mGapBuffer.charAt(end))) {
            end++;
        }

        if (start < end) {
            selectionStart = start;
            selectionEnd = end;
            isSelectMode = true;
            adjustSelectRange(start, end);
            postInvalidate();
        }
    }

    // Move caret to next word start
    public void moveToNextWord() {
        if (mCursorIndex >= mGapBuffer.length()) return;

        int newPos = mCursorIndex;

        // Skip current word if we're in one
        while (newPos < mGapBuffer.length() && Character.isJavaIdentifierPart(mGapBuffer.charAt(newPos))) {
            newPos++;
        }

        // Skip non-word characters
        while (newPos < mGapBuffer.length() && !Character.isJavaIdentifierPart(mGapBuffer.charAt(newPos))) {
            newPos++;
        }

        setCursorPosition(newPos);
        scrollToVisable();
        postInvalidate();
    }

    // Move caret to previous word start
    public void moveToPreviousWord() {
        if (mCursorIndex <= 0) return;

        int newPos = mCursorIndex - 1;

        // Skip non-word characters backwards
        while (newPos > 0 && !Character.isJavaIdentifierPart(mGapBuffer.charAt(newPos))) {
            newPos--;
        }

        // Skip word characters backwards
        while (newPos > 0 && Character.isJavaIdentifierPart(mGapBuffer.charAt(newPos - 1))) {
            newPos--;
        }

        setCursorPosition(newPos);
        scrollToVisable();
        postInvalidate();
    }

    // Select all text
    public void selectAll() {
        removeCallbacks(blinkAction);
        mCursorVisiable = false;
        mHandleMiddleVisable = false; // ← CHANGE TO FALSE
        isSelectMode = true;

        // at first index
        selectionStart = 0;
        // at last index
        selectionEnd = mGapBuffer.length();

        // set handle left at first position
        selectHandleLeftX = getLeftSpace();
        selectHandleLeftY = getLineHeight();

        // set handle right at last position
        selectHandleRightX = getLeftSpace() + getLineWidth(getLineCount());
        selectHandleRightY = getLineCount() * getLineHeight();

        // set cursor index and position
        setCursorPosition(selectionEnd);

        if (!mReplaceList.isEmpty())
            mReplaceList.clear();
        smoothScrollTo(0, Math.max(getLineCount() * getLineHeight() - getHeight() + getLineHeight() * 2, 0));
        showTextSelectionWindow();
        postInvalidate();
    }

    // Helper method to check if all texts are selected
    public boolean isAllTextSelected() {
        return selectionStart == 0 &&
                selectionEnd == mGapBuffer.length() &&
                selectionStart != selectionEnd &&
                mGapBuffer.length() > 0;
    }

    // Clear selection and related UI
    public void clearSelectionMenu() {
        isSelectMode = false;
        mHandleMiddleVisable = false;
        mIsLineSelectionMode = false;
        mStartSelectionLine = -1;
        mEndSelectionLine = -1;
        mFirstSelectedLine = -1;
        mSecondSelectedLine = -1;
        mWaitingForSecondSelection = false;
        mSelectionHandler.removeCallbacks(mClearSelectionRunnable);
        dismissAutoComplete();
        onCursorOrSelectionChanged();
        postInvalidate();
    }

    // Check selection mode
    public boolean isSelectMode() {
        return isSelectMode;
    }

    // Check if focused on line number area
    private boolean isInLineNumberArea(float x, float y) {
        int lineNumberWidth = getLineNumberWidth() + SPACEING * 2;
        return x >= getPaddingLeft() && x <= getPaddingLeft() + lineNumberWidth;
    }

    // Get line from Y dir.
    private int getLineFromY(float y) {
        int line = (int) (y / getLineHeight()) + 1;
        return Math.max(1, Math.min(line, getLineCount()));
    }

    // Get raw line string
    public String getLine(int lineNumber) {
        return mGapBuffer.getLine(lineNumber);
    }

    // Width of specified line text
    private int getLineWidth(int lineNumber) {
        return measureText(getLine(lineNumber));
    }

    // Width of lines
    public int getLineWidth() {
        return lineWidth;
    }

    // Width of space
    public int getSpaceWidth() {
        return spaceWidth;
    }

    // Get maximum scrollable X
    public int getMaxScrollX() {
        return Math.max(0, getLeftSpace() + lineWidth + spaceWidth * 4 - getWidth());
    }

    // Get maximum scrollable Y
    public int getMaxScrollY() {
        return Math.max(0, (getLineCount() + 2) * getLineHeight() - getHeight());
    }

    // ---------- Magnifier ----------
    // Enable or disable magnifier usage, only for Android 8+
    public void setMagnifierEnabled(boolean enabled) {
        mMagnifierEnabled = enabled;
        if (!enabled && mIsMagnifierShowing) {
            dismissMagnifier();
        }
    }

    // Check magnifier enabled
    public boolean isMagnifierEnabled() {
        return mMagnifierEnabled;
    }

    // Show magnifier centered near content coords
    private void showMagnifier(float x, float y) {
        if (!mMagnifierEnabled || mMagnifier == null) return;
        try {
            hideTextSelectionWindow();
            // Convert content coordinates to screen coordinates
            float adjustedX = x - getScrollX();
            float adjustedY = y - getScrollY();

            // Adjust Y to center magnifier on the current line's text
            float lineHeight = getLineHeight();
            adjustedY -= lineHeight * 0.5f; // Center on the current line (baseline)

            // Account for padding and canvas translation
            adjustedY += getPaddingTop();

            // Ensure magnifier stays within view bounds
            adjustedX = Math.max(50, Math.min(adjustedX, getWidth() - 50));
            adjustedY = Math.max(50, Math.min(adjustedY, getHeight() - 50));

            // Debug logging
            Log.d(TAG, "showMagnifier: x=" + adjustedX + ", y=" + adjustedY + ", rawX=" + x + ", rawY=" + y + ", scrollY=" + getScrollY() + ", lineHeight=" + lineHeight);

            mMagnifier.show((int) adjustedX, (int) adjustedY);
            mMagnifierX = adjustedX;
            mMagnifierY = adjustedY;
            mIsMagnifierShowing = true;
        } catch (Exception e) {
            Log.e(TAG, "Error showing magnifier: " + e.getMessage());
            dismissMagnifier();
        }
    }

    // Update magnifier position smoothly
    private void updateMagnifier(float x, float y) {
        if (!mIsMagnifierShowing || !mMagnifierEnabled || mMagnifier == null) return;
        try {
            // Convert content coordinates to screen coordinates
            float adjustedX = x - getScrollX();
            float adjustedY = y - getScrollY();

            // Adjust Y to center magnifier on the current line's text
            float lineHeight = getLineHeight();
            adjustedY -= lineHeight * 0.5f; // Center on the current line (baseline)

            // Account for padding and canvas translation
            adjustedY += getPaddingTop();

            // Ensure magnifier stays within view bounds
            adjustedX = Math.max(50, Math.min(adjustedX, getWidth() - 50));
            adjustedY = Math.max(50, Math.min(adjustedY, getHeight() - 50));

            // Smooth update only if significant movement
            if (Math.abs(adjustedX - mMagnifierX) > 1 || Math.abs(adjustedY - mMagnifierY) > 1) {
                Log.d(TAG, "updateMagnifier: x=" + adjustedX + ", y=" + adjustedY + ", rawX=" + x + ", rawY=" + y + ", scrollY=" + getScrollY() + ", lineHeight=" + lineHeight);

                mMagnifier.show((int) adjustedX, (int) adjustedY);
                mMagnifierX = adjustedX;
                mMagnifierY = adjustedY;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating magnifier: " + e.getMessage());
            dismissMagnifier();
        }
    }

    // Dismiss magnifier if visible
    private void dismissMagnifier() {
        if (mIsMagnifierShowing && mMagnifier != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    mMagnifier.dismiss();
                    mIsMagnifierShowing = false;
                } catch (Exception e) {
                    Log.e(TAG, "Error dismissing magnifier: " + e.getMessage());
                }
            }
        }
    }

    // Syntax Helpers
    public void setSyntaxLanguageFileName(String languageFile) {
        mHighlighter = new MHSyntaxHighlightEngine(getContext(), mTextPaint, languageFile, isSyntaxDarkMode);
    }

    public void setSyntaxDarkMode(boolean isDark) {
        isSyntaxDarkMode = isDark;
    }

    // ---------- Scrolling Helpers ----------
    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    public final void smoothScrollBy(int dx, int dy) {
        if (getHeight() == 0) {
            // Nothing to do.
            return;
        }
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration > DEFAULT_DURATION) {
            mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    public final void smoothScrollTo(int x, int y) {
        smoothScrollBy(x - getScrollX(), y - getScrollY());
    }

    // Compute fling/scroll animation progress
    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }

    // Scroll to ensure caret is visible with margins
    private void scrollToVisable() {
        // Only scroll if cursor is significantly out of view
        int dx = 0;
        int leftMargin = spaceWidth * 3;
        int rightMargin = screenWidth - spaceWidth * 2;

        if (mCursorPosX - getScrollX() < leftMargin) {
            dx = mCursorPosX - getScrollX() - leftMargin;
        } else if (mCursorPosX - getScrollX() > rightMargin) {
            dx = mCursorPosX - getScrollX() - rightMargin;
        }

        int dy = 0;
        int topMargin = getLineHeight();
        int bottomMargin = getHeight() - getLineHeight();

        if (mCursorPosY - getScrollY() < topMargin) {
            dy = mCursorPosY - getScrollY() - topMargin;
        } else if (mCursorPosY - getScrollY() > bottomMargin) {
            dy = mCursorPosY - getScrollY() - bottomMargin;
        }

        // Only scroll if necessary
        if (dx != 0 || dy != 0) {
            smoothScrollBy(dx, dy);
        }
    }

    // ---------- Syntax / Highlights ----------
    // Clear syntax highlight cache
    private void clearSyntaxCache() {
        if (mHighlighter != null) {
            mHighlighter.clearCache();
        }
    }

    // Call this method whenever the text content changes
    public void onTextChanged() {
        clearSyntaxCache(); // keep cache fresh
        mTextListener.onTextChanged();
    }

    // ---------- Insert / Delete with handling ----------
    // Insert text with auto-indent, autocomplete and selection handling
    private void insert(String text) {
        if (!isEditedMode) return; // nothing to do
        if (isSelectMode) {
            mGapBuffer.beginBatchEdit();
            delete();
        }

        if (text.equals("\n") && mAutoIndentEnabled) {
            String indent = getAutoIndent();
            if (!indent.isEmpty()) {
                text = "\n" + indent;
            }
        }

        removeCallbacks(blinkAction);
        mCursorVisiable = true;
        mHandleMiddleVisable = false;

        mCurrentPrefix = getCurrentPrefix();

        mGapBuffer.insert(mCursorIndex, text, true);

        // Handle auto-complete
        if (!text.trim().isEmpty()) {
            if (!mCurrentPrefix.isEmpty()) {
                Log.d(TAG, "Prefix: " + mCurrentPrefix);
                filterAndShowSuggestions(mCurrentPrefix + text);
            } else {
                dismissAutoComplete();
            }
        } else {
            dismissAutoComplete();
        }

        if (mGapBuffer.isBatchEdit())
            mGapBuffer.endBatchEdit();

        // calculate the cursor index and line
        int length = text.length();
        mCursorIndex += length;
        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();

        onTextChanged();
        scrollToVisable();
        postInvalidate();
        postDelayed(blinkAction, BLINK_TIMEOUT);
    }

    // Insert words from typed text to word set
    private void extractWordsFromText(String newText) {
        if (newText == null || newText.isEmpty()) return;
        mWordSet.add(newText); // Fast O(1) operation
    }

    // Delete char before caret or currently selected range
    public void delete() {
        if (!isEditedMode) return; // nothing to do
        if (mCursorIndex <= 0) return;

        removeCallbacks(blinkAction);
        mCursorVisiable = true;
        mHandleMiddleVisable = false;

        if (isSelectMode) {
            isSelectMode = false;
            mGapBuffer.delete(selectionStart, selectionEnd, true);
            mCursorIndex -= selectionEnd - selectionStart;
        } else {
            mGapBuffer.delete(mCursorIndex - 1, mCursorIndex, true);
            mCursorIndex--;
        }

        // calculate cursor index and line
        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();
        onCursorOrSelectionChanged();

        onTextChanged();
        scrollToVisable();
        postInvalidate();
        postDelayed(blinkAction, BLINK_TIMEOUT);
    }

    // Handle forward delete (placeholder call used earlier)
    private void handleForwardDelete() {
        if (!isEditedMode) return;
        if (mCursorIndex >= mGapBuffer.length()) return;

        removeCallbacks(blinkAction);
        mCursorVisiable = true;
        mHandleMiddleVisable = false;

        if (isSelectMode) {
            isSelectMode = false;
            mGapBuffer.delete(selectionStart, selectionEnd, true);
            mCursorIndex = selectionStart;
        } else {
            mGapBuffer.delete(mCursorIndex, mCursorIndex + 1, true);
            // Cursor index stays the same when forward deleting
        }

        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();

        onCursorOrSelectionChanged();

        clearSyntaxCache();
        onTextChanged();
        scrollToVisable();

        mCurrentPrefix = getCurrentPrefix();
        if (mCurrentPrefix.isEmpty()) {
            dismissAutoComplete();
        } else {
            filterAndShowSuggestions(mCurrentPrefix);
        }

        postInvalidate();
        postDelayed(blinkAction, BLINK_TIMEOUT);
    }

    // ---------- Clipboard (copy/cut/paste/share) ----------
    // Copy selected text to clipboard
    public void copy() {
        String text = getSelectedText();
        if (text != null && !text.equals("")) {
            ClipData data = ClipData.newPlainText("content", text);
            mClipboard.setPrimaryClip(data);
        }
    }

    // Cut selected text to clipboard (copy then delete)
    public void cut() {
        copy();
        delete();
        isSelectMode = false;
    }

    // Paste from clipboard at caret
    public void paste() {
        if (mClipboard.hasPrimaryClip()) {
            ClipDescription description = mClipboard.getPrimaryClipDescription();
            if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                ClipData data = mClipboard.getPrimaryClip();
                ClipData.Item item = data.getItemAt(0);
                String text = item.getText().toString();
                insert(text);
            }
        }
    }

    // Share currently selected text via ACTION_SEND
    public void shareText() {
        if (isSelectMode) {
            String selectedText = getSelectedText();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, selectedText);
            getContext().startActivity(Intent.createChooser(shareIntent, "Share text"));
        }
    }

    // ---------- Search / Replace operations ----------
    // Scroll to the found match position
    private void scrollToFindPosition(int curr) {
        int first = mReplaceList.get(curr).first;
        int second = mReplaceList.get(curr).second;

        setCursorPosition(second);
        adjustSelectRange(first, second);

        smoothScrollTo(Math.max(0, selectHandleLeftX - getWidth() / 2),
        Math.max(0, selectHandleLeftY - getHeight() / 2));
        postInvalidate();
    }

    // Find current replace list index for selectionStart/End via binary search
    private int current() {
        // Comparator implementation
        Comparator<Pair<Integer, Integer>> comparator = new Comparator<Pair<Integer, Integer>>() {
            @Override
            public int compare(Pair<Integer, Integer> a, Pair<Integer, Integer> b) {
                int result = a.first - b.first;
                return result == 0 ? a.second - b.second : result;
            }
        };

        // binarySearch using the comparator
        return Collections.binarySearch(mReplaceList,
                new Pair<Integer, Integer>(selectionStart, selectionEnd),
                comparator);
    }

    // Move to previous match
    public void prev() {
        int currIndex = current();
        int prev = --currIndex;
        if (prev < 0) {
            prev = mReplaceList.size() - 1;
        }
        scrollToFindPosition(prev);
    }

    // Move to next match
    public void next() {
        int currIndex = current();
        int next = ++currIndex;
        if (next >= mReplaceList.size()) {
            next = 0;
        }
        scrollToFindPosition(next);
    }

    // Find all matches for regex in buffer
    public void find(String regex) {
        if (!mReplaceList.isEmpty())
            mReplaceList.clear();

        Matcher matcher = Pattern.compile(regex).matcher(mGapBuffer.toString());

        while (matcher.find()) {
            mReplaceList.add(new Pair<Integer, Integer>(matcher.start(), matcher.end()));
        }
    }

    // Replace first match
    public void replaceFirst(String replacement) {
        if (!mReplaceList.isEmpty() && isEditedMode) {
            int start = mReplaceList.get(0).first;
            int end = mReplaceList.get(0).second;

            mGapBuffer.beginBatchEdit();
            mGapBuffer.replace(start, end, replacement, true);
            mGapBuffer.endBatchEdit();

            int length = replacement.length();
            setCursorPosition(start + length);
            adjustSelectRange(start + length, start + length);

            // remove the first item
            mReplaceList.remove(0);

            int delta = start + length - end;
            // do not use the find(regex) method to re-find
            // recalculate replace list by index
            for (int i = 0; i < mReplaceList.size(); ++i) {
                int first = (int) mReplaceList.get(i).first + delta;
                int second = (int) mReplaceList.get(i).second + delta;
                mReplaceList.set(i, new Pair<Integer, Integer>(first, second));
            }
        } else {
            // if the replace Lists is empty
            // set the select mode false
            isSelectMode = false;
        }
        postInvalidate();
    }

    // Replace all matches iteratively
    public void replaceAll(String replacement) {
        while (!mReplaceList.isEmpty() && isEditedMode) {
            replaceFirst(replacement);
        }
    }

    // ---------- Cursor positioning ----------
    // Goto line number (1-based)
    public void gotoLine(int line) {
        line = Math.min(Math.max(line, 1), getLineCount());

        // Clear any active selection to cancel it before navigating
        if (isSelectMode) {
            clearSelectionMenu();
        }

        mCursorIndex = getLineStart(line);
        mCursorLine = line;
        mCursorPosX = getLeftSpace();
        mCursorPosY = (line - 1) * getLineHeight();

        smoothScrollTo(0, Math.max(line * getLineHeight() - getHeight() + getLineHeight() * 2, 0));
        postInvalidate(); // Ensure immediate redraw after clearing selection
    }

    // Check undo available
    public boolean canUndo() {
        return mGapBuffer.canUndo();
    }

    // Check redo available
    public boolean canRedo() {
        return mGapBuffer.canRedo();
    }

    // Undo operation and restore cursor
    public void undo() {
        int index = mGapBuffer.undo();
        if (index >= 0) {
            mCursorIndex = index;
            mCursorLine = getOffsetLine(index);
            adjustCursorPosition();
            onTextChanged();
            scrollToVisable();
        }
    }

    // Redo operation and restore cursor
    public void redo() {
        int index = mGapBuffer.redo();
        if (index >= 0) {
            mCursorIndex = index;
            mCursorLine = getOffsetLine(index);
            adjustCursorPosition();
            onTextChanged();
            scrollToVisable();
        }
    }

    // ---------- Selection handle updates ----------
    // Update selection handle screen coordinates and caret
    private void updateSelectionHandles() {
        if (!isSelectMode) return;

        int left = getLeftSpace();

        // Start handle
        int startLine = getOffsetLine(selectionStart);
        int lineStart = getLineStart(startLine);
        String startText = mGapBuffer.substring(lineStart, Math.min(selectionStart, mGapBuffer.length()));
        selectHandleLeftX = left + (int) mTextPaint.measureText(startText);
        selectHandleLeftY = startLine * getLineHeight();

        // End handle
        int endLine = getOffsetLine(selectionEnd);
        lineStart = getLineStart(endLine);
        String endText = mGapBuffer.substring(lineStart, Math.min(selectionEnd, mGapBuffer.length()));
        selectHandleRightX = left + (int) mTextPaint.measureText(endText);
        selectHandleRightY = endLine * getLineHeight();

        // Update middle handle position
        mCursorPosX = (selectHandleLeftX + selectHandleRightX) / 2;
        mCursorPosY = (selectHandleLeftY + selectHandleRightY) / 2;
    }

    // Adjust cursor's screen coordinates based on mCursorLine and mCursorIndex
    private void adjustCursorPosition() {
        int start = getLineStart(mCursorLine);
        String text = mGapBuffer.substring(start, mCursorIndex);

        // Use precise text measurement with the current text paint
        mCursorPosX = getLeftSpace() + (int) mTextPaint.measureText(text);
        mCursorPosY = (mCursorLine - 1) * getLineHeight();

        // Ensure cursor stays within bounds
        if (mCursorPosX < getLeftSpace()) {
            mCursorPosX = getLeftSpace();
        }
    }

    // Adjust selected range and update handles
    public void adjustSelectRange(int start, int end) {
        selectionStart = start;
        selectionEnd = end;
        updateSelectionHandles(); // Call the new method
        onCursorOrSelectionChanged();
    }

    // ---------- Auto-indent ----------
    // Get current line's leading whitespace for auto-indent
    private String getAutoIndent() {
        if (!mAutoIndentEnabled || mCursorIndex == 0) return "";

        try {
            // Find the start of current line
            int lineStart = getLineStart(mCursorLine);
            if (lineStart < 0 || lineStart >= mGapBuffer.length()) return "";

            String currentLine = mGapBuffer.substring(lineStart, Math.min(mCursorIndex, mGapBuffer.length()));

            // Count leading spaces/tabs
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < currentLine.length(); i++) {
                char c = currentLine.charAt(i);
                if (c == ' ' || c == '\t') {
                    indent.append(c);
                } else {
                    break;
                }
            }

            return indent.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error in getAutoIndent: " + e.getMessage());
            return "";
        }
    }

    // Set cursor position by index and adjust coordinates
    private void setCursorPosition(int index) {
        // calculate the cursor index and position
        mCursorIndex = index;
        mCursorLine = getOffsetLine(index);

        String text = mGapBuffer.substring(getLineStart(mCursorLine), index);
        int width = measureText(text);
        mCursorPosX = getLeftSpace() + width;
        mCursorPosY = (mCursorLine - 1) * getLineHeight();
    }

    // Set cursor position by pixel coordinates and compute nearest index
    public void setCursorPosition(float x, float y) {
        // calculation the cursor y coordinate
        mCursorPosY = (int) y / getLineHeight() * getLineHeight();
        int bottom = getLineCount() * getLineHeight();

        if (mCursorPosY < getPaddingTop())
            mCursorPosY = getPaddingTop();

        if (mCursorPosY > bottom - getLineHeight())
            mCursorPosY = bottom - getLineHeight();

        // estimate the cursor x position
        int left = getLeftSpace();

        int prev = left;
        int next = left;

        mCursorLine = mCursorPosY / getLineHeight() + 1;
        mCursorIndex = getLineStart(mCursorLine);

        String text = getLine(mCursorLine);
        int length = text.length();

        float[] widths = new float[length];
        mTextPaint.getTextWidths(text, widths);

        for (int i = 0; next < x && i < length; ++i) {
            if (i > 0) {
                prev += widths[i - 1];
            }
            next += widths[i];
        }
        onCursorOrSelectionChanged();

        // calculation the cursor x coordinate
        if (Math.abs(x - prev) <= Math.abs(next - x)) {
            mCursorPosX = prev;
        } else {
            mCursorPosX = next;
        }

        // calculation the cursor index
        if (mCursorPosX > left) {
            for (int j = 0; left < mCursorPosX && j < length; ++j) {
                left += widths[j];
                ++mCursorIndex;
            }
        }
    }

    // Called when cursor or selection changed (placeholder for external UI)
    private void onCursorOrSelectionChanged() {
        scheduleSelectionUpdate();
        // Auto-hide after 5 seconds if no interaction
        if (mClipboardPanel != null) {
            mSelectionHandler.removeCallbacks(mAutoHideRunnable);
            mSelectionHandler.postDelayed(mAutoHideRunnable, 5000); // 5 seconds
        }
    }

    // ---------- Autocomplete / Word extraction ----------
    // Update word set from buffer asynchronously
    private void updateWordSet() {
        removeCallbacks(mWordUpdateRunnable);
        if (!isEditedMode) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Off UI for large files
                String fullText = mGapBuffer.toString();
                final Set<String> words = new HashSet<>();
                java.util.regex.Matcher matcher = WORD_PATTERN.matcher(fullText);

                while (matcher.find()) {
                    String word = matcher.group();
                    if (word.length() >= MIN_WORD_LEN) {
                        words.add(word);
                    }
                }

                post(new Runnable() {
                    @Override
                    public void run() {
                        // Back to UI
                        mWordSet = words;
                        if (!mCurrentPrefix.isEmpty()) {
                            showAutoComplete(mCurrentPrefix);
                        }
                    }
                });
            }
        }).start();
    }

    // Get the current identifier-like prefix near caret
    private String getCurrentPrefix() {
        if (mCursorIndex <= 0) return "";

        // Don't convert entire buffer to string - work with the buffer directly
        int start = mCursorIndex;

        // Move backwards to find the start of the word
        while (start > 0) {
            char prevChar = mGapBuffer.charAt(start - 1);
            if (!(Character.isLetterOrDigit(prevChar) || prevChar == '_')) {
                break;
            }
            start--;
        }

        // Extract only the needed substring
        if (start < mCursorIndex) {
            return mGapBuffer.substring(start, mCursorIndex);
        }

        return "";
    }

    // Filter word set and show suggestions
    private void filterAndShowSuggestions(String prefix) {
        if (prefix.isEmpty()) {
            dismissAutoComplete();
            return;
        }

        List<String> priorityList = new ArrayList<>();
        List<String> containsList = new ArrayList<>();

        for (String word : mWordSet) {
            String lowerWord = word.toLowerCase();
            String lowerPrefix = prefix.toLowerCase();

            if (lowerWord.startsWith(lowerPrefix) && !word.equals(prefix)) {
                priorityList.add(word);
            } else if (lowerWord.contains(lowerPrefix) && !word.equals(prefix)) {
                containsList.add(word);
            }
        }

        List<String> suggestions = new ArrayList<>(priorityList);
        suggestions.addAll(containsList);

        if (suggestions.isEmpty()) {
            dismissAutoComplete();
            return;
        }

        // If list is already shown, just update silently
        if (mAutoCompletePopup.isShowing()) {
            mAutoCompleteAdapter.clear();
            mAutoCompleteAdapter.addAll(suggestions);
            mAutoCompleteAdapter.notifyDataSetChanged();
        } else {
            mAutoCompleteAdapter.clear();
            mAutoCompleteAdapter.addAll(suggestions);
            mAutoCompleteAdapter.notifyDataSetChanged();
            showAutoComplete(prefix);
        }
    }

    // Show the autocomplete popup near the caret
    private void showAutoComplete(String prefix) {
        Rect cursorRect = getBoundingBox(mCursorIndex);

        hideTextSelectionWindow();

        // Calculate full width with margin
        int margin = (int) (getResources().getDisplayMetrics().density * 8); // 8dp margin on each
        // side
        int popupWidth = getWidth() - (margin * 2);

        // Dynamically size the height — wrap up to 4 visible items
        int itemHeight = (int) (getResources().getDisplayMetrics().density * 40); // ≈40dp per item
        int visibleCount = Math.min(mAutoCompleteAdapter.getCount(), 4);
        int popupHeight = visibleCount == 0 ? 0 : itemHeight * visibleCount;
        if (popupHeight == 0) popupHeight = ListPopupWindow.WRAP_CONTENT;

        // If already visible, only update position and size — don’t recreate (prevents flicker)
        if (mAutoCompletePopup.isShowing()) {
            mAutoCompletePopup.setHeight(popupHeight);
            mAutoCompleteAdapter.notifyDataSetChanged();
            return;
        }

        // Configure initial show
        mAutoCompletePopup.setWidth(popupWidth);
        mAutoCompletePopup.setHeight(popupHeight);
        mAutoCompletePopup.setAnchorView(this);
        mAutoCompletePopup.setModal(false); // Allow typing
        mAutoCompletePopup.setBackgroundDrawable(
                getResources().getDrawable(android.R.drawable.dialog_holo_light_frame)
        );

        // Offset popup slightly below cursor, centered with margins
        mAutoCompletePopup.setHorizontalOffset(margin);
        mAutoCompletePopup.setVerticalOffset(cursorRect.bottom + (int) (getLineHeight() * 0.6f));

        mAutoCompletePopup.show();
        mCurrentPrefix = prefix;
    }

    // Replace current prefix with a chosen completion
    private void replacePrefixWithWord(String fullWord) {
        String prefix = getCurrentPrefix();
        if (prefix.isEmpty()) {
            insertText(fullWord); // Insert if no prefix
            return;
        }

        int prefixStart = mCursorIndex - prefix.length();
        mGapBuffer.replace(prefixStart, mCursorIndex, fullWord, true); // Replace via GapBuffer
        mCursorIndex = prefixStart + fullWord.length();
        adjustCursorPosition();
        scrollToVisable();
        postInvalidate();
        onTextChanged();
    }

    // Dismiss autocomplete popup
    private void dismissAutoComplete() {
        if (mAutoCompletePopup.isShowing()) {
            mAutoCompletePopup.dismiss();
        }
        mCurrentPrefix = "";
    }

    // ---------- Selection UI / Clipboard Panel ----------
    // Placeholder: show text selection window (clipboard panel)
    public void showTextSelectionWindow() {
        if (mClipboardPanel != null && (isSelectMode || mHandleMiddleVisable)) {
            post(new Runnable() {
                @Override
                public void run() {
                    Rect optimalRect = getOptimalClipboardPosition();
                    mClipboardPanel.showAtLocation(optimalRect);

                    // Schedule auto-hide
                    scheduleAutoHide();
                }
            });
        }
    }

    // Hide text selection window (clipboard panel)
    public void hideTextSelectionWindow() {
        if (mClipboardPanel != null) {
            // Cancel any pending auto-hide
            mSelectionHandler.removeCallbacks(mAutoHideRunnable);

            post(new Runnable() {
                @Override
                public void run() {
                    mClipboardPanel.hide();
                }
            });
        }
    }

    // Schedule auto-hide for selection UI
    private void scheduleAutoHide() {
        mSelectionHandler.removeCallbacks(mAutoHideRunnable);
        if (!isSelectMode && !mHandleMiddleVisable) {
            mSelectionHandler.postDelayed(mAutoHideRunnable, 3000); // 3 seconds for normal taps
        } else {
            mSelectionHandler.postDelayed(mAutoHideRunnable, 5000); // 5 seconds for selections
        }
    }

    private Rect getOptimalClipboardPosition() {
        if (isSelectMode) {
            // For selections, position near the middle using your direct variables
            int middle = (selectionStart + selectionEnd) / 2;
            Rect middleRect = getBoundingBox(middle);
            if (middleRect != null) {
                // Position above selection middle
                middleRect.top -= getLineHeight() * 3;
                middleRect.bottom = middleRect.top + getLineHeight();
                return middleRect;
            }
        }

        // For cursor, position above cursor
        Rect cursorRect = getBoundingBox(mCursorIndex);
        if (cursorRect != null) {
            cursorRect.top -= getLineHeight() * 3;
            cursorRect.bottom = cursorRect.top + getLineHeight();
            return cursorRect;
        }

        return null; // Let ClipboardPanel calculate automatically
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (!gainFocus) dismissAutoComplete();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
        MeasureSpec.getSize(heightMeasureSpec));
    }

    // ---------- Gesture related helpers ----------
    // Auto scroll select handle and cursor when dragging near edge
    private void onMove(int slopX, int slopY) {
        int dx = 0;

        if (mGapBuffer == null || mGapBuffer.length() == 0) {
            return;
        }

        if (mCursorPosX - getScrollX() <= slopX) {
            if (mCursorIndex > 0 && mCursorIndex - 1 < mGapBuffer.length()) {
                try {
                    char prevChar = mGapBuffer.charAt(mCursorIndex - 1);
                    dx = -measureText(String.valueOf(prevChar));
                } catch (Exception e) {
                    dx = -spaceWidth;
                }
            }
        } else if (mCursorPosX - getScrollX() >= screenWidth - slopX) {
            if (mCursorIndex >= 0 && mCursorIndex < mGapBuffer.length()) {
                try {
                    char nextChar = mGapBuffer.charAt(mCursorIndex);
                    dx = measureText(String.valueOf(nextChar));
                } catch (Exception e) {
                    dx = spaceWidth;
                }
            } else if (mCursorIndex == mGapBuffer.length()) {
                dx = spaceWidth;
            }
        }

        if (getHeight() > screenHeight / 2) {
            slopY = slopY * 3;
        }

        int dy = 0;
        if (mCursorPosY - getScrollY() <= 0) {
            dy = -getLineHeight();
        } else if (mCursorPosY - getScrollY() >= getHeight() - slopY) {
            dy = getLineHeight();
        }

        int newScrollX = getScrollX() + dx;
        int newScrollY = getScrollY() + dy;

        newScrollX = Math.max(0, Math.min(newScrollX, getMaxScrollX()));
        newScrollY = Math.max(0, Math.min(newScrollY, getMaxScrollY()));

        smoothScrollTo(newScrollX, newScrollY);

        // Update magnifier during auto-scroll
        if (mIsMagnifierShowing && mMagnifierEnabled) {
            updateMagnifier(mCursorPosX, mCursorPosY + getLineHeight());
        }
    }

    // ---------- Gesture listener inner class ----------
    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private boolean touchOnSelectHandleMiddle = false;
        private boolean touchOnSelectHandleLeft = false;
        private boolean touchOnSelectHandleRight = false;

        private boolean mIsMagnifierActive = false;

        private int mInitialLine = -1;

        // for auto scroll select handle
        private Runnable moveAction = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mIsMagnifierActive && mMagnifierEnabled) {
                        // Use cursor position for magnifier
                        updateMagnifier(mCursorPosX, mCursorPosY + getLineHeight());
                    }
                    onMove(spaceWidth * 4, getLineHeight());
                    if (EditView.this.isAttachedToWindow()) {
                        postDelayed(moveAction, DEFAULT_DURATION);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in moveAction: " + e.getMessage());
                    dismissMagnifier();
                }
            }
        };

        // Swap left/right select handle coordinates and selection indices
        private void reverse() {
            selectHandleLeftX = selectHandleLeftX ^ selectHandleRightX;
            selectHandleRightX = selectHandleLeftX ^ selectHandleRightX;
            selectHandleLeftX = selectHandleLeftX ^ selectHandleRightX;

            selectHandleLeftY = selectHandleLeftY ^ selectHandleRightY;
            selectHandleRightY = selectHandleLeftY ^ selectHandleRightY;
            selectHandleLeftY = selectHandleLeftY ^ selectHandleRightY;

            selectionStart = selectionStart ^ selectionEnd;
            selectionEnd = selectionStart ^ selectionEnd;
            selectionStart = selectionStart ^ selectionEnd;

            touchOnSelectHandleLeft = !touchOnSelectHandleLeft;
            touchOnSelectHandleRight = !touchOnSelectHandleRight;
        }

        // when single tap to check the select region
        private boolean checkSelectRange(float x, float y) {

            if (y < selectHandleLeftY - getLineHeight() || y > selectHandleRightY)
                return false;

            // on the same line
            if (selectHandleLeftY == selectHandleRightY) {
                if (x < selectHandleLeftX || x > selectHandleRightX)
                    return false;
            } else {
                // not on the same line
                int left = getLeftSpace();
                int line = (int) y / getLineHeight() + 1;
                int width = getLineWidth(line) + spaceWidth;
                // select start line
                if (line == selectHandleLeftY / getLineHeight()) {
                    if (x < selectHandleLeftX || x > left + width)
                        return false;
                } else if (line == selectHandleRightY / getLineHeight()) {
                    // select end line
                    if (x < left || x > selectHandleRightX)
                        return false;
                } else {
                    if (x < left || x > left + width)
                        return false;
                }
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            // touch handle middle (show clipboard panel)
            if (mHandleMiddleVisable &&
                    x >= mCursorPosX - handleMiddleWidth / 2 &&
                    x <= mCursorPosX + handleMiddleWidth / 2 &&
                    y >= mCursorPosY + getLineHeight() &&
                    y <= mCursorPosY + getLineHeight() + handleMiddleHeight) {

                touchOnSelectHandleMiddle = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = true;

                // 🔹 Show clipboard panel exactly above this handle
                showTextSelectionWindow();

                // 🔹 Keep magnifier logic working
                if (mMagnifierEnabled) {
                    mIsMagnifierActive = true;
                    showMagnifier(mCursorPosX, mCursorPosY + getLineHeight());
                }

                // ✅ Prevent cursor from moving or triggering a new position
                return true;
            }

            // touch handle left
            if (isSelectMode && x >= selectHandleLeftX - selectHandleWidth + selectHandleWidth / 4
                    && x <= selectHandleLeftX + selectHandleWidth / 4
                    && y >= selectHandleLeftY && y <= selectHandleLeftY + selectHandleHeight) {
                touchOnSelectHandleLeft = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = false;

                showTextSelectionWindow();
                if (mMagnifierEnabled) {
                    mIsMagnifierActive = true;
                    showMagnifier(selectHandleLeftX, selectHandleLeftY);
                }
            }

            // touch handle right
            if (isSelectMode && x >= selectHandleRightX - selectHandleWidth / 4
                    && x <= selectHandleRightX + selectHandleWidth - selectHandleWidth / 4
                    && y >= selectHandleRightY && y <= selectHandleRightY + selectHandleHeight) {
                touchOnSelectHandleRight = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = false;
                showTextSelectionWindow();
                if (mMagnifierEnabled) {
                    mIsMagnifierActive = true;
                    showMagnifier(selectHandleRightX, selectHandleRightY);
                }
            }

            return super.onDown(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            try {
                float x = e2.getX() + getScrollX();
                float y = e2.getY() + getScrollY();

                if (mIsMagnifierActive && mMagnifierEnabled) {
                    if (touchOnSelectHandleMiddle) {
                        updateMagnifier(mCursorPosX, mCursorPosY + getLineHeight());
                    } else if (touchOnSelectHandleLeft) {
                        updateMagnifier(selectHandleLeftX, selectHandleLeftY);
                    } else if (touchOnSelectHandleRight) {
                        updateMagnifier(selectHandleRightX, selectHandleRightY);
                    }
                }

                if (touchOnSelectHandleMiddle) {
                    removeCallbacks(moveAction);
                    post(moveAction);
                    setCursorPosition(x, y - getLineHeight() - Math.min(getLineHeight(), selectHandleHeight) / 2);
                } else if (touchOnSelectHandleLeft) {
                    removeCallbacks(moveAction);
                    post(moveAction);
                    setCursorPosition(x, y - getLineHeight() - Math.min(getLineHeight(), selectHandleHeight) / 2);
                    selectHandleLeftX = mCursorPosX;
                    selectHandleLeftY = mCursorPosY + getLineHeight();
                    selectionStart = mCursorIndex;
                } else if (touchOnSelectHandleRight) {
                    removeCallbacks(moveAction);
                    post(moveAction);
                    setCursorPosition(x, y - getLineHeight() - Math.min(getLineHeight(), selectHandleHeight) / 2);
                    selectHandleRightX = mCursorPosX;
                    selectHandleRightY = mCursorPosY + getLineHeight();
                    selectionEnd = mCursorIndex;
                } else {
                    if (Math.abs(distanceY) > Math.abs(distanceX))
                        distanceX = 0;
                    else
                        distanceY = 0;

                    int newX = (int) distanceX + getScrollX();
                    if (newX < 0) {
                        newX = 0;
                    } else if (newX > getMaxScrollX()) {
                        newX = getMaxScrollX();
                    }

                    int newY = (int) distanceY + getScrollY();
                    if (newY < 0) {
                        newY = 0;
                    } else if (newY > getMaxScrollY()) {
                        newY = getMaxScrollY();
                    }
                    smoothScrollTo(newX, newY);
                }

                if (isSelectMode && ((selectHandleLeftY > selectHandleRightY)
                                || (selectHandleLeftY == selectHandleRightY && selectHandleLeftX > selectHandleRightX))) {
                    reverse();
                }

                postInvalidate();
            } catch (Exception e) {
                dismissMagnifier();
                mIsMagnifierActive = false;
                removeCallbacks(moveAction);
                Log.e(TAG, "Error in onScroll: " + e.getMessage());
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();
            if (isEditedMode) {
                showSoftInput(true);
            }

            showTextSelectionWindow();
            if (!isSelectMode || !checkSelectRange(x, y)) {
                // stop cursor blink
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = true;
                isSelectMode = false;

                if (!mReplaceList.isEmpty())
                    mReplaceList.clear();

                setCursorPosition(x, y);
                postInvalidate();
                mLastTapTime = System.currentTimeMillis();
                // clear long selection process
                clearLineSelection();
                // cursor start blink
                postDelayed(blinkAction, BLINK_TIMEOUT);
            }

            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // TODO: Implement this method
            if (Math.abs(velocityY) > Math.abs(velocityX))
                velocityX = 0;
            else
                velocityY = 0;

            mScroller.fling(getScrollX(), getScrollY(), (int) -velocityX, (int) -velocityY,
                    0, getMaxScrollX(), 0, getMaxScrollY());

            postInvalidate();
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            removeCallbacks(blinkAction);
            showTextSelectionWindow();
            mCursorVisiable = mHandleMiddleVisable = true;

            // Handle line number long press
            if (isInLineNumberArea(x, y)) {
                int currentLine = getLineFromY(y);
                handleLineNumberLongPress(currentLine);
                return;
            }

            if (!touchOnSelectHandleMiddle && mGapBuffer.length() > 0) {

                setCursorPosition(x, y);

                String selectWord = findNearestWord();
                if (selectWord != null) {
                    removeCallbacks(blinkAction);
                    mCursorVisiable = mHandleMiddleVisable = false;
                    isSelectMode = true;

                    int left = getLeftSpace();
                    int lineStart = getLineStart(mCursorLine);
                    selectHandleLeftX = left + measureText(mGapBuffer.substring(lineStart, selectionStart));
                    selectHandleRightX = left + measureText(mGapBuffer.substring(lineStart, selectionEnd));
                    selectHandleLeftY = selectHandleRightY = mCursorPosY + getLineHeight();

                    setCursorPosition(selectionEnd);

                    if (mMagnifierEnabled) {
                        mIsMagnifierActive = true;
                        showMagnifier(selectHandleRightX, selectHandleRightY);
                    }
                }
            }
            postInvalidate();
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            super.onDoubleTap(e);

            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            removeCallbacks(blinkAction);
            mCursorVisiable = mHandleMiddleVisable = true;
            showTextSelectionWindow();

            if (!touchOnSelectHandleMiddle && mGapBuffer.length() > 0) {
                setCursorPosition(x, y);

                String selectWord = findNearestWord();
                if (selectWord != null) {
                    removeCallbacks(blinkAction);
                    mCursorVisiable = mHandleMiddleVisable = false;
                    isSelectMode = true;

                    int left = getLeftSpace();
                    int lineStart = getLineStart(mCursorLine);
                    selectHandleLeftX = left + measureText(mGapBuffer.substring(lineStart, selectionStart));
                    selectHandleRightX = left + measureText(mGapBuffer.substring(lineStart, selectionEnd));
                    selectHandleLeftY = selectHandleRightY = mCursorPosY + getLineHeight();

                    setCursorPosition(selectionEnd);

                    // Show what was selected (for debugging)
                    Log.d(TAG, "Double tap selected: '" + selectWord + "'");
                }
            }
            postInvalidate();
            return super.onDoubleTap(e);
        }

        // Expose onUp for outside calls
        public void onUp(MotionEvent e) {
            if (mIsMagnifierActive) {
                dismissMagnifier();
                mIsMagnifierActive = false;
            }

            if (touchOnSelectHandleLeft || touchOnSelectHandleRight || touchOnSelectHandleMiddle) {
                removeCallbacks(moveAction);
                touchOnSelectHandleMiddle = false;
                touchOnSelectHandleLeft = false;
                touchOnSelectHandleRight = false;

                if (isSelectMode) {
                    setCursorPosition(selectionEnd);
                    // Show immediately and schedule auto-hide
                    showTextSelectionWindow();
                    scheduleAutoHide();
                } else {
                    mLastTapTime = System.currentTimeMillis();
                    postDelayed(blinkAction, BLINK_TIMEOUT);
                    // Hide after delay if not in selection mode
                    scheduleAutoHide();
                }
            }
        }

        private void handleLineNumberLongPress(int currentLine) {
            if (!mWaitingForSecondSelection) {
                // First long press - select single line and wait for second selection
                selectSingleLine(currentLine);
                mFirstSelectedLine = currentLine;
                mWaitingForSecondSelection = true;

                // Show visual hint that we're waiting for second selection
                showSelectionHint();

            } else {
                // Second long press - select range between first and current line
                mSecondSelectedLine = currentLine;
                selectLineRange(mFirstSelectedLine, mSecondSelectedLine);
                mWaitingForSecondSelection = false;
                mSelectionHandler.removeCallbacks(mClearSelectionRunnable);
            }
        }
    }

    private void scheduleSelectionUpdate() {
        mSelectionHandler.removeCallbacks(mUpdateSelectionPosition);
        mSelectionHandler.postDelayed(mUpdateSelectionPosition, 100); // Small delay for smoothness
    }

    private void performHapticFeedback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        } else {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    private void selectLineRange(int startLine, int endLine) {
        if (startLine < 1 || endLine < 1 || startLine > getLineCount() || endLine > getLineCount()) {
            return;
        }

        removeCallbacks(blinkAction);
        mCursorVisiable = false;
        mHandleMiddleVisable = false;
        isSelectMode = true;
        mIsLineSelectionMode = true;

        // Calculate the actual selection range
        int actualStartLine = Math.min(startLine, endLine);
        int actualEndLine = Math.max(startLine, endLine);

        // Start from the beginning of first line
        selectionStart = getLineStart(actualStartLine);

        // End at the end of last line (including newline if present)
        int endLineStart = getLineStart(actualEndLine);
        String endLineText = getLine(actualEndLine);
        selectionEnd = endLineStart + endLineText.length();

        // Include newline character if it's not the last line
        if (actualEndLine < getLineCount()) {
            selectionEnd++;
        }

        mStartSelectionLine = actualStartLine;
        mEndSelectionLine = actualEndLine;

        updateSelectionHandles();
        showTextSelectionWindow();
        postInvalidate();

        // Haptic feedback
        performHapticFeedback();
    }

    private void showSelectionHint() {
        // Flash the selected line or show some visual feedback
        post(new Runnable() {
            @Override
            public void run() {
                // You can change the background color or add animation
                mHandleMiddleVisable = false;
                postInvalidate();

                // Reset after a short time
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mWaitingForSecondSelection) {
                            mHandleMiddleVisable = false;
                            postInvalidate();
                        }
                    }
                }, 500);
            }
        });
    }

    private void selectSingleLine(int line) {
        if (line < 1 || line > getLineCount()) return;

        removeCallbacks(blinkAction);
        mCursorVisiable = false;
        mHandleMiddleVisable = false;
        isSelectMode = true;
        mIsLineSelectionMode = true;

        // Calculate selection range for the entire line
        int lineStart = getLineStart(line);
        int lineEnd = lineStart + getLine(line).length();

        // If it's the last line, include the newline character if present
        if (line < getLineCount()) {
            lineEnd++; // Include the newline character
        }

        selectionStart = lineStart;
        selectionEnd = lineEnd;
        mStartSelectionLine = line;
        mEndSelectionLine = line;

        updateSelectionHandles();
        showTextSelectionWindow();
        postInvalidate();
    }

    // clear long selection process
    private void clearLineSelection() {
        mWaitingForSecondSelection = false;
        mFirstSelectedLine = -1;
    }

    // Helper method that used for selecting nearby word or special symbol
    public void selectNearestWord() {
        String selectWord = findNearestWord();
        if (selectWord != null) {
            removeCallbacks(blinkAction);
            mCursorVisiable = false;
            mHandleMiddleVisable = false;
            isSelectMode = true;
            mIsLineSelectionMode = true;

            int left = getLeftSpace();
            int lineStart = getLineStart(mCursorLine);
            selectHandleLeftX = left + measureText(mGapBuffer.substring(lineStart, selectionStart));
            selectHandleRightX = left + measureText(mGapBuffer.substring(lineStart, selectionEnd));
            selectHandleLeftY = selectHandleRightY = mCursorPosY + getLineHeight();

            setCursorPosition(selectHandleLeftX, selectHandleRightX);
            setCursorPosition(selectionEnd);
            showTextSelectionWindow();
        }
        postInvalidate();
    }

    // when on long press to select a word
    public String findNearestWord() {
        int length = mGapBuffer.length();
        if (length == 0) return null;

        if (mCursorIndex >= length) {
            mCursorIndex = Math.max(0, length - 1);
        }

        if (isCurrentLineEmptyOrWhitespace()) {
            return null;
        }

        // FIRST: Always try to find single special character
        String selected = findSingleSpecialChar();
        if (selected != null) return selected;

        // SECOND: Then try word selection
        selected = findWordAtCursor();
        if (selected != null) return selected;

        selected = findWordInVicinity();
        if (selected != null) return selected;

        selected = findAnyNonWhitespace();
        return selected;
    }

    // Only select single special characters
    private String findSingleSpecialChar() {
        // Check current cursor position
        if (mCursorIndex < mGapBuffer.length()) {
            char currentChar = mGapBuffer.charAt(mCursorIndex);
            if (isSpecialChar(currentChar)) {
                selectionStart = mCursorIndex;
                selectionEnd = mCursorIndex + 1;
                return String.valueOf(currentChar);
            }
        }

        // Check position before cursor
        if (mCursorIndex > 0) {
            char prevChar = mGapBuffer.charAt(mCursorIndex - 1);
            if (isSpecialChar(prevChar)) {
                selectionStart = mCursorIndex - 1;
                selectionEnd = mCursorIndex;
                return String.valueOf(prevChar);
            }
        }

        return null;
    }

    // Special character detection
    private boolean isSpecialChar(char c) {
        // All special characters that should be selected individually
        String specialChars = ":;\"\'`.,!?@#$%^&*()-+=[]{}<>/~|\\";
        return specialChars.indexOf(c) >= 0;
    }

    // WORD selection only - stops at special characters
    private String expandSelectionFrom(int position) {
        int length = mGapBuffer.length();
        if (position < 0 || position >= length) return null;

        char startChar = mGapBuffer.charAt(position);

        // If it's a special char, don't expand - let findSingleSpecialChar handle it
        if (isSpecialChar(startChar)) {
            return null;
        }

        // Expand left until whitespace OR special char
        selectionStart = position;
        while (selectionStart > 0) {
            char c = mGapBuffer.charAt(selectionStart - 1);
            if (Character.isWhitespace(c) || isSpecialChar(c)) break;
            selectionStart--;
        }

        // Expand right until whitespace OR special char
        selectionEnd = position;
        while (selectionEnd < length) {
            char c = mGapBuffer.charAt(selectionEnd);
            if (Character.isWhitespace(c) || isSpecialChar(c)) break;
            selectionEnd++;
        }

        if (selectionStart < selectionEnd) {
            return mGapBuffer.substring(selectionStart, selectionEnd);
        }

        return null;
    }

    // Find word at cursor (EXCLUDING special chars)
    private String findWordAtCursor() {
        // First, check if cursor is directly on a WORD character (not special char)
        if (mCursorIndex < mGapBuffer.length()) {
            char currentChar = mGapBuffer.charAt(mCursorIndex);
            if (!Character.isWhitespace(currentChar) && !isSpecialChar(currentChar)) {
                return expandSelectionFrom(mCursorIndex);
            }
        }

        // Check character before cursor (only if it's a WORD character)
        if (mCursorIndex > 0) {
            char prevChar = mGapBuffer.charAt(mCursorIndex - 1);
            if (!Character.isWhitespace(prevChar) && !isSpecialChar(prevChar)) {
                return expandSelectionFrom(mCursorIndex - 1);
            }
        }

        return null;
    }

    // Find word in vicinity (EXCLUDING special chars)
    private String findWordInVicinity() {
        int length = mGapBuffer.length();
        if (length == 0) return null;

        for (int radius = 1; radius <= 20; radius++) {
            // Check forward
            int forwardPos = mCursorIndex + radius;
            if (forwardPos < length) {
                char c = mGapBuffer.charAt(forwardPos);
                // If it's a special char, select it individually
                if (isSpecialChar(c)) {
                    selectionStart = forwardPos;
                    selectionEnd = forwardPos + 1;
                    return String.valueOf(c);
                }
                // If it's a word char (not special, not whitespace), expand word
                if (!Character.isWhitespace(c) && !isSpecialChar(c)) {
                    return expandSelectionFrom(forwardPos);
                }
            }

            // Check backward
            int backwardPos = mCursorIndex - radius;
            if (backwardPos >= 0) {
                char c = mGapBuffer.charAt(backwardPos);
                // If it's a special char, select it individually
                if (isSpecialChar(c)) {
                    selectionStart = backwardPos;
                    selectionEnd = backwardPos + 1;
                    return String.valueOf(c);
                }
                // If it's a word char (not special, not whitespace), expand word
                if (!Character.isWhitespace(c) && !isSpecialChar(c)) {
                    return expandSelectionFrom(backwardPos);
                }
            }
        }

        return null;
    }

    // Find any non-whitespace (but still respect special chars)
    private String findAnyNonWhitespace() {
        // Check if current line is empty first
        String currentLine = getLine(mCursorLine);
        if (currentLine == null || currentLine.trim().isEmpty()) {
            return null;
        }

        int lineStart = getLineStart(mCursorLine);
        int lineEnd = lineStart + getLine(mCursorLine).length();

        for (int i = lineStart; i < lineEnd; i++) {
            if (i < mGapBuffer.length()) {
                char c = mGapBuffer.charAt(i);
                if (!Character.isWhitespace(c)) {
                    // If it's a special char, select only that char
                    if (isSpecialChar(c)) {
                        selectionStart = i;
                        selectionEnd = i + 1;
                        return String.valueOf(c);
                    }
                    // Otherwise expand word (will stop at special chars)
                    return expandSelectionFrom(i);
                }
            }
        }

        return null;
    }

    //  helper method to check line is empty or not
    private boolean isCurrentLineEmptyOrWhitespace() {
        String currentLine = getLine(mCursorLine);
        if (currentLine == null || currentLine.isEmpty()) {
            return true;
        }

        // Check if line contains only whitespace
        for (int i = 0; i < currentLine.length(); i++) {
            if (!Character.isWhitespace(currentLine.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ===== SCALE GESTURE LISTENER CLASS =====
    class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float factor = detector.getScaleFactor();
            setTextSize(mTextPaint.getTextSize() * factor);
            return true;
        }
    }

    // ---------- Text input connection (IME) ----------
    class TextInputConnection extends BaseInputConnection {

        public TextInputConnection(View view, boolean fullEditor) {
            super(view, fullEditor);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            long currentTime = System.currentTimeMillis();

            if (currentTime - mLastInputTime < INPUT_DEBOUNCE_DELAY &&
                    text.toString().equals(mLastCommittedText)) {
                return true;
            }

            if (text != null && text.length() > 0) {
                mProcessingInput = true;
                mLastCommittedText = text.toString();
                mLastInputTime = currentTime;

                // Call insert to handle the text and trigger auto-complete
                insert(text.toString());

                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mProcessingInput = false;
                    }
                }, INPUT_DEBOUNCE_DELAY);
                return true;
            }
            return super.commitText(text, newCursorPosition);
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            Log.d(TAG, "setComposingText: '" + text + "', newCursor=" + newCursorPosition);

            // For composing text, we still want to handle it but not debounce
            if (text != null && text.length() > 0) {
                mProcessingInput = true;
                insert(text.toString());
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mProcessingInput = false;
                    }
                }, INPUT_DEBOUNCE_DELAY);
                return true;
            }
            return super.setComposingText(text, newCursorPosition);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            Log.d(TAG, "deleteSurroundingText: before=" + beforeLength + ", after=" + afterLength);

            if (mProcessingInput) {
                Log.d(TAG, "Skipping delete - processing input");
                return true;
            }

            if (beforeLength > 0) {
                for (int i = 0; i < beforeLength; i++) {
                    delete();
                }
                return true;
            } else if (afterLength > 0) {
                for (int i = 0; i < afterLength; i++) {
                    handleForwardDelete();
                }
                return true;
            }
            return super.deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
            Log.d(TAG, "deleteSurroundingTextInCodePoints: before=" + beforeLength + ", after=" + afterLength);
            return deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            Log.d(TAG, "sendKeyEvent: " + event.getKeyCode() + ", action=" + event.getAction());

            // Skip key events if we're already processing input to avoid duplicates
            if (mProcessingInput && event.getAction() == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Skipping key event - processing input");
                return true;
            }

            // Let onKeyDown handle most keys, but ensure numbers and special chars work
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int keyCode = event.getKeyCode();

                // Handle keys that might not be properly handled by onKeyDown
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9 ||
                        keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9 ||
                        keyCode == KeyEvent.KEYCODE_SPACE) {

                    int unicodeChar = event.getUnicodeChar();
                    if (unicodeChar != 0) {
                        mProcessingInput = true;
                        insert(String.valueOf((char) unicodeChar));
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mProcessingInput = false;
                            }
                        }, INPUT_DEBOUNCE_DELAY);
                        return true;
                    }
                }

                return onKeyDown(keyCode, event);
            }
            return super.sendKeyEvent(event);
        }

        @Override
        public boolean finishComposingText() {
            Log.d(TAG, "finishComposingText");
            return true;
        }

        @Override
        public CharSequence getTextBeforeCursor(int length, int flags) {
            try {
                int start = Math.max(0, mCursorIndex - length);
                String text = mGapBuffer.substring(start, mCursorIndex);
                Log.d(TAG, "getTextBeforeCursor: " + text);
                return text;
            } catch (Exception e) {
                Log.e(TAG, "Error in getTextBeforeCursor", e);
                return "";
            }
        }

        @Override
        public CharSequence getTextAfterCursor(int length, int flags) {
            try {
                int end = Math.min(mGapBuffer.length(), mCursorIndex + length);
                String text = mGapBuffer.substring(mCursorIndex, end);
                Log.d(TAG, "getTextAfterCursor: " + text);
                return text;
            } catch (Exception e) {
                Log.e(TAG, "Error in getTextAfterCursor", e);
                return "";
            }
        }

        @Override
        public int getCursorCapsMode(int reqModes) {
            // This helps with auto-capitalization
            return TextUtils.getCapsMode(mGapBuffer.toString(), mCursorIndex, reqModes);
        }
    }
}

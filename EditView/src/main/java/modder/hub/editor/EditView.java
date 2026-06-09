/*
 * MH-TextEditor - An Advanced and optimized TextEditor for android
 * Copyright 2025-26, developer-krushna
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


 *     Please contact Krushna by email mt.modder.hub@gmail.com if you need
 *     additional information or have any questions
 */
package modder.hub.editor;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modder.hub.editor.buffer.GapBuffer;
import modder.hub.editor.component.ClipboardPanel;
import modder.hub.editor.component.Magnifier;
import modder.hub.editor.highlight.LineResult;
import modder.hub.editor.highlight.MHSyntaxHighlightEngine;
import modder.hub.editor.lib.R;
import modder.hub.editor.listener.OnTextChangedListener;
import modder.hub.editor.utils.LineHeightManager;
import modder.hub.editor.utils.ScreenUtils;

/* Author : Krushna Chandra Maharna(@developer-krushna)
   This project was actually started by someone using gap buffer
   But i forgot his name and his repository link because I was started
   working on this project in 2024 . During that time due to some personal problem
   I closed this project and saved it in sdcard for future task .
   But unfortunately i unable to save the original author name. I am really sorry.
   But if you are the creator then please let me know so that i can update this
   part. Thank You

   Optimization , code refactoring and comments are made by AI
   @Radhe Radhe
*/

public class EditView extends View {

    // --- Constants ---
    private static final String COPYRIGHT = "MH-TextEditor\nCopyright (C) Krushna Chandra modder-hub@zohomail.in\nThis project is distributed under the LGPL v2.1 license";
    private static final int FAST_SCROLLER_HIDE_DELAY = 1500;
    private static final int FAST_SCROLLER_MIN_THUMB_LENGTH_DP = 64;
    private static final int FAST_SCROLLER_THUMB_THICKNESS_DP = 8;
    private static final int AUTO_COMPLETE_DELAY = 100;
    private final String TAG = this.getClass().getSimpleName();
    private final int DEFAULT_DURATION = 250;
    private final int BLINK_TIMEOUT = 500;
    private final LineHeightManager mHeightManager = new LineHeightManager();
    private final Map<String, Integer> mWordFrequencyMap = new ConcurrentHashMap<String, Integer>();
    // --- Handlers ---
    private final Handler mSelectionHandler = new Handler();
    private final Handler mSearchHandler = new Handler();
    private final Handler mBraceThreadHandler = new Handler();
    private final boolean isSyntaxDarkMode = false;
    // --- Navigation & History ---
    private final List<Integer> mCursorHistory = new ArrayList<>();
    // --- Core Data & Engines ---
    private GapBuffer mGapBuffer;
    private MHSyntaxHighlightEngine mHighlighter;
    // --- Editor Configuration & State ---
    private int mTabSize = 4;
    private boolean mAutoIndentEnabled = true;
    private boolean mShowLineNumbers = true;
    private boolean mShowWrapArrows = true;
    private boolean mStickyLineNumbers = true;
    private boolean mShowIndentGuides = true;
    private boolean mWordWrap = false;
    private boolean isEditedMode = true;
    private boolean mAutoCompleteEnabled = true;
    private boolean mMagnifierEnabled = true;
    // --- Cursor State ---
    private int mCursorLine, mCursorIndex;
    private int mCursorPosX, mCursorPosY;
    private int mCursorYOffsetWithinLine;
    private int mCursorWidth;
    private boolean mCursorVisible = true;
    private int[] mCursorWordBounds = null;
    // --- Selection State ---
    private boolean isSelectMode = false;
    private boolean mHandleMiddleVisible = false;
    private boolean mHideSelectHandles = false;
    private int selectHandleLeftX, selectHandleLeftY;
    private int selectHandleRightX, selectHandleRightY;
    private int mPendingDeleteStart = -1;
    private int mPendingDeleteEnd = -1;
    private int mStartSelectionLine = -1;
    private int mEndSelectionLine = -1;
    private boolean mIsLineSelectionMode = false;
    private int mFirstSelectedLine = -1;
    private int mSecondSelectedLine = -1;
    private boolean mWaitingForSecondSelection = false;
    private final Runnable mClearSelectionRunnable = new Runnable() {
        @Override
        public void run() {
            mWaitingForSecondSelection = false;
            mFirstSelectedLine = -1;
        }
    };
    // --- Composing Text State ---
    private int mComposingStart = -1;
    private int mComposingEnd = -1;
    private int mHistoryIndex = -1;
    private boolean mIsNavigatingHistory = false;
    private int mLastHistoryLine = -1;
    // --- Painting & Graphics ---
    private Paint mPaint;
    private Paint mBracePaint;
    private Paint mGuidelinePaint;
    private TextPaint mTextPaint;
    private Paint mFastScrollerPaint;
    // --- Drawables & Resources ---
    private Drawable mDrawableCursorRes;
    private Drawable mTextSelectHandleLeftRes;
    private Drawable mTextSelectHandleRightRes;
    private Drawable mTextSelectHandleMiddleRes;
    private Drawable mLeftSoftWrap, mRightSoftWrap;
    // --- Layout & Dimensions ---
    private int lineWidth, spaceWidth;
    private int handleMiddleWidth, handleMiddleHeight;
    private int selectHandleWidth, selectHandleHeight;
    private int mTotalHeight = 0;
    private int[] mLineTops;
    private int mOldWrapWidth = -1;
    private volatile boolean mIsCalculatingMaxWidth = false;
    // --- Interaction & Scroll State ---
    private OverScroller mScroller;
    private GestureDetector mGestureDetector;
    private GestureListener mGestureListener;
    private ScaleGestureDetector mScaleGestureDetector;
    private long mLastScroll;
    private long mLastTapTime;
    private float mAutoScrollFactor = 0f;
    private float mLastTouchX, mLastTouchY;
    // --- Feature States ---
    private Magnifier mMagnifier;
    private float mMagnifierX, mMagnifierY;
    private boolean mIsMagnifierShowing = false;
    private boolean mIsScaling = false;
    private float mZoomScale = 1.0f;
    private float mZoomFocusX, mZoomFocusY;
    private boolean mIsDraggingFastScroller = false;
    private boolean mIsDraggingFastScrollerHorizontally = false;
    private float mFastScrollerAlpha = 0;
    private long mLastScrollTimeFast = 0;
    private float mFastScrollerTouchOffset = 0;
    private ArrayList<Pair<Integer, Integer>> mReplaceList = new ArrayList<>();
    private String mLastSearchPattern = "";
    // --- Internal Runnables & Listeners ---
    private final Runnable mSearchRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mLastSearchPattern.isEmpty()) {
                find(mLastSearchPattern);
            }
        }
    };
    private int mMatchingBraceIndex = -1;
    private int mCurrentBraceIndex = -1;
    private int mMatchingBraceLine = -1;
    private int mCurrentBraceLine = -1;
    private long mBraceSearchId = 0;
    private int mPendingBraceCheckIdx = -1;
    private final Runnable mBraceSearchRunnable = new Runnable() {
        @Override
        public void run() {
            final int checkIdx = mPendingBraceCheckIdx;
            final long searchId = mBraceSearchId;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    final int matchIdx = findMatchingBrace(checkIdx);

                    // Pre-calculate line numbers in background to save UI thread time
                    final int currentLine = (matchIdx != -1) ? getOffsetLine(checkIdx) : -1;
                    final int matchLine = (matchIdx != -1) ? getOffsetLine(matchIdx) : -1;

                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (searchId == mBraceSearchId) {
                                mCurrentBraceIndex = (matchIdx != -1) ? checkIdx : -1;
                                mMatchingBraceIndex = matchIdx;
                                mCurrentBraceLine = currentLine;
                                mMatchingBraceLine = matchLine;
                                postInvalidate();
                            }
                        }
                    });
                }
            }).start();
        }
    };
    private Set<String> mWordSet = new HashSet<>();
    private ListPopupWindow mAutoCompletePopup;
    private ArrayAdapter<String> mAutoCompleteAdapter;
    private String mCurrentPrefix = "";
    // --- Components ---
    private ClipboardPanel mClipboardPanel;
    private final Runnable mUpdateSelectionPosition = new Runnable() {
        @Override
        public void run() {
            if (mClipboardPanel != null && (isSelectMode || mHandleMiddleVisible)) {
                mClipboardPanel.updatePosition();
            }
        }
    };
    private ClipboardManager mClipboard;
    // --- Listeners ---
    private OnTextChangedListener mTextListener;    private final Runnable mAutoCompleteRunnable = new Runnable() {
        @Override
        public void run() {
            String prefix = getCurrentPrefix();
            if (prefix.isEmpty()) {
                dismissAutoComplete();
                return;
            }
            // Only re-filter if prefix actually changed
            if (!prefix.equals(mCurrentPrefix)) {
                mCurrentPrefix = prefix;
                mAutoCompleteAdapter.setNotifyOnChange(false);
                mAutoCompleteAdapter.clear();
                mAutoCompleteAdapter.notifyDataSetChanged();
            }
            filterAndShowSuggestions(prefix);
        }
    };
    private OnSelectionChangeListener mSelectionListener;

    public EditView(Context context) {
        super(context);
        initView(context);
    }

    public EditView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }    //Cursor blinking
    private final Runnable blinkAction = new Runnable() {
        @Override
        public void run() {
            if (isSelectMode) {
                mCursorVisible = false;
                return;
            }
            mCursorVisible = !mCursorVisible;
            removeCallbacks(blinkAction);
            postDelayed(blinkAction, BLINK_TIMEOUT);
            postInvalidate();
        }
    };

    public EditView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }    private final Runnable mAutoHideRunnable = new Runnable() {
        @Override
        public void run() {
            mHideSelectHandles = true;
            mHandleMiddleVisible = false;
            hideTextSelectionWindow();
            postInvalidate();
        }
    };

    // Initialize all editor components, drawables, and settings
    private void initView(Context context) {
        Log.v(TAG, COPYRIGHT);

        mGapBuffer = new GapBuffer();
        mGapBuffer.addTextChangedListener(mInternalWatcher);
        mCursorLine = getLineCount();
        setBackgroundColor(Color.WHITE);

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setColor(Color.parseColor("#B0B0B0"));

        // Initialize highlighter with no language to provide basic layout support
        mHighlighter = new MHSyntaxHighlightEngine(context, mTextPaint, null, isSyntaxDarkMode);
        mHighlighter.setLineProvider(new MHSyntaxHighlightEngine.LineProvider() {
            @Override
            public String getLine(int index) {
                return EditView.this.getLine(index);
            }
        });

        mDrawableCursorRes = context.getDrawable(R.drawable.abc_text_cursor_material);
        if (mDrawableCursorRes != null) {
            mDrawableCursorRes.setTint(Color.BLACK);
            mCursorWidth = mDrawableCursorRes.getIntrinsicWidth();
        }

        mClipboardPanel = new ClipboardPanel(this);

        mMagnifier = new Magnifier(this);

        int density = (int) getResources().getDisplayMetrics().density;
        mCursorWidth = Math.max(2, (int) (density * 1.5f));
        if (mCursorWidth > 10) mCursorWidth = 10;

        mTextSelectHandleLeftRes = context.getDrawable(R.drawable.abc_text_select_handle_left_mtrl);
        mTextSelectHandleLeftRes.setTint(Color.parseColor("#63B5F7"));

        selectHandleWidth = (int) (mTextSelectHandleLeftRes.getIntrinsicWidth() * 0.3f);
        selectHandleHeight = (int) (mTextSelectHandleLeftRes.getIntrinsicHeight() * 0.3f);

        mTextSelectHandleRightRes = context.getDrawable(R.drawable.abc_text_select_handle_right_mtrl);
        mTextSelectHandleRightRes.setTint(Color.parseColor("#63B5F7"));

        mTextSelectHandleMiddleRes = context.getDrawable(R.drawable.abc_text_select_handle_middle_mtrl);
        mTextSelectHandleMiddleRes.setTint(Color.parseColor("#63B5F7"));
        handleMiddleWidth = (int) (mTextSelectHandleMiddleRes.getIntrinsicWidth() * 0.5f);
        handleMiddleHeight = (int) (mTextSelectHandleMiddleRes.getIntrinsicHeight() * 0.5f);

        mGestureListener = new GestureListener();
        mGestureDetector = new GestureDetector(context, mGestureListener);
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureListener());

        mBracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBracePaint.setColor(Color.parseColor("#B3DBFB"));

        mGuidelinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGuidelinePaint.setColor(Color.parseColor("#E0E0E0"));
        mGuidelinePaint.setStrokeWidth(1.0f);

        setTextSize(ScreenUtils.dip2px(context, 18));
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.parseColor("#FFFAE3"));
        mPaint.setStrokeWidth(0);

        mScroller = new OverScroller(context);
        mClipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        mReplaceList = new ArrayList<>();

        mFastScrollerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFastScrollerPaint.setTextSize(ScreenUtils.dip2px(context, 12));
        mFastScrollerPaint.setTypeface(Typeface.MONOSPACE);

        mLeftSoftWrap = context.getDrawable(R.drawable.ic_left_soft_wrap);
        mRightSoftWrap = context.getDrawable(R.drawable.ic_right_soft_wrap);

        spaceWidth = (int) mTextPaint.measureText(" ");

        scrollTo(0, 0);
        setTabSize(4);
        setFocusable(true);
        setFocusableInTouchMode(true);
        postDelayed(blinkAction, BLINK_TIMEOUT);
        initializeAutocompleteView(context);
        updateLineWidth();
    }    private final TextWatcher mInternalWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mGapBuffer != null && mGapBuffer.isBatchEdit()) return;
            int line = getOffsetLine(start);
            // If the change span is large, refresh everything
            if (count > 100 || before > 100) {
                EditView.this.onTextChanged();
            } else {
                EditView.this.onTextChanged(line);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            postInvalidate();
        }
    };

    private void initializeAutocompleteView(Context context) {
        mAutoCompletePopup = new ListPopupWindow(getContext());
        mAutoCompleteAdapter = new ArrayAdapter<String>(
                getContext(),
                R.layout.item_autocomplete,
                R.id.text1,
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
        mAutoCompletePopup.setHeight(ScreenUtils.dip2px(context, 150));
        mAutoCompletePopup.setModal(false);
        mAutoCompletePopup.setAnchorView(this);
        mAutoCompletePopup.setModal(false);
        mAutoCompletePopup.setAnimationStyle(0);
        mAutoCompletePopup.setBackgroundDrawable(
                getResources().getDrawable(android.R.drawable.dialog_holo_light_frame)
        );

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
                mCurrentPrefix = "";
            }
        });
    }

    // Initial scan of all words in document
    private void initialWordScan() {
        if (mGapBuffer == null) return;
        final GapBuffer bufferAtStart = mGapBuffer;
        new Thread(new Runnable() {
            @Override
            public void run() {
                mWordFrequencyMap.clear();
                Pattern pattern = Pattern.compile("[a-zA-Z0-9_]{2,}");
                for (int i = 1; ; i++) {
                    // Stop if buffer was swapped or we reached the end
                    if (mGapBuffer != bufferAtStart) return;
                    if (i > getLineCount()) break;

                    String line;
                    try {
                        line = getLine(i);
                    } catch (Exception e) {
                        // Buffer was likely modified during scan, so break it.
                        break;
                    }

                    if (line != null) {
                        Matcher matcher = pattern.matcher(line);
                        while (matcher.find()) {
                            String word = matcher.group();
                            Integer count = mWordFrequencyMap.get(word);
                            if (count == null) {
                                mWordFrequencyMap.put(word, 1);
                            } else {
                                mWordFrequencyMap.put(word, count + 1);
                            }
                        }
                    }
                    if (i % 2000 == 0) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }).start();
    }

    //Add words from text to frequency map for autocomplete
    private void addWordsToMap(String text) {
        if (text == null || text.isEmpty()) return;
        Matcher matcher = Pattern.compile("[a-zA-Z0-9_]{2,}").matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            Integer count = mWordFrequencyMap.get(word);
            if (count == null) {
                mWordFrequencyMap.put(word, 1);
            } else {
                mWordFrequencyMap.put(word, count + 1);
            }
        }
    }

    // Remove words from frequency map
    private void removeWordsFromMap(String text) {
        if (text == null || text.isEmpty()) return;
        Matcher matcher = Pattern.compile("[a-zA-Z0-9_]{2,}").matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            Integer count = mWordFrequencyMap.get(word);
            if (count != null) {
                if (count > 1) {
                    mWordFrequencyMap.put(word, count - 1);
                } else {
                    mWordFrequencyMap.remove(word);
                }
            }
        }
    }

    // Clean up when view detached
    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(blinkAction);
        dismissAutoComplete();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            removeCallbacks(blinkAction);
            mCursorVisible = true;
            postDelayed(blinkAction, BLINK_TIMEOUT);
            // Ensure cursor is visible when gaining focus
            post(new Runnable() {
                @Override
                public void run() {
                    scrollToVisible();
                }
            });
        } else {
            removeCallbacks(blinkAction);
            mCursorVisible = false;
            dismissAutoComplete();
        }
        postInvalidate();
    }

    //Handle layout changes and wrap width updates
    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            int newWrapWidth = getWrapWidth();
            boolean widthChanged = newWrapWidth != mOldWrapWidth;
            mOldWrapWidth = newWrapWidth;

            if (mWordWrap) {
                if (mHighlighter != null) {
                    mHighlighter.setWordWrap(true, newWrapWidth);
                }
                if (widthChanged) {
                    computeLineTops();
                }
            } else {
                calculateMaxWidth();
            }
            adjustCursorPosition();
            if (isSelectMode) {
                setSelection(getSelectionStart(), getSelectionEnd());
            }
            // Ensure cursor is visible when layout changes (e.g. keyboard opens/resizes)
            scrollToVisible();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else {
            width = getLeftSpace() + lineWidth + spaceWidth * 4;
            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(width, widthSize);
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            height = mWordWrap ? mTotalHeight + getLineHeight() * 2 : (getLineCount() + 2) * getLineHeight();
            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(height, heightSize);
            }
        }

        setMeasuredDimension(width, height);
    }

    // Main draw method for all renderings
    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background should be drawn before any scaling transformations
        canvas.drawColor(Color.WHITE);

        // Clamp scroll if out of bounds (avoids blank editor when heights change)
        int maxScrollY = getMaxScrollY();
        if (getScrollY() > maxScrollY) {
            scrollTo(getScrollX(), maxScrollY);
        }

        canvas.save();

        if (mIsScaling) {
            // To keep it "sticky on the left side", if scrollX is small, we anchor horizontal scaling
            // to the left edge of the content (the padding start).
            float focalX;
            if (getScrollX() <= 10) {
                // Anchor to the content's left edge (in canvas coordinates)
                focalX = getScrollX() + getPaddingLeft();
            } else {
                // Anchor to the finger position
                focalX = getScrollX() + mZoomFocusX;
            }
            canvas.scale(mZoomScale, mZoomScale, focalX, getScrollY() + mZoomFocusY);
        }

        // Get actual visible content bounds after scale
        Rect clip = new Rect();
        canvas.getClipBounds(clip);

        // Translate for padding
        canvas.translate(getPaddingLeft(), getPaddingTop());

        // Adjust clip relative to text origin (0,0) for line lookup
        Rect drawClip = new Rect(clip);
        drawClip.offset(-getPaddingLeft(), -getPaddingTop());

        int visibleStartLine = getLogicalLineFromY(drawClip.top);
        int visibleEndLine = getLogicalLineFromY(drawClip.bottom);

        // Pre-fetch syntax results ONCE per visible line
        LineResult[] visibleResults = null;
        if (mHighlighter != null) {
            int visibleLineCount = Math.max(0, visibleEndLine - visibleStartLine + 1);
            visibleResults = new LineResult[visibleLineCount];
            for (int i = 0; i < visibleLineCount; i++) {
                int lineIndex = visibleStartLine + i;
                visibleResults[i] = mHighlighter.getOrTokenize(lineIndex, getLine(lineIndex));

                // Discover actual height Just In Time
                if (mWordWrap && visibleResults[i] != null && visibleResults[i].layout != null) {
                    int measuredHeight = visibleResults[i].layout.getHeight();
                    if (measuredHeight != mHeightManager.getHeight(lineIndex)) {
                        mHeightManager.updateHeight(lineIndex, measuredHeight);
                        mTotalHeight = mHeightManager.getTotalHeight();
                    }
                }
            }
        }

        drawMatchText(canvas);
        drawLineBackground(canvas, visibleStartLine, visibleEndLine, visibleResults);
        drawIndentationGuidelines(canvas, visibleStartLine, visibleEndLine, visibleResults);
        drawEditableText(canvas, visibleStartLine, visibleEndLine, visibleResults);
        drawBraceSeparatorHighlight(canvas);
        drawSelectHandle(canvas);
        drawCursor(canvas);

        canvas.restore();
        // Overlays
        drawFastScroller(canvas);
    }

    // draw fast scroller
    private void drawFastScroller(Canvas canvas) {
        int maxScrollY = getMaxScrollY();
        int maxScrollX = getMaxScrollX();
        if (maxScrollY <= 0 && maxScrollX <= 0) return;

        // only show if content is significantly larger than view
        // Threshold: contentHeight > viewHeight * thresholdFactor
        // or getLineCount() > someThreshold
        int viewHeight = getHeight();
        int viewWidth = getWidth();

        // Dynamic threshold: If keyboard is likely showing (viewHeight is small), threshold is lower
        int screenHeight = ScreenUtils.getScreenHeight(getContext());
        boolean isKeyboardShowing = viewHeight < screenHeight * 0.7f;
        int verticalLineThreshold = isKeyboardShowing ? 95 : 182;

        boolean showVertical = getLineCount() > verticalLineThreshold;
        boolean showHorizontal = !mWordWrap && lineWidth > viewWidth * 1.5f;

        if (!showVertical && !showHorizontal && !mIsDraggingFastScroller && !mIsDraggingFastScrollerHorizontally) {
            mFastScrollerAlpha = 0;
            return;
        }

        long now = System.currentTimeMillis();
        boolean isAnyDragging = mIsDraggingFastScroller || mIsDraggingFastScrollerHorizontally;
        if (!isAnyDragging && now - mLastScrollTimeFast > FAST_SCROLLER_HIDE_DELAY) {
            mFastScrollerAlpha -= 15;
            if (mFastScrollerAlpha < 0) mFastScrollerAlpha = 0;
        } else {
            mFastScrollerAlpha += 25;
            if (mFastScrollerAlpha > 255) mFastScrollerAlpha = 255;
        }

        if (mFastScrollerAlpha <= 0) return;

        int scrollY = getScrollY();
        int scrollX = getScrollX();

        int thumbColorNormal = Color.parseColor("#40808080");
        int thumbColorDragging = Color.parseColor("#FF1E88E5");
        int trackColor = 0x11000000;

        // Vertical Scroller
        if (showVertical) {
            // Constant thumb height
            int thumbHeight = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_MIN_THUMB_LENGTH_DP);

            // Calculate position
            int trackHeight = viewHeight - thumbHeight;
            float scrollRatio = (float) scrollY / maxScrollY;
            int thumbY = (int) (scrollRatio * trackHeight);

            int thumbWidth = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_THUMB_THICKNESS_DP);
            int thumbRight = scrollX + viewWidth;
            int thumbLeft = thumbRight - thumbWidth;
            int thumbTop = scrollY + thumbY;
            int thumbBottom = thumbTop + thumbHeight;

            // Draw track (Always with thumb, same width)
            mFastScrollerPaint.setColor(trackColor);
            mFastScrollerPaint.setAlpha((int) (mFastScrollerAlpha * 0.1f));
            canvas.drawRect(thumbLeft, scrollY, thumbRight, scrollY + viewHeight, mFastScrollerPaint);

            mFastScrollerPaint.setColor(mIsDraggingFastScroller ? thumbColorDragging : thumbColorNormal);
            mFastScrollerPaint.setAlpha((int) mFastScrollerAlpha);

            // Draw thumb
            canvas.drawRect(thumbLeft, thumbTop, thumbRight, thumbBottom, mFastScrollerPaint);

            if (mIsDraggingFastScroller) {
                // Draw line number bubble
                int currentLine = (int) (scrollRatio * (getLineCount() - 1)) + 1;
                String text = String.valueOf(currentLine);

                mFastScrollerPaint.setTextSize(mTextPaint.getTextSize() * 0.8f);
                float textWidth = mFastScrollerPaint.measureText(text);
                float bubbleWidth = textWidth + ScreenUtils.dip2px(getContext(), 24);
                float bubbleHeight = getLineHeight() * 1.2f;

                float bubbleX = thumbLeft - bubbleWidth - ScreenUtils.dip2px(getContext(), 12);

                // Keep bubble within view
                float bubbleY = thumbTop + (thumbHeight / 2f) - (bubbleHeight / 2f);
                if (bubbleY < scrollY) bubbleY = scrollY;
                if (bubbleY + bubbleHeight > scrollY + viewHeight)
                    bubbleY = scrollY + viewHeight - bubbleHeight;

                mFastScrollerPaint.setColor(Color.parseColor("#AA000000"));
                mFastScrollerPaint.setAlpha(180);
                android.graphics.RectF bubbleRect = new android.graphics.RectF(bubbleX, bubbleY, bubbleX + bubbleWidth, bubbleY + bubbleHeight);
                canvas.drawRoundRect(bubbleRect, ScreenUtils.dip2px(getContext(), 8), ScreenUtils.dip2px(getContext(), 8), mFastScrollerPaint);

                mFastScrollerPaint.setColor(Color.WHITE);
                mFastScrollerPaint.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = mFastScrollerPaint.getFontMetrics();
                float textY = bubbleY + (bubbleHeight - fm.ascent - fm.descent) / 2;
                canvas.drawText(text, bubbleX + bubbleWidth / 2, textY, mFastScrollerPaint);
                mFastScrollerPaint.setTextAlign(Paint.Align.LEFT);
            }
        }

        // Horizontal Scroller
        if (showHorizontal) {
            // Constant thumb width
            int thumbWidth = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_MIN_THUMB_LENGTH_DP);

            int trackWidth = viewWidth - thumbWidth;
            float scrollRatio = (float) scrollX / maxScrollX;
            int thumbX = (int) (scrollRatio * trackWidth);

            int thumbHeight = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_THUMB_THICKNESS_DP);
            int thumbBottom = scrollY + viewHeight;
            int thumbTop = thumbBottom - thumbHeight;
            int thumbLeft = scrollX + thumbX;
            int thumbRight = thumbLeft + thumbWidth;

            // Draw track (Always with thumb, same height)
            mFastScrollerPaint.setColor(trackColor);
            mFastScrollerPaint.setAlpha((int) (mFastScrollerAlpha * 0.1f));
            canvas.drawRect(scrollX, thumbTop, scrollX + viewWidth, thumbBottom, mFastScrollerPaint);

            mFastScrollerPaint.setColor(mIsDraggingFastScrollerHorizontally ? thumbColorDragging : thumbColorNormal);
            mFastScrollerPaint.setAlpha((int) mFastScrollerAlpha);

            canvas.drawRect(thumbLeft, thumbTop, thumbRight, thumbBottom, mFastScrollerPaint);
        }

        if (mFastScrollerAlpha > 0 && mFastScrollerAlpha < 255) {
            postInvalidateDelayed(16);
        }
    }

    // Draw line backgrounds including selection and syntax highlighting
    public void drawLineBackground(Canvas canvas) {
        Rect clip = new Rect();
        canvas.getClipBounds(clip);
        int visibleStartLine = getLogicalLineFromY(clip.top);
        int visibleEndLine = getLogicalLineFromY(clip.bottom);

        LineResult[] visibleResults = null;
        if (mHighlighter != null) {
            int count = visibleEndLine - visibleStartLine + 1;
            visibleResults = new LineResult[count];
            for (int i = 0; i < count; i++) {
                int lineIndex = visibleStartLine + i;
                visibleResults[i] = mHighlighter.getOrTokenize(lineIndex, getLine(lineIndex));
            }
        }
        drawLineBackground(canvas, visibleStartLine, visibleEndLine, visibleResults);
    }

    public void drawLineBackground(Canvas canvas, int visibleStartLine, int visibleEndLine, LineResult[] visibleResults) {
        int lineHeight = getLineHeight();
        Rect clip = new Rect();
        canvas.getClipBounds(clip);

        int gutterLeftPadding = ScreenUtils.dip2px(getContext(), 12);
        int lineNumberWidth = getLineNumberWidth();
        int gutterRightPadding = ScreenUtils.dip2px(getContext(), 6);
        int separatorWidth = 2;
        int contentStartX = mShowLineNumbers ? (gutterLeftPadding + lineNumberWidth + gutterRightPadding + separatorWidth) : 0;

        // Draw syntax-based line backgrounds
        if (mHighlighter != null && visibleResults != null) {
            int right = clip.right;
            for (int i = visibleStartLine; i <= visibleEndLine; i++) {
                LineResult res = visibleResults[i - visibleStartLine];
                if (res != null) {
                    // Alignment Fix: Match text rendering baseline shift
                    float top = getLineTop(i);
                    float bottom = getLineBottom(i);
                    mHighlighter.drawLineBackground(canvas, res.text, i, contentStartX, (int) top, right, (int) bottom);
                }
            }
        }

        if (mShowLineNumbers && mIsLineSelectionMode && mStartSelectionLine > 0 && mEndSelectionLine > 0) {
            mPaint.setColor(Color.parseColor("#E3F2FD"));
            int gutterAreaWidth = gutterLeftPadding + lineNumberWidth + gutterRightPadding;
            int gutterStartX = mStickyLineNumbers ? getScrollX() : 0;

            int drawStartLine = Math.max(visibleStartLine, mStartSelectionLine);
            int drawEndLine = Math.min(visibleEndLine, mEndSelectionLine);

            for (int i = drawStartLine; i <= drawEndLine; i++) {
                float top = getLineTop(i);
                float bottom = getLineBottom(i);

                canvas.drawRect(gutterStartX, top,
                        gutterStartX + gutterAreaWidth, bottom, mPaint);
            }
        }

        if (!isSelectMode) {
            // Alignment Fix: Match text rendering baseline shift
            float top = getLineTop(mCursorLine);
            float bottom = getLineBottom(mCursorLine);

            mPaint.setColor(Color.parseColor("#FFFAE3"));
            canvas.drawRect(contentStartX,
                    top,
                    clip.right,
                    bottom,
                    mPaint);
        } else {
            mPaint.setColor(Color.parseColor("#B3DBFB"));
            mPaint.setAntiAlias(false); // Disable AA for solid background blocks to prevent gaps

            int left = getLeftSpace();

            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            // Normalize: Ensure s is always start and e is always end for drawing range
            int s = Math.min(selStart, selEnd);
            int e = Math.max(selStart, selEnd);

            int startLine = getOffsetLine(s);
            int endLine = getOffsetLine(e);
            int drawStartLine = Math.max(visibleStartLine, startLine);
            int drawEndLine = Math.min(visibleEndLine, endLine);

            for (int i = drawStartLine; i <= drawEndLine; i++) {
                String lineText = getLine(i);
                LineResult res = (mHighlighter != null) ? mHighlighter.getOrTokenize(i, lineText) : null;
                int lineTop = getLineTop(i);
                int lineStartIdx = getLineStart(i);

                if (mWordWrap && res != null && res.layout != null) {
                    int lineLen = lineText.length();
                    int selStartInLine = Math.max(0, s - lineStartIdx);
                    int selEndInLine = Math.min(lineLen, e - lineStartIdx);

                    // Newline is selected if selection end is past the logical line end
                    boolean highlightNewline = (e > lineStartIdx + lineLen) && (s <= lineStartIdx + lineLen);

                    if (selStartInLine < selEndInLine || highlightNewline) {
                        canvas.save();
                        canvas.translate(left, lineTop);
                        int firstV = getLayoutLineForOffset(res, selStartInLine);
                        int lastV = getLayoutLineForOffset(res, selEndInLine);
                        for (int v = firstV; v <= lastV; v++) {
                            float sX = (v == firstV) ? getLayoutHorizontal(res, selStartInLine) : res.wrapIndent;
                            float eX;
                            if (v == lastV) {
                                eX = getLayoutHorizontal(res, selEndInLine);
                                if (highlightNewline) {
                                    eX += spaceWidth;
                                }
                            } else {
                                // Exactly cover all characters on this visual line.
                                float textEnd = res.layout.getLineMax(v);
                                if (v > 0) textEnd += res.wrapIndent;
                                eX = textEnd;
                            }

                            if (eX > sX) {
                                float drawTop = res.layout.getLineTop(v);
                                float drawBottom = res.layout.getLineBottom(v);
                                canvas.drawRect(sX, drawTop, eX, drawBottom, mPaint);
                            }
                        }
                        canvas.restore();
                    }
                } else {
                    int lWidth;
                    if (res != null && res.layout != null) {
                        lWidth = (int) Math.ceil(res.layout.getLineWidth(0));
                    } else {
                        lWidth = getLineWidth(i);
                    }
                    lWidth += spaceWidth;

                    if (i == startLine && i == endLine) {
                        int startOffset = s - lineStartIdx;
                        int endOffset = e - lineStartIdx;
                        float sX = left + (res != null && res.layout != null ? res.layout.getPrimaryHorizontal(Math.min(startOffset, lineText.length())) : measureText(lineText.substring(0, Math.min(startOffset, lineText.length())), left));
                        float eX = left + (res != null && res.layout != null ? res.layout.getPrimaryHorizontal(Math.min(endOffset, lineText.length())) : measureText(lineText.substring(0, Math.min(endOffset, lineText.length())), left));
                        canvas.drawRect(sX, lineTop, eX, getLineBottom(i), mPaint);
                    } else if (i == startLine) {
                        int startOffset = s - lineStartIdx;
                        float sX = left + (res != null && res.layout != null ? res.layout.getPrimaryHorizontal(Math.min(startOffset, lineText.length())) : measureText(lineText.substring(0, Math.min(startOffset, lineText.length())), left));
                        canvas.drawRect(sX, lineTop, left + lWidth, getLineBottom(i), mPaint);
                    } else if (i == endLine) {
                        int endOffset = e - lineStartIdx;
                        float eX = left + (res != null && res.layout != null ? res.layout.getPrimaryHorizontal(Math.min(endOffset, lineText.length())) : measureText(lineText.substring(0, Math.min(endOffset, lineText.length())), left));
                        canvas.drawRect(left, lineTop, eX, getLineBottom(i), mPaint);
                    } else {
                        canvas.drawRect(left, lineTop, left + lWidth, getLineBottom(i), mPaint);
                    }
                }
            }
            mPaint.setAntiAlias(true);
        }

        // Draw matching brace highlight AFTER all backgrounds to ensure it is visible
        drawBraceMatch(canvas, visibleStartLine, visibleEndLine);
    }

    // Draw indentation guidelines
    private void drawIndentationGuidelines(Canvas canvas, int visibleStartLine, int visibleEndLine, LineResult[] visibleResults) {
        if (!mShowIndentGuides || mHighlighter == null || visibleResults == null) return;

        int left = getLeftSpace();
        float tabWidth = spaceWidth * mTabSize;

        for (int i = visibleStartLine; i <= visibleEndLine; i++) {
            LineResult res = visibleResults[i - visibleStartLine];
            if (res == null) continue;

            int startL = res.startBraceLevel;
            int endL = res.endBraceLevel;

            int maxL = Math.max(startL, endL);

            int top = getLineTop(i);
            int bottom = getLineBottom(i);

            for (int level = 1; level <= maxL; level++) {
                float x = left + (level - 1) * tabWidth;

                // Draw guideline only if it's within the block (starts AFTER { and ends BEFORE })
                if (level <= startL && level <= endL) {
                    canvas.drawLine(x, top, x, bottom, mGuidelinePaint);
                }
            }
        }
    }

    // Draw selection handles
    public void drawSelectHandle(Canvas canvas) {
        if (isSelectMode && !mHideSelectHandles) {
            int left = getLeftSpace();
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            // Normalize: Ensure start is always the smaller index for visual stability
            int s = Math.min(selStart, selEnd);
            int e = Math.max(selStart, selEnd);

            // Re-calculate positions JIT to ensure handles are always perfectly aligned
            // regardless of scroll, zoom, or gutter width changes.
            int startLine = getOffsetLine(s);
            LineResult startRes = (mHighlighter != null) ? mHighlighter.getOrTokenize(startLine, getLine(startLine)) : null;
            if (startRes != null && startRes.layout != null) {
                int off = s - getLineStart(startLine);
                selectHandleLeftX = left + (int) Math.ceil(getLayoutHorizontal(startRes, off));
                int vLine = getLayoutLineForOffset(startRes, off);
                selectHandleLeftY = getLineTop(startLine) + startRes.layout.getLineBottom(vLine);
            } else {
                selectHandleLeftX = left + measureText(getLine(startLine).substring(0, Math.max(0, s - getLineStart(startLine))), left);
                selectHandleLeftY = getLineBottom(startLine);
            }

            int endLine = getOffsetLine(e);
            LineResult endRes = (mHighlighter != null) ? mHighlighter.getOrTokenize(endLine, getLine(endLine)) : null;
            if (endRes != null && endRes.layout != null) {
                int off = e - getLineStart(endLine);
                selectHandleRightX = left + (int) Math.ceil(getLayoutHorizontal(endRes, off));
                int vLine = getLayoutLineForOffset(endRes, off);
                selectHandleRightY = getLineTop(endLine) + endRes.layout.getLineBottom(vLine);
            } else {
                selectHandleRightX = left + measureText(getLine(endLine).substring(0, Math.max(0, e - getLineStart(endLine))), left);
                selectHandleRightY = getLineBottom(endLine);
            }

            mTextSelectHandleLeftRes.setBounds(selectHandleLeftX - selectHandleWidth + selectHandleWidth / 4,
                    selectHandleLeftY,
                    selectHandleLeftX + selectHandleWidth / 4,
                    selectHandleLeftY + selectHandleHeight
            );

            mTextSelectHandleLeftRes.draw(canvas);

            mTextSelectHandleRightRes.setBounds(selectHandleRightX - selectHandleWidth / 4,
                    selectHandleRightY,
                    selectHandleRightX + selectHandleWidth - selectHandleWidth / 4,
                    selectHandleRightY + selectHandleHeight
            );
            mTextSelectHandleRightRes.draw(canvas);
        }
    }

    // Draw search match highlights
    public void drawMatchText(Canvas canvas) {
        if (mReplaceList != null && !mReplaceList.isEmpty()) {
            int size = mReplaceList.size();
            int left = getLeftSpace();
            Rect clip = canvas.getClipBounds();

            int visibleStartLine = getLogicalLineFromY(clip.top);
            int startOffsetVisible = getLineStart(visibleStartLine);

            int low = 0, high = size - 1, firstVisibleIdx = 0;
            while (low <= high) {
                int mid = (low + high) / 2;
                if (mReplaceList.get(mid).first >= startOffsetVisible) {
                    firstVisibleIdx = mid;
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }

            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();
            int s = Math.min(selStart, selEnd);
            int e = Math.max(selStart, selEnd);

            for (int i = firstVisibleIdx; i < size; ++i) {
                int start = mReplaceList.get(i).first;
                int end = mReplaceList.get(i).second;

                int line = mGapBuffer.findLineNumber(start);
                int lineTop = getLineTop(line);
                int lineBottom = getLineBottom(line);

                if (lineTop > clip.bottom) break;
                if (lineBottom < clip.top) continue;

                // Match containing cursor is Yellow, others are standard highlight
                if (mCursorIndex >= start && mCursorIndex <= end)
                    mPaint.setColor(Color.YELLOW);
                else
                    mPaint.setColor(Color.parseColor("#FFFD54"));

                int lineStartIdx = getLineStart(line);
                String lineText = getLine(line);
                LineResult res = (mHighlighter != null) ? mHighlighter.getOrTokenize(line, lineText) : null;

                if (res != null && res.layout != null) {
                    int offStart = start - lineStartIdx;
                    int offEnd = end - lineStartIdx;

                    int vLine = getLayoutLineForOffset(res, offStart);
                    float x1 = left + getLayoutHorizontal(res, offStart);
                    float x2 = left + getLayoutHorizontal(res, offEnd);
                    float y1 = lineTop + res.layout.getLineTop(vLine);
                    float y2 = lineTop + res.layout.getLineBottom(vLine);

                    canvas.drawRect(x1, y1, x2, y2, mPaint);
                } else {
                    float x1 = left + measureText(lineText.substring(0, Math.max(0, Math.min(start - lineStartIdx, lineText.length()))), left);
                    float x2 = left + measureText(lineText.substring(0, Math.max(0, Math.min(end - lineStartIdx, lineText.length()))), left);
                    canvas.drawRect(x1, lineTop, x2, lineBottom, mPaint);
                }
            }
        }
    }

    // Draw blinking cursor
    public void drawCursor(Canvas canvas) {
        if (mCursorVisible && !isSelectMode) {
            // Re-calculate Y position JIT to stick to text even if heights above update
            mCursorPosY = getLineTop(mCursorLine) + mCursorYOffsetWithinLine;

            int left = getLeftSpace();
            int half = 0;
            if (mCursorPosX >= left) {
                half = mCursorWidth / 2;
            } else {
                mCursorPosX = left;
            }

            int cursorHeight = getLineHeight();
            if (mWordWrap && mHighlighter != null) {
                LineResult res = mHighlighter.getOrTokenize(mCursorLine, getLine(mCursorLine));
                if (res != null && res.layout != null) {
                    int vLine = getLayoutLineForOffset(res, mCursorIndex - getLineStart(mCursorLine));
                    cursorHeight = res.layout.getLineBottom(vLine) - res.layout.getLineTop(vLine);
                }
            }

            mDrawableCursorRes.setBounds(mCursorPosX - half,
                    mCursorPosY,
                    mCursorPosX - half + mCursorWidth,
                    mCursorPosY + cursorHeight
            );
            mDrawableCursorRes.draw(canvas);
        }

        if (mHandleMiddleVisible && !isSelectMode) {
            // Update Middle Handle Y JIT
            mCursorPosY = getLineTop(mCursorLine) + mCursorYOffsetWithinLine;
            int cursorHeight = getLineHeight();
            if (mWordWrap && mHighlighter != null) {
                LineResult res = mHighlighter.getOrTokenize(mCursorLine, getLine(mCursorLine));
                if (res != null && res.layout != null) {
                    int vLine = getLayoutLineForOffset(res, mCursorIndex - getLineStart(mCursorLine));
                    cursorHeight = res.layout.getLineBottom(vLine) - res.layout.getLineTop(vLine);
                }
            }

            mTextSelectHandleMiddleRes.setBounds(mCursorPosX - handleMiddleWidth / 2,
                    mCursorPosY + cursorHeight,
                    mCursorPosX + handleMiddleWidth / 2,
                    mCursorPosY + cursorHeight + handleMiddleHeight
            );
            mTextSelectHandleMiddleRes.draw(canvas);
        }
    }

    // Draw editable text content
    public void drawEditableText(Canvas canvas) {
        Rect clip = new Rect();
        canvas.getClipBounds(clip);

        int startLine = getLogicalLineFromY(clip.top);
        int endLine = getLogicalLineFromY(clip.bottom);

        LineResult[] visibleResults = null;
        if (mHighlighter != null) {
            int count = endLine - startLine + 1;
            visibleResults = new LineResult[count];
            for (int i = 0; i < count; i++) {
                int lineIndex = startLine + i;
                visibleResults[i] = mHighlighter.getOrTokenize(lineIndex, getLine(lineIndex));
            }
        }
        drawEditableText(canvas, startLine, endLine, visibleResults);
    }

    public void drawEditableText(Canvas canvas, int startLine, int endLine, LineResult[] visibleResults) {
        int lineHeight = getLineHeight();
        Rect clip = new Rect();
        canvas.getClipBounds(clip);

        int gutterLeftPadding = ScreenUtils.dip2px(getContext(), 12);
        int lineNumberWidth = getLineNumberWidth();
        int gutterRightPadding = ScreenUtils.dip2px(getContext(), 6);
        int scrollX = getScrollX();
        int gutterAreaWidth = gutterLeftPadding + lineNumberWidth + gutterRightPadding;
        int contentStartX = getLeftSpace();

        // 1. Draw the text content first
        canvas.save();
        // Clip to the content area so text doesn't bleed into the sticky gutter
        if (mShowLineNumbers && mStickyLineNumbers) {
            canvas.clipRect(scrollX + gutterAreaWidth, clip.top, clip.right, clip.bottom);
        }

        int viewWidth = getWidth();
        // HORIZONTAL VIRTUALIZATION:
        int visibleColStart = Math.max(0, (scrollX - contentStartX) / spaceWidth - 2);
        int visibleColEnd = (scrollX - contentStartX + viewWidth) / spaceWidth + 2;

        for (int i = startLine; i <= endLine; i++) {
            int lineTop = getLineTop(i);
            int paintY = lineTop + (int) Math.ceil(-mTextPaint.ascent());
            String text = getLine(i);
            LineResult res = (visibleResults != null && i - startLine < visibleResults.length) ? visibleResults[i - startLine] : null;

            if (mHighlighter != null && res != null) {
                if (res.width > lineWidth && !mWordWrap) {
                    lineWidth = res.width;
                }
                mHighlighter.drawLineText(canvas, text, i, contentStartX, lineTop);
            } else {
                if (text != null && !mWordWrap) {
                    int currentW = measureText(text);
                    if (currentW > lineWidth) {
                        lineWidth = currentW;
                    }
                }
                mTextPaint.setColor(Color.BLACK);
                if (text != null && text.length() > visibleColEnd + 100 && !mWordWrap) {
                    int s = Math.min(text.length(), visibleColStart);
                    int e = Math.min(text.length(), visibleColEnd + 50);
                    String visiblePart = text.substring(s, e);
                    float offsetX = contentStartX + measureText(text.substring(0, s), contentStartX);
                    drawTextWithTabs(canvas, visiblePart, offsetX, paintY, mTextPaint);
                } else {
                    drawTextWithTabs(canvas, text != null ? text : "", contentStartX, paintY, mTextPaint);
                }
            }

            // Draw soft wrap icons
            if (mWordWrap && mShowWrapArrows && res != null && res.layout != null && res.layout.getLineCount() > 1) {
                int layoutLineCount = res.layout.getLineCount();
                int wrapIconSize = (int) (lineHeight * 0.45f);
                int wrapIndent = res.wrapIndent;
                int iconPadding = ScreenUtils.dip2px(getContext(), 4);

                for (int v = 0; v < layoutLineCount; v++) {
                    int vTop = lineTop + res.layout.getLineTop(v);
                    if (vTop > clip.bottom) break;
                    if (vTop + lineHeight < clip.top) continue;

                    if (v < layoutLineCount - 1) {
                        float textEnd = res.layout.getLineMax(v);
                        if (v > 0) textEnd += wrapIndent;
                        float iconX = contentStartX + textEnd + iconPadding;
                        if (iconX + wrapIconSize > contentStartX + res.width) {
                            iconX = contentStartX + res.width - wrapIconSize;
                        }

                        if (mLeftSoftWrap != null && iconX + wrapIconSize > scrollX && iconX < scrollX + viewWidth) {
                            mLeftSoftWrap.setBounds((int) iconX, vTop + (lineHeight - wrapIconSize) / 2,
                                    (int) iconX + wrapIconSize, vTop + (lineHeight + wrapIconSize) / 2);
                            mLeftSoftWrap.draw(canvas);
                        }
                    }
                    if (v > 0) {
                        float x = contentStartX + (wrapIndent - wrapIconSize) / 2f;
                        if (mRightSoftWrap != null && x + wrapIconSize > scrollX && x < scrollX + viewWidth) {
                            mRightSoftWrap.setBounds((int) x, vTop + (lineHeight - wrapIconSize) / 2,
                                    (int) x + wrapIconSize, vTop + (lineHeight + wrapIconSize) / 2);
                            mRightSoftWrap.draw(canvas);
                        }
                    }
                }
            }
            // Draw composing underline
            if (mComposingStart != -1 && mComposingEnd != -1) {
                int lineStart = getLineStart(i);
                int lineEnd = getLineEnd(i);
                int compStart = Math.max(lineStart, mComposingStart);
                int compEnd = Math.min(lineEnd, mComposingEnd);

                if (compStart < compEnd) {
                    float startX, endX, underlineY;
                    if (mWordWrap && res != null && res.layout != null) {
                        int offStart = compStart - lineStart;
                        int offEnd = compEnd - lineStart;
                        int vLine = getLayoutLineForOffset(res, offStart);
                        startX = contentStartX + getLayoutHorizontal(res, offStart);
                        endX = contentStartX + getLayoutHorizontal(res, offEnd);
                        underlineY = lineTop + res.layout.getLineBottom(vLine) - 2;
                    } else if (res != null && res.layout != null) {
                        int offStart = compStart - lineStart;
                        int offEnd = compEnd - lineStart;
                        startX = contentStartX + getLayoutHorizontal(res, offStart);
                        endX = contentStartX + getLayoutHorizontal(res, offEnd);
                        underlineY = paintY + 2;
                    } else {
                        startX = contentStartX + measureText(text.substring(0, Math.max(0, Math.min(compStart - lineStart, text.length()))), contentStartX);
                        endX = contentStartX + measureText(text.substring(0, Math.max(0, Math.min(compEnd - lineStart, text.length()))), contentStartX);
                        underlineY = paintY + 2;
                    }

                    mPaint.setColor(mTextPaint.getColor());
                    mPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(startX, underlineY, endX, underlineY + 2, mPaint);
                }
            } else if (i == mCursorLine && !isSelectMode) {
                // Word underlining at cursor
                if (mCursorWordBounds != null) {
                    int lineStart = getLineStart(i);
                    int wordStart = Math.max(lineStart, mCursorWordBounds[0]);
                    int wordEnd = Math.min(getLineEnd(i) + 1, mCursorWordBounds[1]);

                    if (wordStart < wordEnd) {
                        float startX, endX, underlineY;

                        if (mWordWrap && res != null && res.layout != null) {
                            int offStart = wordStart - lineStart;
                            int offEnd = wordEnd - lineStart;
                            int vLine = getLayoutLineForOffset(res, offStart);
                            startX = contentStartX + getLayoutHorizontal(res, offStart);
                            endX = contentStartX + getLayoutHorizontal(res, offEnd);
                            underlineY = lineTop + res.layout.getLineBottom(vLine) - 2;
                        } else if (res != null && res.layout != null) {
                            int offStart = wordStart - lineStart;
                            int offEnd = wordEnd - lineStart;
                            startX = contentStartX + getLayoutHorizontal(res, offStart);
                            endX = contentStartX + getLayoutHorizontal(res, offEnd);
                            underlineY = paintY + 2;
                        } else {
                            startX = contentStartX + measureText(text.substring(0, Math.max(0, Math.min(wordStart - lineStart, text.length()))), contentStartX);
                            endX = contentStartX + measureText(text.substring(0, Math.max(0, Math.min(wordEnd - lineStart, text.length()))), contentStartX);
                            underlineY = paintY + 2;
                        }

                        mPaint.setColor(Color.parseColor("#55534C"));
                        mPaint.setAlpha(120);
                        mPaint.setStyle(Paint.Style.FILL);
                        canvas.drawRect(startX, underlineY, endX, underlineY + 3, mPaint);
                        mPaint.setAlpha(255);
                    }
                }
            }
        }
        canvas.restore();

        // 2. Draw the Sticky Gutter ON TOP
        if (mShowLineNumbers) {
            int gutterStartX = mStickyLineNumbers ? scrollX : 0;

            // Gutter background
            mPaint.setColor(Color.parseColor("#F8F8F8"));
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(
                    gutterStartX,
                    clip.top,
                    gutterStartX + gutterAreaWidth,
                    clip.bottom,
                    mPaint
            );

            // Separator line
            int separatorWidth = 3;
            int separatorX = gutterStartX + gutterAreaWidth;
            mPaint.setColor(Color.parseColor("#E4E4E4"));
            mPaint.setStrokeWidth(separatorWidth);
            canvas.drawLine(
                    separatorX,
                    clip.top,
                    separatorX,
                    clip.bottom,
                    mPaint
            );

            // Line numbers
            mTextPaint.setColor(Color.parseColor("#B0B0B0"));
            for (int i = startLine; i <= endLine; i++) {
                int lineTop = getLineTop(i);
                String lineNumberText = String.valueOf(i);
                int textWidth = (int) mTextPaint.measureText(lineNumberText);
                int paintY = lineTop + (int) Math.ceil(-mTextPaint.ascent());
                float drawX = gutterStartX + gutterLeftPadding + (lineNumberWidth - textWidth);
                canvas.drawText(lineNumberText, drawX, paintY, mTextPaint);
            }
        }
    }

    // Draw brace match highlights
    private void drawBraceMatch(Canvas canvas, int visibleStartLine, int visibleEndLine) {
        if (mCurrentBraceIndex != -1 && mCurrentBraceLine != -1) {
            drawSingleBraceHighlight(canvas, mCurrentBraceIndex, mCurrentBraceLine, visibleStartLine, visibleEndLine);
        }
        if (mMatchingBraceIndex != -1 && mMatchingBraceLine != -1) {
            drawSingleBraceHighlight(canvas, mMatchingBraceIndex, mMatchingBraceLine, visibleStartLine, visibleEndLine);
        }

        // Draw continuous gutter background between braces
        if (mShowLineNumbers && mCurrentBraceLine != -1 && mMatchingBraceLine != -1) {
            int startLine = Math.min(mCurrentBraceLine, mMatchingBraceLine);
            int endLine = Math.max(mCurrentBraceLine, mMatchingBraceLine);

            int drawStartLine = Math.max(visibleStartLine, startLine);
            int drawEndLine = Math.min(visibleEndLine, endLine);

            if (drawStartLine <= drawEndLine) {
                int gutterLeftPadding = ScreenUtils.dip2px(getContext(), 12);
                int lineNumberWidth = getLineNumberWidth();
                int gutterRightPadding = ScreenUtils.dip2px(getContext(), 6);
                int gutterAreaWidth = gutterLeftPadding + lineNumberWidth + gutterRightPadding;
                int gutterStartX = mStickyLineNumbers ? getScrollX() : 0;

                Paint gutterPaint = new Paint(mBracePaint);
                gutterPaint.setAlpha(26);
                canvas.drawRect(gutterStartX, getLineTop(drawStartLine), gutterStartX + gutterAreaWidth, getLineBottom(drawEndLine), gutterPaint);
            }
        }
    }

    private void drawSingleBraceHighlight(Canvas canvas, int index, int line, int visibleStartLine, int visibleEndLine) {
        if (line < visibleStartLine || line > visibleEndLine) return;

        int left = getLeftSpace();
        int lineStart = getLineStart(line);
        String text = getLine(line);
        int offset = index - lineStart;

        if (text == null || offset < 0 || offset >= text.length()) return;

        float braceX;
        float braceWidth;
        float braceTop;
        float braceBottom;

        LineResult result = (mHighlighter != null) ? mHighlighter.getOrTokenize(line, text) : null;
        if (result != null && result.layout != null) {
            int offsetInLine = offset;
            int vLine = getLayoutLineForOffset(result, offsetInLine);

            braceX = left + getLayoutHorizontal(result, offsetInLine);

            // Calculate width safely using layouts
            int nextOffset = offsetInLine + 1;
            float nextX;
            if (nextOffset <= text.length() && getLayoutLineForOffset(result, nextOffset) == vLine) {
                nextX = left + getLayoutHorizontal(result, nextOffset);
                braceWidth = nextX - braceX;
            } else {
                // If it's the last character on a visual line, measure the character itself
                String charAt = text.substring(offsetInLine, offsetInLine + 1);
                braceWidth = mTextPaint.measureText(charAt);
            }

            // Draw matching brace highlight AFTER all backgrounds to ensure it is visible
            braceTop = getLineTop(line) + result.layout.getLineTop(vLine);
            braceBottom = getLineTop(line) + result.layout.getLineBottom(vLine);
        } else {
            braceX = left + measureText(text.substring(0, offset), left);
            braceWidth = measureText(text.substring(offset, offset + 1), left);
            braceTop = getLineTop(line);
            braceBottom = getLineBottom(line);
        }

        canvas.drawRect(braceX, braceTop, braceX + braceWidth, braceBottom, mBracePaint);
    }

    private void drawBraceSeparatorHighlight(Canvas canvas) {
        if (mShowLineNumbers && mCurrentBraceLine != -1 && mMatchingBraceLine != -1) {
            int startLine = Math.min(mCurrentBraceLine, mMatchingBraceLine);
            int endLine = Math.max(mCurrentBraceLine, mMatchingBraceLine);

            Rect clip = canvas.getClipBounds();
            int visibleStartLine = getLogicalLineFromY(clip.top);
            int visibleEndLine = getLogicalLineFromY(clip.bottom);

            int drawStartLine = Math.max(visibleStartLine, startLine);
            int drawEndLine = Math.min(visibleEndLine, endLine);

            if (drawStartLine <= drawEndLine) {
                int gutterLeftPadding = ScreenUtils.dip2px(getContext(), 12);
                int lineNumberWidth = getLineNumberWidth();
                int gutterRightPadding = ScreenUtils.dip2px(getContext(), 6);
                int gutterAreaWidth = gutterLeftPadding + lineNumberWidth + gutterRightPadding;
                int gutterStartX = mStickyLineNumbers ? getScrollX() : 0;
                int separatorX = gutterStartX + gutterAreaWidth;
                int separatorWidth = 3;

                Paint separatorPaint = new Paint();
                separatorPaint.setColor(Color.parseColor("#669797"));
                separatorPaint.setStrokeWidth(separatorWidth);

                float startY = getLineTop(drawStartLine);
                float endY = getLineBottom(drawEndLine);

                canvas.drawLine(separatorX, startY, separatorX, endY, separatorPaint);
            }
        }
    }

    // No-origin version: used only for widths that don't need tab-stop anchoring
    // (line number width, lineWidth tracking, getLineWidth)
    public int measureText(String text) {
        return measureText(text, 0f);
    }

    // Origin-aware version: ALL drawing/cursor/selection X positions must use this
    public int measureText(String text, float originX) {
        if (text == null || text.isEmpty()) return 0;

        if (text.indexOf('\t') == -1) {
            return (int) Math.ceil(mTextPaint.measureText(text));
        }

        float totalWidth = 0;
        float spaceWidth = mTextPaint.measureText(" ");
        float tabWidth = spaceWidth * mTabSize;

        int len = text.length();
        int start = 0;
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '\t') {
                if (i > start) {
                    totalWidth += mTextPaint.measureText(text, start, i);
                }
                float absoluteX = originX + totalWidth;
                float nextTab = ((int) (absoluteX / tabWidth + 0.001f) + 1) * tabWidth;
                totalWidth = nextTab - originX;
                start = i + 1;
            }
        }
        if (start < len) {
            totalWidth += mTextPaint.measureText(text, start, len);
        }
        return (int) Math.ceil(totalWidth);
    }

    private void drawTextWithTabs(Canvas canvas, String text, float x, float y, Paint paint) {
        if (text == null || text.isEmpty()) return;

        float currentX = x;
        float spaceWidth = paint.measureText(" ");
        float tabWidth = spaceWidth * mTabSize;

        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\t') {
                if (i > start) {
                    String segment = text.substring(start, i);
                    canvas.drawText(segment, currentX, y, paint);
                    currentX += paint.measureText(segment);
                }
                // Tab stop relative to content origin x
                float relativeX = currentX - x;
                float nextTabRel = ((int) (relativeX / tabWidth + 0.001f) + 1) * tabWidth;
                currentX = x + nextTabRel;
                start = i + 1;
            }
        }
        if (start < text.length()) {
            canvas.drawText(text.substring(start), currentX, y, paint);
        }
    }

    public int getLineHeight() {
        TextPaint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        return metrics.bottom - metrics.top;
    }

    private int getLineNumberWidth() {
        return measureText(Integer.toString(getLineCount()));
    }

    public int getLeftSpace() {
        if (!mShowLineNumbers) {
            return ScreenUtils.dip2px(getContext(), 12);
        }
        int gutterLeftPadding = ScreenUtils.dip2px(getContext(), 12);
        int lineNumberWidth = getLineNumberWidth();
        int gutterRightPadding = ScreenUtils.dip2px(getContext(), 6);
        int separatorWidth = 2;
        int contentPadding = ScreenUtils.dip2px(getContext(), 4);

        return gutterLeftPadding + lineNumberWidth + gutterRightPadding + separatorWidth + contentPadding;
    }

    private int getLineTop(int line) {
        if (!mWordWrap) {
            return (line - 1) * getLineHeight();
        }
        return mHeightManager.getTop(line);
    }

    private int getLineBottom(int line) {
        if (!mWordWrap) {
            return line * getLineHeight();
        }
        return mHeightManager.getBottom(line);
    }

    private void computeLineTops() {
        int count = getLineCount();
        // DO NOT measure every line here.
        mHeightManager.init(count, getLineHeight());
        mTotalHeight = mHeightManager.getTotalHeight();
    }

    private int getLogicalLineFromY(float y) {
        if (!mWordWrap) {
            int line = (int) (y / getLineHeight()) + 1;
            return Math.max(1, Math.min(line, getLineCount()));
        }
        return mHeightManager.getLineAtY((int) y);
    }

    private void ensureHeightsMeasuredUntil(int targetY) {
        // No-op for performance. Individual interactions now measure JIT.
    }

    private float getLayoutHorizontal(LineResult res, int originalOffsetInLine) {
        if (res == null || res.layout == null) return 0;
        int layoutOffset = originalOffsetInLine;
        if (res.shiftMap != null && originalOffsetInLine >= 0) {
            if (originalOffsetInLine < res.shiftMap.length) {
                layoutOffset += res.shiftMap[originalOffsetInLine];
            } else if (res.shiftMap.length > 0) {
                // For the end of the line, use the last known shift
                layoutOffset += res.shiftMap[res.shiftMap.length - 1];
            }
        }
        int clampedOffset = Math.max(0, Math.min(layoutOffset, res.layout.getText().length()));
        return res.layout.getPrimaryHorizontal(clampedOffset);
    }

    private int getLayoutLineForOffset(LineResult res, int originalOffsetInLine) {
        if (res == null || res.layout == null) return 0;
        int layoutOffset = originalOffsetInLine;
        if (res.shiftMap != null && originalOffsetInLine >= 0) {
            if (originalOffsetInLine < res.shiftMap.length) {
                layoutOffset += res.shiftMap[originalOffsetInLine];
            } else if (res.shiftMap.length > 0) {
                layoutOffset += res.shiftMap[res.shiftMap.length - 1];
            }
        }
        return res.layout.getLineForOffset(Math.max(0, Math.min(layoutOffset, res.layout.getText().length())));
    }

    private int getOriginalOffset(LineResult res, int layoutLine, float x) {
        if (res == null || res.layout == null) return 0;
        int layoutOffset = res.layout.getOffsetForHorizontal(layoutLine, x);
        if (res.shiftMap == null) return layoutOffset;

        int len = res.shiftMap.length;
        if (len == 0) return layoutOffset;

        // Binary search for the original offset that maps to layoutOffset
        int low = 0;
        int high = len; // Can return up to len (end of string)
        while (low < high) {
            int mid = (low + high) / 2;
            int currentShift = (mid < len) ? res.shiftMap[mid] : res.shiftMap[len - 1];
            int modIdx = mid + currentShift;

            if (modIdx < layoutOffset) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public Rect getBoundingBox(int index) {
        int left = getLeftSpace();
        int line = getOffsetLine(index);
        int lineStart = getLineStart(line);
        int x;
        int y;
        int height;

        if (mHighlighter != null) {
            LineResult result = mHighlighter.getOrTokenize(line, getLine(line));
            if (result != null && result.layout != null) {
                int offsetInLine = index - lineStart;
                x = left + (int) Math.ceil(getLayoutHorizontal(result, offsetInLine));
                if (mWordWrap) {
                    int vLine = getLayoutLineForOffset(result, offsetInLine);
                    y = getLineTop(line) + result.layout.getLineTop(vLine);
                    height = result.layout.getLineBottom(vLine) - result.layout.getLineTop(vLine);
                } else {
                    y = getLineTop(line);
                    height = getLineHeight();
                }
            } else {
                String text = mGapBuffer.substring(lineStart, Math.min(index, mGapBuffer.length()));
                x = left + measureText(text);
                y = getLineTop(line);
                height = getLineHeight();
            }
        } else {
            String text = mGapBuffer.substring(lineStart, Math.min(index, mGapBuffer.length()));
            x = left + measureText(text);
            y = getLineTop(line);
            height = getLineHeight();
        }

        int viewX = x - getScrollX() + getPaddingLeft();
        int viewY = y - getScrollY() + getPaddingTop();

        return new Rect(viewX, viewY, viewX + (int) mTextPaint.measureText(" "), viewY + height);
    }

    private void calculateMaxWidth() {
        if (mGapBuffer == null || mIsCalculatingMaxWidth || mWordWrap) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                mIsCalculatingMaxWidth = true;
                try {
                    int maxChars = 0;
                    int lineCount = getLineCount();
                    float spaceW = mTextPaint.measureText(" ");

                    // Faster estimation of max width by calling getLineOffset once per line
                    int lastOffset = mGapBuffer.getLineOffset(1);
                    for (int i = 1; i <= lineCount; i++) {
                        // Yield to other threads periodically
                        if (i % 500 == 0) {
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                            }
                        }

                        int nextOffset = (i < lineCount) ? mGapBuffer.getLineOffset(i + 1) : mGapBuffer.length();
                        int lineLen = nextOffset - lastOffset;
                        // Skip newline char if present
                        if (lineLen > 0 && i < lineCount) lineLen--;

                        if (lineLen > maxChars) {
                            maxChars = lineLen;
                        }
                        lastOffset = nextOffset;
                    }

                    final int finalLineWidth = (int) (maxChars * spaceW) + (mTabSize * (int) spaceW);

                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (finalLineWidth > lineWidth) {
                                lineWidth = finalLineWidth;
                                postInvalidate();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error calculating max width", e);
                } finally {
                    mIsCalculatingMaxWidth = false;
                }
            }
        }).start();
    }

    private void updateLineWidth() {
        int lineCount = getLineCount();
        if (mCursorLine < 1 || mCursorLine > lineCount) {
            mCursorLine = Math.max(1, Math.min(mCursorLine, lineCount));
        }
        int currentLineW = measureText(getLine(mCursorLine));
        if (currentLineW > lineWidth) {
            lineWidth = currentLineW;
        }
    }

    private int getWrapWidth() {
        int width = getWidth() - getLeftSpace() - ScreenUtils.dip2px(getContext(), 24);
        return Math.max(width, 100); // Minimum width
    }

    public int getMaxScrollX() {
        if (mWordWrap) return 0;
        return Math.max(0, getLeftSpace() + lineWidth + spaceWidth * 4 - getWidth());
    }

    public int getMaxScrollY() {
        int height = mWordWrap ? mTotalHeight : (getLineCount() + 2) * getLineHeight();
        return Math.max(0, height - getHeight() + (mWordWrap ? getLineHeight() : 0));
    }

    private void clearSyntaxCache() {
        if (mHighlighter != null) {
            mHighlighter.clearCache();
            postInvalidate();
        }
    }

    public boolean getEditedMode() {
        return isEditedMode;
    }

    public void setEditedMode(boolean editMode) {
        isEditedMode = editMode;
    }

    public boolean isShowLineNumbers() {
        return mShowLineNumbers;
    }

    public void setShowLineNumbers(boolean show) {
        if (mShowLineNumbers != show) {
            mShowLineNumbers = show;
            requestLayout();
            postInvalidate();
        }
    }

    public boolean isStickyLineNumbers() {
        return mStickyLineNumbers;
    }

    public void setStickyLineNumbers(boolean sticky) {
        if (mStickyLineNumbers != sticky) {
            mStickyLineNumbers = sticky;
            postInvalidate();
        }
    }

    public boolean isWordWrap() {
        return mWordWrap;
    }

    public void setWordWrap(boolean enabled) {
        if (mWordWrap != enabled) {
            mWordWrap = enabled;
            if (mHighlighter != null) {
                mHighlighter.setWordWrap(enabled, getWrapWidth());
                mHighlighter.clearCache();
            }
            if (enabled) {
                computeLineTops();
                // Performance Optimization: Measure currently visible lines plus cursor line JIT
                // to avoid "jumping" when wrap is first enabled.
                int scrollY = getScrollY();
                int height = getHeight();
                if (height > 0) {
                    int start = Math.max(1, getLogicalLineFromY(scrollY));
                    int end = Math.min(getLineCount(), getLogicalLineFromY(scrollY + height) + 1);
                    for (int i = start; i <= end; i++) {
                        LineResult res = mHighlighter != null ? mHighlighter.getOrTokenize(i, getLine(i)) : null;
                        if (res != null && res.layout != null) {
                            mHeightManager.updateHeight(i, res.layout.getHeight());
                        }
                    }
                    mTotalHeight = mHeightManager.getTotalHeight();
                }
            } else {
                mLineTops = null;
                mTotalHeight = 0;
                lineWidth = 0;
                updateLineWidth();
            }
            adjustCursorPosition();
            if (isSelectMode) {
                updateSelectionHandles();
            }
            requestLayout();
            postInvalidate();
        }
    }

    public boolean isAutoIndentEnabled() {
        return mAutoIndentEnabled;
    }

    public void setAutoIndentEnabled(boolean enabled) {
        mAutoIndentEnabled = enabled;
    }

    public boolean isShowIndentGuides() {
        return mShowIndentGuides;
    }

    public void setShowIndentGuides(boolean show) {
        if (mShowIndentGuides != show) {
            mShowIndentGuides = show;
            postInvalidate();
        }
    }

    public boolean isShowWrapArrows() {
        return mShowWrapArrows;
    }

    public void setShowWrapArrows(boolean show) {
        if (mShowWrapArrows != show) {
            mShowWrapArrows = show;
            postInvalidate();
        }
    }

    public boolean isMagnifierEnabled() {
        return mMagnifierEnabled;
    }

    public void setMagnifierEnabled(boolean enabled) {
        mMagnifierEnabled = enabled;
        if (!enabled && mIsMagnifierShowing) {
            dismissMagnifier();
        }
    }

    public boolean isAutoCompleteEnabled() {
        return mAutoCompleteEnabled;
    }

    public void setAutoCompleteEnabled(boolean enabled) {
        this.mAutoCompleteEnabled = enabled;
        if (!enabled) {
            dismissAutoComplete();
        }
    }

    public void setTypeface(Typeface typeface) {
        mTextPaint.setTypeface(typeface);
        spaceWidth = (int) Math.ceil(mTextPaint.measureText(" "));
        if (spaceWidth <= 0) spaceWidth = 1;
        if (mHighlighter != null) {
            mHighlighter.updateTabStops();
            mHighlighter.clearLayoutCache();
        }
        lineWidth = 0;
        updateLineWidth();
        adjustCursorPosition();
        if (isSelectMode) {
            updateSelectionHandles();
        }
        postInvalidate();
    }

    public int getTabSize() {
        return mTabSize;
    }

    public void setTabSize(int size) {
        if (size > 0 && size != mTabSize) {
            mTabSize = size;
            if (mHighlighter != null) {
                mHighlighter.setTabSize(size);
            }
            clearSyntaxCache();
            invalidate();
        }
    }

    public float getTextSize() {
        return mTextPaint.getTextSize();
    }

    public void setTextSize(float size) {
        setTextSize(size, 0, 0, false);
    }

    public void setTextSize(float size, float focusX, float focusY, boolean useFocus) {
        float min = ScreenUtils.dip2px(getContext(), 10);
        float max = ScreenUtils.dip2px(getContext(), 30);

        float px = size;
        if (px < min) px = min;
        if (px > max) px = max;

        float oldSize = mTextPaint.getTextSize();
        if (px == oldSize) return;

        int oldLineHeight = getLineHeight();
        int oldLeftSpace = getLeftSpace();
        int oldScrollX = getScrollX();
        int oldScrollY = getScrollY();

        // Calculate focal point in content coordinates
        float vFocusX = focusX - getPaddingLeft();
        float vFocusY = focusY - getPaddingTop();
        float contentX = vFocusX + oldScrollX;
        float contentY = vFocusY + oldScrollY;

        int focalLine = getLogicalLineFromY(contentY);
        float lineTop = getLineTop(focalLine);
        float lineHeightOnFocal = getLineBottom(focalLine) - lineTop;
        float relativeYInLine = (lineHeightOnFocal > 0) ? (contentY - lineTop) / lineHeightOnFocal : 0;

        mTextPaint.setTextSize(px);

        // Update spaceWidth for consistent measurements across the editor
        spaceWidth = (int) Math.ceil(mTextPaint.measureText(" "));

        int newLeftSpace = getLeftSpace();
        int newLineHeightTotal = getLineHeight();
        float ratioX = px / oldSize;
        float ratioY = (float) newLineHeightTotal / oldLineHeight;

        if (mHighlighter != null) {
            mHighlighter.clearLayoutCache();
            mHighlighter.updateTabStops();
            if (mWordWrap) {
                mHighlighter.setWordWrap(true, getWrapWidth());
            }
        }

        if (mWordWrap) {
            mHeightManager.scaleHeights(ratioY, newLineHeightTotal);
            mTotalHeight = mHeightManager.getTotalHeight();
        }

        // Approximate new horizontal bounds
        lineWidth = (int) (lineWidth * ratioX);

        adjustCursorPosition();
        if (isSelectMode) {
            updateSelectionHandles();
        }

        if (useFocus) {
            int newScrollX;
            // Sticky Left Side: If we are at the left edge or zooming near the gutter, keep it at 0
            if (oldScrollX <= 10 && vFocusX < newLeftSpace * 1.5f) {
                newScrollX = 0;
            } else {
                // Precise scaling relative to text start, accounting for the gutter
                float textRelX = contentX - oldLeftSpace;
                float newContentX = newLeftSpace + textRelX * ratioX;
                newScrollX = (int) (newContentX - vFocusX);
            }

            // Precise vertical scaling using line-relative positioning
            float newFocalLineTop = getLineTop(focalLine);
            float newFocalLineHeight = getLineBottom(focalLine) - newFocalLineTop;
            int newScrollY = (int) (newFocalLineTop + relativeYInLine * newFocalLineHeight - vFocusY);

            newScrollX = Math.max(0, Math.min(newScrollX, getMaxScrollX()));
            newScrollY = Math.max(0, Math.min(newScrollY, getMaxScrollY()));

            scrollTo(newScrollX, newScrollY);
        } else {
            // Scale scroll position relative to text start to avoid gutter-induced drift
            int newScrollX = (int) ((oldScrollX + oldLeftSpace) * ratioX - newLeftSpace);
            int newScrollY = (int) (oldScrollY * (float) newLineHeightTotal / oldLineHeight);

            newScrollX = Math.max(0, Math.min(newScrollX, getMaxScrollX()));
            newScrollY = Math.max(0, Math.min(newScrollY, getMaxScrollY()));

            scrollTo(newScrollX, newScrollY);
        }
        postInvalidate();
    }

    public void setSyntaxLanguageFileName(String languageFile) {
        if (languageFile == null || languageFile.isEmpty()) {
            mHighlighter = new MHSyntaxHighlightEngine(getContext(), mTextPaint, null, isSyntaxDarkMode);
            mHighlighter.setLineProvider(new MHSyntaxHighlightEngine.LineProvider() {
                @Override
                public String getLine(int index) {
                    return EditView.this.getLine(index);
                }
            });
            lineWidth = 0;
            updateLineWidth();
            adjustCursorPosition();
            if (isSelectMode) {
                updateSelectionHandles();
            }
            postInvalidate();
            dismissAutoComplete();
            return;
        }
        mHighlighter = new MHSyntaxHighlightEngine(getContext(), mTextPaint, languageFile, isSyntaxDarkMode);
        mHighlighter.setWordWrap(mWordWrap, getWrapWidth());
        mHighlighter.setLineProvider(new MHSyntaxHighlightEngine.LineProvider() {
            @Override
            public String getLine(int index) {
                return EditView.this.getLine(index);
            }
        });
        lineWidth = 0;
        updateLineWidth();
        adjustCursorPosition();
        if (isSelectMode) {
            updateSelectionHandles();
        }
        postInvalidate();
        dismissAutoComplete();
    }

    public void setInstructions(Set<String> instructions) {
        this.mWordSet = instructions;
    }

    public String getCommentBlock() {
        String block = mHighlighter.getCommentSyntaxBlock();
        if (block == null) return "";
        return block;
    }

    public void setOnTextChangedListener(OnTextChangedListener listener) {
        mTextListener = listener;
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        mSelectionListener = listener;
    }

    public void addTextChangedListener(TextWatcher watcher) {
        if (mGapBuffer != null) {
            mGapBuffer.addTextChangedListener(watcher);
        }
    }

    public void removeTextChangedListener(TextWatcher watcher) {
        if (mGapBuffer != null) {
            mGapBuffer.removeTextChangedListener(watcher);
        }
    }

    public void setMenuStyle(ClipboardPanel.MenuDisplayMode mode) {
        mClipboardPanel.setMenuDisplayMode(mode);
    }

    public GapBuffer getBuffer() {
        return this.mGapBuffer;
    }

    public void setBuffer(GapBuffer buffer) {
        if (mGapBuffer != null) {
            mGapBuffer.removeTextChangedListener(mInternalWatcher);
        }
        mGapBuffer = buffer != null ? buffer : new GapBuffer();
        mGapBuffer.addTextChangedListener(mInternalWatcher);
        Selection.setSelection(mGapBuffer, 0, 0);
        mCursorIndex = 0;
        mCursorLine = 1;
        mCursorPosX = getLeftSpace();
        mCursorPosY = 0;
        mComposingStart = -1;
        mComposingEnd = -1;
        isSelectMode = false;
        mHandleMiddleVisible = false;
        mPendingDeleteStart = -1;
        mPendingDeleteEnd = -1;
        spaceWidth = (int) Math.ceil(mTextPaint.measureText(" "));
        if (spaceWidth <= 0) spaceWidth = 1;
        clearSyntaxCache();
        lineWidth = 0;
        calculateMaxWidth();
        if (mWordWrap) {
            computeLineTops();
        }
        adjustCursorPosition();
        onCursorOrSelectionChanged();
        scrollTo(0, 0);
        dismissAutoComplete();
        initialWordScan();
        recordHistory(0); // Set initial history point
        requestLayout();
        postInvalidate();
        post(new Runnable() {
            @Override
            public void run() {
                clearSyntaxCache();
                lineWidth = 0;
                updateLineWidth();
                adjustCursorPosition();
                if (isSelectMode) {
                    updateSelectionHandles();
                }
                postInvalidate();
            }
        });
    }

    public Editable getText() {
        return mGapBuffer;
    }

    public void setText(String text) {
        setBuffer(new GapBuffer(text));
    }

    public String getLine(int lineNumber) {
        return mGapBuffer.getLine(lineNumber);
    }

    private int getLineStart(int lineNumber) {
        return mGapBuffer.getLineOffset(lineNumber);
    }

    private int getLineEnd(int line) {
        int nextLine = line + 1;
        if (nextLine > getLineCount()) {
            return mGapBuffer.length();
        }
        return getLineStart(nextLine) - 1;
    }

    private int getOffsetLine(int offset) {
        return mGapBuffer.findLineNumber(offset);
    }

    public int getLineCount() {
        return mGapBuffer.getLineCount();
    }

    public int getLineWidth() {
        return lineWidth;
    }

    private int getLineWidth(int lineNumber) {
        return measureText(getLine(lineNumber));
    }

    public int getSpaceWidth() {
        return spaceWidth;
    }

    public int getColumn() {
        if (mCursorLine < 1 || mCursorLine > getLineCount()) {
            return 0;
        }
        int lineStart = getLineStart(mCursorLine);
        return mCursorIndex - lineStart;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
                | EditorInfo.IME_ACTION_DONE
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        outAttrs.initialSelStart = getSelectionStart();
        outAttrs.initialSelEnd = getSelectionEnd();

        return new TextInputConnection(this, true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEditedMode) return super.onKeyDown(keyCode, event);

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "onKeyDown: keyCode=" + keyCode + ", unicode=" + event.getUnicodeChar());

            boolean ctrl = event.isCtrlPressed();
            boolean shift = event.isShiftPressed();

            if (ctrl) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_A:
                        selectAll();
                        return true;
                    case KeyEvent.KEYCODE_C:
                        if (isSelectMode) copy();
                        else copyLine();
                        return true;
                    case KeyEvent.KEYCODE_X:
                        if (isSelectMode) cut();
                        else cutLine();
                        return true;
                    case KeyEvent.KEYCODE_V:
                        paste();
                        return true;
                    case KeyEvent.KEYCODE_Z:
                        if (shift) redo();
                        else undo();
                        return true;
                    case KeyEvent.KEYCODE_Y:
                        redo();
                        return true;
                    case KeyEvent.KEYCODE_D:
                        duplicateLine();
                        return true;
                    case KeyEvent.KEYCODE_L:
                        if (shift) deleteLine();
                        else selectSingleLine(mCursorLine);
                        return true;
                    case KeyEvent.KEYCODE_SLASH:
                        toggleComment();
                        return true;
                    case KeyEvent.KEYCODE_I:
                        if (shift) decreaseIndent();
                        else increaseIndent();
                        return true;
                    case KeyEvent.KEYCODE_U:
                        if (shift) convertSelectionToUpperCase();
                        else convertSelectionToLowerCase();
                        return true;
                }
            }

            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    insert("\n");
                    return true;

                case KeyEvent.KEYCODE_DEL:
                    if (ctrl) {
                        int start = findPreviousWordStart(mCursorIndex);
                        replaceInternal(start, mCursorIndex, "");
                    } else {
                        delete();
                    }
                    return true;

                case KeyEvent.KEYCODE_FORWARD_DEL:
                    if (ctrl) {
                        int end = findNextWordEnd(mCursorIndex);
                        replaceInternal(mCursorIndex, end, "");
                    } else {
                        handleForwardDelete();
                    }
                    return true;

                case KeyEvent.KEYCODE_SPACE:
                    insert(" ");
                    return true;

                case KeyEvent.KEYCODE_TAB:
                    if (shift) decreaseIndent();
                    else insert("\t");
                    return true;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (ctrl) moveCursor(8, shift);
                    else moveCursor(0, shift);
                    return true;

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (ctrl) moveCursor(9, shift);
                    else moveCursor(1, shift);
                    return true;

                case KeyEvent.KEYCODE_DPAD_UP:
                    moveCursor(2, shift);
                    return true;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveCursor(3, shift);
                    return true;

                case KeyEvent.KEYCODE_PAGE_UP:
                    moveCursor(10, shift);
                    return true;

                case KeyEvent.KEYCODE_PAGE_DOWN:
                    moveCursor(11, shift);
                    return true;

                case KeyEvent.KEYCODE_MOVE_HOME:
                    if (ctrl) moveCursor(6, shift);
                    else moveCursor(4, shift);
                    return true;

                case KeyEvent.KEYCODE_MOVE_END:
                    if (ctrl) moveCursor(7, shift);
                    else moveCursor(5, shift);
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    public void showSoftInput(boolean show) {
        if (isEditedMode) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (show)
                imm.showSoftInput(this, 0);
            else
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mLastTouchX = event.getX();
        mLastTouchY = event.getY();

        if (handleFastScrollerTouch(event)) {
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            requestFocus();
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

    private boolean handleFastScrollerTouch(MotionEvent event) {
        int maxScrollY = getMaxScrollY();
        int maxScrollX = getMaxScrollX();
        if (maxScrollY <= 0 && maxScrollX <= 0) return false;

        float x = event.getX();
        float y = event.getY();
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // Constants matching drawFastScroller
        int minLength = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_MIN_THUMB_LENGTH_DP);
        int thicknessNormal = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_THUMB_THICKNESS_DP);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check vertical thumb
                if (maxScrollY > 0) {
                    int vTrackHeight = viewHeight - minLength;
                    int vThumbY = (int) (((float) getScrollY() / maxScrollY) * vTrackHeight);
                    int vThumbRight = viewWidth;
                    int vThumbLeft = vThumbRight - thicknessNormal;

                    // Expand touch area for easier grabbing
                    int slop = ScreenUtils.dip2px(getContext(), 12);
                    if (x >= vThumbLeft - slop && x <= vThumbRight + slop && y >= vThumbY && y <= vThumbY + minLength) {
                        mScroller.abortAnimation();
                        mIsDraggingFastScroller = true;
                        mFastScrollerTouchOffset = y - vThumbY;
                        mLastScrollTimeFast = System.currentTimeMillis();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        updateScrollingFromTouch(y);
                        invalidate();
                        return true;
                    }
                }
                // Check horizontal thumb
                if (maxScrollX > 0) {
                    int hTrackWidth = viewWidth - minLength;
                    int hThumbX = (int) (((float) getScrollX() / maxScrollX) * hTrackWidth);
                    int hThumbBottom = viewHeight;
                    int hThumbTop = hThumbBottom - thicknessNormal;

                    int slop = ScreenUtils.dip2px(getContext(), 12);
                    if (y >= hThumbTop - slop && y <= hThumbBottom + slop && x >= hThumbX && x <= hThumbX + minLength) {
                        mScroller.abortAnimation();
                        mIsDraggingFastScrollerHorizontally = true;
                        mFastScrollerTouchOffset = x - hThumbX;
                        mLastScrollTimeFast = System.currentTimeMillis();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        updateHorizontalScrollingFromTouch(x);
                        invalidate();
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsDraggingFastScroller) {
                    mLastScrollTimeFast = System.currentTimeMillis();
                    updateScrollingFromTouch(y);
                    invalidate();
                    return true;
                } else if (mIsDraggingFastScrollerHorizontally) {
                    mLastScrollTimeFast = System.currentTimeMillis();
                    updateHorizontalScrollingFromTouch(x);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsDraggingFastScroller || mIsDraggingFastScrollerHorizontally) {
                    mIsDraggingFastScroller = false;
                    mIsDraggingFastScrollerHorizontally = false;
                    mLastScrollTimeFast = System.currentTimeMillis();
                    invalidate();
                    return true;
                }
                break;
        }
        return false;
    }

    private void updateScrollingFromTouch(float touchY) {
        int viewHeight = getHeight();
        int maxScrollY = getMaxScrollY();

        int thumbHeight = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_MIN_THUMB_LENGTH_DP);

        float trackHeight = viewHeight - thumbHeight;
        float progress = (touchY - mFastScrollerTouchOffset) / trackHeight;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        int newScrollY = (int) (progress * maxScrollY);
        scrollTo(getScrollX(), newScrollY);
    }

    private void updateHorizontalScrollingFromTouch(float touchX) {
        int viewWidth = getWidth();
        int maxScrollX = getMaxScrollX();

        int thumbWidth = ScreenUtils.dip2px(getContext(), FAST_SCROLLER_MIN_THUMB_LENGTH_DP);

        float trackWidth = viewWidth - thumbWidth;
        float progress = (touchX - mFastScrollerTouchOffset) / trackWidth;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        int newScrollX = (int) (progress * maxScrollX);
        scrollTo(newScrollX, getScrollY());
    }

    private boolean onMove() {
        if (mGapBuffer == null || mGapBuffer.length() == 0) {
            return false;
        }
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // Use raw touch coordinates for edge detection
        float touchX = mLastTouchX;
        float touchY = mLastTouchY;

        int dx = 0;
        // Gradual horizontal speed
        int slopX = spaceWidth * 4;
        float speedX = spaceWidth * (0.5f + mAutoScrollFactor);
        if (touchX <= slopX) {
            dx = (int) -speedX;
        } else if (touchX >= viewWidth - slopX) {
            dx = (int) speedX;
        }

        int dy = 0;
        //  gradual vertical scroll
        int slopY = getLineHeight();
        float speedY = getLineHeight() * (0.2f + mAutoScrollFactor * 0.4f);
        if (touchY <= slopY) {
            dy = (int) -speedY;
        } else if (touchY >= viewHeight - slopY) {
            dy = (int) speedY;
        }

        if (dx == 0 && dy == 0) {
            return false;
        }

        int oldX = getScrollX();
        int oldY = getScrollY();
        int newScrollX = Math.max(0, Math.min(oldX + dx, getMaxScrollX()));
        int newScrollY = Math.max(0, Math.min(oldY + dy, getMaxScrollY()));

        if (newScrollX == oldX && newScrollY == oldY) {
            return false;
        }

        if (mAutoScrollFactor > 0.8f && mIsMagnifierShowing) {
            dismissMagnifier();
        }

        scrollTo(newScrollX, newScrollY);

        // Update selection while scrolling if a handle is held
        if (mGestureListener != null && (mGestureListener.touchOnSelectHandleMiddle ||
                mGestureListener.touchOnSelectHandleLeft ||
                mGestureListener.touchOnSelectHandleRight)) {

            float contentX = touchX + newScrollX;
            float contentY = touchY + newScrollY;

            // Follow onScroll logic for Y offset
            float adjustedY = contentY - getLineHeight() - (float) Math.min(getLineHeight(), selectHandleHeight) / 2;

            setCursorPosition(contentX, adjustedY);

            if (mGestureListener.touchOnSelectHandleLeft) {
                selectHandleLeftX = mCursorPosX;
                selectHandleLeftY = mCursorPosY + getLineHeight();
                setSelection(mCursorIndex, getSelectionEnd());
            } else if (mGestureListener.touchOnSelectHandleRight) {
                selectHandleRightX = mCursorPosX;
                selectHandleRightY = mCursorPosY + getLineHeight();
                setSelection(getSelectionStart(), mCursorIndex);
            }
            postInvalidate();
        }

        return true;
    }

    private void performHapticFeedback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        } else {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    private void resetAutoHideTimer() {
        mHideSelectHandles = false;
        if (!isSelectMode) {
            mHandleMiddleVisible = true;
        }
        scheduleAutoHide();
        postInvalidate();
    }

    public final void smoothScrollBy(int dx, int dy) {
        if (getHeight() == 0) {
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

    public final void smoothScrollTo(int x, int y) {
        smoothScrollBy(x - getScrollX(), y - getScrollY());
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mLastScrollTimeFast = System.currentTimeMillis();

        // Refresh search highlights for the newly visible area
        if (mLastSearchPattern != null && !mLastSearchPattern.isEmpty()) {
            find(mLastSearchPattern);
        }

        postInvalidate();
    }

    @Override
    public void scrollTo(int x, int y) {
        int scrollX = x;
        int scrollY = y;
        int maxX = getMaxScrollX();
        int maxY = getMaxScrollY();
        if (scrollX < 0) scrollX = 0;
        if (scrollX > maxX) scrollX = maxX;
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxY) scrollY = maxY;
        super.scrollTo(scrollX, scrollY);
    }

    private void scrollToVisible() {
        int dx = 0;
        int leftMargin = spaceWidth * 6;
        int rightMargin = getWidth() - spaceWidth * 4;

        if (mCursorPosX - getScrollX() < leftMargin) {
            dx = mCursorPosX - getScrollX() - leftMargin;
        } else if (mCursorPosX - getScrollX() > rightMargin) {
            dx = mCursorPosX - getScrollX() - rightMargin;
        }
        int dy = 0;
        int topMargin = getLineHeight();
        int bottomMargin = getHeight() - getLineHeight() * 2; // Keep at least one extra line height buffer

        if (mCursorLine > 1 && mCursorPosY - getScrollY() < topMargin) {
            dy = mCursorPosY - getScrollY() - topMargin;
        } else if (mCursorLine == 1 && getScrollY() > 0) {
            dy = -getScrollY();
        } else if (mCursorPosY - getScrollY() > bottomMargin) {
            dy = mCursorPosY - getScrollY() - bottomMargin;
        }

        if (dx != 0 || dy != 0) {
            smoothScrollBy(dx, dy);
        }
    }

    public boolean canGoBack() {
        return mHistoryIndex > 0;
    }

    public boolean canGoForward() {
        return mHistoryIndex < mCursorHistory.size() - 1;
    }

    public void goBack() {
        if (canGoBack()) {
            mIsNavigatingHistory = true;
            mHistoryIndex--;
            int index = mCursorHistory.get(mHistoryIndex);
            setSelection(index, index);
            mIsNavigatingHistory = false;
            scrollToVisible();
        }
    }

    public void goForward() {
        if (canGoForward()) {
            mIsNavigatingHistory = true;
            mHistoryIndex++;
            int index = mCursorHistory.get(mHistoryIndex);
            setSelection(index, index);
            mIsNavigatingHistory = false;
            scrollToVisible();
        }
    }

    public void recordHistory() {
        recordHistory(mCursorIndex);
    }

    private void recordHistory(int index) {
        if (mIsNavigatingHistory || mGapBuffer == null) return;

        // Don't record if it's the same position as current history point
        if (mHistoryIndex >= 0 && mCursorHistory.get(mHistoryIndex) == index) return;

        // Truncate future
        while (mCursorHistory.size() > mHistoryIndex + 1) {
            mCursorHistory.remove(mCursorHistory.size() - 1);
        }

        mCursorHistory.add(index);
        mHistoryIndex++;

        if (mCursorHistory.size() > 100) {
            mCursorHistory.remove(0);
            mHistoryIndex--;
        }
    }

    public void gotoLine(int lineIndex) {
        recordHistory(); // Record where we are BEFORE jump
        int line = Math.min(Math.max(lineIndex, 1), getLineCount());
        if (isSelectMode) {
            clearSelectionMenu();
        }
        mCursorIndex = getLineStart(line);
        mCursorLine = line;
        mCursorPosX = getLeftSpace();
        mCursorPosY = getLineTop(line);

        recordHistory(); // Record new position

        smoothScrollTo(0, Math.max(getLineBottom(line) - getHeight() + getLineHeight() * 2, 0));
        postInvalidate();
    }

    private void moveCursor(int direction, boolean extendSelection) {
        int oldCursor = mCursorIndex;
        int newCursor = oldCursor;
        int len = mGapBuffer.length();

        switch (direction) {
            case 0: // Left
                if (newCursor > 0) newCursor--;
                break;
            case 1: // Right
                if (newCursor < len) newCursor++;
                break;
            case 2: // Up
                int upLine = mCursorLine - 1;
                if (upLine >= 1) {
                    setCursorPosition(mCursorPosX + getPaddingLeft(), getLineTop(upLine) + (float) getLineHeight() / 2 + getPaddingTop());
                    newCursor = mCursorIndex;
                } else {
                    newCursor = 0;
                }
                break;
            case 3: // Down
                int downLine = mCursorLine + 1;
                if (downLine <= getLineCount()) {
                    setCursorPosition(mCursorPosX + getPaddingLeft(), getLineTop(downLine) + (float) getLineHeight() / 2 + getPaddingTop());
                    newCursor = mCursorIndex;
                } else {
                    newCursor = len;
                }
                break;
            case 4: // Home
                newCursor = getLineStart(mCursorLine);
                break;
            case 5: // End
                newCursor = getLineEnd(mCursorLine);
                break;
            case 6: // Document Home
                newCursor = 0;
                break;
            case 7: // Document End
                newCursor = len;
                break;
            case 8: // Word Left
                newCursor = findPreviousWordStart(newCursor);
                break;
            case 9: // Word Right
                newCursor = findNextWordEnd(newCursor);
                break;
            case 10: // Page Up
            case 11: // Page Down
                int pageLines = getHeight() / getLineHeight();
                if (pageLines <= 0) pageLines = 10;
                int targetLine = (direction == 10) ? mCursorLine - pageLines : mCursorLine + pageLines;
                targetLine = Math.max(1, Math.min(targetLine, getLineCount()));
                setCursorPosition(mCursorPosX + getPaddingLeft(), getLineTop(targetLine) + (float) getLineHeight() / 2 + getPaddingTop());
                newCursor = mCursorIndex;
                break;
        }

        if (extendSelection) {
            int selStart = Selection.getSelectionStart(mGapBuffer);
            int selEnd = Selection.getSelectionEnd(mGapBuffer);
            if (selStart < 0 || selEnd < 0) {
                setSelection(oldCursor, newCursor);
            } else {
                int anchor = (selStart == oldCursor) ? selEnd : selStart;
                setSelection(anchor, newCursor);
            }
        } else {
            setSelection(newCursor, newCursor);
        }
        scrollToVisible();
        postInvalidate();
    }

    private int findPreviousWordStart(int index) {
        int i = index;
        if (i <= 0) return 0;
        i--;
        // Skip whitespace
        while (i > 0 && Character.isWhitespace(mGapBuffer.charAt(i))) {
            i--;
        }
        // Skip word characters
        while (i > 0 && !Character.isWhitespace(mGapBuffer.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int findNextWordEnd(int index) {
        int i = index;
        int len = mGapBuffer.length();
        if (i >= len) return len;
        // Skip whitespace
        while (i < len && Character.isWhitespace(mGapBuffer.charAt(i))) {
            i++;
        }
        // Skip word characters
        while (i < len && !Character.isWhitespace(mGapBuffer.charAt(i))) {
            i++;
        }
        return i;
    }

    private void setCursorPosition(int index) {
        mCursorIndex = index;
        mCursorLine = getOffsetLine(index);
        adjustCursorPosition();
    }

    public void setCursorPosition(float x, float y) {
        int oldIndex = mCursorIndex;
        int oldLine = mCursorLine;

        float internalY = y - getPaddingTop();
        if (internalY < 0) internalY = 0;

        mCursorLine = getLogicalLineFromY(internalY);
        int lineTop = getLineTop(mCursorLine);
        float relativeY = internalY - lineTop;

        int lineStart = getLineStart(mCursorLine);
        String text = getLine(mCursorLine);

        int left = getLeftSpace();
        float internalX = x - getPaddingLeft();
        float relativeX = internalX - left;

        if (text.isEmpty()) {
            mCursorIndex = lineStart;
            mCursorPosX = left;
            mCursorPosY = lineTop;
        } else {
            LineResult result = (mHighlighter != null) ? mHighlighter.getOrTokenize(mCursorLine, text) : null;
            if (result != null && result.layout != null) {
                // Ensure this specific line's height is accurate in the BIT
                int measuredHeight = result.layout.getHeight();
                if (measuredHeight != mHeightManager.getHeight(mCursorLine)) {
                    mHeightManager.updateHeight(mCursorLine, measuredHeight);
                    mTotalHeight = mHeightManager.getTotalHeight();
                    // Re-calculate lineTop because updating the BIT might have shifted it
                    lineTop = getLineTop(mCursorLine);
                    relativeY = internalY - lineTop;
                }

                int vLine = 0;
                if (mWordWrap) {
                    vLine = result.layout.getLineForVertical((int) relativeY);
                }
                int offsetInLine = getOriginalOffset(result, vLine, relativeX);
                int originalLen = text.length();

                // Double check with measurement to find the closer character edge
                float wLow = getLayoutHorizontal(result, offsetInLine);
                if (offsetInLine < originalLen) {
                    float wNext;
                    int vLineOfNext = getLayoutLineForOffset(result, offsetInLine + 1);
                    if (vLineOfNext != vLine) {
                        // The next offset is on a different visual line, so its horizontal coordinate
                        // is actually the right edge of the current character on the current visual line.
                        // Since we are using monospace font, we can estimate it as wLow + characterWidth.
                        // We can get the character width by measuring that specific character.
                        char c = text.charAt(offsetInLine);
                        float charWidth = (c == '\t') ? (spaceWidth * mTabSize) : mTextPaint.measureText(String.valueOf(c));
                        wNext = wLow + charWidth;
                    } else {
                        wNext = getLayoutHorizontal(result, offsetInLine + 1);
                    }
                    if (Math.abs(relativeX - wNext) < Math.abs(relativeX - wLow)) {
                        offsetInLine++;
                        wLow = wNext;
                    }
                }

                mCursorIndex = lineStart + offsetInLine;
                mCursorPosX = left + (int) Math.ceil(wLow);
                mCursorYOffsetWithinLine = result.layout.getLineTop(vLine);
                mCursorPosY = lineTop + mCursorYOffsetWithinLine;
            } else {
                fallbackSearch(text, lineStart, left, relativeX);
                mCursorYOffsetWithinLine = 0;
                mCursorPosY = lineTop;
            }
        }

        if (mCursorIndex != oldIndex || mCursorLine != oldLine) {
            onCursorOrSelectionChanged();
        }
    }

    private void fallbackSearch(String text, int lineStart, int left, float relativeX) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (measureText(text.substring(0, mid), left) <= relativeX) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        float wLow = measureText(text.substring(0, low), left);
        if (low < text.length()) {
            float wNext = measureText(text.substring(0, low + 1), left);
            if (Math.abs(relativeX - wNext) < Math.abs(relativeX - wLow)) {
                low++;
                wLow = wNext;
            }
        }

        mCursorIndex = lineStart + low;
        mCursorPosX = left + (int) wLow;
    }

    private void adjustCursorPosition() {
        int start = getLineStart(mCursorLine);
        int lineIndex = mCursorIndex - start;

        int width;
        int yOffset = 0;
        if (mHighlighter != null) {
            String lineText = getLine(mCursorLine);
            LineResult result = mHighlighter.getOrTokenize(mCursorLine, lineText);
            if (result != null && result.layout != null) {
                width = (int) Math.ceil(getLayoutHorizontal(result, lineIndex));
                if (mWordWrap) {
                    int vLine = getLayoutLineForOffset(result, lineIndex);
                    yOffset = result.layout.getLineTop(vLine);

                    // Force JIT height measurement to ensure stability
                    int measuredHeight = result.layout.getHeight();
                    if (measuredHeight != mHeightManager.getHeight(mCursorLine)) {
                        mHeightManager.updateHeight(mCursorLine, measuredHeight);
                        mTotalHeight = mHeightManager.getTotalHeight();
                    }
                }
            } else {
                String text = mGapBuffer.substring(start, Math.min(mCursorIndex, mGapBuffer.length()));
                width = measureText(text, getLeftSpace());
            }
        } else {
            String text = mGapBuffer.substring(start, Math.min(mCursorIndex, mGapBuffer.length()));
            width = measureText(text, getLeftSpace());
        }

        mCursorPosX = getLeftSpace() + width;
        mCursorYOffsetWithinLine = yOffset;
        mCursorPosY = getLineTop(mCursorLine) + yOffset;

        if (mCursorPosX < getLeftSpace()) {
            mCursorPosX = getLeftSpace();
        }
    }

    public int getCaretPosition() {
        return mCursorIndex;
    }

    public void setSelection(int start, int end) {
        int len = mGapBuffer.length();
        int s = Math.max(0, Math.min(start, len));
        int e = Math.max(0, Math.min(end, len));

        Selection.setSelection(mGapBuffer, s, e);

        mCursorIndex = e;
        mCursorLine = getOffsetLine(mCursorIndex);

        isSelectMode = s != e;
        mHandleMiddleVisible = !isSelectMode;

        // Cache selection range for delete operations
        if (isSelectMode) {
            mPendingDeleteStart = s;
            mPendingDeleteEnd = e;
        } else {
            mPendingDeleteStart = -1;
            mPendingDeleteEnd = -1;
        }

        adjustCursorPosition();
        updateSelectionHandles();
        onCursorOrSelectionChanged();
    }

    public int getSelectionStart() {
        if (mGapBuffer == null) return 0;
        int res = Selection.getSelectionStart(mGapBuffer);
        return res < 0 ? mCursorIndex : res;
    }

    public int getSelectionEnd() {
        if (mGapBuffer == null) return 0;
        int res = Selection.getSelectionEnd(mGapBuffer);
        return res < 0 ? mCursorIndex : res;
    }

    public String getSelectedText() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (isSelectMode && start < end) {
            return mGapBuffer.substring(start, end);
        }
        return "";
    }

    public int getSelectionLength() {
        if (!isSelectMode) return 0;
        return getSelectionEnd() - getSelectionStart();
    }

    public boolean isSelectMode() {
        return isSelectMode;
    }

    public void selectAll() {
        removeCallbacks(blinkAction);
        mCursorVisible = false;
        mHandleMiddleVisible = false;
        isSelectMode = true;
        mHideSelectHandles = false;

        setSelection(0, mGapBuffer.length());

        setCursorPosition(getSelectionEnd());

        int bottom = mWordWrap ? mTotalHeight : getLineCount() * getLineHeight();
        smoothScrollTo(0, Math.max(bottom - getHeight() + getLineHeight() * 2, 0));
        showTextSelectionWindow();
        postInvalidate();
    }

    public boolean isAllTextSelected() {
        return getSelectionStart() == 0 &&
                getSelectionEnd() == mGapBuffer.length() &&
                getSelectionStart() != getSelectionEnd() &&
                mGapBuffer.length() > 0;
    }

    public void clearSelectionMenu() {
        isSelectMode = false;
        mHandleMiddleVisible = false;
        mIsLineSelectionMode = false;
        mPendingDeleteStart = -1;
        mPendingDeleteEnd = -1;
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

    private void clearSelection() {
        clearSelectionMenu();
        isSelectMode = false;
        Selection.setSelection(mGapBuffer, -1);
    }

    private int[] normalizeSelection() {
        int s = getSelectionStart();
        int e = getSelectionEnd();
        s = Math.max(0, Math.min(s, mGapBuffer.length()));
        e = Math.max(0, Math.min(e, mGapBuffer.length()));
        return new int[]{s, e};
    }

    private void updateSelectionHandles() {
        if (!isSelectMode) return;

        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();

        // Normalize offsets for handle calculations
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);

        // 1. Calculate handle positions using shared logic
        int[] leftPos = getPositionForOffset(start, true);
        selectHandleLeftX = leftPos[0];
        selectHandleLeftY = leftPos[1];

        int[] rightPos = getPositionForOffset(end, true);
        selectHandleRightX = rightPos[0];
        selectHandleRightY = rightPos[1];

        // 2. Sync cursor with the 'active' end of the selection (selEnd)
        // We use anchorBottom=false for the cursor to get the top Y position
        int[] activePosTop = getPositionForOffset(selEnd, false);
        mCursorPosX = (selEnd < selStart) ? selectHandleLeftX : selectHandleRightX;
        mCursorPosY = activePosTop[1];

        // Update vertical offset state for the cursor
        int activeLine = getOffsetLine(selEnd);
        mCursorYOffsetWithinLine = mCursorPosY - getLineTop(activeLine);
    }

    /**
     * Helper to calculate content-relative X and Y for a given buffer offset.
     * Centralizing this ensures consistency between handles and the cursor.
     */
    private int[] getPositionForOffset(int offset, boolean anchorBottom) {
        int left = getLeftSpace();
        int line = getOffsetLine(offset);
        int lineStart = getLineStart(line);
        String text = getLine(line);

        if (mHighlighter != null) {
            LineResult result = mHighlighter.getOrTokenize(line, text);
            if (result != null && result.layout != null) {
                int offsetInLine = offset - lineStart;
                int x = left + (int) Math.ceil(getLayoutHorizontal(result, offsetInLine));

                // JIT height update for layout stability
                int h = result.layout.getHeight();
                if (h != mHeightManager.getHeight(line)) {
                    mHeightManager.updateHeight(line, h);
                    mTotalHeight = mHeightManager.getTotalHeight();
                }

                int y;
                if (mWordWrap) {
                    int vLine = getLayoutLineForOffset(result, offsetInLine);
                    y = getLineTop(line) + (anchorBottom ? result.layout.getLineBottom(vLine) : result.layout.getLineTop(vLine));
                } else {
                    y = anchorBottom ? getLineBottom(line) : getLineTop(line);
                }
                return new int[]{x, y};
            }
        }

        // Fallback: Measure text manually if no highlighter/layout is available
        // Optimized: Only perform substring here when actually needed
        String prefix = text.substring(0, Math.max(0, Math.min(offset - lineStart, text.length())));
        int x = left + measureText(prefix, left);
        int y = anchorBottom ? getLineBottom(line) : getLineTop(line);
        return new int[]{x, y};
    }

    private void scheduleSelectionUpdate() {
        mSelectionHandler.removeCallbacks(mUpdateSelectionPosition);
        mSelectionHandler.postDelayed(mUpdateSelectionPosition, 100);
    }

    private void onCursorOrSelectionChanged() {
        isSelectMode = getSelectionStart() != getSelectionEnd();
        scheduleSelectionUpdate();
        if (mComposingStart == -1) {
            mCursorWordBounds = getWordBoundsAt(mCursorIndex);
        } else {
            mCursorWordBounds = null;
        }

        // Update matching brace - ASYNCHRONOUSLY
        mBraceSearchId++;
        mMatchingBraceIndex = -1;
        mCurrentBraceIndex = -1;
        mMatchingBraceLine = -1;
        mCurrentBraceLine = -1;
        mPendingBraceCheckIdx = -1;
        mBraceThreadHandler.removeCallbacks(mBraceSearchRunnable);

        if (!isSelectMode && mGapBuffer.length() > 0) {
            int checkIdx = -1;
            if (mCursorIndex < mGapBuffer.length()) {
                char c = mGapBuffer.charAt(mCursorIndex);
                if (c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']')
                    checkIdx = mCursorIndex;
            }
            if (checkIdx == -1 && mCursorIndex > 0) {
                char c = mGapBuffer.charAt(mCursorIndex - 1);
                if (c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']')
                    checkIdx = mCursorIndex - 1;
            }

            if (checkIdx != -1) {
                mPendingBraceCheckIdx = checkIdx;
                // Delay slightly to wait for rapid cursor movement to stop
                mBraceThreadHandler.postDelayed(mBraceSearchRunnable, 50);
            }
        }

        if (mClipboardPanel != null) {
            mSelectionHandler.removeCallbacks(mAutoHideRunnable);
            mSelectionHandler.postDelayed(mAutoHideRunnable, 5000);
        }

        // Notify Input Method Manager about selection change
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            int selStart = isSelectMode ? getSelectionStart() : mCursorIndex;
            int selEnd = isSelectMode ? getSelectionEnd() : mCursorIndex;

            // If the cursor moved away from the composing region, reset it
            if (mComposingStart != -1 && (mCursorIndex < mComposingStart || mCursorIndex > mComposingEnd)) {
                mComposingStart = -1;
                mComposingEnd = -1;
            }

            imm.updateSelection(this, selStart, selEnd, mComposingStart, mComposingEnd);
        }

        // Reset blinking on cursor movement/typing
        removeCallbacks(blinkAction);
        mCursorVisible = true;
        postDelayed(blinkAction, BLINK_TIMEOUT * 2);

        if (mSelectionListener != null) {
            mSelectionListener.onSelectionChanged(getSelectionStart(), getSelectionEnd());
        }

        // Auto-record history if line changed
        int line = getOffsetLine(mCursorIndex);
        if (mLastHistoryLine != line) {
            // Record previous line before moving if we stayed there long enough
            if (mLastHistoryLine != -1) {
                recordHistory();
            }
            mLastHistoryLine = line;
        }

        // Always refresh search highlights to update the "active match" (yellow)
        if (mLastSearchPattern != null && !mLastSearchPattern.isEmpty()) {
            find(mLastSearchPattern);
        }

        postInvalidate();
    }

    private boolean isInLineNumberArea(float x, float y) {
        if (!mShowLineNumbers) return false;
        int gutterLeftPadding = ScreenUtils.dip2px(getContext(), 12);
        int gutterRightPadding = ScreenUtils.dip2px(getContext(), 6);
        int totalGutterWidth = gutterLeftPadding + getLineNumberWidth() + gutterRightPadding;
        float startX = mStickyLineNumbers ? getScrollX() : 0;
        return x >= startX && x <= startX + totalGutterWidth;
    }

    private int getLineFromY(float y) {
        ensureHeightsMeasuredUntil((int) y);
        return getLogicalLineFromY(y);
    }

    private void selectLineRange(int startLine, int endLine) {
        if (startLine < 1 || endLine < 1 || startLine > getLineCount() || endLine > getLineCount()) {
            return;
        }

        removeCallbacks(blinkAction);
        mCursorVisible = false;
        mHandleMiddleVisible = false;
        isSelectMode = true;
        mIsLineSelectionMode = true;

        int actualStartLine = Math.min(startLine, endLine);
        int actualEndLine = Math.max(startLine, endLine);

        int start = getLineStart(actualStartLine);
        int end = getLineEnd(actualEndLine) + (actualEndLine < getLineCount() ? 1 : 0);

        Selection.setSelection(mGapBuffer, start, end);

        mStartSelectionLine = actualStartLine;
        mEndSelectionLine = actualEndLine;

        updateSelectionHandles();
        showTextSelectionWindow();
        postInvalidate();

        performHapticFeedback();
    }

    private void selectSingleLine(int line) {
        if (line < 1 || line > getLineCount()) return;
        removeCallbacks(blinkAction);
        mCursorVisible = false;
        mHandleMiddleVisible = false;
        isSelectMode = true;
        mIsLineSelectionMode = true;

        int lineStart = getLineStart(line);
        int lineEnd = lineStart + getLine(line).length() + (line < getLineCount() ? 1 : 0);
        setSelection(lineStart, lineEnd);
        mStartSelectionLine = line;
        mEndSelectionLine = line;

        updateSelectionHandles();
        showTextSelectionWindow();
        postInvalidate();
    }

    private void clearLineSelection() {
        mWaitingForSecondSelection = false;
        mFirstSelectedLine = -1;
    }

    private int[] fullLineRangeForSelection(int s, int e) {
        if (s >= e) return null;
        int firstLine = getOffsetLine(s);
        int lastLine = getOffsetLine(Math.max(0, e - 1));
        int start = getLineStart(firstLine);
        int lastLineStart = getLineStart(lastLine);
        int end = lastLineStart + getLine(lastLine).length();
        start = Math.max(0, Math.min(start, mGapBuffer.length()));
        end = Math.max(0, Math.min(end, mGapBuffer.length()));
        if (start > end) end = start;
        return new int[]{start, end};
    }

    private int[] fullLineRangeForCursorLine(int line) {
        int start = getLineStart(line);
        int end = start + getLine(line).length();
        start = Math.max(0, Math.min(start, mGapBuffer.length()));
        end = Math.max(0, Math.min(end, mGapBuffer.length()));
        if (start > end) end = start;
        return new int[]{start, end};
    }

    public void selectNearestWord() {
        String selectWord = findNearestWord();
        if (selectWord != null) {
            removeCallbacks(blinkAction);
            mCursorVisible = mHandleMiddleVisible = false;
            isSelectMode = true;
            updateSelectionHandles();
            setCursorPosition(getSelectionEnd());

        }
        postInvalidate();
    }

    public String findNearestWord() {
        int length = mGapBuffer.length();
        if (length == 0) return null;

        if (mCursorIndex >= length) {
            mCursorIndex = Math.max(0, length - 1);
        }

        int[] bounds = findSingleSpecialCharBounds();
        if (bounds != null) {
            setSelection(bounds[0], bounds[1]);
            return mGapBuffer.substring(bounds[0], bounds[1]);
        }

        bounds = findWordAtCursorBounds();
        if (bounds != null) {
            setSelection(bounds[0], bounds[1]);
            return mGapBuffer.substring(bounds[0], bounds[1]);
        }

        bounds = findWordInVicinityBounds();
        if (bounds != null) {
            setSelection(bounds[0], bounds[1]);
            return mGapBuffer.substring(bounds[0], bounds[1]);
        }

        // If it's an empty line or just whitespace, select the line content
        String lineText = getLine(mCursorLine);
        if (lineText.isEmpty() || lineText.trim().isEmpty()) {
            int start = getLineStart(mCursorLine);
            int end = start + lineText.length();
            // If it's not the last line, we can include the newline to select the whole empty line
            if (end < mGapBuffer.length()) {
                end++;
            }
            setSelection(start, end);
            return mGapBuffer.substring(start, end);
        }

        bounds = findAnyNonWhitespaceBounds();
        if (bounds != null) {
            setSelection(bounds[0], bounds[1]);
            return mGapBuffer.substring(bounds[0], bounds[1]);
        }
        return null;
    }

    private int[] findSingleSpecialCharBounds() {
        if (mCursorIndex < mGapBuffer.length()) {
            char currentChar = mGapBuffer.charAt(mCursorIndex);
            if (isSpecialChar(currentChar)) {
                return new int[]{mCursorIndex, mCursorIndex + 1};
            }
        }

        if (mCursorIndex > 0) {
            char prevChar = mGapBuffer.charAt(mCursorIndex - 1);
            if (isSpecialChar(prevChar)) {
                return new int[]{mCursorIndex - 1, mCursorIndex};
            }
        }
        return null;
    }

    private int[] expandSelectionFrom(int position) {
        int length = mGapBuffer.length();
        if (position < 0 || position >= length) return null;
        char startChar = mGapBuffer.charAt(position);

        if (startChar == '\n' || isSpecialChar(startChar) || startChar == '\u200B') {
            return null;
        }

        boolean isWhitespace = Character.isWhitespace(startChar);

        int start = position;
        while (start > 0) {
            char c = mGapBuffer.charAt(start - 1);
            if (c == '\n' || c == '\u200B' || (isWhitespace != Character.isWhitespace(c)) || (!isWhitespace && isSpecialChar(c)))
                break;
            start--;
        }
        int end = position;
        while (end < length) {
            char c = mGapBuffer.charAt(end);
            if (c == '\n' || c == '\u200B' || (isWhitespace != Character.isWhitespace(c)) || (!isWhitespace && isSpecialChar(c)))
                break;
            end++;
        }

        if (start < end) {
            return new int[]{start, end};
        }
        return null;
    }

    private int[] findWordAtCursorBounds() {
        boolean atBoundary = false;
        if (mCursorIndex < mGapBuffer.length()) {
            char c = mGapBuffer.charAt(mCursorIndex);
            if (Character.isWhitespace(c) || isSpecialChar(c) || c == '\u200B') atBoundary = true;
        } else {
            atBoundary = true;
        }

        if (atBoundary && mCursorIndex > 0) {
            char prevChar = mGapBuffer.charAt(mCursorIndex - 1);
            if (!Character.isWhitespace(prevChar) && !isSpecialChar(prevChar) && prevChar != '\u200B') {
                return expandSelectionFrom(mCursorIndex - 1);
            }
        }

        if (mCursorIndex < mGapBuffer.length()) {
            char currentChar = mGapBuffer.charAt(mCursorIndex);
            if (!Character.isWhitespace(currentChar) && !isSpecialChar(currentChar) && currentChar != '\u200B') {
                return expandSelectionFrom(mCursorIndex);
            }
            if (Character.isWhitespace(currentChar) && currentChar != '\n') {
                return expandSelectionFrom(mCursorIndex);
            }
        }

        if (mCursorIndex > 0) {
            char prevChar = mGapBuffer.charAt(mCursorIndex - 1);
            if (Character.isWhitespace(prevChar) && prevChar != '\n') {
                return expandSelectionFrom(mCursorIndex - 1);
            }
        }
        return null;
    }

    private int[] findWordInVicinityBounds() {
        int length = mGapBuffer.length();
        if (length == 0) return null;

        boolean hitLeftNL = false;
        boolean hitRightNL = false;

        for (int radius = 1; radius <= 20; radius++) {
            int backwardPos = mCursorIndex - radius;
            if (backwardPos >= 0 && !hitLeftNL) {
                char c = mGapBuffer.charAt(backwardPos);
                if (c == '\n') {
                    hitLeftNL = true;
                } else {
                    if (isSpecialChar(c)) {
                        return new int[]{backwardPos, backwardPos + 1};
                    }
                    if (!Character.isWhitespace(c)) {
                        return expandSelectionFrom(backwardPos);
                    }
                }
            }

            int forwardPos = mCursorIndex + radius;
            if (forwardPos < length && !hitRightNL) {
                char c = mGapBuffer.charAt(forwardPos);
                if (c == '\n') {
                    hitRightNL = true;
                } else {
                    if (isSpecialChar(c)) {
                        return new int[]{forwardPos, forwardPos + 1};
                    }
                    if (!Character.isWhitespace(c)) {
                        return expandSelectionFrom(forwardPos);
                    }
                }
            }
            if (hitLeftNL && hitRightNL) break;
        }
        return null;
    }

    private int[] findAnyNonWhitespaceBounds() {
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
                    if (isSpecialChar(c)) {
                        return new int[]{i, i + 1};
                    }
                    return expandSelectionFrom(i);
                }
            }
        }
        return null;
    }

    private int[] getWordBoundsAt(int index) {
        int len = mGapBuffer.length();
        if (index < 0 || index > len) return null;

        char atCursor = (index < len) ? mGapBuffer.charAt(index) : 0;
        char beforeCursor = (index > 0) ? mGapBuffer.charAt(index - 1) : 0;

        int scanFrom = -1;
        if (isWordUnderlinePart(atCursor)) {
            scanFrom = index;
        } else if (isWordUnderlinePart(beforeCursor)) {
            scanFrom = index - 1;
        }

        if (scanFrom < 0) return null;

        int start = scanFrom;
        while (start > 0 && isWordUnderlinePart(mGapBuffer.charAt(start - 1))) {
            start--;
        }

        int end = scanFrom;
        while (end < len && isWordUnderlinePart(mGapBuffer.charAt(end))) {
            end++;
        }

        if (start >= end) return null;

        // Reject pure-number tokens
        boolean hasLetter = false;
        for (int i = start; i < end; i++) {
            if (Character.isLetter(mGapBuffer.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) return null;

        // Reject if the surrounding token (including separators) contains _
        // Walk outward to check the full identifier context
        int ctxStart = start;
        while (ctxStart > 0) {
            char c = mGapBuffer.charAt(ctxStart - 1);
            if (!isWordUnderlinePart(c) && c != '_') break;
            ctxStart--;
        }
        int ctxEnd = end;
        while (ctxEnd < len) {
            char c = mGapBuffer.charAt(ctxEnd);
            if (!isWordUnderlinePart(c) && c != '_') break;
            ctxEnd++;
        }
        String fullToken = mGapBuffer.substring(ctxStart, ctxEnd);
        if (fullToken.contains("_")) return null;

        if (end - start < 2) return null;

        return new int[]{start, end};
    }

    private boolean isSpecialChar(char c) {
        String specialChars = ":;\"'`.,!?@#$%^&*()+=[]{}<>~|\\";
        return specialChars.indexOf(c) >= 0;
    }

    private boolean isInstructionPart(char c) {
        // Used ONLY for autocomplete prefix scanning
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean isWordUnderlinePart(char c) {
        // Used for cursor word underline — only letters, digits, and hyphen
        // Excludes: / _ . : (these are separators in smali-style identifiers)
        return Character.isLetter(c) || c == '-';
    }

    private void replaceInternal(int start, int end, String text) {
        if (!isEditedMode) return;

        // Efficiently track word changes here instead of in TextWatcher to avoid batching bugs
        // Optimization: Skip word map updates for very large changes to avoid OOM and lag
        int changeLen = end - start;
        if (changeLen > 0 && changeLen < 100000 && end <= mGapBuffer.length()) {
            removeWordsFromMap(mGapBuffer.substring(start, end));
        }

        if (text != null && !text.isEmpty()) {
            if (text.length() < 100000) {
                addWordsToMap(text);
            }
        }

        mGapBuffer.markSelectionBefore(getSelectionStart(), getSelectionEnd(), isSelectMode);

        mGapBuffer.beginBatchEdit();
        try {
            mGapBuffer.replace(start, end, text, true);
            onTextChanged(getOffsetLine(start));

            int afterLength = (text != null ? text.length() : 0);
            int newCursor = start + afterLength;

            Selection.setSelection(mGapBuffer, newCursor);

            mCursorIndex = newCursor;
            mCursorLine = getOffsetLine(mCursorIndex);
            adjustCursorPosition();
            onCursorOrSelectionChanged();

            mGapBuffer.markSelectionAfter(mCursorIndex, mCursorIndex, false);
        } finally {
            mGapBuffer.endBatchEdit();
        }

        scrollToVisible();
    }

    private void insert(String textToInsert) {
        if (!isEditedMode) return;

        int start = getSelectionStart();
        int end = getSelectionEnd();

        // Composing range replacement
        if (!isSelectMode && mComposingStart != -1 && mComposingEnd != -1) {
            start = Math.min(mComposingStart, mComposingEnd);
            end = Math.max(mComposingStart, mComposingEnd);
            mComposingStart = mComposingEnd = -1;
        }

        String actualText = textToInsert;
        if (actualText.equals("\n") && mAutoIndentEnabled) {
            String indent = getAutoIndent(start);
            actualText = "\n" + indent;
        }

        removeCallbacks(blinkAction);
        mCursorVisible = true;
        mHandleMiddleVisible = false;

        replaceInternal(start, end, actualText);

        isSelectMode = false;
        postDelayed(blinkAction, BLINK_TIMEOUT * 2);
    }

    public void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        replaceInternal(getSelectionStart(), getSelectionEnd(), text);
    }

    public void delete() {
        if (!isEditedMode) return;

        int start, end;

        if (mPendingDeleteStart != -1 && mPendingDeleteEnd != -1
                && mPendingDeleteStart != mPendingDeleteEnd) {
            // Use cached selection — immune to span/isSelectMode corruption by IME
            start = mPendingDeleteStart;
            end = mPendingDeleteEnd;
            mPendingDeleteStart = -1;
            mPendingDeleteEnd = -1;
        } else {
            if (mCursorIndex <= 0) return;
            start = mCursorIndex - 1;
            end = mCursorIndex;
        }

        int len = mGapBuffer.length();
        start = Math.max(0, Math.min(start, len));
        end = Math.max(0, Math.min(end, len));
        if (start >= end) {
            if (mCursorIndex <= 0) return;
            start = mCursorIndex - 1;
            end = mCursorIndex;
        }

        removeCallbacks(blinkAction);
        mCursorVisible = true;
        mHandleMiddleVisible = false;
        isSelectMode = false;

        replaceInternal(start, end, "");

        postDelayed(blinkAction, BLINK_TIMEOUT * 2);
    }

    private void handleForwardDelete() {
        if (!isEditedMode) return;

        int start = getSelectionStart();
        int end = getSelectionEnd();

        if (!isSelectMode) {
            if (mCursorIndex >= mGapBuffer.length()) return;
            start = mCursorIndex;
            end = mCursorIndex + 1;
        }

        removeCallbacks(blinkAction);
        mCursorVisible = true;
        mHandleMiddleVisible = false;

        replaceInternal(start, end, "");

        isSelectMode = false;
        postDelayed(blinkAction, BLINK_TIMEOUT * 2);
    }

    public boolean canUndo() {
        return mGapBuffer.canUndo();
    }

    public boolean canRedo() {
        return mGapBuffer.canRedo();
    }

    public void undo() {
        removeCallbacks(blinkAction);
        int index = mGapBuffer.undo();
        if (index >= 0) {
            // Selection spans are automatically restored in GapBuffer.undo() via setSelection
            // We just need to sync our UI fields
            mCursorIndex = getSelectionEnd();
            isSelectMode = getSelectionStart() != getSelectionEnd();
            mCursorLine = getOffsetLine(mCursorIndex);
            adjustCursorPosition();
            onCursorOrSelectionChanged();
            scrollToVisible();

            if (isSelectMode) {
                mCursorVisible = false;
                mHandleMiddleVisible = false;
                mHideSelectHandles = false;
                showTextSelectionWindow();
            } else {
                mCursorVisible = true;
                mHandleMiddleVisible = true;
                mHideSelectHandles = false;
                hideTextSelectionWindow();
                postDelayed(blinkAction, BLINK_TIMEOUT);
            }
        }
    }

    public void redo() {
        removeCallbacks(blinkAction);
        int index = mGapBuffer.redo();
        if (index >= 0) {
            mCursorIndex = getSelectionEnd();
            isSelectMode = getSelectionStart() != getSelectionEnd();
            mCursorLine = getOffsetLine(mCursorIndex);
            adjustCursorPosition();
            onCursorOrSelectionChanged();
            scrollToVisible();

            if (isSelectMode) {
                mCursorVisible = false;
                mHandleMiddleVisible = false;
                mHideSelectHandles = false;
                showTextSelectionWindow();
            } else {
                mCursorVisible = true;
                mHandleMiddleVisible = true;
                mHideSelectHandles = false;
                hideTextSelectionWindow();
                postDelayed(blinkAction, BLINK_TIMEOUT);
            }
        }
    }

    public void copy() {
        String text = getSelectedText();
        if (text != null && !text.isEmpty()) {
            ClipData data = ClipData.newPlainText("content", text);
            mClipboard.setPrimaryClip(data);
        }
    }

    public void cut() {
        copy();
        delete();
        isSelectMode = false;
    }

    public void paste() {
        if (mClipboard.hasPrimaryClip()) {
            ClipDescription description = mClipboard.getPrimaryClipDescription();
            if (description != null && description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                ClipData data = mClipboard.getPrimaryClip();
                ClipData.Item item = null;
                if (data != null) {
                    item = data.getItemAt(0);
                }
                String text = null;
                if (item != null) {
                    text = item.getText().toString();
                }
                insert(text);
            }
        }
    }

    private void copyRangeToClipboard(int start, int end, String label) {
        int s = Math.max(0, Math.min(start, mGapBuffer.length()));
        int e = Math.max(0, Math.min(end, mGapBuffer.length()));
        if (s > e) e = s;
        String text = mGapBuffer.substring(s, e);
        if (text != null && !text.isEmpty()) {
            ClipData data = ClipData.newPlainText(label, text);
            mClipboard.setPrimaryClip(data);
        }
    }

    private String getAutoIndent(int offset) {
        if (!mAutoIndentEnabled || offset < 0) return "";

        try {
            int line = getOffsetLine(offset);
            int lineStart = getLineStart(line);
            if (lineStart < 0 || lineStart > mGapBuffer.length()) return "";

            String currentLinePrefix = mGapBuffer.substring(lineStart, Math.min(offset, mGapBuffer.length()));

            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < currentLinePrefix.length(); i++) {
                char c = currentLinePrefix.charAt(i);
                if (c == ' ' || c == '\t') {
                    indent.append(c);
                } else {
                    break;
                }
            }

            String indentStr = indent.toString();
            String trimmed = currentLinePrefix.trim();

            // Smart Indent: Increase indentation after certain characters/directives
            if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[") ||
                    trimmed.startsWith(".method") || trimmed.startsWith(".annotation") ||
                    trimmed.startsWith(".subannotation")) {
                // Add tab size in spaces
                for (int i = 0; i < mTabSize; i++) {
                    indent.append(" ");
                }
                indentStr = indent.toString();
            }

            return indentStr;
        } catch (Exception e) {
            Log.e(TAG, "Error in getAutoIndent: " + e.getMessage());
            return "";
        }
    }

    public void onTextChanged() {
        if (mHighlighter != null) {
            mHighlighter.clearCache();
        }
        updateLineWidth();
        if (mWordWrap) {
            computeLineTops();
        }
        if (mTextListener != null) mTextListener.onTextChanged();
    }

    public void onTextChanged(int fromLine) {
        if (mHighlighter != null) {
            mHighlighter.invalidateSubsequentStates(fromLine);
            mHighlighter.invalidateSubsequentLines(fromLine);
        }
        // Sync cursor line before updateLineWidth touches it
        int lineCount = getLineCount();
        if (mCursorLine < 1 || mCursorLine > lineCount) {
            mCursorLine = Math.max(1, Math.min(mCursorLine, lineCount));
            mCursorIndex = Math.max(0, Math.min(mCursorIndex, mGapBuffer.length()));
        }
        updateLineWidth();
        if (mWordWrap) {
            int oldCount = mHeightManager.count;
            int newCount = getLineCount();
            mHeightManager.adjustLineCount(fromLine, oldCount, newCount, getLineHeight());
            mTotalHeight = mHeightManager.getTotalHeight();
        }
        if (mTextListener != null) mTextListener.onTextChanged();

        removeCallbacks(mAutoCompleteRunnable);
        postDelayed(mAutoCompleteRunnable, AUTO_COMPLETE_DELAY);

        // Schedule re-search if pattern exists
        if (!mLastSearchPattern.isEmpty()) {
            mSearchHandler.removeCallbacks(mSearchRunnable);
            mSearchHandler.postDelayed(mSearchRunnable, 500);
        }
    }

    public void copyLine() {
        if (isSelectMode) {
            int[] sel = normalizeSelection();
            int s = sel[0], e = sel[1];
            int[] range = fullLineRangeForSelection(s, e);
            if (range != null) {
                copyRangeToClipboard(range[0], range[1], "lines");
                return;
            }

        }

        int[] range = fullLineRangeForCursorLine(mCursorLine);
        copyRangeToClipboard(range[0], range[1], "line");
        setSelection(range[0], range[1]);
    }

    public void cutLine() {
        if (isSelectMode) {
            int[] sel = normalizeSelection();
            int s = sel[0], e = sel[1];
            int[] range = fullLineRangeForSelection(s, e);
            if (range != null) {
                copyRangeToClipboard(range[0], range[1], "lines");
                batchDeleteWithSelectionSnapshot(range[0], range[1],
                        true, getSelectionStart(), getSelectionEnd(),
                        false, -1, -1);
                clearSelection();
                mCursorIndex = range[0];
                mCursorLine = getOffsetLine(mCursorIndex);
                adjustCursorPosition();
                postInvalidate();
                return;
            }

        }

        int[] range = fullLineRangeForCursorLine(mCursorLine);
        copyRangeToClipboard(range[0], range[1], "line");
        batchDeleteWithSelectionSnapshot(range[0], range[1],
                false, getSelectionStart(), getSelectionEnd(),
                false, -1, -1);
        mCursorIndex = range[0];
        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();
        clearSelectionMenu();
        postInvalidate();
    }

    public void replaceLine() {
        if (!mClipboard.hasPrimaryClip()) return;
        ClipData data = mClipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return;
        ClipData.Item item = data.getItemAt(0);
        CharSequence raw = item.getText();
        if (raw == null) return;
        String clipboardText = raw.toString();
        if (isSelectMode) {
            int[] sel = normalizeSelection();
            int s = sel[0], e = sel[1];
            int startLine = getOffsetLine(s);
            int[] range = fullLineRangeForSelection(s, e);
            if (range != null) {
                int replaceStart = range[0];
                int replaceEnd = range[1];
                int newSelEnd = replaceStart + clipboardText.length();
                batchReplaceWithSelectionSnapshot(replaceStart, replaceEnd, clipboardText,
                        true, s, e,
                        true, replaceStart, newSelEnd);


                clearSelectionMenu();
                mCursorIndex = getSelectionEnd();
                mCursorLine = getOffsetLine(mCursorIndex);
                adjustCursorPosition();
                onTextChanged(startLine);
                postInvalidate();
                return;
            }
        }

        int[] range = fullLineRangeForCursorLine(mCursorLine);
        int lineStart = range[0];
        int lineEnd = range[1];
        batchReplaceWithSelectionSnapshot(lineStart, lineEnd, clipboardText,
                false, getSelectionStart(), getSelectionEnd(),
                false, -1, -1);
        clearSelection();
        mCursorIndex = lineStart + clipboardText.length();
        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();
        postInvalidate();
    }

    public void deleteLine() {
        if (isSelectMode) {
            int[] sel = normalizeSelection();
            int s = sel[0], e = sel[1];
            int[] range = fullLineRangeForSelection(s, e);
            if (range != null) {
                batchDeleteWithSelectionSnapshot(range[0], range[1],
                        true, getSelectionStart(), getSelectionEnd(),
                        false, -1, -1);
                clearSelection();
                mCursorIndex = Math.min(range[0], mGapBuffer.length());
                mCursorLine = getOffsetLine(mCursorIndex);
                adjustCursorPosition();
                postInvalidate();
                return;
            }
        }

        int[] range = fullLineRangeForCursorLine(mCursorLine);
        batchDeleteWithSelectionSnapshot(range[0], range[1],
                false, getSelectionStart(), getSelectionEnd(),
                false, -1, -1);
        mCursorIndex = Math.min(range[0], mGapBuffer.length());
        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();
        clearSelectionMenu();
        postInvalidate();
    }

    public void emptyLine() {
        if (isSelectMode) {
            int[] sel = normalizeSelection();
            int s = sel[0], e = sel[1];
            int[] range = fullLineRangeForSelection(s, e);
            if (range != null) {
                mGapBuffer.markSelectionBefore(getSelectionStart(), getSelectionEnd(), true);
                mGapBuffer.beginBatchEdit();
                try {
                    int firstLine = getOffsetLine(s);
                    int lastLine = getOffsetLine(Math.max(0, e - 1));
                    for (int line = firstLine; line <= lastLine; line++) {
                        int start = getLineStart(line);
                        int end = start + getLine(line).length();
                        if (end > start) {
                            mGapBuffer.delete(start, end, true);
                        }
                    }
                    mGapBuffer.markSelectionAfter(-1, -1, false);
                } finally {
                    mGapBuffer.endBatchEdit();
                }

                clearSelection();
                mCursorLine = getOffsetLine(range[0]);
                mCursorIndex = getLineStart(mCursorLine);
                adjustCursorPosition();
                postInvalidate();
                return;
            }

        }

        int[] range = fullLineRangeForCursorLine(mCursorLine);
        int lineStart = range[0];
        int lineEnd = range[1];
        if (lineEnd > lineStart) {
            mGapBuffer.beginBatchEdit();
            try {
                mGapBuffer.delete(lineStart, lineEnd, true);
            } finally {
                mGapBuffer.endBatchEdit();
            }
        }
        mCursorIndex = lineStart;
        adjustCursorPosition();
        clearSelectionMenu();
        postInvalidate();
    }

    public void duplicateLine() {
        mGapBuffer.beginBatchEdit();
        try {
            if (isSelectMode) {
                int start = getSelectionStart();
                int end = getSelectionEnd();

                int startLine = getOffsetLine(start);
                int endLine = getOffsetLine(end);

                int lineStart = getLineStart(startLine);
                int lineEnd = getLineEnd(endLine);

                String block = mGapBuffer.substring(lineStart, lineEnd);

                mGapBuffer.insert(lineEnd, "\n" + block, true);
                onTextChanged(startLine);

                int dupStart = lineEnd + 1;
                int dupEnd = dupStart + block.length();

                setSelection(dupStart, dupEnd);

                updateSelectionHandles();
                scrollToVisible();
                postInvalidate();
                return;
            }

            int currentLine = mCursorLine;
            int lineStart = getLineStart(currentLine);
            int lineEnd = getLineEnd(currentLine);
            String lineText = mGapBuffer.substring(lineStart, lineEnd);

            mGapBuffer.insert(lineEnd, "\n" + lineText, true);
            onTextChanged(currentLine);

            int col = getColumn();
            int newLine = currentLine + 1;
            int newLineStart = getLineStart(newLine);
            int target = newLineStart + col;

            int newLineEnd = getLineEnd(newLine);
            if (target > newLineEnd) {
                target = newLineEnd;
            }

            setCursorPosition(target);

            isSelectMode = false;
            clearLineSelection();
            scrollToVisible();
            postInvalidate();
            postInvalidate();
        } finally {
            mGapBuffer.endBatchEdit();
        }
    }

    public void convertSelectionToLowerCase() {
        if (isSelectMode) {
            int s = getSelectionStart();
            int e = getSelectionEnd();
            if (s == e) return;

            String selectedText = mGapBuffer.substring(s, e);
            if (selectedText != null && !selectedText.isEmpty()) {
                String lowerCaseText = selectedText.toLowerCase();

                mGapBuffer.beginBatchEdit();
                try {
                    mGapBuffer.replace(s, e, lowerCaseText, true);
                    setSelection(s, s + lowerCaseText.length());
                } finally {
                    mGapBuffer.endBatchEdit();
                }
                updateSelectionHandles();
            }
        } else {
            int lineStart = getLineStart(mCursorLine);
            String currentLine = getLine(mCursorLine);
            if (currentLine != null && !currentLine.isEmpty()) {
                String lowerCaseText = currentLine.toLowerCase();

                mGapBuffer.beginBatchEdit();
                mGapBuffer.replace(lineStart, lineStart + currentLine.length(), lowerCaseText, true);
                mGapBuffer.endBatchEdit();
                clearSelectionMenu();

                mCursorIndex = lineStart + lowerCaseText.length();
                mCursorLine = getOffsetLine(mCursorIndex);
                adjustCursorPosition();
            }
        }
        onTextChanged();
        postInvalidate();
    }

    public void convertSelectionToUpperCase() {
        if (isSelectMode) {
            int s = getSelectionStart();
            int e = getSelectionEnd();
            if (s == e) return;

            String selectedText = mGapBuffer.substring(s, e);
            if (selectedText != null && !selectedText.isEmpty()) {
                String upperCaseText = selectedText.toUpperCase();

                mGapBuffer.beginBatchEdit();
                try {
                    mGapBuffer.replace(s, e, upperCaseText, true);
                    setSelection(s, s + upperCaseText.length());
                } finally {
                    mGapBuffer.endBatchEdit();
                }
                updateSelectionHandles();
            }
        } else {

            int lineStart = getLineStart(mCursorLine);
            String currentLine = getLine(mCursorLine);
            if (currentLine != null && !currentLine.isEmpty()) {
                String upperCaseText = currentLine.toUpperCase();
                mGapBuffer.beginBatchEdit();
                mGapBuffer.replace(lineStart, lineStart + currentLine.length(), upperCaseText, true);
                mGapBuffer.endBatchEdit();
                clearSelectionMenu();

                mCursorIndex = lineStart + upperCaseText.length();
                mCursorLine = getOffsetLine(mCursorIndex);
                adjustCursorPosition();
            }
        }
        onTextChanged();
        postInvalidate();
    }

    public void increaseIndent() {
        int s = getSelectionStart();
        int e = getSelectionEnd();
        int start = Math.min(s, e);
        int end = Math.max(s, e);

        mGapBuffer.beginBatchEdit();
        try {
            if (isSelectMode) {
                int startLine = getOffsetLine(start);
                int endLine = getOffsetLine(end == 0 ? 0 : end - 1);

                int blockStart = getLineStart(startLine);
                int lastLineEnd = getLineEnd(endLine);
                String block = mGapBuffer.substring(blockStart, lastLineEnd);

                String[] lines = block.split("\n", -1);
                StringBuilder sb = new StringBuilder();
                int addedTotal = 0;
                for (int i = 0; i < lines.length; i++) {
                    sb.append("    ").append(lines[i]);
                    if (i < lines.length - 1) sb.append("\n");
                    addedTotal += 4;
                }

                mGapBuffer.replace(blockStart, lastLineEnd, sb.toString(), true);
                onTextChanged(startLine);

                // Adjust selection to cover the newly indented lines
                setSelection(start + 4, end + addedTotal);
                updateSelectionHandles();
            } else {
                int lineStart = getLineStart(mCursorLine);
                mGapBuffer.insert(lineStart, "    ", true);
                onTextChanged(mCursorLine);

                mCursorIndex += 4;
                adjustCursorPosition();
                clearSelectionMenu();
            }
        } finally {
            mGapBuffer.endBatchEdit();
        }
        postInvalidate();
    }

    public void decreaseIndent() {
        int s = getSelectionStart();
        int e = getSelectionEnd();
        int start = Math.min(s, e);
        int end = Math.max(s, e);

        mGapBuffer.beginBatchEdit();
        try {
            if (!isSelectMode) {
                int lineStart = getLineStart(mCursorLine);
                String line = getLine(mCursorLine);

                int spacesToRemove = 0;
                if (line.startsWith("    ")) spacesToRemove = 4;
                else if (line.startsWith("  ")) spacesToRemove = 2;
                else if (line.startsWith("\t")) spacesToRemove = 1;
                else if (line.startsWith(" ")) spacesToRemove = 1;

                if (spacesToRemove > 0) {
                    mGapBuffer.delete(lineStart, lineStart + spacesToRemove, true);
                    onTextChanged(mCursorLine);
                    mCursorIndex = Math.max(0, mCursorIndex - spacesToRemove);
                    adjustCursorPosition();
                }
            } else {
                int startLine = getOffsetLine(start);
                int endLine = getOffsetLine(end == 0 ? 0 : end - 1);

                int blockStart = getLineStart(startLine);
                int lastLineEnd = getLineEnd(endLine);
                String block = mGapBuffer.substring(blockStart, lastLineEnd);

                String[] lines = block.split("\n", -1);
                StringBuilder sb = new StringBuilder();
                int removedTotal = 0;
                int firstLineRemoved = 0;

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    int spacesToRemove = 0;
                    if (line.startsWith("    ")) spacesToRemove = 4;
                    else if (line.startsWith("  ")) spacesToRemove = 2;
                    else if (line.startsWith("\t")) spacesToRemove = 1;
                    else if (line.startsWith(" ")) spacesToRemove = 1;

                    if (spacesToRemove > 0) {
                        sb.append(line.substring(spacesToRemove));
                        removedTotal += spacesToRemove;
                        if (i == 0) firstLineRemoved = spacesToRemove;
                    } else {
                        sb.append(line);
                    }
                    if (i < lines.length - 1) sb.append("\n");
                }

                mGapBuffer.replace(blockStart, lastLineEnd, sb.toString(), true);
                onTextChanged(startLine);

                setSelection(Math.max(blockStart, start - firstLineRemoved), Math.max(blockStart, end - removedTotal));
                updateSelectionHandles();
            }
        } finally {
            mGapBuffer.endBatchEdit();
        }
        postInvalidate();
    }

    public void toggleComment() {
        int startLine, endLine;
        int s = getSelectionStart();
        int e = getSelectionEnd();
        if (isSelectMode) {
            startLine = getOffsetLine(s);
            endLine = getOffsetLine(e == 0 ? 0 : Math.max(0, e - 1));
        } else {
            startLine = endLine = mCursorLine;
        }

        String commentStart = getCommentBlock();
        String commentEnd = null;

        if (commentStart == null || commentStart.isEmpty()) {
            if (mHighlighter != null) {
                List<modder.hub.editor.highlight.CommentDef> defs = mHighlighter.getCommentDefs();
                if (!defs.isEmpty()) {
                    commentStart = defs.get(0).startsWith;
                    commentEnd = defs.get(0).endsWith;
                }
            }
        }

        if (commentStart == null || commentStart.isEmpty()) return;

        final String cStart = commentStart;
        final String cEnd = commentEnd;
        final boolean isDoubleMarker = cEnd != null && !cEnd.isEmpty();

        mGapBuffer.beginBatchEdit();
        try {
            boolean allCommented = true;
            for (int i = startLine; i <= endLine; i++) {
                String line = getLine(i);
                if (line.trim().isEmpty()) continue;
                if (!isLineCommented(line, cStart, cEnd)) {
                    allCommented = false;
                    break;
                }
            }

            int blockStart = getLineStart(startLine);
            int blockEnd = getLineEnd(endLine);
            String block = mGapBuffer.substring(blockStart, blockEnd);
            String[] lines = block.split("\n", -1);
            StringBuilder sb = new StringBuilder();

            int newSelStart = s;
            int newSelEnd = e;
            int currentOffsetInBuf = blockStart;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineLen = line.length();
                String newLine = line;
                int lineStartInBuf = currentOffsetInBuf;

                if (!line.trim().isEmpty()) {
                    if (allCommented) {
                        // Uncomment
                        if (isLineCommented(line, cStart, cEnd)) {
                            int contentStart = 0;
                            while (contentStart < line.length() && Character.isWhitespace(line.charAt(contentStart))) {
                                contentStart++;
                            }

                            int contentEnd = line.length();
                            while (contentEnd > contentStart && Character.isWhitespace(line.charAt(contentEnd - 1))) {
                                contentEnd--;
                            }

                            // Remove start marker
                            int removeStartLen = cStart.length();
                            if (contentStart + removeStartLen < line.length() && line.charAt(contentStart + removeStartLen) == ' ') {
                                removeStartLen++;
                            }

                            // Remove end marker if exists
                            int removeEndLen = 0;
                            if (isDoubleMarker) {
                                removeEndLen = cEnd.length();
                                if (contentEnd - removeStartLen - removeEndLen >= contentStart && line.charAt(contentEnd - removeEndLen - 1) == ' ') {
                                    removeEndLen++;
                                }
                            }

                            newLine = line.substring(0, contentStart) +
                                    line.substring(contentStart + removeStartLen, contentEnd - removeEndLen) +
                                    line.substring(contentEnd);

                            // Re-calculate more precisely for selection shift
                            // Start Shift
                            int shiftStart = -removeStartLen;
                            int removeStartPos = lineStartInBuf + contentStart;
                            if (newSelStart > removeStartPos) {
                                if (newSelStart < removeStartPos - shiftStart)
                                    newSelStart = removeStartPos;
                                else newSelStart += shiftStart;
                            }
                            if (newSelEnd > removeStartPos) {
                                if (newSelEnd < removeStartPos - shiftStart)
                                    newSelEnd = removeStartPos;
                                else newSelEnd += shiftStart;
                            }

                            // End Shift
                            if (isDoubleMarker) {
                                int shiftEnd = -removeEndLen;
                                int removeEndPos = lineStartInBuf + contentEnd - removeEndLen;
                                if (newSelStart > removeEndPos) {
                                    if (newSelStart < removeEndPos - shiftEnd)
                                        newSelStart = removeEndPos;
                                    else newSelStart += shiftEnd;
                                }
                                if (newSelEnd > removeEndPos) {
                                    if (newSelEnd < removeEndPos - shiftEnd)
                                        newSelEnd = removeEndPos;
                                    else newSelEnd += shiftEnd;
                                }
                            }
                        }
                    } else {
                        // Comment
                        if (!isLineCommented(line, cStart, cEnd)) {
                            int contentStart = 0;
                            while (contentStart < line.length() && Character.isWhitespace(line.charAt(contentStart))) {
                                contentStart++;
                            }

                            int contentEnd = line.length();
                            while (contentEnd > contentStart && Character.isWhitespace(line.charAt(contentEnd - 1))) {
                                contentEnd--;
                            }

                            String startAdd = cStart + " ";
                            String endAdd = isDoubleMarker ? (" " + cEnd) : "";

                            newLine = line.substring(0, contentStart) + startAdd +
                                    line.substring(contentStart, contentEnd) +
                                    endAdd + line.substring(contentEnd);

                            // Selection shifts
                            int insertStartAt = lineStartInBuf + contentStart;
                            if (newSelStart >= insertStartAt) newSelStart += startAdd.length();
                            if (newSelEnd >= insertStartAt) newSelEnd += startAdd.length();

                            if (isDoubleMarker) {
                                int insertEndAt = lineStartInBuf + contentEnd + startAdd.length();
                                if (newSelStart >= insertEndAt) newSelStart += endAdd.length();
                                if (newSelEnd >= insertEndAt) newSelEnd += endAdd.length();
                            }
                        }
                    }
                }

                sb.append(newLine);
                if (i < lines.length - 1) sb.append("\n");
                currentOffsetInBuf += lineLen + 1;
            }

            mGapBuffer.replace(blockStart, blockEnd, sb.toString(), true);
            onTextChanged(startLine);

            if (isSelectMode) {
                setSelection(newSelStart, newSelEnd);
                updateSelectionHandles();
            } else {
                mCursorIndex = newSelStart;
                mCursorLine = getOffsetLine(mCursorIndex);
                adjustCursorPosition();
                clearSelectionMenu();
            }
        } finally {
            mGapBuffer.endBatchEdit();
        }
        postInvalidate();
    }

    private boolean isLineCommented(String line, String cStart, String cEnd) {
        if (line == null || line.trim().isEmpty()) return false;
        int contentStart = 0;
        while (contentStart < line.length() && Character.isWhitespace(line.charAt(contentStart))) {
            contentStart++;
        }
        if (!line.startsWith(cStart, contentStart)) return false;

        if (cEnd != null && !cEnd.isEmpty()) {
            int contentEnd = line.length();
            while (contentEnd > contentStart && Character.isWhitespace(line.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            return line.substring(contentStart, contentEnd).endsWith(cEnd);
        }
        return true;
    }

    public void find(String regex) {
        mLastSearchPattern = regex != null ? regex : "";
        if (mReplaceList == null) mReplaceList = new ArrayList<>();
        mReplaceList.clear();

        if (mLastSearchPattern.isEmpty()) {
            postInvalidate();
            return;
        }

        try {
            // OPTIMIZATION: Only find matches in and around the visible area for drawing
            Rect clip = new Rect();
            getDrawingRect(clip);
            clip.offset(getScrollX(), getScrollY());

            int startLine = getLogicalLineFromY(Math.max(0, clip.top - getHeight()));
            int endLine = getLogicalLineFromY(clip.bottom + getHeight());

            int startOffset = getLineStart(startLine);
            int endOffset = getLineEnd(endLine);

            Matcher matcher = Pattern.compile(mLastSearchPattern).matcher(mGapBuffer);
            matcher.region(startOffset, endOffset);

            while (matcher.find()) {
                mReplaceList.add(new Pair<>(matcher.start(), matcher.end()));
            }

            // CRITICAL: Ensure the match containing the cursor is ALWAYS highlighted
            int cursor = mCursorIndex;
            Matcher cursorMatcher = Pattern.compile(mLastSearchPattern).matcher(mGapBuffer);
            if (cursorMatcher.find(Math.max(0, cursor - 1000))) { // Search slightly before cursor
                // Re-scan from the result to see if cursor is actually inside it
                matcher.reset();
                if (matcher.find(cursorMatcher.start()) && matcher.start() <= cursor && matcher.end() >= cursor) {
                    boolean found = false;
                    for (Pair<Integer, Integer> p : mReplaceList) {
                        if (p.first == matcher.start()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        mReplaceList.add(new Pair<>(matcher.start(), matcher.end()));
                        Collections.sort(mReplaceList, new Comparator<Pair<Integer, Integer>>() {
                            @Override
                            public int compare(Pair<Integer, Integer> a, Pair<Integer, Integer> b) {
                                return Integer.compare(a.first, b.first);
                            }
                        });
                    }
                }
            }

        } catch (Exception e) {
            // Invalid regex
        }
        postInvalidate();
    }

    public void previous() {
        if (mLastSearchPattern.isEmpty()) return;

        int startPos = getSelectionStart();
        try {
            Matcher matcher = Pattern.compile(mLastSearchPattern).matcher(mGapBuffer);
            int lastMatchStart = -1;
            int lastMatchEnd = -1;

            // Search from beginning until we hit the current cursor
            while (matcher.find()) {
                if (matcher.start() >= startPos) break;
                lastMatchStart = matcher.start();
                lastMatchEnd = matcher.end();
            }

            // If no match before cursor, try to find the very last one in the file (wrap around)
            if (lastMatchStart == -1) {
                matcher.reset();
                while (matcher.find()) {
                    lastMatchStart = matcher.start();
                    lastMatchEnd = matcher.end();
                }
            }

            if (lastMatchStart != -1) {
                recordHistory();
                setCursorPosition(lastMatchEnd);
                setSelection(lastMatchStart, lastMatchEnd);
                recordHistory();
                scrollToVisible();

                // Update the visible highlights
                find(mLastSearchPattern);
            }
        } catch (Exception e) {
            Log.e(TAG, "Search error", e);
        }
    }

    public void next() {
        if (mLastSearchPattern.isEmpty()) return;

        int startPos = getSelectionEnd();
        try {
            Matcher matcher = Pattern.compile(mLastSearchPattern).matcher(mGapBuffer);

            // Find the first match AFTER the current cursor/selection
            if (matcher.find(startPos)) {
                recordHistory();
                setCursorPosition(matcher.end());
                setSelection(matcher.start(), matcher.end());
                recordHistory();
                scrollToVisible();
            } else {
                // Wrap around to the beginning
                if (matcher.find(0)) {
                    recordHistory();
                    setCursorPosition(matcher.end());
                    setSelection(matcher.start(), matcher.end());
                    recordHistory();
                    scrollToVisible();
                }
            }
            // Refresh list for highlighting
            find(mLastSearchPattern);
        } catch (Exception e) {
            Log.e(TAG, "Search error", e);
        }
    }

    public void replaceFirst(String replacement) {
        if (!mReplaceList.isEmpty() && isEditedMode) {
            int start = mReplaceList.get(0).first;
            int end = mReplaceList.get(0).second;

            mGapBuffer.beginBatchEdit();
            mGapBuffer.replace(start, end, replacement, true);
            mGapBuffer.endBatchEdit();

            int length = replacement.length();
            setCursorPosition(start + length);
            setSelection(start + length, start + length);

            mReplaceList.remove(0);

            int delta = start + length - end;

            for (int i = 0; i < mReplaceList.size(); ++i) {
                int first = mReplaceList.get(i).first + delta;
                int second = mReplaceList.get(i).second + delta;
                mReplaceList.set(i, new Pair<Integer, Integer>(first, second));
            }
        } else {
            isSelectMode = false;
        }
        postInvalidate();
    }

    public void replaceAll(String replacement) {
        while (!mReplaceList.isEmpty() && isEditedMode) {
            replaceFirst(replacement);
        }
    }

    private int findMatchingBrace(int index) {
        if (index < 0 || index >= mGapBuffer.length()) return -1;
        char c = mGapBuffer.charAt(index);
        int direction;
        char matchChar;
        if (c == '{') {
            direction = 1;
            matchChar = '}';
        } else if (c == '}') {
            direction = -1;
            matchChar = '{';
        } else if (c == '(') {
            direction = 1;
            matchChar = ')';
        } else if (c == ')') {
            direction = -1;
            matchChar = '(';
        } else if (c == '[') {
            direction = 1;
            matchChar = ']';
        } else if (c == ']') {
            direction = -1;
            matchChar = '[';
        } else return -1;

        int depth = 0;
        int i = index;

        // Robust scanner that skips strings and comments (Java/Smali style)
        // Background search allows us to increase this significantly (e.g. 100 million chars)
        int maxSearch = 100000000;
        int steps = 0;

        while (i >= 0 && i < mGapBuffer.length() && steps < maxSearch) {
            steps++;
            char curr = mGapBuffer.charAt(i);

            if (direction == 1) { // Forward Search
                // Skip strings
                if (curr == '"' || curr == '\'') {
                    char quote = curr;
                    i++;
                    while (i < mGapBuffer.length()) {
                        char c2 = mGapBuffer.charAt(i);
                        if (c2 == '\\') {
                            i++;
                        } else if (c2 == quote) {
                            break;
                        } else if (c2 == '\n') {
                            break;
                        }
                        i++;
                    }
                }
                // Skip line comments
                else if (curr == '/' && i + 1 < mGapBuffer.length() && mGapBuffer.charAt(i + 1) == '/') {
                    while (i < mGapBuffer.length() && mGapBuffer.charAt(i) != '\n') i++;
                } else if (curr == '#') {
                    while (i < mGapBuffer.length() && mGapBuffer.charAt(i) != '\n') i++;
                }
                // Skip block comments
                else if (curr == '/' && i + 1 < mGapBuffer.length() && mGapBuffer.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < mGapBuffer.length()) {
                        if (mGapBuffer.charAt(i) == '*' && mGapBuffer.charAt(i + 1) == '/') {
                            i++;
                            break;
                        }
                        i++;
                    }
                } else {
                    if (curr == c) depth++;
                    else if (curr == matchChar) depth--;
                }
            } else { // Backward Search
                // In backward search, we simply check depth but then verify if the result
                // is inside a comment/string on its line.
                if (curr == c) depth++;
                else if (curr == matchChar) depth--;

                if (depth == 0) {
                    // Verification of the matched candidate
                    int line = getOffsetLine(i);
                    int lineStart = getLineStart(line);
                    String lineText = getLine(line);
                    int offsetInLine = i - lineStart;

                    boolean inComment = false;
                    boolean inString = false;
                    char q = 0;
                    for (int j = 0; j < offsetInLine; j++) {
                        char ch = lineText.charAt(j);
                        if (inString) {
                            if (ch == '\\') j++;
                            else if (ch == q) inString = false;
                        } else {
                            if (ch == '"' || ch == '\'') {
                                inString = true;
                                q = ch;
                            } else if (ch == '/' && j + 1 < lineText.length() && lineText.charAt(j + 1) == '/') {
                                inComment = true;
                                break;
                            } else if (ch == '#') {
                                inComment = true;
                                break;
                            }
                        }
                    }
                    if (inComment || inString) {
                        depth = -1; // mismatch found inside comment/string, keep going
                    }
                }
            }

            if (depth == 0) return i;
            i += direction;
        }
        return -1;
    }

    private String getCurrentPrefix() {
        if (mCursorIndex <= 0) return "";

        int start = mCursorIndex;
        while (start > 0) {
            char prevChar = mGapBuffer.charAt(start - 1);
            if (!isInstructionPart(prevChar)) {
                break;
            }
            start--;
        }

        if (start >= mCursorIndex) return "";

        String prefix = mGapBuffer.substring(start, mCursorIndex);

        // Reject trivial/ambiguous prefixes
        if (prefix.equals("/") || prefix.equals("-") || prefix.equals("//")
                || prefix.equals("--") || prefix.equals("_") || prefix.equals(".")) {
            return "";
        }
        if ((prefix.startsWith("/") || prefix.startsWith("-") || prefix.startsWith("_"))
                && prefix.length() < 2) {
            return "";
        }

        return prefix;
    }

    private void filterAndShowSuggestions(String prefix) {
        if (prefix.isEmpty()) {
            dismissAutoComplete();
            return;
        }
        final String lowerPrefix = prefix.toLowerCase();
        List<String> priorityList = new ArrayList<>();
        List<String> containsList = new ArrayList<>();

        for (String word : mWordSet) {
            String langName = (mHighlighter != null) ? mHighlighter.getLanguageName() : null;
            if (!"Smali".equalsIgnoreCase(langName)) {
                break;
            }
            if (word.length() < prefix.length()) continue;

            String lowerWord = word.toLowerCase();
            if (lowerWord.startsWith(lowerPrefix) && !word.equals(prefix)) {
                priorityList.add(word);
            } else if (lowerWord.contains(lowerPrefix) && !word.equals(prefix)) {
                containsList.add(word);
            }
        }

        for (String word : mWordFrequencyMap.keySet()) {
            if (word.length() < prefix.length()) continue;
            if (mWordSet.contains(word)) continue;

            String lowerWord = word.toLowerCase();
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

        // Always update adapter if prefix changed to refresh highlighting
        mAutoCompleteAdapter.setNotifyOnChange(false);
        mAutoCompleteAdapter.clear();
        mAutoCompleteAdapter.addAll(suggestions);
        mAutoCompleteAdapter.notifyDataSetChanged();

        showAutoComplete(prefix);
    }

    private void showAutoComplete(String prefix) {
        if (!mAutoCompleteEnabled || prefix == null || prefix.isEmpty() || mAutoCompleteAdapter.isEmpty()) {
            dismissAutoComplete();
            return;
        }

        // Anchor horizontally to the start of the word to prevent flickering while typing
        int wordStartIdx = mCursorIndex - prefix.length();
        Rect anchorRect = getBoundingBox(wordStartIdx);

        if (mClipboardPanel != null && mClipboardPanel.isShowing()) {
            hideTextSelectionWindow();
        }

        int count = mAutoCompleteAdapter.getCount();
        float density = getResources().getDisplayMetrics().density;

        int viewWidth = getWidth();
        int popupWidth = (int) (viewWidth * 0.85f);
        int maxAllowedWidth = (int) (density * 400);
        if (popupWidth > maxAllowedWidth) popupWidth = maxAllowedWidth;

        // Position horizontally: anchor to word start but ensure it fits on screen
        int horizontalOffset = anchorRect.left;
        if (horizontalOffset + popupWidth > viewWidth) {
            horizontalOffset = viewWidth - popupWidth - (int) (density * 16);
        }
        horizontalOffset = Math.max(0, horizontalOffset);

        int itemHeight = (int) (density * 40);
        int visibleCount = Math.min(count, 3);
        int popupHeight = itemHeight * visibleCount;

        int verticalOffset = anchorRect.bottom - getHeight();

        boolean isShowing = mAutoCompletePopup.isShowing();

        // only refresh window if it actually needs to move line or change size
        // Anchoring to word start means horizontalOffset stays constant while typing the same word.
        boolean moved = isShowing && (mAutoCompletePopup.getVerticalOffset() != verticalOffset ||
                Math.abs(mAutoCompletePopup.getHorizontalOffset() - horizontalOffset) > density * 5);
        boolean resized = isShowing && (mAutoCompletePopup.getHeight() != popupHeight);

        if (!isShowing || moved || resized) {
            mAutoCompletePopup.setWidth(popupWidth);
            mAutoCompletePopup.setHeight(popupHeight);
            mAutoCompletePopup.setHorizontalOffset(horizontalOffset);
            mAutoCompletePopup.setVerticalOffset(verticalOffset);

            if (!isShowing) {
                mAutoCompletePopup.setModal(false);
                mAutoCompletePopup.setAnimationStyle(0); // No animation
                mAutoCompletePopup.setBackgroundDrawable(
                        getResources().getDrawable(android.R.drawable.dialog_holo_light_frame)
                );
            }
            mAutoCompletePopup.show();
        }

        // Ensure the cursor and the autocomplete popup are visible
        int popupBottom = anchorRect.bottom + popupHeight;
        int viewBottom = getHeight();
        if (popupBottom > viewBottom) {
            smoothScrollBy(0, popupBottom - viewBottom + (int) (density * 16));
        } else {
            scrollToVisible();
        }
    }

    private void replacePrefixWithWord(String fullWord) {
        int[] bounds = getWordBoundsAt(mCursorIndex);
        int start, end;
        if (bounds != null) {
            start = bounds[0];
            end = bounds[1];
        } else {
            String prefix = getCurrentPrefix();
            start = mCursorIndex - prefix.length();
            end = mCursorIndex;
        }

        int oldLine = mCursorLine;

        mGapBuffer.beginBatchEdit();
        mGapBuffer.replace(start, end, fullWord, true);
        mGapBuffer.endBatchEdit();

        // Clear any active composing state from IME
        mComposingStart = -1;
        mComposingEnd = -1;

        mCursorIndex = start + fullWord.length();
        mCursorLine = getOffsetLine(mCursorIndex);

        adjustCursorPosition();
        scrollToVisible();
        onCursorOrSelectionChanged(); // This notifies IMM
        postInvalidate();
        onTextChanged(oldLine);
    }

    private void dismissAutoComplete() {
        if (mAutoCompletePopup.isShowing()) {
            mAutoCompletePopup.dismiss();
        }
        mCurrentPrefix = "";
    }

    private void showMagnifier(float x, float y) {
        if (!mMagnifierEnabled || mMagnifier == null) return;
        try {
            hideTextSelectionWindow();

            // Magnifier should focus on the text, so if y is bottom of a line/handle,
            // we adjust it to the middle of the line.
            float adjustedX = x - getScrollX() + getPaddingLeft();
            float adjustedY = y - getScrollY() + getPaddingTop() - getLineHeight() * 0.5f;

            adjustedX = Math.max(0, Math.min(adjustedX, getWidth()));
            adjustedY = Math.max(0, Math.min(adjustedY, getHeight()));

            mMagnifier.show(adjustedX, adjustedY);
            mMagnifierX = adjustedX;
            mMagnifierY = adjustedY;
            mIsMagnifierShowing = true;
        } catch (Exception e) {
            Log.e(TAG, "Error showing magnifier: " + e.getMessage());
            dismissMagnifier();
        }
    }

    private void updateMagnifier(float x, float y) {
        if (!mIsMagnifierShowing || !mMagnifierEnabled || mMagnifier == null) return;
        try {
            float adjustedX = x - getScrollX() + getPaddingLeft();
            float adjustedY = y - getScrollY() + getPaddingTop() - getLineHeight() * 0.5f;

            adjustedX = Math.max(0, Math.min(adjustedX, getWidth()));
            adjustedY = Math.max(0, Math.min(adjustedY, getHeight()));

            // Smoother threshold: only update if moved significantly or if always updating for MT vibe
            if (Math.abs(adjustedX - mMagnifierX) >= 0.1f || Math.abs(adjustedY - mMagnifierY) >= 0.1f) {
                mMagnifier.show(adjustedX, adjustedY);
                mMagnifierX = adjustedX;
                mMagnifierY = adjustedY;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating magnifier: " + e.getMessage());
            dismissMagnifier();
        }
    }

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

    public void showTextSelectionWindow(Rect targetRect) {
        if (mClipboardPanel != null && (isSelectMode || mHandleMiddleVisible)) {
            post(new Runnable() {
                @Override
                public void run() {
                    mClipboardPanel.showAtLocation(targetRect);
                    scheduleAutoHide();
                }
            });
        }
    }

    public void showTextSelectionWindow() {
        showTextSelectionWindow(null);
    }

    public void hideTextSelectionWindow() {
        if (mClipboardPanel != null) {
            mSelectionHandler.removeCallbacks(mAutoHideRunnable);
            post(new Runnable() {
                @Override
                public void run() {
                    mClipboardPanel.hide();
                }
            });
        }
    }

    private void scheduleAutoHide() {
        mSelectionHandler.removeCallbacks(mAutoHideRunnable);
        mSelectionHandler.postDelayed(mAutoHideRunnable, 4000);
    }

    private void batchReplaceWithSelectionSnapshot(int replaceStart, int replaceEnd, String text,
                                                   boolean wasSelectBefore, int selBeforeStart, int selBeforeEnd,
                                                   boolean setSelectionAfter, int selAfterStart, int selAfterEnd) {
        mGapBuffer.markSelectionBefore(selBeforeStart, selBeforeEnd, wasSelectBefore);
        mGapBuffer.beginBatchEdit();
        try {
            mGapBuffer.replace(replaceStart, replaceEnd, text, true);
            if (setSelectionAfter) {
                setSelection(selAfterStart, selAfterEnd);
                mGapBuffer.markSelectionAfter(selAfterStart, selAfterEnd, true);
            } else {
                mGapBuffer.markSelectionAfter(-1, -1, false);
            }
        } finally {
            mGapBuffer.endBatchEdit();
        }
    }

    private void batchDeleteWithSelectionSnapshot(int delStart, int delEnd,
                                                  boolean wasSelectBefore, int selBeforeStart, int selBeforeEnd,
                                                  boolean setSelectionAfter, int selAfterStart, int selAfterEnd) {
        mGapBuffer.markSelectionBefore(selBeforeStart, selBeforeEnd, wasSelectBefore);
        mGapBuffer.beginBatchEdit();
        try {
            mGapBuffer.delete(delStart, delEnd, true);
            if (setSelectionAfter) {
                setSelection(selAfterStart, selAfterEnd);
                mGapBuffer.markSelectionAfter(selAfterStart, selAfterEnd, true);
            } else {
                mGapBuffer.markSelectionAfter(-1, -1, false);
            }
        } finally {
            mGapBuffer.endBatchEdit();
        }
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int start, int end);
    }

    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        boolean touchOnSelectHandleMiddle = false;
        boolean touchOnSelectHandleLeft = false;
        boolean touchOnSelectHandleRight = false;
        private boolean mIsMagnifierActive = false;

        private void reverse() {
            int start = getSelectionStart();
            int end = getSelectionEnd();

            selectHandleLeftX = selectHandleLeftX ^ selectHandleRightX;
            selectHandleRightX = selectHandleLeftX ^ selectHandleRightX;
            selectHandleLeftX = selectHandleLeftX ^ selectHandleRightX;

            selectHandleLeftY = selectHandleLeftY ^ selectHandleRightY;
            selectHandleRightY = selectHandleLeftY ^ selectHandleRightY;
            selectHandleLeftY = selectHandleLeftY ^ selectHandleRightY;

            setSelection(end, start);

            touchOnSelectHandleLeft = !touchOnSelectHandleLeft;
            touchOnSelectHandleRight = !touchOnSelectHandleRight;
        }

        private boolean checkSelectRange(float x, float y) {
            float internalY = y - getPaddingTop();
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();
            if (selStart == selEnd) return false;

            // Normalize for hit-testing range
            int s = Math.min(selStart, selEnd);
            int e = Math.max(selStart, selEnd);

            int startLine = getOffsetLine(s);
            int endLine = getOffsetLine(e);

            float slop = ScreenUtils.dip2px(getContext(), 12);

            // Basic vertical filter with small slop
            if (internalY < getLineTop(startLine) - slop || internalY > getLineBottom(endLine) + slop)
                return false;

            int line = getLogicalLineFromY(internalY);
            if (line < startLine || line > endLine) return false;

            int left = getLeftSpace();
            String lineText = getLine(line);
            int lineStartIdx = getLineStart(line);
            LineResult res = (mHighlighter != null) ? mHighlighter.getOrTokenize(line, lineText) : null;

            if (mWordWrap && res != null && res.layout != null) {
                int vLine = res.layout.getLineForVertical((int) (internalY - getLineTop(line)));
                int vStartLayout = res.layout.getLineStart(vLine);
                int vEndLayout = res.layout.getLineEnd(vLine);

                // Map original selection offsets to layout offsets for this line
                int offStart = Math.max(0, s - lineStartIdx);
                int offEnd = Math.min(lineText.length(), e - lineStartIdx);

                int layoutSelStart;
                if (res.shiftMap != null) {
                    if (offStart < res.shiftMap.length) {
                        layoutSelStart = offStart + res.shiftMap[offStart];
                    } else {
                        layoutSelStart = offStart + (res.shiftMap.length > 0 ? res.shiftMap[res.shiftMap.length - 1] : 0);
                    }
                } else {
                    layoutSelStart = offStart;
                }

                int layoutSelEnd;
                if (res.shiftMap != null) {
                    if (offEnd < res.shiftMap.length) {
                        layoutSelEnd = offEnd + res.shiftMap[offEnd];
                    } else {
                        layoutSelEnd = offEnd + (res.shiftMap.length > 0 ? res.shiftMap[res.shiftMap.length - 1] : 0);
                    }
                } else {
                    layoutSelEnd = offEnd;
                }

                // If selection spans multiple lines, cap it for this visual line
                int overlapStart = Math.max(vStartLayout, layoutSelStart);
                int overlapEnd = Math.min(vEndLayout, layoutSelEnd);

                // If selection ends on a future line, this visual line is selected till the end
                if (e > lineStartIdx + lineText.length()) {
                    overlapEnd = vEndLayout;
                }
                // If selection starts on a previous line, this visual line is selected from the start
                if (s < lineStartIdx) {
                    overlapStart = vStartLayout;
                }

                if (overlapStart >= overlapEnd && e <= lineStartIdx + lineText.length()) {
                    // Check if it's hitting the newline area
                    if (e > lineStartIdx + lineText.length() && vLine == res.layout.getLineCount() - 1) {
                        float lastX = res.layout.getPrimaryHorizontal(vEndLayout);
                        return x >= left + lastX - slop && x <= left + lastX + spaceWidth + slop;
                    }
                    return false;
                }

                float minX = res.layout.getPrimaryHorizontal(overlapStart);
                float maxX = res.layout.getPrimaryHorizontal(overlapEnd);

                float leftEdge = left + Math.min(minX, maxX);
                float rightEdge = left + Math.max(minX, maxX);

                // If it's the end of a line and selection continues, expand right edge
                if (e > lineStartIdx + lineText.length() && overlapEnd == vEndLayout) {
                    rightEdge += spaceWidth;
                }

                return x >= leftEdge - slop && x <= rightEdge + slop;
            } else {
                int overlapStart = Math.max(lineStartIdx, s);
                int overlapEnd = Math.min(lineStartIdx + lineText.length(), e);
                if (overlapStart >= overlapEnd) return false;

                float sX = left + measureText(lineText.substring(0, overlapStart - lineStartIdx), left);
                float eX = left + measureText(lineText.substring(0, overlapEnd - lineStartIdx), left);
                return x >= sX - slop && x <= eX + slop;
            }
        }

        private boolean isTouchOnHandleMiddle(float x, float y) {
            if (!mHandleMiddleVisible) return false;
            float slop = ScreenUtils.dip2px(getContext(), 2);
            float internalX = x - getPaddingLeft();
            float internalY = y - getPaddingTop();

            float left = mCursorPosX - (float) handleMiddleWidth / 2;
            float right = mCursorPosX + (float) handleMiddleWidth / 2;
            float top = mCursorPosY + getLineHeight();
            float bottom = top + handleMiddleHeight;
            return internalX >= left - slop && internalX <= right + slop && internalY >= top - slop && internalY <= bottom + slop;
        }

        private boolean isTouchOnHandleLeft(float x, float y) {
            if (!isSelectMode) return false;
            float slop = ScreenUtils.dip2px(getContext(), 2);
            float internalX = x - getPaddingLeft();
            float internalY = y - getPaddingTop();

            float left = selectHandleLeftX - selectHandleWidth + (float) selectHandleWidth / 4;
            float right = selectHandleLeftX + (float) selectHandleWidth / 4;
            float top = selectHandleLeftY;
            float bottom = top + selectHandleHeight;
            return internalX >= left - slop && internalX <= right + slop && internalY >= top - slop && internalY <= bottom + slop;
        }

        private boolean isTouchOnHandleRight(float x, float y) {
            if (!isSelectMode) return false;
            float slop = ScreenUtils.dip2px(getContext(), 2);
            float internalX = x - getPaddingLeft();
            float internalY = y - getPaddingTop();

            float left = selectHandleRightX - (float) selectHandleWidth / 4;
            float right = selectHandleRightX + selectHandleWidth - (float) selectHandleWidth / 4;
            float top = selectHandleRightY;
            float bottom = top + selectHandleHeight;
            return internalX >= left - slop && internalX <= right + slop && internalY >= top - slop && internalY <= bottom + slop;
        }        private final Runnable moveAction = new Runnable() {
            @Override
            public void run() {
                try {
                    boolean scrolled = onMove();
                    if (scrolled) {
                        mAutoScrollFactor = Math.min(mAutoScrollFactor + 0.1f, 3f);
                    } else {
                        mAutoScrollFactor = 0f;
                        if (mIsMagnifierActive && !mIsMagnifierShowing && mMagnifierEnabled) {
                            showMagnifierForHandle();
                        }
                    }
                    if (EditView.this.isAttachedToWindow() && (touchOnSelectHandleMiddle || touchOnSelectHandleLeft || touchOnSelectHandleRight)) {
                        long delay = scrolled ? Math.max(25, 100 - (int) (mAutoScrollFactor * 25)) : 100;
                        postDelayed(moveAction, delay);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in moveAction: " + e.getMessage());
                    dismissMagnifier();
                }
            }

            private void showMagnifierForHandle() {
                if (touchOnSelectHandleMiddle) {
                    showMagnifier(mCursorPosX, mCursorPosY + getLineHeight());
                } else if (touchOnSelectHandleLeft) {
                    showMagnifier(selectHandleLeftX, selectHandleLeftY);
                } else if (touchOnSelectHandleRight) {
                    showMagnifier(selectHandleRightX, selectHandleRightY);
                }
            }
        };

        private boolean isTouchOnAnyHandle(float x, float y) {
            return isTouchOnHandleMiddle(x, y) || isTouchOnHandleLeft(x, y) || isTouchOnHandleRight(x, y);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            mAutoScrollFactor = 0f;
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            if (isTouchOnHandleMiddle(x, y)) {
                touchOnSelectHandleMiddle = true;
                removeCallbacks(blinkAction);
                mCursorVisible = mHandleMiddleVisible = true;

                showTextSelectionWindow();

                if (mMagnifierEnabled) {
                    mIsMagnifierActive = true;
                    showMagnifier(mCursorPosX, mCursorPosY + getLineHeight());
                }

                return true;
            }

            if (isTouchOnHandleLeft(x, y)) {
                touchOnSelectHandleLeft = true;
                removeCallbacks(blinkAction);
                mCursorVisible = mHandleMiddleVisible = false;

                showTextSelectionWindow();
                if (mMagnifierEnabled) {
                    mIsMagnifierActive = true;
                    showMagnifier(selectHandleLeftX, selectHandleLeftY);
                }
                return true;
            }
            if (isTouchOnHandleRight(x, y)) {
                touchOnSelectHandleRight = true;
                removeCallbacks(blinkAction);
                mCursorVisible = mHandleMiddleVisible = false;
                showTextSelectionWindow();
                if (mMagnifierEnabled) {
                    mIsMagnifierActive = true;
                    showMagnifier(selectHandleRightX, selectHandleRightY);
                }
                return true;
            }
            return super.onDown(e);
        }

        @Override
        public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            try {
                float x = e2.getX() + getScrollX();
                float y = e2.getY() + getScrollY();

                float dx = distanceX;
                float dy = distanceY;

                if (touchOnSelectHandleMiddle) {
                    hideTextSelectionWindow();
                    setCursorPosition(x, y - getLineHeight() - (float) Math.min(getLineHeight(), selectHandleHeight) / 2);
                } else if (touchOnSelectHandleLeft) {
                    hideTextSelectionWindow();
                    setCursorPosition(x, y - getLineHeight() - (float) Math.min(getLineHeight(), selectHandleHeight) / 2);
                    selectHandleLeftX = mCursorPosX;
                    selectHandleLeftY = mCursorPosY + getLineHeight();
                    setSelection(mCursorIndex, getSelectionEnd());
                } else if (touchOnSelectHandleRight) {
                    hideTextSelectionWindow();
                    setCursorPosition(x, y - getLineHeight() - (float) Math.min(getLineHeight(), selectHandleHeight) / 2);
                    selectHandleRightX = mCursorPosX;
                    selectHandleRightY = mCursorPosY + getLineHeight();
                    setSelection(getSelectionStart(), mCursorIndex);
                } else {
                    if (Math.abs(dy) > Math.abs(dx))
                        dx = 0;
                    else
                        dy = 0;

                    int newX = (int) dx + getScrollX();
                    if (newX < 0) {
                        newX = 0;
                    } else if (newX > getMaxScrollX()) {
                        newX = getMaxScrollX();
                    }

                    int newY = (int) dy + getScrollY();
                    if (newY < 0) {
                        newY = 0;
                    } else if (newY > getMaxScrollY()) {
                        newY = getMaxScrollY();
                    }
                    smoothScrollTo(newX, newY);
                }

                if (touchOnSelectHandleMiddle || touchOnSelectHandleLeft || touchOnSelectHandleRight) {
                    // Just make sure moveAction is scheduled if not already
                    removeCallbacks(moveAction);
                    post(moveAction);
                }

                if (mIsMagnifierActive && mMagnifierEnabled && mAutoScrollFactor <= 0.8f) {
                    if (touchOnSelectHandleMiddle) {
                        float clampedX = Math.min(x, getLeftSpace() + getLineWidth(mCursorLine) + spaceWidth);
                        clampedX = Math.max(clampedX, (float) getLeftSpace());
                        updateMagnifier(clampedX, mCursorPosY + getLineHeight());
                    } else if (touchOnSelectHandleLeft) {
                        int leftLine = getOffsetLine(getSelectionStart());
                        float clampedX = Math.min(x, getLeftSpace() + getLineWidth(leftLine) + spaceWidth);
                        clampedX = Math.max(clampedX, (float) getLeftSpace());
                        updateMagnifier(clampedX, selectHandleLeftY);
                    } else if (touchOnSelectHandleRight) {
                        int rightLine = getOffsetLine(getSelectionEnd());
                        float clampedX = Math.min(x, getLeftSpace() + getLineWidth(rightLine) + spaceWidth);
                        clampedX = Math.max(clampedX, (float) getLeftSpace());
                        updateMagnifier(clampedX, selectHandleRightY);
                    }
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
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            // Clear composing state when user taps to move cursor
            mComposingStart = -1;
            mComposingEnd = -1;

            if (isEditedMode) {
                showSoftInput(true);
            }

            boolean hitHandle = isTouchOnAnyHandle(x, y);
            // Recovery: prevent cancellation of selection when tapping inside selected region
            boolean hitSelection = isSelectMode && checkSelectRange(x, y);

            if (!hitHandle && !hitSelection) {
                removeCallbacks(blinkAction);
                mCursorVisible = mHandleMiddleVisible = true;

                if (!mReplaceList.isEmpty())
                    mReplaceList.clear();

                setCursorPosition(x, y);
                // Sync the clear selection to the buffer
                setSelection(mCursorIndex, mCursorIndex);

                mHideSelectHandles = false;
                clearLineSelection();
                postInvalidate();
                // Ensure the tapped position is visible
                scrollToVisible();
                mLastTapTime = System.currentTimeMillis();
                postDelayed(blinkAction, BLINK_TIMEOUT);
            } else {
                mHideSelectHandles = false;
                showTextSelectionWindow();
                postInvalidate();
            }

            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            float vX = velocityX;
            float vY = velocityY;
            if (Math.abs(vY) > Math.abs(vX))
                vX = 0;
            else
                vY = 0;

            mScroller.fling(getScrollX(), getScrollY(), (int) -vX, (int) -vY,
                    0, getMaxScrollX(), 0, getMaxScrollY());

            postInvalidate();
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {
            super.onLongPress(e);
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            mHideSelectHandles = false;
            removeCallbacks(blinkAction);

            // For all text selected, show menu at press coordinate
            if (isAllTextSelected() || (isSelectMode && checkSelectRange(x, y))) {
                int viewX = (int) e.getX();
                int viewY = (int) e.getY();
                showTextSelectionWindow(new Rect(viewX, viewY, viewX, viewY));
            } else {
                showTextSelectionWindow();
            }

            mCursorVisible = mHandleMiddleVisible = true;

            if (isInLineNumberArea(x, y)) {
                int currentLine = getLineFromY(y);
                handleLineNumberLongPress(currentLine);
                return;
            }

            if (!touchOnSelectHandleMiddle && !touchOnSelectHandleLeft && !touchOnSelectHandleRight && mGapBuffer.length() > 0) {

                setCursorPosition(x, y);
                selectNearestWord();
            }
            postInvalidate();
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            super.onDoubleTap(e);

            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            mHideSelectHandles = false;
            removeCallbacks(blinkAction);
            mCursorVisible = mHandleMiddleVisible = true;

            int viewX = (int) e.getX();
            int viewY = (int) e.getY();
            showTextSelectionWindow(new Rect(viewX, viewY, viewX, viewY));

            if (!touchOnSelectHandleMiddle && !touchOnSelectHandleLeft && !touchOnSelectHandleRight && mGapBuffer.length() > 0) {
                setCursorPosition(x, y);
                selectNearestWord();
            }
            postInvalidate();
            return super.onDoubleTap(e);
        }

        public void onUp(MotionEvent ignoredE) {
            mAutoScrollFactor = 0f;
            if (mIsMagnifierActive) {
                dismissMagnifier();
                mIsMagnifierActive = false;
            }
            if (touchOnSelectHandleLeft || touchOnSelectHandleRight || touchOnSelectHandleMiddle) {
                Rect targetRect = null;
                if (touchOnSelectHandleLeft) {
                    targetRect = getBoundingBox(getSelectionStart());
                } else if (touchOnSelectHandleRight) {
                    targetRect = getBoundingBox(getSelectionEnd());
                } else {
                    targetRect = getBoundingBox(mCursorIndex);
                }

                removeCallbacks(moveAction);
                touchOnSelectHandleMiddle = false;
                touchOnSelectHandleLeft = false;
                touchOnSelectHandleRight = false;

                if (isSelectMode) {
                    setCursorPosition(getSelectionEnd());
                } else {
                    mLastTapTime = System.currentTimeMillis();
                    removeCallbacks(blinkAction);
                    mCursorVisible = true;
                    postDelayed(blinkAction, BLINK_TIMEOUT);
                }

                showTextSelectionWindow(targetRect); // Show menu at handle position
                scheduleAutoHide();
            }
        }

        private void handleLineNumberLongPress(int currentLine) {
            if (!mWaitingForSecondSelection) {
                selectSingleLine(currentLine);
                mFirstSelectedLine = currentLine;
                mWaitingForSecondSelection = true;
            } else {
                mSecondSelectedLine = currentLine;
                selectLineRange(mFirstSelectedLine, mSecondSelectedLine);
                mWaitingForSecondSelection = false;
                mSelectionHandler.removeCallbacks(mClearSelectionRunnable);
            }
        }
    }

    class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mIsScaling = true;
            mZoomScale = 1.0f;
            mZoomFocusX = detector.getFocusX();
            mZoomFocusY = detector.getFocusY();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newZoomScale = mZoomScale * scaleFactor;

            // Prevent over-zoom by clamping the projected text size during the gesture
            float currentSize = mTextPaint.getTextSize();
            float minPx = ScreenUtils.dip2px(getContext(), 10);
            float maxPx = ScreenUtils.dip2px(getContext(), 30);
            float projectedSize = currentSize * newZoomScale;

            if (projectedSize < minPx) {
                newZoomScale = minPx / currentSize;
            } else if (projectedSize > maxPx) {
                newZoomScale = maxPx / currentSize;
            }

            mZoomScale = newZoomScale;
            mZoomFocusX = detector.getFocusX();
            mZoomFocusY = detector.getFocusY();
            invalidate();
            return true;
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            float finalSize = mTextPaint.getTextSize() * mZoomScale;
            mIsScaling = false;
            mZoomScale = 1.0f;

            setTextSize(finalSize, mZoomFocusX, mZoomFocusY, true);

            if (mWordWrap) {
                if (mHighlighter != null) {
                    mHighlighter.setWordWrap(true, getWrapWidth());
                }
                computeLineTops();
            } else {
                calculateMaxWidth();
            }
            adjustCursorPosition();
            if (isSelectMode) {
                updateSelectionHandles();
            }
            postInvalidate();
        }
    }

    class TextInputConnection extends BaseInputConnection {

        public TextInputConnection(View view, boolean fullEditor) {
            super(view, fullEditor);
        }

        @Override
        public Editable getEditable() {
            return getText();
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            int len = mGapBuffer.length();
            int start = Math.max(0, mCursorIndex - n);
            int end = Math.min(len, mCursorIndex);
            if (start > end) return "";
            return mGapBuffer.substring(start, end);
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            int len = mGapBuffer.length();
            int start = Math.min(len, mCursorIndex);
            int end = Math.min(len, mCursorIndex + n);
            if (start > end) return "";
            return mGapBuffer.substring(start, end);
        }

        @Override
        public CharSequence getSelectedText(int flags) {
            if (isSelectMode) {
                return EditView.this.getSelectedText();
            }
            return null;
        }

        @Override
        public int getCursorCapsMode(int reqModes) {
            if (mCursorIndex <= 0) return 0;
            // Simplified caps mode detection
            return super.getCursorCapsMode(reqModes);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (text != null) {
                int start = isSelectMode ? getSelectionStart() : mCursorIndex;
                int end = isSelectMode ? getSelectionEnd() : mCursorIndex;

                if (mComposingStart != -1 && mComposingEnd != -1) {
                    start = Math.min(mComposingStart, mComposingEnd);
                    end = Math.max(mComposingStart, mComposingEnd);
                }

                String textToCommit = text.toString();
                if (mAutoIndentEnabled && textToCommit.equals("\n")) {
                    textToCommit = "\n" + getAutoIndent(start);
                }

                // Reset composing region before replaceInternal to ensure
                // onCursorOrSelectionChanged() reports finished composing state smoothly.
                mComposingStart = mComposingEnd = -1;
                isSelectMode = false;

                // IME owns this edit — don't double-capture in our undo stack
                mGapBuffer.setSuppressUndoCapture(true);
                try {
                    replaceInternal(start, end, textToCommit);
                } finally {
                    mGapBuffer.setSuppressUndoCapture(false);
                }

                return true;
            }
            return super.commitText(text, newCursorPosition);
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            if (text != null) {
                if (isSelectMode) {
                    delete();
                }
                if (mComposingStart == -1) {
                    mComposingStart = mCursorIndex;
                    mComposingEnd = mCursorIndex;
                }
                int start = Math.min(mComposingStart, mComposingEnd);
                int end = Math.max(mComposingStart, mComposingEnd);

                // Set expected composing range before editing so that
                // onCursorOrSelectionChanged() has correct boundaries.
                mComposingStart = start;
                mComposingEnd = start + text.length();

                mGapBuffer.setSuppressUndoCapture(true);
                try {
                    replaceInternal(start, end, text.toString());
                } finally {
                    mGapBuffer.setSuppressUndoCapture(false);
                }

                return true;
            }
            return super.setComposingText(text, newCursorPosition);
        }

        @Override
        public boolean setComposingRegion(int start, int end) {
            mComposingStart = start;
            mComposingEnd = end;
            EditView.this.onCursorOrSelectionChanged();
            return super.setComposingRegion(start, end);
        }

        @Override
        public boolean finishComposingText() {
            mComposingStart = mComposingEnd = -1;
            EditView.this.onCursorOrSelectionChanged();
            return super.finishComposingText();
        }

        @Override
        public boolean setSelection(int start, int end) {
            boolean result = super.setSelection(start, end);
            EditView.this.setSelection(start, end);
            return result;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (onKeyDown(event.getKeyCode(), event)) {
                    return true;
                }
            }
            return super.sendKeyEvent(event);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            if (beforeLength <= 0 && afterLength <= 0) return true;

            int spanStart = Selection.getSelectionStart(mGapBuffer);
            int spanEnd = Selection.getSelectionEnd(mGapBuffer);

            // If text is selected, any delete operation should first clear the selection
            if ((spanStart >= 0 && spanEnd >= 0 && spanStart != spanEnd) || isSelectMode) {
                delete();
                return true;
            }

            int len = mGapBuffer.length();
            int start = Math.max(0, mCursorIndex - beforeLength);
            int end = Math.min(len, mCursorIndex + afterLength);

            if (start != end) {
                replaceInternal(start, end, "");
            }
            return true;
        }

        @Override
        public boolean performContextMenuAction(int id) {
            if (id == android.R.id.undo) {
                undo();
                return true;
            }
            if (id == android.R.id.redo) {
                redo();
                return true;
            }
            if (id == android.R.id.copy) {
                copy();
                return true;
            }
            if (id == android.R.id.cut) {
                cut();
                return true;
            }
            if (id == android.R.id.paste) {
                paste();
                return true;
            }
            if (id == android.R.id.selectAll) {
                selectAll();
                return true;
            }
            return super.performContextMenuAction(id);
        }

        @Override
        public android.view.inputmethod.ExtractedText getExtractedText(android.view.inputmethod.ExtractedTextRequest request, int flags) {
            android.view.inputmethod.ExtractedText extracted = new android.view.inputmethod.ExtractedText();
            extracted.text = mGapBuffer;
            extracted.startOffset = 0;
            extracted.selectionStart = getSelectionStart();
            extracted.selectionEnd = getSelectionEnd();
            extracted.flags = 0;
            return extracted;
        }
    }

}


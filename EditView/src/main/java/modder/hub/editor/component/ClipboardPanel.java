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

package modder.hub.editor.component;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import modder.hub.editor.EditView;

/* Author @MrIkso */
/* Optmization done by Chat GPT */
/* Other ideas implemented by @developer-krushna */

public class ClipboardPanel {
    protected EditView _editView;
    private Context _context;

    private ActionMode _clipboardActionMode;
    private ActionMode.Callback2 _clipboardActionModeCallback2;
    private Rect _caretRect;
    private boolean _isSelectionMode;

    private Handler _autoHideHandler = new Handler();
    private static final long AUTO_HIDE_DELAY = 5000; // 5 seconds
    private Runnable _autoHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    public ClipboardPanel(EditView editView) {
        _editView = editView;
        _context = editView.getContext();
    }

    public Context getContext() {
        return _context;
    }

    public void show() {
        showAtLocation(null); // Use default positioning
    }

    public void showAtLocation(Rect preferredRect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            initData(preferredRect);
            startClipboardActionNew();
        } else {
            startClipboardAction();
        }

        // Start auto-hide timer
        startAutoHideTimer();
    }

    public void startClipboardAction() {
        if (_clipboardActionMode == null)
            _editView.startActionMode(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    _clipboardActionMode = mode;
                    mode.setTitle(android.R.string.selectTextMode);
                    setupMenuItems(menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    // Update menu items based on current state
                    updateMenuItems(menu);
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    handleActionItemClick(item);
                    return true;
                }

                @Override
                public void onDestroyActionMode(ActionMode p1) {
                    _editView.selectText(false);
                    _clipboardActionMode = null;
                    _caretRect = null;
                }
            });
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startClipboardActionNew() {
        if (_clipboardActionMode == null) {
            _editView.startActionMode(_clipboardActionModeCallback2, ActionMode.TYPE_FLOATING);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void initData(final Rect preferredRect) {
        _clipboardActionModeCallback2 = new ActionMode.Callback2() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                _clipboardActionMode = mode;
                setupMenuItems(menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                updateMenuItems(menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                handleActionItemClick(item);
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                _clipboardActionMode = null;
                _caretRect = null;
                cancelAutoHideTimer(); // Cancel timer when destroyed
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                // Always recalculate position based on current cursor/selection
                calculateOptimalPosition(outRect);
                ensureRectInBounds(outRect, view);

                // Restart auto-hide timer when position updates
                restartAutoHideTimer();
            }
        };
    }

    private void calculateOptimalPosition(Rect outRect) {
        // Get current caret position
        _caretRect = _editView.getBoundingBox(_editView.getCaretPosition());
        _isSelectionMode = _editView.isSelectMode();

        if (_caretRect == null) {
            // Fallback to a default position
            outRect.set(0, 0, 100, 100);
            return;
        }

        int caretX = _caretRect.left;
        int caretY = _caretRect.top;
        int lineHeight = _editView.getLineHeight();
        int screenWidth = _editView.getWidth();
        int screenHeight = _editView.getHeight();

        // Estimate action mode size
        int actionModeWidth = Math.min(screenWidth, 400); // Reasonable max width
        int actionModeHeight = lineHeight * 2; // Approximate height

        // Calculate optimal X position
        int optimalX = caretX;
        if (caretX + actionModeWidth > screenWidth) {
            // If it would go off-screen right, align to right edge
            optimalX = screenWidth - actionModeWidth - 20; // 20px margin
        }
        if (optimalX < 20) {
            optimalX = 20; // Minimum left margin
        }

        // Calculate optimal Y position - prefer above cursor
        int optimalY;
        if (caretY - actionModeHeight - lineHeight > 0) {
            // Position above cursor if there's space
            optimalY = caretY - actionModeHeight - lineHeight;
        } else {
            // Position below cursor if no space above
            optimalY = caretY + lineHeight * 2;

            // If it would go off-screen bottom, try to fit it
            if (optimalY + actionModeHeight > screenHeight) {
                optimalY = Math.max(20, screenHeight - actionModeHeight - 20);
            }
        }

        // For selection mode, position near the selection middle
        if (_isSelectionMode) {
            Rect selectionRect = getSelectionMiddleRect();
            if (selectionRect != null) {
                optimalX = selectionRect.left;
                optimalY = selectionRect.top - actionModeHeight - lineHeight;

                // Ensure it doesn't go off-screen
                if (optimalY < 0) {
                    optimalY = selectionRect.bottom + lineHeight;
                }
            }
        }

        outRect.set(optimalX, optimalY, optimalX + actionModeWidth, optimalY + actionModeHeight);
    }

    private Rect getSelectionMiddleRect() {
        if (!_isSelectionMode) return null;

        try {
            // Use your EditView's selection variables directly
            int selectionStart = _editView.getSelectionStart();
            int selectionEnd = _editView.getSelectionEnd();
            int middle = (selectionStart + selectionEnd) / 2;

            return _editView.getBoundingBox(middle);
        } catch (Exception e) {
            return _caretRect;
        }
    }

    private void ensureRectInBounds(Rect rect, View view) {
        int screenWidth = view.getWidth();
        int screenHeight = view.getHeight();

        // Ensure within horizontal bounds
        if (rect.left < 0) {
            rect.offsetTo(0, rect.top);
        }
        if (rect.right > screenWidth) {
            rect.offsetTo(screenWidth - rect.width(), rect.top);
        }

        // Ensure within vertical bounds
        if (rect.top < 0) {
            rect.offsetTo(rect.left, 0);
        }
        if (rect.bottom > screenHeight) {
            rect.offsetTo(rect.left, screenHeight - rect.height());
        }

        // Ensure minimum size
        if (rect.width() < 100) {
            rect.right = rect.left + 100;
        }
        if (rect.height() < 60) {
            rect.bottom = rect.top + 60;
        }
    }

    private void setupMenuItems(Menu menu) {
        TypedArray array = _context.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.actionModeSelectAllDrawable,
                android.R.attr.actionModeCutDrawable,
                android.R.attr.actionModeCopyDrawable,
                android.R.attr.actionModePasteDrawable,
        });

        menu.add(0, 0, 0, _context.getString(android.R.string.selectAll))
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                .setAlphabeticShortcut('a')
                .setIcon(array.getDrawable(0));

        menu.add(0, 1, 0, _context.getString(android.R.string.cut))
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                .setAlphabeticShortcut('x')
                .setIcon(array.getDrawable(1));

        menu.add(0, 2, 0, _context.getString(android.R.string.copy))
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                .setAlphabeticShortcut('c')
                .setIcon(array.getDrawable(2));

        menu.add(0, 3, 0, _context.getString(android.R.string.paste))
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                .setAlphabeticShortcut('v')
                .setIcon(array.getDrawable(3));

        // Add delete option for newer APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            menu.add(0, 4, 0, "Delete")
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    .setAlphabeticShortcut('d');
        }

        array.recycle();
    }

    private void updateMenuItems(Menu menu) {
        // Enable/disable items based on current state
        boolean hasSelection = _editView.isSelectMode() &&
                _editView.getSelectionStart() != _editView.getSelectionEnd();
        boolean canPaste = canPaste();

        // Update menu items visibility/enabled state
        MenuItem cutItem = menu.findItem(1);
        MenuItem copyItem = menu.findItem(2);
        MenuItem pasteItem = menu.findItem(3);
        MenuItem deleteItem = menu.findItem(4);

        if (cutItem != null) cutItem.setEnabled(hasSelection);
        if (copyItem != null) copyItem.setEnabled(hasSelection);
        if (pasteItem != null) pasteItem.setEnabled(canPaste);
        if (deleteItem != null) deleteItem.setEnabled(hasSelection);
    }

    private void handleActionItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case 0: // Select All
                _editView.selectAll();
                // Don't finish - keep the menu open after select all
                break;
            case 1: // Cut
                _editView.cut();
                // Only finish if you want to close after cut
                // finishActionMode();
                break;
            case 2: // Copy
                _editView.copy();
                // Only finish if you want to close after copy
                // finishActionMode();
                break;
            case 3: // Paste
                _editView.paste();
                // Only finish if you want to close after paste
                // finishActionMode();
                break;
            case 4: // Delete
                _editView.delete();
                // Only finish if you want to close after delete
                // finishActionMode();
                break;
        }

        // Optional: Update menu state after action
        if (_clipboardActionMode != null) {
            _clipboardActionMode.invalidate(); // Refresh the menu
        }
    }

    private boolean canPaste() {
        ClipboardManager clipboard = (ClipboardManager) _context.getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboard != null && clipboard.hasPrimaryClip();
    }


    private void startAutoHideTimer() {
        cancelAutoHideTimer();
        _autoHideHandler.postDelayed(_autoHideRunnable, AUTO_HIDE_DELAY);
    }

    private void restartAutoHideTimer() {
        cancelAutoHideTimer();
        _autoHideHandler.postDelayed(_autoHideRunnable, AUTO_HIDE_DELAY);
    }

    private void cancelAutoHideTimer() {
        _autoHideHandler.removeCallbacks(_autoHideRunnable);
    }

    public void hide() {
        cancelAutoHideTimer();
        stopClipboardAction();
    }

    // Call this when cursor/selection changes to update position
    public void updatePosition() {
        if (_clipboardActionMode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // This will trigger onGetContentRect and reposition the menu
            _clipboardActionMode.invalidate();
        }
    }

    public void stopClipboardAction() {
        if (_clipboardActionMode != null) {
            _clipboardActionMode.finish();
            _clipboardActionMode = null;
            _caretRect = null;
        }
    }
}

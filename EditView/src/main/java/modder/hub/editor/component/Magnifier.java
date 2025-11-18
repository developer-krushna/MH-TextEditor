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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;
import modder.hub.editor.EditView;
import modder.hub.editor.R;

/**
 * Magnifier specially designed for EditView Provides a popup magnifying glass effect for text
 * editing Originally replicated from Sora Code Editor for Android
 */
public class Magnifier {

    private final EditView editor;
    private final PopupWindow popup;
    private final ImageView image;
    private final Paint paint;
    private final float maxTextSize;

    private int viewX, viewY; // View-relative coordinates for positioning
    private int contentX, contentY; // Content-relative coordinates for capture
    private boolean enabled = true;
    private View parentView;
    private float scaleFactor;

    public Magnifier(EditView editor) {
        this.editor = editor;
        this.parentView = editor;

        // Initialize popup window
        popup = new PopupWindow();
        popup.setElevation(dpToPx(4));

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(editor.getContext()).inflate(R.layout.magnifier_popup, null);
        image = view.findViewById(R.id.magnifier_image_view);

        // Set popup dimensions
        popup.setHeight((int) dpToPx(60)); // Slightly reduced height for compactness
        popup.setWidth((int) dpToPx(80)); // Reduced initial width
        popup.setContentView(view);

        // Initialize text size limits and scaling
        maxTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28,
                editor.getResources().getDisplayMetrics());
        scaleFactor = 1.25f;
        paint = new Paint();
    }

    // ==================== PUBLIC METHODS ====================

    /** Show magnifier at specified view coordinates */
    public void show(int viewX, int viewY) {
        if (!enabled) {
            return;
        }
        if (Math.abs(viewX - this.viewX) < 2 && Math.abs(viewY - this.viewY) < 2) {
            return;
        }

        if (getEditorTextSize() > maxTextSize) {
            if (isShowing()) {
                dismiss();
            }
            return;
        }

        popup.setWidth(Math.min(editor.getWidth() * 2 / 5, (int) dpToPx(150))); // Reduced dynamic
                                                                                // width (2/5 and
                                                                                // 150dp max)
        this.viewX = viewX;
        this.viewY = viewY;

        int scrollX = editor.getScrollX();
        int scrollY = editor.getScrollY();
        this.contentX = viewX + scrollX;
        this.contentY = viewY + scrollY;

        // Get screen coordinates
        int[] pos = new int[2];
        editor.getLocationOnScreen(pos);

        // Calculate popup position in screen coordinates using view-relative
        int screenX = pos[0] + viewX;
        int screenY = pos[1] + viewY;

        // Position magnifier above the cursor with proper offset
        int popupLeft = screenX - popup.getWidth() / 2;
        int popupTop = screenY - popup.getHeight() - getEditorLineHeight();

        // Ensure popup stays within screen bounds
        DisplayMetrics metrics = editor.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        popupLeft = Math.max(0, Math.min(popupLeft, screenWidth - popup.getWidth()));
        popupTop = Math.max(0, Math.min(popupTop, screenHeight - popup.getHeight()));

        // If there's not enough space above, put it below
        if (popupTop < 0) {
            popupTop = screenY + getEditorLineHeight();
            popupTop = Math.min(popupTop, screenHeight - popup.getHeight());
        }

        if (popup.isShowing()) {
            popup.update(popupLeft, popupTop, popup.getWidth(), popup.getHeight());
        } else {
            popup.showAtLocation(parentView, Gravity.NO_GRAVITY, popupLeft, popupTop);
        }
        updateDisplay();
    }

    /** Check if magnifier is currently showing */
    public boolean isShowing() {
        return popup.isShowing();
    }

    /** Dismiss the magnifier */
    public void dismiss() {
        popup.dismiss();
    }

    /** Update the magnifier display */
    public void updateDisplay() {
        if (!isShowing()) {
            return;
        }
        updateDisplayWithinEditor();
    }

    /** Enable or disable the magnifier */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            dismiss();
        }
    }

    /** Check if magnifier is enabled */
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== PRIVATE METHODS ====================

    /** Convert dp to pixels */
    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                editor.getResources().getDisplayMetrics());
    }

    /** Get current editor text size */
    private float getEditorTextSize() {
        return editor.getTextSize();
    }

    /** Get current editor line height */
    private int getEditorLineHeight() {
        return editor.getLineHeight();
    }

    /** Update the magnifier content by capturing and scaling editor content */
    private void updateDisplayWithinEditor() {
        if (popup.getWidth() <= 0 || popup.getHeight() <= 0) {
            dismiss();
            return;
        }

        int totalContentHeight = editor.getLineCount() * getEditorLineHeight();
        int totalContentWidth = editor.getLeftSpace() + editor.getLineWidth() + editor.getSpaceWidth() * 4; // Full content width based on max line
        int scrollX = editor.getScrollX();
        int scrollY = editor.getScrollY();

        // Recompute content in case of scroll during magnifier (rare)
        int contentX = viewX + scrollX;
        int contentY = viewY + scrollY;

        Bitmap dest = Bitmap.createBitmap(popup.getWidth(), popup.getHeight(), Bitmap.Config.ARGB_8888);
        int requiredWidth = (int) (popup.getWidth() / scaleFactor);
        int requiredHeight = (int) (popup.getHeight() / scaleFactor);

        // Calculate capture area in content coordinates
        int left = Math.max(contentX - requiredWidth / 2, 0);
        int top = Math.max(contentY - requiredHeight / 2, 0);
        int right = Math.min(left + requiredWidth, totalContentWidth);
        int bottom = Math.min(top + requiredHeight, totalContentHeight);

        // Adjust for edges
        if (right - left < requiredWidth) {
            left = Math.max(0, right - requiredWidth);
        }
        if (bottom - top < requiredHeight) {
            top = Math.max(0, bottom - requiredHeight);
        }

        // Special handling for bottom of document
        if (contentY > totalContentHeight - getEditorLineHeight() * 3) {
            // Near bottom of content - show area above cursor
            top = Math.max(0, contentY - requiredHeight);
            bottom = Math.min(top + requiredHeight, totalContentHeight);
        }

        if (right - left <= 0 || bottom - top <= 0) {
            dismiss();
            dest.recycle();
            return;
        }

        int actualWidth = right - left;
        int actualHeight = bottom - top;

        Bitmap clip = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
        Canvas viewCanvas = new Canvas(clip);
        // Translate to align content area to clip
        viewCanvas.translate(-left, -top);
        // Clip to the content area in post-translate coordinates
        viewCanvas.clipRect(left, top, right, bottom);

        // Draw editor content without scroll translation
        editor.drawMatchText(viewCanvas);
        editor.drawLineBackground(viewCanvas);
        editor.drawEditableText(viewCanvas);
        editor.drawSelectHandle(viewCanvas);
        editor.drawCursor(viewCanvas);

        Bitmap scaled = Bitmap.createScaledBitmap(clip, popup.getWidth(), popup.getHeight(), true);
        clip.recycle();

        Canvas canvas = new Canvas(dest);
        paint.reset();
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        final int roundFactor = 6;
        canvas.drawRoundRect(0, 0, popup.getWidth(), popup.getHeight(), dpToPx(roundFactor), dpToPx(roundFactor), paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaled, 0, 0, paint);
        scaled.recycle();

        image.setImageBitmap(dest);
    }

    /** Calculate the capture area bounds with edge protection */
    private Rect calculateCaptureBounds(int contentX, int contentY, int requiredWidth,
            int requiredHeight, int totalContentWidth, int totalContentHeight) {
        int left = Math.max(contentX - requiredWidth / 2, 0);
        int top = Math.max(contentY - requiredHeight / 2, 0);
        int right = Math.min(left + requiredWidth, totalContentWidth);
        int bottom = Math.min(top + requiredHeight, totalContentHeight);

        // Adjust for edges
        if (right - left < requiredWidth) {
            left = Math.max(0, right - requiredWidth);
        }
        if (bottom - top < requiredHeight) {
            top = Math.max(0, bottom - requiredHeight);
        }

        // Special handling for bottom of document
        if (contentY > totalContentHeight - getEditorLineHeight() * 3) {
            // Near bottom of content - show area above cursor
            top = Math.max(0, contentY - requiredHeight);
            bottom = Math.min(top + requiredHeight, totalContentHeight);
        }

        return new Rect(left, top, right - left, bottom - top);
    }

    /** Capture, scale and display the magnified content */
    private void displayMagnifiedContent(Bitmap dest, Rect captureBounds, int scrollX, int scrollY) {
        // Create clip bitmap from capture area
        Bitmap clip = Bitmap.createBitmap(captureBounds.width, captureBounds.height, Bitmap.Config.ARGB_8888);
        Canvas viewCanvas = new Canvas(clip);

        // Translate to align content area to clip
        viewCanvas.translate(-(captureBounds.left + scrollX), -(captureBounds.top + scrollY));

        // Draw editor content to clip
        editor.drawEditorContent(viewCanvas, captureBounds.top, captureBounds.bottom);

        // Scale clip to popup size
        Bitmap scaled = Bitmap.createScaledBitmap(clip, popup.getWidth(), popup.getHeight(), true);
        clip.recycle();

        // Apply rounded corners to destination
        Canvas canvas = new Canvas(dest);
        paint.reset();
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);

        final int roundFactor = 6;
        canvas.drawRoundRect(0, 0, popup.getWidth(), popup.getHeight(),
                dpToPx(roundFactor), dpToPx(roundFactor), paint);

        // Apply source-in mode for rounded corners
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaled, 0, 0, paint);
        scaled.recycle();

        // Set the final bitmap to image view
        image.setImageBitmap(dest);
    }

    /** Simple rectangle helper class */
    private static class Rect {
        final int left, top, width, height;
        final int right, bottom;

        Rect(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.right = left + width;
            this.bottom = top + height;
        }
    }
}

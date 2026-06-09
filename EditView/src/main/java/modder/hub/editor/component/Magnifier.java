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
import modder.hub.editor.lib.R;

/**
 * Author - @developer-krushna
 * optimized by AI
 * Magnifier specially designed for EditView Provides a popup magnifying glass effect for text
 * editing Optimized for smooth capture and positioning like MT Manager.
 * Got idea from sora editor
 */
public class Magnifier {

    private final EditView editor;
    private final PopupWindow popup;
    private final ImageView image;
    private final Paint paint;
    private final float maxTextSize;

    private float viewX, viewY; // View-relative coordinates for positioning
    private boolean enabled = true;
    private final View parentView;
    private final float scaleFactor;

    private Bitmap destBitmap;
    private Canvas destCanvas;
    private Bitmap captureBitmap;
    private Canvas captureCanvas;
    private final PorterDuffXfermode srcInMode = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);

    public Magnifier(EditView editor) {
        this.editor = editor;
        this.parentView = editor;

        // Initialize popup window
        popup = new PopupWindow();
        popup.setElevation(dpToPx(12)); // Higher elevation for cleaner look

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(editor.getContext()).inflate(R.layout.magnifier_popup, null);
        image = view.findViewById(R.id.magnifier_image_view);

        // Wider and clearer display
        popup.setHeight((int) dpToPx(60));
        popup.setWidth((int) dpToPx(170));
        popup.setContentView(view);

        // Initialize text size limits and scaling
        // Cap magnifier at 28dp text size to avoid "over zoom" when text is already large
        maxTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28,
                editor.getResources().getDisplayMetrics());
        scaleFactor = 1.5f; // More conservative scale
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    // ==================== PUBLIC METHODS ====================

    /** Show magnifier at specified view coordinates */
    public void show(float viewX, float viewY) {
        if (!enabled) {
            return;
        }

        // Extremely low threshold for ultra-smooth updates
        if (Math.abs(viewX - this.viewX) < 3f && Math.abs(viewY - this.viewY) < 1f && isShowing()) {
            // Position close enough — skip popup.update() but still refresh bitmap content
            updateDisplayWithinEditor();
            return;
        }
        if (getEditorTextSize() > maxTextSize) {
            if (isShowing()) {
                editor.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isShowing() || destBitmap == null || destBitmap.isRecycled()) return;
                        renderCapture();
                    }
                });
                dismiss();
            }
            return;
        }

        this.viewX = viewX;
        this.viewY = viewY;

        // Get screen coordinates
        int[] pos = new int[2];
        editor.getLocationOnScreen(pos);

        // Calculate popup position in screen coordinates
        float screenX = pos[0] + viewX;
        float screenY = pos[1] + viewY;

        int popupLeft = (int) (screenX - popup.getWidth() / 2f);
        int verticalOffset = (int) dpToPx(65); // More space above cursor to avoid covering text
        int popupTop = (int) (screenY - popup.getHeight() - verticalOffset);

        // Ensure popup stays within screen bounds
        DisplayMetrics metrics = editor.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;

        // Safety: If touching top of screen, show magnifier below
        if (popupTop < dpToPx(5)) {
            popupTop = (int) (screenY + getEditorLineHeight() + dpToPx(35));
        }

        // Clamp horizontally
        popupLeft = Math.max(0, Math.min(popupLeft, screenWidth - popup.getWidth()));

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
        image.setImageBitmap(null); // clear before recycle
        if (destBitmap != null) {
            destBitmap.recycle();
            destBitmap = null;
            destCanvas = null;
        }
        if (captureBitmap != null) {
            captureBitmap.recycle();
            captureBitmap = null;
            captureCanvas = null;
        }
    }

    /** Update the magnifier display */
    public void updateDisplay() {
        if (!isShowing()) return;
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
        // Post the capture to next frame so editor has finished its own redraw first
        editor.post(new Runnable() {
            @Override
            public void run() {
                if (!isShowing()) return;
                renderCapture();
            }
        });
    }

    private void renderCapture() {
        int width = popup.getWidth();
        int height = popup.getHeight();
        if (width <= 0 || height <= 0) {
            dismiss();
            return;
        }

        if (destBitmap == null || destBitmap.getWidth() != width || destBitmap.getHeight() != height) {
            if (destBitmap != null) destBitmap.recycle();
            destBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            destCanvas = new Canvas(destBitmap);
        }

        // Capture at full resolution to avoid "picture" blurriness
        if (captureBitmap == null || captureBitmap.getWidth() != width || captureBitmap.getHeight() != height) {
            if (captureBitmap != null) captureBitmap.recycle();
            captureBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            captureCanvas = new Canvas(captureBitmap);
        }

        int scrollX = editor.getScrollX();
        int scrollY = editor.getScrollY();
        float contentX = viewX + scrollX;
        float contentY = viewY + scrollY;

        captureBitmap.eraseColor(0);
        captureCanvas.save();
        
        // High-quality rendering: Scale the canvas, not the result bitmap
        captureCanvas.translate(width / 2f, height / 2f);
        captureCanvas.scale(scaleFactor, scaleFactor);
        captureCanvas.translate(-contentX, -contentY);
        captureCanvas.translate(editor.getPaddingLeft(), editor.getPaddingTop());
        
        editor.drawMatchText(captureCanvas);
        editor.drawLineBackground(captureCanvas);
        editor.drawEditableText(captureCanvas);
        editor.drawSelectHandle(captureCanvas);
        editor.drawCursor(captureCanvas);
        captureCanvas.restore();

        destBitmap.eraseColor(0);
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);

        float cornerRadius = dpToPx(15);
        destCanvas.drawRoundRect(0, 0, width, height, cornerRadius, cornerRadius, paint);

        paint.setXfermode(srcInMode);
        // Draw capture directly (1:1) into dest for crispness
        destCanvas.drawBitmap(captureBitmap, 0, 0, paint);
        paint.setXfermode(null);

        // at the very end, before image.setImageBitmap
        if (destBitmap == null || destBitmap.isRecycled()) return;
        image.setImageBitmap(destBitmap);
    }
}

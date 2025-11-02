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
import modder.hub.editor.R;
import modder.hub.editor.EditView;

/** Magnifier specially designed for EditView */
// Originally repicated from Sora Code Editor for Android
// Not ready yet
public class Magnifier {

    private final EditView editor;
    private final PopupWindow popup;
    private final ImageView image;
    private final Paint paint;
    private final float maxTextSize;
    private int x, y;
    private boolean enabled = true;
    private View parentView;
    private float scaleFactor;

    public Magnifier(EditView editor) {
        this.editor = editor;
        this.parentView = editor;
        popup = new PopupWindow();
        popup.setElevation(dpToPx(4));

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(editor.getContext()).inflate(R.layout.magnifier_popup, null);
        image = view.findViewById(R.id.magnifier_image_view);
        popup.setHeight((int) (dpToPx(70)));
        popup.setWidth((int) (dpToPx(100)));
        popup.setContentView(view);

        maxTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28,
                editor.getResources().getDisplayMetrics());
        scaleFactor = 1.25f;
        paint = new Paint();
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                editor.getResources().getDisplayMetrics());
    }

    private float getEditorTextSize() {
        return editor.getTextSize();
    }

    private int getEditorLineHeight() {
        return editor.getLineHeight();
    }

    public void show(int x, int y) {
        if (!enabled) {
            return;
        }
        if (Math.abs(x - this.x) < 2 && Math.abs(y - this.y) < 2) {
            return;
        }

        if (getEditorTextSize() > maxTextSize) {
            if (isShowing()) {
                dismiss();
            }
            return;
        }

        popup.setWidth(Math.min(editor.getWidth() * 3 / 5, (int) dpToPx(250)));
        this.x = x;
        this.y = y;

        // Get screen coordinates like sora-editor does
        int[] pos = new int[2];
        editor.getLocationOnScreen(pos);

        // Calculate popup position in screen coordinates
        int screenX = pos[0] + x;
        int screenY = pos[1] + y;

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

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void dismiss() {
        popup.dismiss();
    }

    public void updateDisplay() {
        if (!isShowing()) {
            return;
        }
        updateDisplayWithinEditor();
    }

    private void updateDisplayWithinEditor() {
        if (popup.getWidth() <= 0 || popup.getHeight() <= 0) {
            dismiss();
            return;
        }

        Bitmap dest = Bitmap.createBitmap(popup.getWidth(), popup.getHeight(), Bitmap.Config.ARGB_8888);
        int requiredWidth = (int) (popup.getWidth() / scaleFactor);
        int requiredHeight = (int) (popup.getHeight() / scaleFactor);

        // Calculate capture area considering scroll position
        int left = Math.max(x - requiredWidth / 2, 0);
        int top = Math.max(y - requiredHeight / 2, 0);
        int right = Math.min(left + requiredWidth, editor.getWidth());
        int bottom = Math.min(top + requiredHeight, editor.getHeight());

        // Adjust for edges - this is the key fix
        if (right - left < requiredWidth) {
            left = Math.max(0, right - requiredWidth);
        }
        if (bottom - top < requiredHeight) {
            top = Math.max(0, bottom - requiredHeight);
        }

        // Special handling for bottom of document
        int totalContentHeight = editor.getLineCount() * getEditorLineHeight();
        int visibleBottom = editor.getScrollY() + editor.getHeight();

        if (y > totalContentHeight - getEditorLineHeight() * 3) {
            // Near bottom of content - show area above cursor
            top = Math.max(0, y - requiredHeight);
            bottom = Math.min(top + requiredHeight, totalContentHeight);
        }

        if (right - left <= 0 || bottom - top <= 0) {
            dismiss();
            dest.recycle();
            return;
        }

        Bitmap clip = Bitmap.createBitmap(requiredWidth, requiredHeight, Bitmap.Config.ARGB_8888);
        Canvas viewCanvas = new Canvas(clip);
        viewCanvas.translate(-left, -top);

        // Draw editor content
        editor.drawEditorContent(viewCanvas);

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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            dismiss();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}

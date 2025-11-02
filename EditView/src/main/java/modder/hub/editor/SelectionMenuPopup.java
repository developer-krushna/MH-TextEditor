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

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * A custom selection menu aim to be implement in the EditView but due to lack of some which i dont
 * know i left it for other people to check it out
 */
public class SelectionMenuPopup {
    public interface Callback {
        void onCopy();

        void onCut();

        void onPaste();

        void onSelectAll();

        void onShare();
    }

    private final PopupWindow popup;
    private final LinearLayout firstLevelContainer;
    private final LinearLayout secondLevelContainer;
    private final Context ctx;
    private final Callback callback;
    private boolean isSecondLevelVisible = false;
    private final String[] firstLevelActions = {"Copy", "Cut", "Paste", "Select All"};
    private final String[] secondLevelActions = {"Share"};
    private final Runnable dismissRunnable;
    private static final long DISMISS_TIMEOUT = 3000; // 3 seconds

    public SelectionMenuPopup(Context c, Callback cb) {
        ctx = c;
        callback = cb;

        LinearLayout rootContainer = new LinearLayout(c);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(8);
        rootContainer.setPadding(pad, pad / 2, pad, pad / 2);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(1, 0x22000000);
        rootContainer.setBackground(bg);

        firstLevelContainer = new LinearLayout(c);
        firstLevelContainer.setOrientation(LinearLayout.HORIZONTAL);
        firstLevelContainer.setPadding(0, 0, 0, 0);
        rootContainer.addView(firstLevelContainer);

        secondLevelContainer = new LinearLayout(c);
        secondLevelContainer.setOrientation(LinearLayout.VERTICAL);
        secondLevelContainer.setVisibility(View.GONE);
        secondLevelContainer.setBackground(bg);
        secondLevelContainer.setPadding(pad, pad / 2, pad, pad / 2);
        rootContainer.addView(secondLevelContainer);

        popup = new PopupWindow(rootContainer,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        true);
        popup.setBackgroundDrawable(null);
        popup.setOutsideTouchable(true);
        popup.setElevation(dpToPx(8));
        popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                isSecondLevelVisible = false;
                secondLevelContainer.setVisibility(View.GONE);
                firstLevelContainer.removeCallbacks(dismissRunnable);
            }
        });

        dismissRunnable = new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        };
    }

    public int[] measure() {
        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        firstLevelContainer.measure(spec, spec);
        int firstLevelWidth = firstLevelContainer.getMeasuredWidth();
        int firstLevelHeight = firstLevelContainer.getMeasuredHeight();

        int secondLevelHeight = 0;
        if (isSecondLevelVisible) {
            secondLevelContainer.measure(spec, spec);
            secondLevelHeight = secondLevelContainer.getMeasuredHeight();
        }

        return new int[]{firstLevelWidth, firstLevelHeight + secondLevelHeight};
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void showAt(int x, int y, View anchor, int lineHeight) {
        firstLevelContainer.removeCallbacks(dismissRunnable);

        firstLevelContainer.removeAllViews();
        secondLevelContainer.removeAllViews();

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int maxFirstLevelWidth = (int) (screenWidth * 0.8f);
        int buttonPadding = dpToPx(10);
        int maxButtons = calculateMaxButtons(maxFirstLevelWidth, buttonPadding);

        for (int i = 0; i < firstLevelActions.length && i < maxButtons; i++) {
            addEntry(firstLevelContainer, firstLevelActions[i]);
        }

        if (firstLevelActions.length > maxButtons || secondLevelActions.length > 0) {
            addEntry(firstLevelContainer, "More", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSecondLevel();
                }
            });
        }

        for (int i = maxButtons; i < firstLevelActions.length; i++) {
            addEntry(secondLevelContainer, firstLevelActions[i]);
        }
        for (String action : secondLevelActions) {
            addEntry(secondLevelContainer, action);
        }

        int[] size = measure();
        int pw = size[0];
        int ph = size[1];

        int screenX = x - pw / 2;
        int screenY = y - ph - dpToPx(8);

        int screenH = dm.heightPixels;
        screenX = Math.max(8, Math.min(screenX, screenWidth - pw - 8));
        screenY = Math.max(8, Math.min(screenY, screenH - ph - 8));

        if (popup.isShowing()) {
            popup.update(screenX, screenY, pw, ph);
        } else {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, screenX, screenY);
            AlphaAnimation a = new AlphaAnimation(0f, 1f);
            a.setDuration(160);
            a.setFillAfter(true);
            firstLevelContainer.startAnimation(a);
            if (isSecondLevelVisible) {
                secondLevelContainer.startAnimation(a);
            }
        }

        firstLevelContainer.postDelayed(dismissRunnable, DISMISS_TIMEOUT);

        Log.d("SelectionMenuPopup", "showAt: screenX=" + screenX + ", screenY=" + screenY + ", pw=" + pw + ", ph=" + ph);
    }

    private int calculateMaxButtons(int maxWidth, int buttonPadding) {
        int totalWidth = 0;
        int maxButtons = 0;
        for (String action : firstLevelActions) {
            int textWidth = (int) (action.length() * 14 * ctx.getResources().getDisplayMetrics().density);
            totalWidth += textWidth + buttonPadding * 2;
            if (totalWidth < maxWidth) {
                maxButtons++;
            } else {
                break;
            }
        }
        return Math.max(1, maxButtons);
    }

    private void addEntry(LinearLayout container, final String text) {
        addEntry(container, text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleAction(text);
                firstLevelContainer.removeCallbacks(dismissRunnable);
                firstLevelContainer.postDelayed(dismissRunnable, DISMISS_TIMEOUT);
            }
        });
    }

    private void addEntry(LinearLayout container, String text, View.OnClickListener listener) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFF000000);
        tv.setTextSize(14);
        int pad = dpToPx(10);
        tv.setPadding(pad, dpToPx(8), pad, dpToPx(8));
        tv.setBackgroundResource(R.drawable.ripple_effect);
        tv.setOnClickListener(listener);
        tv.setContentDescription(text + " action");
        container.addView(tv);
    }

    private void toggleSecondLevel() {
        isSecondLevelVisible = !isSecondLevelVisible;
        secondLevelContainer.setVisibility(isSecondLevelVisible ? View.VISIBLE : View.GONE);
        popup.update();
        if (isSecondLevelVisible) {
            AlphaAnimation a = new AlphaAnimation(0f, 1f);
            a.setDuration(160);
            a.setFillAfter(true);
            secondLevelContainer.startAnimation(a);
        }
        firstLevelContainer.removeCallbacks(dismissRunnable);
        firstLevelContainer.postDelayed(dismissRunnable, DISMISS_TIMEOUT);
    }

    private void handleAction(String action) {
        if ("Copy".equals(action)) {
            callback.onCopy();
        } else if ("Cut".equals(action)) {
            callback.onCut();
        } else if ("Paste".equals(action)) {
            callback.onPaste();
        } else if ("Select All".equals(action)) {
            callback.onSelectAll();
        } else if ("Share".equals(action)) {
            callback.onShare();
        }
    }

    public void dismiss() {
        if (popup.isShowing()) {
            AlphaAnimation a = new AlphaAnimation(1f, 0f);
            a.setDuration(160);
            a.setFillAfter(true);
            firstLevelContainer.startAnimation(a);
            if (isSecondLevelVisible) {
                secondLevelContainer.startAnimation(a);
            }
            firstLevelContainer.postDelayed(new Runnable() {
                @Override
                public void run() {
                    popup.dismiss();
                }
            }, 160);
        }
        firstLevelContainer.removeCallbacks(dismissRunnable);
    }

    private int dpToPx(int dp) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return (int) (dp * dm.density + 0.5f);
    }
}

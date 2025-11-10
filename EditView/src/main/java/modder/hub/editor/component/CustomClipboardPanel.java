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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import modder.hub.editor.EditView;
import modder.hub.editor.R;

/* Author @developer-krushna */
/* Ideas implemented by ChatGPT */

public class CustomClipboardPanel {
    protected EditView _editView;
    private Context _context;

    // Custom popup menu variables
    private PopupWindow _customPopupWindow;
    private View _popupView;
    private LinearLayout _primaryMenu;
    private LinearLayout _secondaryMenu;
    private ImageView _expandButtonIcon;
    private View _divider;
    private boolean _isExpanded = false;
    private boolean _isAnimating = false;

    private Rect _caretRect;
    private boolean _isSelectionMode;

    // Menu display mode
    private MenuDisplayMode _menuDisplayMode = MenuDisplayMode.ICON_ONLY;

    // Radius for rounded corners
    private float _menuCornerRadius;

    // Menu item dimensions
    private int _menuItemHeight;
    private int _menuItemMinWidth;
    private int _menuIconSize;

    // Overflow management
    private boolean _hasOverflow = false;
    private java.util.ArrayList<MenuItemData> _overflowItems = new java.util.ArrayList<>();

    // Animation variables
    private int _animationDuration = 200;
    private int _originalPrimaryWidth = 0;
    private int _expandedWidth = 0;

    private Handler _autoHideHandler = new Handler();
    private static final long AUTO_HIDE_DELAY = 5000;
    private Runnable _autoHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    // Menu display mode enum
    public enum MenuDisplayMode {
        TEXT_ONLY,
        ICON_ONLY,
        ICON_AND_TEXT
		}

    public CustomClipboardPanel(EditView textField) {
        _editView = textField;
        _context = textField.getContext();

        // Initialize dimensions
        _menuCornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, _context.getResources().getDisplayMetrics());
        _menuItemHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, _context.getResources().getDisplayMetrics());
        _menuItemMinWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, _context.getResources().getDisplayMetrics());
        _menuIconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, _context.getResources().getDisplayMetrics());

        initCustomPopup();
    }

    private void initCustomPopup() {
        LayoutInflater inflater = LayoutInflater.from(_context);
        _popupView = inflater.inflate(R.layout.custom_selection_menu, null);

        _primaryMenu = _popupView.findViewById(R.id.primaryMenu);
        _secondaryMenu = _popupView.findViewById(R.id.secondaryMenu);
        _divider = _popupView.findViewById(R.id.divider);

        _customPopupWindow = new PopupWindow(
            _popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        _customPopupWindow.setOutsideTouchable(true);
        _customPopupWindow.setFocusable(false);
        _customPopupWindow.setElevation(16f);
        _customPopupWindow.setBackgroundDrawable(_context.getDrawable(R.drawable.popup_background));

        // Fix keyboard issues
        _customPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    }

    private void setupMenuItems() {
        Log.d("CustomClipboardPanel", "setupMenuItems called with mode: " + _menuDisplayMode);

        // Clear existing views
        _primaryMenu.removeAllViews();
        _secondaryMenu.removeAllViews();
        _overflowItems.clear();
        _hasOverflow = false;

        // All menu items
        java.util.ArrayList<MenuItemData> allItems = new java.util.ArrayList<>();
        allItems.add(createMenuItem("Copy", R.drawable.ic_copy, MenuAction.COPY));
        allItems.add(createMenuItem("Cut", R.drawable.ic_cut, MenuAction.CUT));
        allItems.add(createMenuItem("Paste", R.drawable.ic_paste, MenuAction.PASTE));
        allItems.add(createMenuItem("Select All", R.drawable.ic_select_all, MenuAction.SELECT_ALL));
        allItems.add(createMenuItem("Share", R.drawable.ic_share, MenuAction.SHARE));
        allItems.add(createMenuItem("Go to", R.drawable.ic_goto, MenuAction.GOTO));
        allItems.add(createMenuItem("Delete", R.drawable.ic_delete, MenuAction.DELETE));

        // Get screen density and calculate available space more accurately
        float density = _context.getResources().getDisplayMetrics().density;
        int screenWidth = _editView.getWidth();

        Log.d("CustomClipboardPanel", "Screen width: " + screenWidth + "px, Density: " + density);

        java.util.ArrayList<MenuItemData> primaryItems = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItemData> secondaryItems = new java.util.ArrayList<>();

        // Calculate how many items we can fit based on display mode and screen width
        int maxPrimaryItems = calculateMaxPrimaryItems(screenWidth, density);

        Log.d("CustomClipboardPanel", "Max primary items possible: " + maxPrimaryItems);

        // Simple logic: always show first N items in primary, rest in secondary
        for (int i = 0; i < allItems.size(); i++) {
            if (i < maxPrimaryItems) {
                primaryItems.add(allItems.get(i));
            } else {
                secondaryItems.add(allItems.get(i));
                _hasOverflow = true;
            }
        }

        // Add primary menu items
        for (int i = 0; i < primaryItems.size(); i++) {
            boolean isLastPrimary = (i == primaryItems.size() - 1) && !_hasOverflow;
            addMenuItemToLayout(primaryItems.get(i), _primaryMenu, false, i, isLastPrimary);
        }

        // Add expand button if we have overflow items
        if (_hasOverflow && !secondaryItems.isEmpty()) {
            addExpandButtonToPrimaryMenu();
            _overflowItems.addAll(secondaryItems);
            Log.d("CustomClipboardPanel", "Added expand button with " + secondaryItems.size() + " overflow items");
        } else {
            // No overflow, add remaining items to secondary menu directly
            for (int i = 0; i < secondaryItems.size(); i++) {
                addMenuItemToLayout(secondaryItems.get(i), _secondaryMenu, true, i, i == secondaryItems.size() - 1);
            }
            Log.d("CustomClipboardPanel", "No overflow, added " + secondaryItems.size() + " items directly to secondary");
        }

        Log.d("CustomClipboardPanel", "Final distribution: " + primaryItems.size() + " primary, " + 
              secondaryItems.size() + " secondary, overflow: " + _hasOverflow);
    }

    private int calculateMaxPrimaryItems(int screenWidth, float density) {
        // Convert screen width to dp for consistent calculations
        int screenWidthDp = (int) (screenWidth / density);

        Log.d("CustomClipboardPanel", "Screen width in dp: " + screenWidthDp);

        // Calculate item width based on display mode
        int itemWidthDp;
        switch (_menuDisplayMode) {
            case TEXT_ONLY:
                itemWidthDp = 80; // Approximate width for text items
                break;
            case ICON_ONLY:
                itemWidthDp = 56; // Compact width for icon-only
                break;
            case ICON_AND_TEXT:
                itemWidthDp = 100; // Wider for icon + text
                break;
            default:
                itemWidthDp = 80;
        }

        // Expand button width
        int expandButtonWidthDp = 48;

        // Available width (use 85% of screen width)
        int availableWidthDp = (int) (screenWidthDp * 0.85f);

        // Calculate how many items we can fit
        int maxItemsWithoutOverflow = availableWidthDp / itemWidthDp;

        // With overflow button, we need space for it
        int maxItemsWithOverflow = (availableWidthDp - expandButtonWidthDp) / itemWidthDp;

        // We always want at least 2 items in primary
        int minItems = 2;
        int maxItems = Math.max(minItems, maxItemsWithOverflow);

        // For icon-only mode, we can be more aggressive
        if (_menuDisplayMode == MenuDisplayMode.ICON_ONLY) {
            maxItems = Math.min(5, maxItemsWithOverflow + 1); // Allow one more since icons are compact
        }

        // For text-only mode, follow Android system behavior (usually 4 items)
        if (_menuDisplayMode == MenuDisplayMode.TEXT_ONLY) {
            maxItems = Math.min(4, maxItemsWithOverflow);
        }

        Log.d("CustomClipboardPanel", "Item width: " + itemWidthDp + "dp, Available: " + availableWidthDp + 
              "dp, Max items: " + maxItems);

        return maxItems;
    }

    private void addExpandButtonToPrimaryMenu() {
        LayoutInflater inflater = LayoutInflater.from(_primaryMenu.getContext());
        View expandButtonView = inflater.inflate(R.layout.expand_button, _primaryMenu, false);

        _expandButtonIcon = expandButtonView.findViewById(R.id.expandIcon);
        _expandButtonIcon.setImageResource(R.drawable.ic_more);

        // Set fixed height for expand button
        ViewGroup.LayoutParams params = expandButtonView.getLayoutParams();
        if (params != null) {
            params.height = _menuItemHeight;
        }

        expandButtonView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					if (!_isAnimating) {
						toggleExpand();
					}
				}
			});

        applyPerfectRoundedBackground(expandButtonView, false, true, false, true);
        _primaryMenu.addView(expandButtonView);
    }

    private MenuItemData createMenuItem(String title, int iconRes, MenuAction action) {
        return new MenuItemData(title, iconRes, action);
    }

    private void addMenuItemToLayout(final MenuItemData menuItem, LinearLayout layout, final boolean isVertical, final int position, final boolean isEdgeItem) {
        LayoutInflater inflater = LayoutInflater.from(layout.getContext());
        View menuItemView = inflater.inflate(R.layout.menu_item, layout, false);

        // Set fixed height for menu items
        ViewGroup.LayoutParams params = menuItemView.getLayoutParams();
        if (params != null) {
            params.height = _menuItemHeight;
        }

        ImageView icon = menuItemView.findViewById(R.id.menuIcon);
        TextView titleView = menuItemView.findViewById(R.id.menuTitle);

        // Set icon size
        ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
        if (iconParams != null) {
            iconParams.width = _menuIconSize;
            iconParams.height = _menuIconSize;
            icon.setLayoutParams(iconParams);
        }

        // Apply display mode with special handling for secondary menu
        applyDisplayMode(menuItemView, menuItem, isVertical);

        if (isVertical) {
            // Vertical layout - full width
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) menuItemView.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = _menuItemHeight;
            menuItemView.setLayoutParams(layoutParams);

            // Center content vertically for vertical layout
            if (menuItemView instanceof LinearLayout) {
                ((LinearLayout) menuItemView).setGravity(android.view.Gravity.CENTER_VERTICAL);
            }
        } else {
            // Horizontal layout - fixed width based on content
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) menuItemView.getLayoutParams();

            // Calculate width based on content
            int itemWidth;
            switch (_menuDisplayMode) {
                case TEXT_ONLY:
                    // Measure text width and add padding
                    titleView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    itemWidth = titleView.getMeasuredWidth() + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, _context.getResources().getDisplayMetrics());
                    break;
                case ICON_ONLY:
                    itemWidth = _menuItemHeight; // Square for icon-only
                    break;
                case ICON_AND_TEXT:
                    // Measure text width and add icon space
                    titleView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    itemWidth = titleView.getMeasuredWidth() + _menuIconSize + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, _context.getResources().getDisplayMetrics());
                    break;
                default:
                    itemWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, _context.getResources().getDisplayMetrics());
            }

            // Ensure minimum width
            int minWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, _context.getResources().getDisplayMetrics());
            layoutParams.width = Math.max(itemWidth, minWidth);
            layoutParams.height = _menuItemHeight;
            menuItemView.setLayoutParams(layoutParams);

            // Center content both vertically and horizontally for horizontal layout
            if (menuItemView instanceof LinearLayout) {
                ((LinearLayout) menuItemView).setGravity(android.view.Gravity.CENTER);
            }
        }

        // Apply perfect rounded corners based on position and menu type
        if (isVertical) {
            boolean isFirst = position == 0;
            boolean isLast = isEdgeItem;
            applyPerfectRoundedBackground(menuItemView, isFirst, isFirst, isLast, isLast);
        } else {
            boolean isFirst = position == 0;
            boolean isLast = isEdgeItem;
            applyPerfectRoundedBackground(menuItemView, isFirst, false, isLast, false);
        }

        menuItemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					handleCustomMenuAction(menuItem.action);
					if (!isVertical) {
						hide();
					}
				}
			});

        layout.addView(menuItemView);
    }

    private void applyDisplayMode(View menuItemView, MenuItemData menuItem, boolean isSecondaryMenu) {
        ImageView icon = menuItemView.findViewById(R.id.menuIcon);
        TextView titleView = menuItemView.findViewById(R.id.menuTitle);

        Log.d("CustomClipboardPanel", "applyDisplayMode: " + _menuDisplayMode + " for " + menuItem.title + ", secondary: " + isSecondaryMenu);

        // Special rule: Secondary menu always shows icon + text regardless of mode
        if (isSecondaryMenu) {
            icon.setVisibility(View.VISIBLE);
            titleView.setVisibility(View.VISIBLE);
            Log.d("CustomClipboardPanel", "Secondary menu - showing icon+text for: " + menuItem.title);
        } else {
            // Primary menu follows the selected display mode
            switch (_menuDisplayMode) {
                case TEXT_ONLY:
                    icon.setVisibility(View.GONE);
                    titleView.setVisibility(View.VISIBLE);
                    Log.d("CustomClipboardPanel", "TEXT_ONLY - hiding icon for: " + menuItem.title);
                    break;
                case ICON_ONLY:
                    icon.setVisibility(View.VISIBLE);
                    titleView.setVisibility(View.GONE);
                    Log.d("CustomClipboardPanel", "ICON_ONLY - hiding text for: " + menuItem.title);
                    break;
                case ICON_AND_TEXT:
                    icon.setVisibility(View.VISIBLE);
                    titleView.setVisibility(View.VISIBLE);
                    Log.d("CustomClipboardPanel", "ICON_AND_TEXT - showing both for: " + menuItem.title);
                    break;
            }
        }

        // Set the content
        icon.setImageResource(menuItem.iconRes);
        titleView.setText(menuItem.title);
    }

    private void applyPerfectRoundedBackground(View view, boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight) {
        if (view == null) return;

        float[] radii = new float[]{
            topLeft ? _menuCornerRadius : 0,
            topLeft ? _menuCornerRadius : 0,
            topRight ? _menuCornerRadius : 0,
            topRight ? _menuCornerRadius : 0,
            bottomRight ? _menuCornerRadius : 0,
            bottomRight ? _menuCornerRadius : 0,
            bottomLeft ? _menuCornerRadius : 0,
            bottomLeft ? _menuCornerRadius : 0
        };

        // Create a background with the same rounded corners for consistency
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x00000000); // Transparent
        background.setCornerRadii(radii);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // For ripple effect, create a mask with the exact same rounded corners
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFFFFFFFF); // White mask
            mask.setCornerRadii(radii);

            RippleDrawable rippleDrawable = new RippleDrawable(
                getRippleColor(),
                background,
                mask
            );
            view.setBackground(rippleDrawable);
        } else {
            // For older versions, use StateListDrawable
            GradientDrawable pressedBackground = new GradientDrawable();
            pressedBackground.setColor(getPressedColor());
            pressedBackground.setCornerRadii(radii);

            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
            stateListDrawable.addState(new int[]{}, background);
            view.setBackgroundDrawable(stateListDrawable);
        }

        // Set padding to ensure content stays within rounded bounds
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, _context.getResources().getDisplayMetrics());
        view.setPadding(padding, padding, padding, padding);

        // Important: Set clipToOutline to ensure the ripple stays within bounds
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            view.setClipToOutline(true);
        }
    }

    private int getPressedColor() {
        return 0x20000000;
    }

    private android.content.res.ColorStateList getRippleColor() {
        try {
            TypedValue typedValue = new TypedValue();
            TypedArray a = _context.obtainStyledAttributes(typedValue.data, new int[]{android.R.attr.colorControlHighlight});
            int color = a.getColor(0, 0x20000000);
            a.recycle();
            return android.content.res.ColorStateList.valueOf(color);
        } catch (Exception e) {
            return android.content.res.ColorStateList.valueOf(0x20000000);
        }
    }

    // PUBLIC METHODS
    public void setMenuDisplayMode(MenuDisplayMode mode) {
        Log.d("CustomClipboardPanel", "SETTING MENU MODE: " + mode);
        _menuDisplayMode = mode;

        // If popup is showing, hide it so it recreates with new mode
        if (_customPopupWindow != null && _customPopupWindow.isShowing()) {
            Log.d("CustomClipboardPanel", "Popup is showing, hiding to force recreation");
            hideCustomPopup();
        }
    }

    public MenuDisplayMode getMenuDisplayMode() {
        return _menuDisplayMode;
    }

    public void setTextOnlyMenu() {
        setMenuDisplayMode(MenuDisplayMode.TEXT_ONLY);
    }

    public void setIconOnlyMenu() {
        setMenuDisplayMode(MenuDisplayMode.ICON_ONLY);
    }

    public void setIconAndTextMenu() {
        setMenuDisplayMode(MenuDisplayMode.ICON_AND_TEXT);
    }

    public void forceRecreate() {
        if (_customPopupWindow != null && _customPopupWindow.isShowing()) {
            hideCustomPopup();
        }
    }

    private void toggleExpand() {
        if (_isExpanded) {
            collapseMenu();
        } else {
            expandMenu();
        }
    }

    private void expandMenu() {
        if (_isAnimating) return;

        _isAnimating = true;
        _isExpanded = true;

        // Store original width for animation
        _originalPrimaryWidth = _primaryMenu.getWidth();

        // If we have overflow items, add them to secondary menu
        if (_hasOverflow && !_overflowItems.isEmpty()) {
            _secondaryMenu.removeAllViews();
            for (int i = 0; i < _overflowItems.size(); i++) {
                addMenuItemToLayout(_overflowItems.get(i), _secondaryMenu, true, i, i == _overflowItems.size() - 1);
            }
        }

        // Step 1: Animate icon rotation (more -> arrow)
        animateIconToArrow(new Runnable() {
				@Override
				public void run() {
					// Step 2: Collapse primary menu and show secondary
					collapsePrimaryAndShowSecondary();
				}
			});
    }

    private void collapseMenu() {
        if (_isAnimating) return;

        _isAnimating = true;
        _isExpanded = false;

        // Step 1: Hide secondary and expand primary
        hideSecondaryAndExpandPrimary(new Runnable() {
				@Override
				public void run() {
					// Step 2: Animate icon rotation (arrow -> more)
					animateArrowToMore(new Runnable() {
							@Override
							public void run() {
								_isAnimating = false;
							}
						});
				}
			});
    }

    private void animateIconToArrow(final Runnable onComplete) {
        // Create rotation animation from 0 to 90 degrees (more -> arrow)
        RotateAnimation rotate = new RotateAnimation(0, 90, 
													 Animation.RELATIVE_TO_SELF, 0.5f, 
													 Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(_animationDuration);
        rotate.setFillAfter(true);

        rotate.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {}

				@Override
				public void onAnimationEnd(Animation animation) {
					// Change icon to arrow after rotation
					_expandButtonIcon.setImageResource(R.drawable.ic_arrow_back);
					if (onComplete != null) {
						onComplete.run();
					}
				}

				@Override
				public void onAnimationRepeat(Animation animation) {}
			});

        _expandButtonIcon.startAnimation(rotate);
    }

    private void animateArrowToMore(final Runnable onComplete) {
        // Create rotation animation from 90 to 0 degrees (arrow -> more)
        RotateAnimation rotate = new RotateAnimation(90, 0, 
													 Animation.RELATIVE_TO_SELF, 0.5f, 
													 Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(_animationDuration);
        rotate.setFillAfter(true);

        rotate.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {}

				@Override
				public void onAnimationEnd(Animation animation) {
					// Change icon to more after rotation
					_expandButtonIcon.setImageResource(R.drawable.ic_more);
					if (onComplete != null) {
						onComplete.run();
					}
				}

				@Override
				public void onAnimationRepeat(Animation animation) {}
			});

        _expandButtonIcon.startAnimation(rotate);
    }

    private void collapsePrimaryAndShowSecondary() {
        // Measure secondary menu for proper width calculation
        _secondaryMenu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int secondaryWidth = _secondaryMenu.getMeasuredWidth();
        _expandedWidth = Math.max(_originalPrimaryWidth, secondaryWidth);

        // Create scale animation to collapse primary menu
        ScaleAnimation scaleDown = new ScaleAnimation(1.0f, 0.0f, 1.0f, 1.0f,
													  Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleDown.setDuration(_animationDuration);
        scaleDown.setFillAfter(true);

        scaleDown.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {}

				@Override
				public void onAnimationEnd(Animation animation) {
					// Hide primary menu and show secondary
					_primaryMenu.setVisibility(View.GONE);
					_divider.setVisibility(View.VISIBLE);
					_secondaryMenu.setVisibility(View.VISIBLE);

					// Animate secondary menu slide in
					TranslateAnimation slideIn = new TranslateAnimation(
						Animation.RELATIVE_TO_SELF, 1.0f,
						Animation.RELATIVE_TO_SELF, 0.0f,
						Animation.RELATIVE_TO_SELF, 0.0f,
						Animation.RELATIVE_TO_SELF, 0.0f
					);
					slideIn.setDuration(_animationDuration);
					slideIn.setFillAfter(true);

					slideIn.setAnimationListener(new Animation.AnimationListener() {
							@Override
							public void onAnimationStart(Animation animation) {}

							@Override
							public void onAnimationEnd(Animation animation) {
								_isAnimating = false;
								updatePopupSizeSmoothly();
							}

							@Override
							public void onAnimationRepeat(Animation animation) {}
						});

					_secondaryMenu.startAnimation(slideIn);
					_divider.startAnimation(slideIn);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {}
			});

        _primaryMenu.startAnimation(scaleDown);
    }

    private void hideSecondaryAndExpandPrimary(final Runnable onComplete) {
        // Animate secondary menu slide out
        TranslateAnimation slideOut = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f
        );
        slideOut.setDuration(_animationDuration);
        slideOut.setFillAfter(true);

        slideOut.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {}

				@Override
				public void onAnimationEnd(Animation animation) {
					// Hide secondary and show primary
					_secondaryMenu.setVisibility(View.GONE);
					_divider.setVisibility(View.GONE);
					_primaryMenu.setVisibility(View.VISIBLE);

					// Animate primary menu scale up
					ScaleAnimation scaleUp = new ScaleAnimation(0.0f, 1.0f, 1.0f, 1.0f,
																Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f);
					scaleUp.setDuration(_animationDuration);
					scaleUp.setFillAfter(true);

					scaleUp.setAnimationListener(new Animation.AnimationListener() {
							@Override
							public void onAnimationStart(Animation animation) {}

							@Override
							public void onAnimationEnd(Animation animation) {
								if (onComplete != null) {
									onComplete.run();
								}
								updatePopupSizeSmoothly();
							}

							@Override
							public void onAnimationRepeat(Animation animation) {}
						});

					_primaryMenu.startAnimation(scaleUp);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {}
			});

        _secondaryMenu.startAnimation(slideOut);
        _divider.startAnimation(slideOut);
    }

    private void updatePopupSizeSmoothly() {
        if (_customPopupWindow != null && _customPopupWindow.isShowing() && _popupView != null) {
            _popupView.post(new Runnable() {
					@Override
					public void run() {
						try {
							// Check if popup is still showing and view is attached
							if (_customPopupWindow != null && _customPopupWindow.isShowing() && _popupView != null && _popupView.isAttachedToWindow()) {
								// Measure the actual content size
								_popupView.measure(
									View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
									View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
								);

								int width = _popupView.getMeasuredWidth();
								int height = _popupView.getMeasuredHeight();

								// Update without animation to prevent flickering
								_customPopupWindow.update(width, height);
							}
						} catch (Exception e) {
							Log.e("CustomClipboardPanel", "Error updating popup size: " + e.getMessage());
						}
					}
				});
        }
    }

    public Context getContext() {
        return _context;
    }

    public void show() {
        showAtLocation(null);
    }

    public void showAtLocation(Rect preferredRect) {
        showCustomPopup(preferredRect);
        startAutoHideTimer();
    }

    private void showCustomPopup(Rect preferredRect) {
        if (_customPopupWindow.isShowing()) {
            return;
        }

        Log.d("CustomClipboardPanel", "showCustomPopup with mode: " + _menuDisplayMode);

        // SETUP MENU ITEMS EVERY TIME BEFORE SHOWING
        setupMenuItems();

        resetMenuToCollapsed();

        _popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        int width = _popupView.getMeasuredWidth();
        int height = _popupView.getMeasuredHeight();

        _customPopupWindow.setWidth(width);
        _customPopupWindow.setHeight(height);

        Rect positionRect = new Rect();
        calculateOptimalPosition(positionRect);

        _customPopupWindow.showAtLocation(
            _editView, 
            Gravity.NO_GRAVITY, 
            positionRect.left, 
            positionRect.top
        );

        Log.d("CustomClipboardPanel", "Popup shown with mode: " + _menuDisplayMode + ", overflow: " + _hasOverflow);
    }

    private void resetMenuToCollapsed() {
        _isExpanded = false;
        _isAnimating = false;
        _divider.setVisibility(View.GONE);
        _secondaryMenu.setVisibility(View.GONE);
        _secondaryMenu.removeAllViews(); // Clear secondary menu when collapsed
        _primaryMenu.setVisibility(View.VISIBLE);
        _primaryMenu.setScaleX(1.0f);
        _primaryMenu.setScaleY(1.0f);
        if (_expandButtonIcon != null) {
            _expandButtonIcon.setImageResource(R.drawable.ic_more);
            _expandButtonIcon.clearAnimation();
            _expandButtonIcon.setRotation(0);
        }
    }

    private void hideCustomPopup() {
        if (_customPopupWindow.isShowing()) {
            _customPopupWindow.dismiss();
        }
    }

    private void handleCustomMenuAction(MenuAction action) {
        switch (action) {
            case COPY: _editView.copy(); break;
            case CUT: _editView.cut(); break;
            case PASTE: _editView.paste(); break;
            case SELECT_ALL: _editView.selectAll(); break;
            case SHARE: break;
            case GOTO: break;
            case DELETE: break;
        }
        restartAutoHideTimer();
    }

    private void calculateOptimalPosition(Rect outRect) {
        _caretRect = _editView.getBoundingBox(_editView.getCaretPosition());
        _isSelectionMode = _editView.isSelectMode();

        if (_caretRect == null) {
            outRect.set(0, 0, 100, 100);
            return;
        }

        int caretX = _caretRect.left;
        int caretY = _caretRect.top;
        int lineHeight = _editView.getLineHeight();
        int screenWidth = _editView.getWidth();
        int screenHeight = _editView.getHeight();

        int actionModeWidth = Math.min(screenWidth, 400);
        int actionModeHeight = lineHeight * 2;

        int optimalX = caretX;
        if (caretX + actionModeWidth > screenWidth) {
            optimalX = screenWidth - actionModeWidth - 20;
        }
        if (optimalX < 20) {
            optimalX = 20;
        }

        int optimalY;
        if (caretY - actionModeHeight - lineHeight > 0) {
            optimalY = caretY - actionModeHeight - lineHeight;
        } else {
            optimalY = caretY + lineHeight * 2;
            if (optimalY + actionModeHeight > screenHeight) {
                optimalY = Math.max(20, screenHeight - actionModeHeight - 20);
            }
        }

        if (_isSelectionMode) {
            Rect selectionRect = getSelectionMiddleRect();
            if (selectionRect != null) {
                optimalX = selectionRect.left;
                optimalY = selectionRect.top - actionModeHeight - lineHeight;
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
            int selectionStart = _editView.getSelectionStart();
            int selectionEnd = _editView.getSelectionEnd();
            int middle = (selectionStart + selectionEnd) / 2;
            return _editView.getBoundingBox(middle);
        } catch (Exception e) {
            return _caretRect;
        }
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
        hideCustomPopup();
    }

    public void updatePosition() {
        if (_customPopupWindow.isShowing()) {
            Rect positionRect = new Rect();
            calculateOptimalPosition(positionRect);
            _customPopupWindow.update(
                positionRect.left, 
                positionRect.top, 
                _customPopupWindow.getWidth(), 
                _customPopupWindow.getHeight()
            );
        }
    }

    // Enum for menu actions
    private enum MenuAction {
        COPY, CUT, PASTE, SELECT_ALL, SHARE, GOTO, DELETE
		}

    // Data class for menu items
    private static class MenuItemData {
        String title;
        int iconRes;
        MenuAction action;

        MenuItemData(String title, int iconRes, MenuAction action) {
            this.title = title;
            this.iconRes = iconRes;
            this.action = action;
        }
    }
}

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
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import modder.hub.editor.EditView;
import modder.hub.editor.R;
import modder.hub.editor.treeObserver.OnComputeInternalInsetsListener;
import modder.hub.editor.treeObserver.ViewTreeObserverReflection;
import modder.hub.editor.utils.menuUtils.MenuAction;
import modder.hub.editor.utils.menuUtils.MenuItemConfig;
import modder.hub.editor.utils.menuUtils.MenuItemData;
import modder.hub.editor.utils.menuUtils.ViewFader;
import org.json.JSONArray;
import org.json.JSONObject;

/*
 * Author : @developer-krushna (Krushna Chandra)
 * Ideas copied from Android System FloatingToolBar.java class
 * Fixed bug, comments, and optimization done by ChatGPT
 */

public class ClipboardPanel {
    // ===============================================
    // CLASS VARIABLES AND CONSTANTS
    // ===============================================

    protected EditView _editView;
    private Context _context;

    // Custom popup menu variables
    private PopupWindow _customPopupWindow;
    private FrameLayout _popupContentHolder;
    private FrameLayout _contentContainer;
    private LinearLayout _primaryMenu;
    private LinearLayout _secondaryMenu;
    private View _upButton;
    private View _divider;
    private boolean _isExpanded = false;
    private boolean _isAnimating = false;

    private Rect _caretRect;
    private boolean _isSelectionMode;

    // Menu display mode
    private MenuDisplayMode _menuDisplayMode = MenuDisplayMode.ICON_ONLY;

    // Menu item dimensions
    private int _menuItemHeight;
    private int _menuItemMinWidth;
    private int _menuIconSize;

    // Sizes
    private int _primaryWidth;
    private int _primaryHeight;
    private int _secondaryWidth;
    private int _secondaryHeight;

    // Overflow management
    private boolean _hasOverflow = false;
    private ArrayList<MenuItemData> _overflowItems = new ArrayList<>();

    // Menu items list in JSON order
    private ArrayList<String> _menuItems = new ArrayList<>();
    private Map<String, MenuItemConfig> _allMenuItems = new HashMap<>();

    // Animation variables
    private AnimationSet _openOverflowAnimation = new AnimationSet(true);
    private AnimationSet _closeOverflowAnimation = new AnimationSet(true);

    private Handler _autoHideHandler = new Handler();
    private static final long AUTO_HIDE_DELAY = 5000;
    private Runnable _autoHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    // Faders
    private ViewFader _primaryFader;
    private ViewFader _secondaryFader;

    // class fields
    private OnComputeInternalInsetsListener mInvocationHandler;
    private Region mTouchableRegion = new Region();

    // Edge margin
    private int _edgeMargin;

    // Menu display mode enum
    public enum MenuDisplayMode {
        TEXT_ONLY,
        ICON_ONLY,
        ICON_AND_TEXT
    }

    // ===============================================
    // CONSTRUCTOR AND INITIALIZATION
    // ===============================================

    public ClipboardPanel(EditView editView) {
        _editView = editView;
        _context = editView.getContext();

        // Initialize dimensions
        _menuItemHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, _context.getResources().getDisplayMetrics());
        _menuItemMinWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, _context.getResources().getDisplayMetrics());
        _menuIconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, _context.getResources().getDisplayMetrics());
        _edgeMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, _context.getResources().getDisplayMetrics());

        // Initialize all menu items configuration
        initializeAllMenuItems();

        initCustomPopup();
    }

    // ===============================================
    // MENU CONFIGURATION METHODS
    // ===============================================

    /** Initialize all possible menu items with their configurations */
    private void initializeAllMenuItems() {
        _allMenuItems.clear();
        _allMenuItems.put("panel_btn_select", new MenuItemConfig("Select", R.drawable.ic_select, MenuAction.SELECT));
        _allMenuItems.put("panel_btn_copy", new MenuItemConfig("Copy", R.drawable.ic_copy, MenuAction.COPY));
        _allMenuItems.put("panel_btn_cut", new MenuItemConfig("Cut", R.drawable.ic_cut, MenuAction.CUT));
        _allMenuItems.put("panel_btn_paste", new MenuItemConfig("Paste", R.drawable.ic_paste, MenuAction.PASTE));
        _allMenuItems.put("panel_btn_select_all", new MenuItemConfig("Select All", R.drawable.ic_select_all, MenuAction.SELECT_ALL));
        _allMenuItems.put("share_btn", new MenuItemConfig("Share", R.drawable.ic_share, MenuAction.SHARE));
        _allMenuItems.put("goto_btn", new MenuItemConfig("Go to", R.drawable.ic_goto, MenuAction.GOTO));
        _allMenuItems.put("panel_btn_translate", new MenuItemConfig("Translate", R.drawable.ic_translate, MenuAction.TRANSLATE));
        _allMenuItems.put("delete_btn", new MenuItemConfig("Delete", R.drawable.ic_delete, MenuAction.DELETE));
    }

    /** Load menu configuration from JSON with sequence */
    public void loadMenuConfiguration() {
        SharedPreferences prefs = _context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE);
        String jsonConfig = prefs.getString("menu_order", null);

        // If not exists, save default config first
        if (jsonConfig == null) {
            jsonConfig = "[" +
                    "{\"id\":\"panel_btn_select\",\"title\":\"Select\",\"disabled\":false}," +
                    "{\"id\":\"panel_btn_select_all\",\"title\":\"Select All\",\"disabled\":false}," +
                    "{\"id\":\"panel_btn_copy\",\"title\":\"Copy\",\"disabled\":false}," +
                    "{\"id\":\"panel_btn_paste\",\"title\":\"Paste\",\"disabled\":false}," +
                    "{\"id\":\"goto_btn\",\"title\":\"Go To\",\"disabled\":true}," +
                    "{\"id\":\"panel_btn_cut\",\"title\":\"Cut\",\"disabled\":false}," +
                    "{\"id\":\"share_btn\",\"title\":\"Share\",\"disabled\":false}," +
                    "{\"id\":\"panel_btn_translate\",\"title\":\"Translate\",\"disabled\":false}," +
                    "{\"id\":\"delete_btn\",\"title\":\"Delete\",\"disabled\":false}" +
                    "]";
            prefs.edit().putString("menu_order", jsonConfig).apply();
        }

        _menuItems.clear();

        try {
            JSONArray jsonArray = new JSONArray(jsonConfig);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                String id = item.getString("id");
                boolean disabled = item.optBoolean("disabled", false);

                // Only add if not disabled and exists in our configuration
                if (!disabled && _allMenuItems.containsKey(id)) {
                    _menuItems.add(id);
                }
            }
        } catch (Exception e) {
            Log.e("ClipboardPanel", "Error loading menu config: " + e.getMessage());
            // Fallback to default order
            setDefaultMenuOrder();
        }

        // If no items loaded, use default order
        if (_menuItems.isEmpty()) {
            setDefaultMenuOrder();
        }

        Log.d("ClipboardPanel", "Loaded " + _menuItems.size() + " menu items in order: " + _menuItems);
    }

    /** Set default menu order when no configuration is available */
    private void setDefaultMenuOrder() {
        _menuItems.clear();
        _menuItems.add("panel_btn_select");
        _menuItems.add("panel_btn_select_all");
        _menuItems.add("panel_btn_copy");
        _menuItems.add("panel_btn_paste");
        _menuItems.add("goto_btn");
        _menuItems.add("panel_btn_cut");
        _menuItems.add("share_btn");
        _menuItems.add("panel_btn_translate");
        _menuItems.add("delete_btn");
    }

    // ===============================================
    // POPUP WINDOW INITIALIZATION
    // ===============================================

    /** Initialize the custom popup window */
    private void initCustomPopup() {
        _popupContentHolder = new FrameLayout(_context);

        _customPopupWindow = new PopupWindow(
        _popupContentHolder,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
        );
        _customPopupWindow.setOutsideTouchable(true);
        _customPopupWindow.setFocusable(false);
        _customPopupWindow.setElevation(24f);
        _customPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        _contentContainer = new FrameLayout(_context);

        // Create background with stroke
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xE6FFFFFF); // Semi-transparent white
        background.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, _context.getResources().getDisplayMetrics()));
        background.setStroke(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, _context.getResources().getDisplayMetrics()),
                0x33000000 // Semi-transparent black stroke
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Enable proper clipping for rounded corners
            _contentContainer.setClipToOutline(true);
            _contentContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, _context.getResources().getDisplayMetrics());
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
        }

        _contentContainer.setBackground(background);
        _popupContentHolder.addView(_contentContainer, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
        ));

        _divider = new View(_context);
        _divider.setBackgroundColor(0xFFDDDDDD);
        _customPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    }

    // ===============================================
    // TOUCH AND INSETS HANDLING
    // ===============================================

    /** Configure touch handling for the popup */
    private void configTouch() {
        mInvocationHandler = new OnComputeInternalInsetsListener();
        ViewTreeObserverReflection.removeOnComputeInternalInsetsListener(_popupContentHolder.getViewTreeObserver());
        ViewTreeObserverReflection.addOnComputeInternalInsetsListener(_popupContentHolder.getViewTreeObserver(), mInvocationHandler.getListener());
        mInvocationHandler.setTouchRegion(mTouchableRegion);
    }

    /** Detach insets listener when hiding popup */
    private void detachInsetsListener() {
        if (mInvocationHandler != null && _popupContentHolder != null) {
            ViewTreeObserverReflection.removeOnComputeInternalInsetsListener(_popupContentHolder.getViewTreeObserver());
            mInvocationHandler = null;
        }
    }

    // ===============================================
    // MENU ITEM CREATION AND SETUP
    // ===============================================

    /** Set up menu items based on configuration and display mode */
    private void setupMenuItems() {
        Log.d("ClipboardPanel", "setupMenuItems called with mode: " + _menuDisplayMode);

        // Load menu configuration first
        loadMenuConfiguration();

        // Clear existing
        if (_primaryMenu != null) {
            _primaryFader = null;
            _primaryMenu.removeAllViews();
        }
        if (_secondaryMenu != null) {
            _secondaryFader = null;
            _secondaryMenu.removeAllViews();
            if (_upButton != null) {
                _secondaryMenu.removeView(_upButton);
                _upButton = null;
            }
        }
        _overflowItems.clear();
        _hasOverflow = false;

        // Create primary
        _primaryMenu = new LinearLayout(_context);
        _primaryMenu.setOrientation(LinearLayout.HORIZONTAL);
        _primaryFader = new ViewFader(_primaryMenu);

        // Create menu items in JSON-defined order
        ArrayList<MenuItemData> allItems = new ArrayList<>();
        for (String menuId : _menuItems) {
            MenuItemConfig config = _allMenuItems.get(menuId);
            if (config != null) {
                if (config.action == MenuAction.SELECT) {
                    if (isTextSelected()) {
                        continue;
                    }
                    if (_editView.getBuffer().length() == 0) {
                        continue;
                    }
                }
                if (config.action == MenuAction.SELECT_ALL) {
                    if (_editView.isAllTextSelected()) {
                        continue;
                    }
                    if (_editView.getBuffer().length() == 0) {
                        continue;
                    }
                }

                if (config.action == MenuAction.PASTE) {
                    if (!canPaste()) {
                        continue;
                    }
                    if (!_editView.getEditedMode()) {
                        continue;
                    }
                }

                if (config.action == MenuAction.CUT) {
                    if (!isTextSelected()) {
                        continue;
                    }
                    if (!_editView.getEditedMode()) {
                        continue;
                    }
                }
                if (config.action == MenuAction.COPY && !isTextSelected()) {
                    continue;
                }

                if (config.action == MenuAction.DELETE) {
                    if (!isTextSelected()) {
                        continue;
                    }
                    if (!_editView.getEditedMode()) {
                        continue;
                    }
                }
                if (config.action == MenuAction.SHARE && !isTextSelected()) {
                    continue;
                }

                if (config.action == MenuAction.TRANSLATE && !isTextSelected()) {
                    continue;
                }

                if (config.action == MenuAction.GOTO && !isTextSelected()) {
                    continue;
                }

                allItems.add(createMenuItem(config.title, config.iconRes, config.action));
            }
        }

        // Get screen density and calculate available space more accurately
        float density = _context.getResources().getDisplayMetrics().density;
        int screenWidth = _editView.getWidth();

        Log.d("ClipboardPanel", "Screen width: " + screenWidth + "px, Density: " + density + ", Total items: " + allItems.size());

        ArrayList<MenuItemData> primaryItems = new ArrayList<>();
        ArrayList<MenuItemData> secondaryItems = new ArrayList<>();

        // If no overflow needed (all fit in primary), no expand button
        int maxPrimaryItems = calculateMaxPrimaryItems(screenWidth, density);
        boolean needsOverflow = allItems.size() > maxPrimaryItems;

        Log.d("ClipboardPanel", "Max primary items possible: " + maxPrimaryItems + ", Needs overflow: " + needsOverflow);

        // Simple logic: always show first N items in primary, rest in secondary
        for (int i = 0; i < allItems.size(); i++) {
            if (i < maxPrimaryItems) {
                primaryItems.add(allItems.get(i));
            } else {
                secondaryItems.add(allItems.get(i));
                _hasOverflow = needsOverflow;
            }
        }

        // Add primary menu items
        for (int i = 0; i < primaryItems.size(); i++) {
            boolean isLastPrimary = (i == primaryItems.size() - 1) && !_hasOverflow;
            addMenuItemToLayout(primaryItems.get(i), _primaryMenu, false, i, isLastPrimary);
        }

        // Add expand button only if we have overflow items
        if (_hasOverflow && !secondaryItems.isEmpty()) {
            addExpandButtonToPrimaryMenu();
            _overflowItems.addAll(secondaryItems);
            Log.d("ClipboardPanel", "Added expand button with " + secondaryItems.size() + " overflow items");

            // Create secondary menu
            _secondaryMenu = new LinearLayout(_context);
            _secondaryMenu.setOrientation(LinearLayout.VERTICAL);
            _secondaryFader = new ViewFader(_secondaryMenu);

            createUpButton();
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
            _secondaryMenu.addView(_divider, dividerParams);

            for (int i = 0; i < _overflowItems.size(); i++) {
                addMenuItemToLayout(_overflowItems.get(i), _secondaryMenu, true, i, i == _overflowItems.size() - 1);
            }
        }

        // Measure
        _primaryMenu.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
        );
        _primaryWidth = _primaryMenu.getMeasuredWidth();
        _primaryHeight = _primaryMenu.getMeasuredHeight();

        if (_hasOverflow) {
            _secondaryMenu.measure(
                    View.MeasureSpec.UNSPECIFIED,
                    View.MeasureSpec.UNSPECIFIED
            );
            _secondaryWidth = _secondaryMenu.getMeasuredWidth();
            _secondaryHeight = _secondaryMenu.getMeasuredHeight();
        }

        allItems.clear();

        Log.d("ClipboardPanel", "Final distribution: " + primaryItems.size() + " primary, " +
                secondaryItems.size() + " secondary, overflow: " + _hasOverflow);
    }

    /** Calculate maximum number of primary menu items that can fit on screen */
    private int calculateMaxPrimaryItems(int screenWidth, float density) {
        // Convert screen width to dp for consistent calculations
        int screenWidthDp = (int) (screenWidth / density);

        Log.d("ClipboardPanel", "Screen width in dp: " + screenWidthDp);

        // Calculate item width based on display mode
        int itemWidthDp;
        switch (_menuDisplayMode) {
            case TEXT_ONLY:
                itemWidthDp = 60; // Approximate width for text items
                break;
            case ICON_ONLY:
                itemWidthDp = 56; // Compact width for icon-only
                break;
            case ICON_AND_TEXT:
                itemWidthDp = 90; // Wider for icon + text
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
            maxItems = Math.min(5, maxItemsWithOverflow + 1); // Allow one more since icons are
                                                              // compact
        }

        // For text-only mode, follow Android system behavior (usually 4 items)
        if (_menuDisplayMode == MenuDisplayMode.TEXT_ONLY) {
            maxItems = Math.min(4, maxItemsWithOverflow);
        }

        Log.d("ClipboardPanel", "Item width: " + itemWidthDp + "dp, Available: " + availableWidthDp +
                "dp, Max items: " + maxItems);

        return maxItems;
    }

    /** Add expand button to primary menu for accessing overflow items */
    private void addExpandButtonToPrimaryMenu() {
        LayoutInflater inflater = LayoutInflater.from(_primaryMenu.getContext());
        View expandButtonView = inflater.inflate(R.layout.expand_button, _primaryMenu, false);

        ImageView expandIcon = expandButtonView.findViewById(R.id.expandIcon);
        expandIcon.setImageResource(R.drawable.ic_more);

        // Set proper icon size - this is the key fix
        ViewGroup.LayoutParams iconParams = expandIcon.getLayoutParams();
        if (iconParams != null) {
            iconParams.width = _menuIconSize;
            iconParams.height = _menuIconSize;
            expandIcon.setLayoutParams(iconParams);
        } else {
            // If no layout params, create new ones
            expandIcon.setLayoutParams(new LinearLayout.LayoutParams(_menuIconSize, _menuIconSize));
        }

        // Center the icon
        if (expandButtonView instanceof LinearLayout) {
            ((LinearLayout) expandButtonView).setGravity(Gravity.CENTER);
        }

        // Set fixed height for expand button
        ViewGroup.LayoutParams params = expandButtonView.getLayoutParams();
        if (params != null) {
            params.height = _menuItemHeight;
            if (params instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) params).width = _menuItemHeight; // Make it square
            }
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

    /** Create up button for secondary menu navigation */
    private void createUpButton() {
        if (_upButton != null || _secondaryMenu == null) return;

        LayoutInflater inflater = LayoutInflater.from(_context);
        _upButton = inflater.inflate(R.layout.expand_button, _secondaryMenu, false);

        ImageView icon = _upButton.findViewById(R.id.expandIcon);
        icon.setImageResource(R.drawable.ic_arrow_back);

        // Set proper icon size - same as more icon
        ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
        if (iconParams != null) {
            iconParams.width = _menuIconSize;
            iconParams.height = _menuIconSize;
            icon.setLayoutParams(iconParams);
        } else {
            icon.setLayoutParams(new LinearLayout.LayoutParams(_menuIconSize, _menuIconSize));
        }

        // Set fixed height for up button
        ViewGroup.LayoutParams params = _upButton.getLayoutParams();
        if (params != null) {
            params.height = _menuItemHeight;
            if (params instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) params).width = LinearLayout.LayoutParams.MATCH_PARENT;
            }
        }
        _upButton.setLayoutParams(params);

        // KEY FIX: Align icon to left instead of center
        if (_upButton instanceof LinearLayout) {
            ((LinearLayout) _upButton).setGravity(Gravity.CENTER_VERTICAL | Gravity.START); // Left
                                                                                            // alignment
        }

        // Add left padding to position the icon properly
        int leftPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, _context.getResources().getDisplayMetrics());
        int verticalPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, _context.getResources().getDisplayMetrics());
        _upButton.setPadding(leftPadding, verticalPadding, leftPadding, verticalPadding);

        _upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!_isAnimating) {
                    collapseMenu();
                }
            }
        });

        applyPerfectRoundedBackground(_upButton, true, true, false, false);
        _secondaryMenu.addView(_upButton, 0);
    }

    /** Create a menu item data object */
    private MenuItemData createMenuItem(String title, int iconRes, MenuAction action) {
        return new MenuItemData(title, iconRes, action);
    }

    /** Add menu item to layout with proper styling */
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

        // Add tooltip only for icon-only mode
        if (_menuDisplayMode == MenuDisplayMode.ICON_ONLY) {
            if (menuItemView != null && Build.VERSION.SDK_INT >= 26) {
                menuItemView.setTooltipText(menuItem.title);
            }
        }

        if (isVertical) {
            // Vertical layout - full width
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) menuItemView.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = _menuItemHeight;
            menuItemView.setLayoutParams(layoutParams);

            // Center content vertically for vertical layout
            if (menuItemView instanceof LinearLayout) {
                ((LinearLayout) menuItemView).setGravity(Gravity.CENTER_VERTICAL);
            }
        } else {
            // Horizontal layout - fixed width based on content
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) menuItemView.getLayoutParams();

            // Calculate width based on content
            int itemWidth;
            titleView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            switch (_menuDisplayMode) {
                case TEXT_ONLY:
                    itemWidth = titleView.getMeasuredWidth() + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, _context.getResources().getDisplayMetrics());
                    break;
                case ICON_ONLY:
                    itemWidth = _menuItemHeight; // Square for icon-only
                    break;
                case ICON_AND_TEXT:
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
                ((LinearLayout) menuItemView).setGravity(Gravity.CENTER);
            }
        }

        // Apply perfect rounded background based on position and menu type
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

    // ===============================================
    // DISPLAY MODE AND STYLING
    // ===============================================

    /** Apply display mode to menu item (icon only, text only, or both) */
    private void applyDisplayMode(View menuItemView, MenuItemData menuItem, boolean isSecondaryMenu) {
        ImageView icon = menuItemView.findViewById(R.id.menuIcon);
        TextView titleView = menuItemView.findViewById(R.id.menuTitle);

        Log.d("ClipboardPanel", "applyDisplayMode: " + _menuDisplayMode + " for " + menuItem.title + ", secondary: " + isSecondaryMenu);

        // Primary menu follows the selected display mode
        switch (_menuDisplayMode) {
            case TEXT_ONLY:
                icon.setVisibility(View.GONE);
                titleView.setVisibility(View.VISIBLE);
                Log.d("ClipboardPanel", "TEXT_ONLY - hiding icon for: " + menuItem.title);
                break;
            case ICON_ONLY:
                icon.setVisibility(View.VISIBLE);
                titleView.setVisibility(View.GONE);
                Log.d("ClipboardPanel", "ICON_ONLY - hiding text for: " + menuItem.title);
                break;
            case ICON_AND_TEXT:
                icon.setVisibility(View.VISIBLE);
                titleView.setVisibility(View.VISIBLE);
                Log.d("ClipboardPanel", "ICON_AND_TEXT - showing both for: " + menuItem.title);
                break;
        }

        // For secondary menu, override to show both unless TEXT_ONLY (hide icon)
        if (isSecondaryMenu && _menuDisplayMode != MenuDisplayMode.TEXT_ONLY) {
            icon.setVisibility(View.VISIBLE);
            titleView.setVisibility(View.VISIBLE);
            Log.d("ClipboardPanel", "Secondary menu - showing icon+text for: " + menuItem.title);
        }

        // Set the content
        icon.setImageResource(menuItem.iconRes);
        titleView.setText(menuItem.title);
    }

    /** Apply rounded background with specific corner rounding */
    private void applyPerfectRoundedBackground(View view, boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight) {
        if (view == null) return;

        float cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, _context.getResources().getDisplayMetrics());
        float[] radii = new float[]{
                topLeft ? cornerRadius : 0, topLeft ? cornerRadius : 0,
                topRight ? cornerRadius : 0, topRight ? cornerRadius : 0,
                bottomRight ? cornerRadius : 0, bottomRight ? cornerRadius : 0,
                bottomLeft ? cornerRadius : 0, bottomLeft ? cornerRadius : 0
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Create a transparent background with proper corners for ripple mask
            GradientDrawable transparentBackground = new GradientDrawable();
            transparentBackground.setColor(Color.TRANSPARENT);
            transparentBackground.setCornerRadii(radii);

            // Create mask with same corners for proper ripple bounds
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE); // Color doesn't matter for mask, just needs to be opaque
            mask.setCornerRadii(radii);

            RippleDrawable rippleDrawable = new RippleDrawable(
            getRippleColor(),
            transparentBackground, // Transparent background but maintains corner shape
            mask
            );
            view.setBackground(rippleDrawable);
        } else {
            // For older versions - transparent background with pressed state
            GradientDrawable pressedBackground = new GradientDrawable();
            pressedBackground.setColor(getPressedColor());
            pressedBackground.setCornerRadii(radii);

            GradientDrawable normalBackground = new GradientDrawable();
            normalBackground.setColor(Color.TRANSPARENT);
            normalBackground.setCornerRadii(radii);

            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
            stateListDrawable.addState(new int[]{}, normalBackground);
            view.setBackground(stateListDrawable);
        }

        // Set padding to ensure content stays within bounds
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, _context.getResources().getDisplayMetrics());
        view.setPadding(padding, padding, padding, padding);
    }

    /** Get pressed state color */
    private int getPressedColor() {
        return 0x20000000;
    }

    /** Get ripple color from theme or default */
    private ColorStateList getRippleColor() {
        try {
            TypedArray a = _context.obtainStyledAttributes(new int
                    []{android.R.attr.colorControlHighlight});
            int color = a.getColor(0, 0x20000000);
            a.recycle();
            return ColorStateList.valueOf(color);
        } catch (Exception e) {
            return ColorStateList.valueOf(0x20000000);
        }
    }

    // ===============================================
    // PUBLIC API METHODS
    // ===============================================

    /** Set menu display mode and force recreation if showing */
    public void setMenuDisplayMode(MenuDisplayMode mode) {
        Log.d("ClipboardPanel", "SETTING MENU MODE: " + mode);
        _menuDisplayMode = mode;

        // If popup is showing, hide it so it recreates with new mode
        if (_customPopupWindow != null && _customPopupWindow.isShowing()) {
            Log.d("ClipboardPanel", "Popup is showing, hiding to force recreation");
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

    /** Force recreation of the popup */
    public void forceRecreate() {
        if (_customPopupWindow != null && _customPopupWindow.isShowing()) {
            hideCustomPopup();
        }
    }

    public Context getContext() {
        return _context;
    }

    /** Show the clipboard panel */
    public void show() {
        showAtLocation(null);
    }

    /** Show the clipboard panel at specified location */
    public void showAtLocation(Rect preferredRect) {
        showCustomPopup(preferredRect);
        startAutoHideTimer();
    }

    /** Hide the clipboard panel */
    public void hide() {
        cancelAutoHideTimer();
        hideCustomPopup();
    }

    /** Update panel position (e.g., when caret moves) */
    public void updatePosition() {
        if (_customPopupWindow.isShowing()) {
            int popupWidth = _customPopupWindow.getWidth();
            int popupHeight = _customPopupWindow.getHeight();
            Rect positionRect = new Rect();
            calculateOptimalPosition(positionRect, popupWidth, popupHeight);
            _customPopupWindow.update(
                    positionRect.left,
                    positionRect.top,
                    popupWidth,
                    popupHeight
            );
        }
    }

    // ===============================================
    // EXPANSION AND ANIMATION METHODS
    // ===============================================

    /** Toggle between expanded and collapsed state */
    private void toggleExpand() {
        if (_isExpanded) {
            collapseMenu();
        } else {
            expandMenu();
        }
    }

    /** Expand menu to show overflow items */
    private void expandMenu() {
        if (_isAnimating || !_hasOverflow) return;

        _isAnimating = true;
        _isExpanded = true;

        _primaryFader.fadeOut(true);

        final int targetWidth = _secondaryWidth;
        final int targetHeight = _secondaryHeight;
        final int startWidth = _contentContainer.getWidth();
        final int startHeight = _contentContainer.getHeight();
        final int popupWidth = _customPopupWindow.getWidth();
        final float startY = _contentContainer.getY();
        final boolean morphUpwards = false; // Assume down

        Animation widthAnimation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                ViewGroup.LayoutParams params = _contentContainer.getLayoutParams();
                params.width = (int) (startWidth + interpolatedTime * (targetWidth - startWidth));
                _contentContainer.setLayoutParams(params);
                // Anchor from right
                _contentContainer.setX(popupWidth - params.width);
            }
        };
        widthAnimation.setDuration(240);

        Animation heightAnimation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                ViewGroup.LayoutParams params = _contentContainer.getLayoutParams();
                params.height = (int) (startHeight + interpolatedTime * (targetHeight - startHeight));
                _contentContainer.setLayoutParams(params);
                if (morphUpwards) {
                    _contentContainer.setY(startY - (params.height - startHeight));
                }
            }
        };
        heightAnimation.setDuration(180);
        heightAnimation.setStartOffset(60);

        _openOverflowAnimation = new AnimationSet(true);
        _openOverflowAnimation.addAnimation(widthAnimation);
        _openOverflowAnimation.addAnimation(heightAnimation);
        _openOverflowAnimation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                setSecondaryAsContent();
                _secondaryFader.fadeIn(true);
                _isAnimating = false;
                // Update touch region after animation completes
                updateTouchRegion();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        _contentContainer.startAnimation(_openOverflowAnimation);
    }

    /** Collapse menu back to primary items */
    private void collapseMenu() {
        if (_isAnimating || !_hasOverflow) return;

        _isAnimating = true;
        _isExpanded = false;

        _secondaryFader.fadeOut(true);

        final int targetWidth = _primaryWidth;
        final int targetHeight = _primaryHeight;
        final int startWidth = _contentContainer.getWidth();
        final int startHeight = _contentContainer.getHeight();
        final int popupWidth = _customPopupWindow.getWidth();
        final float startY = _contentContainer.getY();
        final boolean morphUpwards = false;

        Animation widthAnimation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                ViewGroup.LayoutParams params = _contentContainer.getLayoutParams();
                params.width = (int) (startWidth + interpolatedTime * (targetWidth - startWidth));
                _contentContainer.setLayoutParams(params);
                // Anchor from right during collapse
                _contentContainer.setX(popupWidth - params.width);
            }
        };
        widthAnimation.setDuration(150);
        widthAnimation.setStartOffset(150);

        Animation heightAnimation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                ViewGroup.LayoutParams params = _contentContainer.getLayoutParams();
                params.height = (int) (startHeight + interpolatedTime * (targetHeight - startHeight));
                _contentContainer.setLayoutParams(params);
                if (morphUpwards) {
                    _contentContainer.setY(startY + (startHeight - params.height));
                }
            }
        };
        heightAnimation.setDuration(210);

        _closeOverflowAnimation = new AnimationSet(true);
        _closeOverflowAnimation.addAnimation(widthAnimation);
        _closeOverflowAnimation.addAnimation(heightAnimation);
        _closeOverflowAnimation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                setPrimaryAsContent();
                _primaryFader.fadeIn(true);
                _isAnimating = false;
                // Update touch region after animation completes
                updateTouchRegion();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        _contentContainer.startAnimation(_closeOverflowAnimation);
    }

    /** Switch to secondary menu content */
    private void setSecondaryAsContent() {
        _contentContainer.removeAllViews();
        _contentContainer.addView(_secondaryMenu);
        ViewGroup.LayoutParams params = _contentContainer.getLayoutParams();
        params.width = _secondaryWidth;
        params.height = _secondaryHeight;
        _contentContainer.setLayoutParams(params);
        _contentContainer.setX(_customPopupWindow.getWidth() - _secondaryWidth);
        _contentContainer.setY(0);
        _contentContainer.requestLayout(); // Trigger layout update
        _secondaryFader.fadeIn(true);

        // CRITICAL: Update touch region after switching to secondary menu
        updateTouchRegion();
    }

    /** Switch to primary menu content */
    private void setPrimaryAsContent() {
        _contentContainer.removeAllViews();
        _contentContainer.addView(_primaryMenu);
        ViewGroup.LayoutParams params = _contentContainer.getLayoutParams();
        params.width = _primaryWidth;
        params.height = _primaryHeight;
        _contentContainer.setLayoutParams(params);
        _contentContainer.setX(0);
        _contentContainer.setY(0);
        _contentContainer.requestLayout(); // Trigger layout update
        _primaryFader.fadeIn(false);

        // CRITICAL: Update touch region after switching to primary menu
        updateTouchRegion();
    }

    // ===============================================
    // TOUCH REGION AND POSITIONING
    // ===============================================

    /** Update the touchable region for proper touch handling */
    private void updateTouchRegion() {
        if (_contentContainer != null && mInvocationHandler != null) {
            // Use post to ensure layout is complete
            _contentContainer.post(new Runnable() {
                @Override
                public void run() {
                    if (mInvocationHandler == null) {
                        return; // Handler detached, ignore
                    }
                    mTouchableRegion.setEmpty();
                    Rect bounds = getContentBounds();
                    mTouchableRegion.op(bounds.left, bounds.top, bounds.right, bounds.bottom, Region.Op.UNION);
                    mInvocationHandler.setTouchRegion(mTouchableRegion);
                    Log.d("ClipboardPanel", "Touch region updated: " + bounds.toString());
                }
            });
        }
    }

    /** Get content bounds for touch region calculation */
    private Rect getContentBounds() {
        Rect bounds = new Rect();
        _contentContainer.getGlobalVisibleRect(bounds);
        return bounds;
    }

    /** Show custom popup at specified location */
    private void showCustomPopup(Rect preferredRect) {
        if (_customPopupWindow.isShowing()) {
            return;
        }

        Log.d("ClipboardPanel", "showCustomPopup with mode: " + _menuDisplayMode);

        setupMenuItems();

        int popupWidth = _primaryWidth;
        int popupHeight = _primaryHeight;
        if (_hasOverflow) {
            popupWidth = Math.max(popupWidth, _secondaryWidth);
            popupHeight = Math.max(popupHeight, _secondaryHeight);
        }

        _customPopupWindow.setWidth(popupWidth);
        _customPopupWindow.setHeight(popupHeight);

        resetMenuToCollapsed();

        Rect positionRect = new Rect();
        calculateOptimalPosition(positionRect, popupWidth, popupHeight);

        _customPopupWindow.showAtLocation(
                _editView,
                Gravity.NO_GRAVITY,
                positionRect.left,
                positionRect.top
        );

        // Wait a frame for the view to be attached
        _popupContentHolder.post(new Runnable() {
            @Override
            public void run() {
                configTouch();
                updateTouchRegion();
            }
        });
    }

    /** Reset menu to collapsed state */
    private void resetMenuToCollapsed() {
        _isExpanded = false;
        _isAnimating = false;
        setPrimaryAsContent();
    }

    /** Hide custom popup and clean up */
    private void hideCustomPopup() {
        if (_customPopupWindow.isShowing()) {
            _customPopupWindow.dismiss();
        }
        detachInsetsListener();
    }

    /** Calculate optimal position for the popup */
    private void calculateOptimalPosition(Rect outRect, int width, int height) {
        _caretRect = _editView.getBoundingBox(_editView.getCaretPosition());
        _isSelectionMode = _editView.isSelectMode();

        if (_caretRect == null) {
            outRect.set(_edgeMargin, _edgeMargin, _edgeMargin + width, _edgeMargin + height);
            return;
        }

        int caretX = _caretRect.left;
        int caretY = _caretRect.top;
        int lineHeight = _editView.getLineHeight();
        int screenWidth = _editView.getWidth();
        int screenHeight = _editView.getHeight();

        int actionModeWidth = width;
        int actionModeHeight = height;

        // Center X on caret, clamped
        int optimalX = caretX + (_caretRect.width() / 2) - (actionModeWidth / 2);
        optimalX = Math.max(_edgeMargin, Math.min(optimalX, screenWidth - actionModeWidth - _edgeMargin));

        // Prefer below cursor (downward), fallback above or bottom
        int optimalY = caretY + lineHeight * 2; // Start below
        if (optimalY + actionModeHeight > screenHeight - _edgeMargin) {
            // Fallback to above if no space below
            optimalY = caretY - actionModeHeight - lineHeight;
            if (optimalY < _edgeMargin) {
                // Final fallback to bottom
                optimalY = screenHeight - actionModeHeight - _edgeMargin;
            }
        }
        optimalY = Math.max(_edgeMargin, Math.min(optimalY, screenHeight - actionModeHeight - _edgeMargin));

        outRect.set(optimalX, optimalY, optimalX + actionModeWidth, optimalY + actionModeHeight);
    }

    // ===============================================
    // AUTO-HIDE TIMER METHODS
    // ===============================================

    /** Start auto-hide timer */
    private void startAutoHideTimer() {
        cancelAutoHideTimer();
        _autoHideHandler.postDelayed(_autoHideRunnable, AUTO_HIDE_DELAY);
    }

    /** Restart auto-hide timer */
    private void restartAutoHideTimer() {
        cancelAutoHideTimer();
        _autoHideHandler.postDelayed(_autoHideRunnable, AUTO_HIDE_DELAY);
    }

    /** Cancel auto-hide timer */
    private void cancelAutoHideTimer() {
        _autoHideHandler.removeCallbacks(_autoHideRunnable);
    }

    // ===============================================
    // MENU ACTION HANDLING
    // ===============================================

    /** Handle menu item actions */
    private void handleCustomMenuAction(MenuAction action) {
        switch (action) {
            case SELECT:
                _editView.selectNearestWord();
                break;
            case COPY:
                _editView.copy();
                break;
            case CUT:
                _editView.cut();
                break;
            case PASTE:
                _editView.paste();
                break;
            case SELECT_ALL:
                _editView.selectAll();
                break;
            case SHARE:
                break;
            case GOTO:
                break;
            case DELETE:
                _editView.delete();
                break;
        }
        hide();
        restartAutoHideTimer();
    }

    // ===============================================
    // UTILITY METHODS
    // ===============================================

    /** Check if paste is available */
    private boolean canPaste() {
        ClipboardManager clipboard = (ClipboardManager) _context.getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboard != null && clipboard.hasPrimaryClip();
    }

    /** Check if text is currently selected */
    private boolean isTextSelected() {
        return _editView.isSelectMode();
    }
}

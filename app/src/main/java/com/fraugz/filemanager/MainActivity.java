package com.fraugz.filemanager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.content.ContentUris;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

import android.content.res.ColorStateList;

public class MainActivity extends AppCompatActivity implements FileAdapter.Listener {

    private static class AppChoice {
        final String packageName;
        final String label;
        final Drawable icon;

        AppChoice(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static class IncomingSharedItem {
        final Uri uri;
        final String displayName;

        IncomingSharedItem(Uri uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
        }
    }

    private static class IncomingSharedText {
        final String text;
        final String displayName;

        IncomingSharedText(String text, String displayName) {
            this.text = text;
            this.displayName = displayName;
        }
    }

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSION = 100;
    private static final int REQ_MANAGE_FILES = 101;
    private static final int REQ_SYSTEM_TRASH = 102;
    private static final int SORT_NAME = 0, SORT_DATE = 1, SORT_SIZE = 2;
    private static final long AUTO_RECENT_SCAN_INTERVAL_MS = 60_000L;
    private static final long AUTO_RECENT_LOOKBACK_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final int AUTO_RECENT_MAX_CANDIDATES = 120;
    private static final int AUTO_RECENT_MAX_SCAN_DEPTH = 4;
    private static final int AUTO_RECENT_MAX_VISITED_DIRS = 300;
    private int sortMode = SORT_NAME;
    private boolean isDark;
    private float uiScale = ThemeManager.UI_SCALE_DEFAULT;

    // Current tab
    private static final int TAB_STORAGE = 0, TAB_RECENT = 1;
    private int currentTab = TAB_STORAGE;

    // Views
    private View mainRoot, viewStorage, viewRecent, topActionBar, searchBar;
    private RecyclerView recycler, recyclerRecent;
    private View emptyView, emptyRecentView;
    private View selectionActionsBar;
    private HorizontalScrollView breadcrumbScroll;
    private LinearLayout breadcrumbContainer;
    private EditText searchInput;
    private LinearLayout pasteBar;
    private TextView pasteLabel, pageTitle, recentTitle, searchStatus, cancelSearchBtn, loadingStatusLabel;
    private TextView actionSendLabel, actionOpenWithLabel, actionPlayLabel, actionSelectAllLabel, actionMoveLabel, actionCopyLabel, actionDeleteLabel, actionRenameLabel, actionInfoLabel;
    private ImageView actionSendIcon, actionOpenWithIcon, actionPlayIcon, actionSelectAllIcon, actionMoveIcon, actionCopyIcon, actionDeleteIcon, actionRenameIcon, actionInfoIcon;
    private ProgressBar searchProgress;
    private SwipeRefreshLayout swipeRefresh;
    private ImageButton btnClearRecent;
    private ImageButton btnSelectAllInline;
    private ImageButton btnHomeInline;

    // Custom bottom nav
    private LinearLayout bottomNav, tabRecent, tabStorage;
    private ImageView tabRecentIcon, tabStorageIcon;
    private TextView tabRecentLabel, tabStorageLabel;

    // Adapters
    private FileAdapter adapter;
    private FileAdapter recentListAdapter;
    private final List<FileItem> fileItems = new ArrayList<>();
    private final List<FileItem> recentFileItems = new ArrayList<>();
    private final List<File> recentFilesCache = new ArrayList<>();
    private final Map<String, Long> recentAccessByPath = new HashMap<>();
    private final Map<String, Boolean> recentPinnedByPath = new HashMap<>();

    // State
    private File currentDir;
    private final Stack<File> backStack = new Stack<>();
    private final List<File> clipboardFiles = new ArrayList<>();
    private final List<IncomingSharedItem> incomingSharedItems = new ArrayList<>();
    private final List<IncomingSharedText> incomingSharedTexts = new ArrayList<>();
    private final List<String> pendingSharedTextPayloads = new ArrayList<>();
    private boolean pendingSharedTextConfig = false;
    private boolean clipboardIsCopy = false;
    private volatile boolean recursiveSearchCancelled = false;
    private Thread recursiveSearchThread;
    private int searchRequestId = 0;
    private volatile boolean directoryLoadCancelled = false;
    private Thread directoryLoadThread;
    private int directoryLoadRequestId = 0;
    private final Runnable deferredSearch = this::startRecursiveSearchFromInput;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean tabTransitionRunning = false;
    private boolean tabSwipeTracking = false;
    private boolean tabSwipeActive = false;
    private int tabSwipeFromTab = TAB_STORAGE;
    private int tabSwipeToTab = -1;
    private float tabSwipeStartX = 0f;
    private float tabSwipeStartY = 0f;
    private float tabSwipeLastDx = 0f;
    private long tabSwipeStartTimeMs = 0L;
    private long suppressItemInteractionUntilMs = 0L;
    private String pendingLocateFilePath = null;
    private volatile boolean autoRecentScanRunning = false;
    private long lastAutoRecentScanAtMs = 0L;
    private Thread autoRecentScanThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate start");
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isDark = ThemeManager.getTheme(this) == ThemeManager.THEME_DARK;
        uiScale = ThemeManager.getUiScale(this);
        Log.d(TAG, "isDark=" + isDark);

        bindViews();
        applyThemeColors();
        setupRecyclerViews();
        setupListeners();
        selectTab(TAB_STORAGE);
        handleIncomingShareIntent(getIntent());
        requestPermissions();
        Log.d(TAG, "onCreate complete");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShareIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTab == TAB_RECENT) {
            // Skip reload if we're in selection mode (e.g. returning from share chooser)
            boolean inSelection = recentListAdapter != null && recentListAdapter.isSelectionMode();
            if (!inSelection) loadRecentFiles();
        } else {
            triggerAutoRecentDiscovery(false);
        }
    }

    // ─────────────────────── BIND ────────────────────────────────────

    private void bindViews() {
        mainRoot            = findViewById(R.id.main_root);
        viewStorage         = findViewById(R.id.view_storage);
        viewRecent          = findViewById(R.id.view_recent);
        topActionBar        = findViewById(R.id.top_action_bar);
        recycler            = findViewById(R.id.recycler);
        recyclerRecent      = findViewById(R.id.recycler_recent);
        emptyView           = findViewById(R.id.empty_view);
        emptyRecentView     = findViewById(R.id.empty_recent_view);
        selectionActionsBar = findViewById(R.id.selection_actions_bar);
        breadcrumbScroll    = findViewById(R.id.breadcrumb_scroll);
        breadcrumbContainer = findViewById(R.id.breadcrumb_container);
        searchBar           = findViewById(R.id.search_bar);
        searchInput         = findViewById(R.id.search_input);
        pasteBar            = findViewById(R.id.paste_bar);
        pasteLabel          = findViewById(R.id.paste_label);
        pageTitle           = findViewById(R.id.page_title);
        recentTitle         = findViewById(R.id.recent_title);
        btnClearRecent      = findViewById(R.id.btn_clear_recent);
        btnSelectAllInline  = findViewById(R.id.btn_select_all_inline);
        btnHomeInline       = findViewById(R.id.btn_home_inline);
        searchProgress      = findViewById(R.id.search_progress);
        searchStatus        = findViewById(R.id.search_status);
        cancelSearchBtn     = findViewById(R.id.btn_cancel_search);
        loadingStatusLabel  = findViewById(R.id.loading_status_label);
        actionSendLabel     = findViewById(R.id.action_send_label);
        actionOpenWithLabel = findViewById(R.id.action_open_with_label);
        actionPlayLabel     = findViewById(R.id.action_play_label);
        actionSelectAllLabel = findViewById(R.id.action_select_all_label);
        actionMoveLabel     = findViewById(R.id.action_move_label);
        actionCopyLabel     = findViewById(R.id.action_copy_label);
        actionDeleteLabel   = findViewById(R.id.action_delete_label);
        actionRenameLabel   = findViewById(R.id.action_rename_label);
        actionInfoLabel     = findViewById(R.id.action_info_label);
        actionSendIcon      = findViewById(R.id.action_send_icon);
        actionOpenWithIcon  = findViewById(R.id.action_open_with_icon);
        actionPlayIcon      = findViewById(R.id.action_play_icon);
        actionSelectAllIcon = findViewById(R.id.action_select_all_icon);
        actionMoveIcon      = findViewById(R.id.action_move_icon);
        actionCopyIcon      = findViewById(R.id.action_copy_icon);
        actionDeleteIcon    = findViewById(R.id.action_delete_icon);
        actionRenameIcon    = findViewById(R.id.action_rename_icon);
        actionInfoIcon      = findViewById(R.id.action_info_icon);
        swipeRefresh        = findViewById(R.id.swipe_refresh);
        bottomNav           = findViewById(R.id.bottom_nav);
        tabRecent           = findViewById(R.id.tab_recent);
        tabStorage          = findViewById(R.id.tab_storage);
        tabRecentIcon       = findViewById(R.id.tab_recent_icon);
        tabStorageIcon      = findViewById(R.id.tab_storage_icon);
        tabRecentLabel      = findViewById(R.id.tab_recent_label);
        tabStorageLabel     = findViewById(R.id.tab_storage_label);

        // Validate critical views
        for (int[] pair : new int[][]{{R.id.recycler},{R.id.view_storage},{R.id.bottom_nav}}) {
            if (findViewById(pair[0]) == null)
                Log.e(TAG, "MISSING VIEW: " + getResources().getResourceEntryName(pair[0]));
        }
    }

    // ─────────────────────── THEME ───────────────────────────────────

    private void applyThemeColors() {
        int bgMain      = isDark ? 0xFF000000 : 0xFFF2F2F7;
        int bgCard      = isDark ? 0xFF1C1C1E : 0xFFFFFFFF;
        int textPrimary = isDark ? 0xFFFFFFFF : 0xFF1C1C1E;
        int textSec     = isDark ? 0xFF888888 : 0xFF6B6B6B;
        int accent      = isDark ? 0xFF1E88E5 : 0xFF1565C0;
        int navBg       = isDark ? 0xFF111111 : 0xFFFFFFFF;
        int iconTint    = isDark ? 0xFFFFFFFF : 0xFF1C1C1E;
        int searchBg    = isDark ? 0xFF2C2C2E : 0xFFE5E5EA;
        int pasteBg     = isDark ? 0xFF1A2744 : 0xFFDCEBFF;
        int pasteText   = isDark ? 0xFFD6E0FF : 0xFF1F3A5B;
        int pasteBtn    = isDark ? 0xFF1E88E5 : 0xFF1976D2;

        safeSetBg(mainRoot, bgMain);
        safeSetBg(viewStorage, bgMain);
        safeSetBg(viewRecent, bgMain);
        safeSetBg(topActionBar, bgMain);
        safeSetBg(bottomNav, navBg);
        safeSetBg(selectionActionsBar, navBg);
        safeSetBg(pasteBar, pasteBg);
        if (recycler != null) recycler.setBackgroundColor(bgMain);
        if (recyclerRecent != null) recyclerRecent.setBackgroundColor(bgMain);

        if (pageTitle != null) pageTitle.setTextColor(textPrimary);
        if (recentTitle != null) recentTitle.setTextColor(textPrimary);
        if (btnClearRecent != null) btnClearRecent.setColorFilter(iconTint);

        safeSetBg(searchBar, bgCard);
        if (searchInput != null) {
            searchInput.setTextColor(textPrimary);
            searchInput.setHintTextColor(textSec);
            searchInput.setBackgroundColor(searchBg);
        }
        if (searchStatus != null) searchStatus.setTextColor(textSec);
        if (loadingStatusLabel != null) loadingStatusLabel.setTextColor(textSec);
        if (cancelSearchBtn != null) cancelSearchBtn.setTextColor(accent);
        if (pasteLabel != null) pasteLabel.setTextColor(pasteText);
        View pasteBtnView = findViewById(R.id.btn_paste);
        if (pasteBtnView instanceof android.widget.Button) {
            ((android.widget.Button) pasteBtnView).setBackgroundTintList(ColorStateList.valueOf(pasteBtn));
        }
        if (actionSendLabel != null) actionSendLabel.setTextColor(iconTint);
        if (actionOpenWithLabel != null) actionOpenWithLabel.setTextColor(iconTint);
        if (actionPlayLabel != null) actionPlayLabel.setTextColor(iconTint);
        if (actionSelectAllLabel != null) actionSelectAllLabel.setTextColor(iconTint);
        if (actionMoveLabel != null) actionMoveLabel.setTextColor(iconTint);
        if (actionCopyLabel != null) actionCopyLabel.setTextColor(iconTint);
        if (actionDeleteLabel != null) actionDeleteLabel.setTextColor(iconTint);
        if (actionRenameLabel != null) actionRenameLabel.setTextColor(iconTint);
        if (actionInfoLabel != null) actionInfoLabel.setTextColor(iconTint);
        if (actionSendIcon != null) actionSendIcon.setColorFilter(iconTint);
        if (actionOpenWithIcon != null) actionOpenWithIcon.setColorFilter(iconTint);
        if (actionPlayIcon != null) actionPlayIcon.setColorFilter(iconTint);
        if (actionSelectAllIcon != null) actionSelectAllIcon.setColorFilter(iconTint);
        if (actionMoveIcon != null) actionMoveIcon.setColorFilter(iconTint);
        if (actionCopyIcon != null) actionCopyIcon.setColorFilter(iconTint);
        if (actionDeleteIcon != null) actionDeleteIcon.setColorFilter(iconTint);
        if (actionRenameIcon != null) actionRenameIcon.setColorFilter(iconTint);
        if (actionInfoIcon != null) actionInfoIcon.setColorFilter(iconTint);

        int[] iconBtns = {R.id.btn_search, R.id.btn_filter, R.id.btn_trash, R.id.btn_overflow, R.id.btn_select_all_inline, R.id.btn_new_folder_inline, R.id.btn_home_inline, R.id.btn_clear_recent};
        for (int id : iconBtns) {
            View v = findViewById(id);
            if (v instanceof ImageButton) ((ImageButton) v).setColorFilter(iconTint);
        }

        tintEmptyState(emptyView, textSec);
        tintEmptyState(emptyRecentView, textSec);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(accent);
            swipeRefresh.setProgressBackgroundColorSchemeColor(bgCard);
        }

        applyUiScale();

        // Keep status bar readable in light mode with enough contrast.
        int statusBarColor = isDark ? 0xFF000000 : 0xFFDCE3EC;
        getWindow().setStatusBarColor(statusBarColor);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isDark);
        }
    }

    private void safeSetBg(View v, int color) {
        if (v != null) v.setBackgroundColor(color);
    }

    private void applyUiScale() {
        if (pageTitle != null) pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * uiScale);
        if (recentTitle != null) recentTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * uiScale);
        if (searchInput != null) searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * uiScale);
        if (pasteLabel != null) pasteLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * uiScale);
        if (tabRecentLabel != null) tabRecentLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * uiScale);
        if (tabStorageLabel != null) tabStorageLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * uiScale);
        if (searchStatus != null) searchStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (loadingStatusLabel != null) loadingStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (cancelSearchBtn != null) cancelSearchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * uiScale);
        if (actionSendLabel != null) actionSendLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionOpenWithLabel != null) actionOpenWithLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionPlayLabel != null) actionPlayLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionSelectAllLabel != null) actionSelectAllLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionMoveLabel != null) actionMoveLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionCopyLabel != null) actionCopyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionDeleteLabel != null) actionDeleteLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionRenameLabel != null) actionRenameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);
        if (actionInfoLabel != null) actionInfoLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * uiScale);

        int iconSize = dp(44f * uiScale);
        int[] topIconButtons = {R.id.btn_search, R.id.btn_filter, R.id.btn_trash, R.id.btn_overflow, R.id.btn_clear_recent};
        for (int id : topIconButtons) {
            View v = findViewById(id);
            setSquareSize(v, iconSize);
        }

        setSquareSize(findViewById(R.id.btn_new_folder_inline), dp(38f * uiScale));
        setSquareSize(findViewById(R.id.btn_select_all_inline), dp(38f * uiScale));
        setSquareSize(findViewById(R.id.btn_home_inline), dp(38f * uiScale));
        setSquareSize(findViewById(R.id.btn_paste_dismiss), dp(36f * uiScale));
        setSquareSize(actionSendIcon, dp(24f * uiScale));
        setSquareSize(actionOpenWithIcon, dp(24f * uiScale));
        setSquareSize(actionPlayIcon, dp(24f * uiScale));
        setSquareSize(actionSelectAllIcon, dp(24f * uiScale));
        setSquareSize(actionMoveIcon, dp(24f * uiScale));
        setSquareSize(actionCopyIcon, dp(24f * uiScale));
        setSquareSize(actionDeleteIcon, dp(24f * uiScale));
        setSquareSize(actionRenameIcon, dp(24f * uiScale));
        setSquareSize(actionInfoIcon, dp(24f * uiScale));

        if (adapter != null) adapter.setUiScale(uiScale);
        if (recentListAdapter != null) recentListAdapter.setUiScale(uiScale);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()));
    }

    private void setSquareSize(View v, int sizePx) {
        if (v == null) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        if (lp.width != sizePx || lp.height != sizePx) {
            lp.width = sizePx;
            lp.height = sizePx;
            v.setLayoutParams(lp);
        }
    }

    private void tintEmptyState(View parent, int color) {
        if (!(parent instanceof LinearLayout)) return;
        for (int i = 0; i < ((LinearLayout) parent).getChildCount(); i++) {
            View c = ((LinearLayout) parent).getChildAt(i);
            if (c instanceof TextView) ((TextView) c).setTextColor(color);
        }
    }

    // ─────────────────────── CUSTOM BOTTOM NAV ───────────────────────

    private void selectTab(int tab) {
        currentTab = tab;
        updateTabVisualState(tab);

        if (tab == TAB_STORAGE) showStorageView();
        else                    showRecentView();
    }

    private void updateTabVisualState(int tab) {
        int accent   = isDark ? 0xFF1E88E5 : 0xFF1565C0;
        int inactive = isDark ? 0xFF666666 : 0xFF999999;
        int navBg    = isDark ? 0xFF111111 : 0xFFFFFFFF;

        safeSetBg(bottomNav, navBg);

        boolean storageActive = (tab == TAB_STORAGE);

        if (tabStorageIcon != null) tabStorageIcon.setColorFilter(storageActive ? accent : inactive);
        if (tabStorageLabel != null) tabStorageLabel.setTextColor(storageActive ? accent : inactive);
        if (tabRecentIcon != null)   tabRecentIcon.setColorFilter(storageActive ? inactive : accent);
        if (tabRecentLabel != null)  tabRecentLabel.setTextColor(storageActive ? inactive : accent);

        if (!storageActive && adapter != null && adapter.isSelectionMode()) {
            exitSelectionMode();
        }
        if (storageActive && recentListAdapter != null && recentListAdapter.isSelectionMode()) {
            exitSelectionMode();
        }
    }

    private void animateToTabBySwipe(int tab, boolean swipeLeft) {
        if (tabTransitionRunning || tab == currentTab) {
            selectTab(tab);
            return;
        }

        int fromTab = currentTab;
        View fromView = fromTab == TAB_STORAGE ? viewStorage : viewRecent;
        View toView = tab == TAB_STORAGE ? viewStorage : viewRecent;
        if (fromView == null || toView == null) {
            selectTab(tab);
            return;
        }

        currentTab = tab;
        updateTabVisualState(tab);

        View sortBtn = findViewById(R.id.btn_filter);
        if (sortBtn != null) sortBtn.setVisibility(tab == TAB_STORAGE ? View.VISIBLE : View.GONE);
        View trashBtn = findViewById(R.id.btn_trash);
        if (trashBtn != null) trashBtn.setVisibility(tab == TAB_STORAGE ? View.VISIBLE : View.GONE);
        if (tab == TAB_RECENT) loadRecentFiles();

        float width = mainRoot != null ? mainRoot.getWidth() : 0f;
        if (width <= 0f) width = fromView.getWidth();
        if (width <= 0f) width = getResources().getDisplayMetrics().widthPixels;
        float offset = swipeLeft ? -width : width;

        tabTransitionRunning = true;
        toView.setVisibility(View.VISIBLE);
        toView.setTranslationX(-offset);
        toView.setAlpha(0.92f);

        fromView.animate()
                .translationX(offset)
                .alpha(0.70f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        toView.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    fromView.setVisibility(View.GONE);
                    fromView.setTranslationX(0f);
                    fromView.setAlpha(1f);
                    toView.setTranslationX(0f);
                    toView.setAlpha(1f);
                    tabTransitionRunning = false;
                })
                .start();
    }

    // ─────────────────────── SETUP ───────────────────────────────────

    private void setupRecyclerViews() {
        if (recycler != null) {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
            recycler.setVerticalScrollBarEnabled(true);
            adapter = new FileAdapter(fileItems, this);
            adapter.setDarkTheme(isDark);
            adapter.setUiScale(uiScale);
            recycler.setAdapter(adapter);
        }

        if (recyclerRecent != null) {
            recyclerRecent.setLayoutManager(new LinearLayoutManager(this));
            recyclerRecent.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
            recyclerRecent.setVerticalScrollBarEnabled(true);
            recentListAdapter = new FileAdapter(recentFileItems, new FileAdapter.Listener() {
                @Override
                public void onItemClick(FileItem item) {
                    MainActivity.this.onItemClick(item);
                }

                @Override
                public void onItemLongClick(FileItem item) {
                    MainActivity.this.onItemLongClick(item);
                }

                @Override
                public void onMoreClick(FileItem item, View anchor) {
                    MainActivity.this.onMoreClick(item, anchor);
                }
            });
            recentListAdapter.setDarkTheme(isDark);
            recentListAdapter.setUiScale(uiScale);
            recentListAdapter.setRecentMode(true);
            recyclerRecent.setAdapter(recentListAdapter);
        }
    }

    private void setupListeners() {
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                try { if (currentDir != null) loadDirectory(currentDir); }
                catch (Exception e) { Log.e(TAG, "refresh error", e); }
                finally { swipeRefresh.setRefreshing(false); }
            });
        }

        setClickSafe(R.id.btn_search,            v -> toggleSearch());
        setClickSafe(R.id.btn_filter,            v -> showSortMenu(v));
        setClickSafe(R.id.btn_trash,             v -> startActivity(new Intent(this, TrashActivity.class)));
        setClickSafe(R.id.btn_overflow,          v -> startActivity(new Intent(this, SettingsActivity.class)));
        setClickSafe(R.id.btn_clear_recent,      v -> confirmClearRecents());
        setClickSafe(R.id.btn_select_all_inline, v -> toggleSelectAllInline());
        setClickSafe(R.id.btn_new_folder_inline, v -> showNewFolderDialog());
        setClickSafe(R.id.btn_home_inline, v -> navigateToStorageRoot());
        setClickSafe(R.id.btn_paste,             v -> pasteClipboard());
        setClickSafe(R.id.btn_paste_dismiss,     v -> {
            clipboardFiles.clear();
            incomingSharedItems.clear();
            incomingSharedTexts.clear();
            pendingSharedTextPayloads.clear();
            pendingSharedTextConfig = false;
            updatePasteBar();
        });
        setClickSafe(R.id.action_send,           v -> handlePrimaryAction());
        setClickSafe(R.id.action_open_with,      v -> handleOpenAction());
        setClickSafe(R.id.action_play,           v -> handleSecondaryAction());
        setClickSafe(R.id.action_move,           v -> markSelectionForMove());
        setClickSafe(R.id.action_copy,           v -> copySelection());
        setClickSafe(R.id.action_delete,         v -> handleDeleteAction());
        setClickSafe(R.id.action_rename,         v -> renameSelection());
        setClickSafe(R.id.action_info,           v -> { if (currentTab == TAB_RECENT) togglePinForSelectedRecent(); else showInfoForSelection(); });

        setupSwipeNavigation();

        if (cancelSearchBtn != null) {
            cancelSearchBtn.setOnClickListener(v -> cancelRecursiveSearch(true));
        }

        if (tabStorage != null) tabStorage.setOnClickListener(v -> selectTab(TAB_STORAGE));
        if (tabRecent != null)  tabRecent.setOnClickListener(v -> selectTab(TAB_RECENT));

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    try { filterFiles(s.toString()); } catch (Exception e) { Log.e(TAG, "filter", e); }
                }
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupSwipeNavigation() {
        // Swipe gesture is handled in dispatchTouchEvent with finger-following animation.
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        handleInteractiveTabSwipe(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void handleInteractiveTabSwipe(MotionEvent ev) {
        if (ev == null || tabTransitionRunning) return;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tabSwipeTracking = true;
                tabSwipeActive = false;
                tabSwipeToTab = -1;
                tabSwipeFromTab = currentTab;
                tabSwipeStartX = ev.getX();
                tabSwipeStartY = ev.getY();
                tabSwipeLastDx = 0f;
                tabSwipeStartTimeMs = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!tabSwipeTracking) break;
                float dx = ev.getX() - tabSwipeStartX;
                float dy = ev.getY() - tabSwipeStartY;
                if (!tabSwipeActive) {
                    if (Math.abs(dx) < dp(12)) break;
                    if (Math.abs(dx) <= Math.abs(dy)) {
                        tabSwipeTracking = false;
                        break;
                    }
                    if (tabSwipeFromTab == TAB_STORAGE && dx > 0) {
                        tabSwipeToTab = TAB_RECENT;
                    } else if (tabSwipeFromTab == TAB_RECENT && dx < 0) {
                        tabSwipeToTab = TAB_STORAGE;
                    } else {
                        tabSwipeTracking = false;
                        break;
                    }
                    prepareInteractiveTabSwipe();
                    tabSwipeActive = true;
                    suppressItemInteractionUntilMs = System.currentTimeMillis() + 250L;
                }
                if (tabSwipeActive) {
                    applyInteractiveTabSwipe(dx);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tabSwipeActive) {
                    finishInteractiveTabSwipe();
                    suppressItemInteractionUntilMs = System.currentTimeMillis() + 250L;
                }
                tabSwipeTracking = false;
                break;
        }
    }

    private boolean shouldBlockItemInteraction() {
        if (tabTransitionRunning || tabSwipeActive) return true;
        return System.currentTimeMillis() < suppressItemInteractionUntilMs;
    }

    private void prepareInteractiveTabSwipe() {
        View toView = tabSwipeToTab == TAB_STORAGE ? viewStorage : viewRecent;
        if (toView == null) return;
        toView.setVisibility(View.VISIBLE);
        toView.setAlpha(0.92f);
        tabSwipeLastDx = 0f;
    }

    private void applyInteractiveTabSwipe(float rawDx) {
        View fromView = tabSwipeFromTab == TAB_STORAGE ? viewStorage : viewRecent;
        View toView = tabSwipeToTab == TAB_STORAGE ? viewStorage : viewRecent;
        if (fromView == null || toView == null) return;

        float width = mainRoot != null ? mainRoot.getWidth() : 0f;
        if (width <= 0f) width = getResources().getDisplayMetrics().widthPixels;

        float dx = rawDx;
        if (tabSwipeFromTab == TAB_STORAGE && tabSwipeToTab == TAB_RECENT) {
            dx = Math.max(0f, Math.min(width, dx));
            fromView.setTranslationX(dx);
            toView.setTranslationX(dx - width);
        } else if (tabSwipeFromTab == TAB_RECENT && tabSwipeToTab == TAB_STORAGE) {
            dx = Math.min(0f, Math.max(-width, dx));
            fromView.setTranslationX(dx);
            toView.setTranslationX(dx + width);
        }

        float progress = Math.min(1f, Math.abs(dx) / Math.max(1f, width));
        fromView.setAlpha(1f - (0.25f * progress));
        toView.setAlpha(0.92f + (0.08f * progress));
        tabSwipeLastDx = dx;
    }

    private void finishInteractiveTabSwipe() {
        View fromView = tabSwipeFromTab == TAB_STORAGE ? viewStorage : viewRecent;
        View toView = tabSwipeToTab == TAB_STORAGE ? viewStorage : viewRecent;
        if (fromView == null || toView == null) {
            tabSwipeActive = false;
            return;
        }

        float width = mainRoot != null ? mainRoot.getWidth() : 0f;
        if (width <= 0f) width = getResources().getDisplayMetrics().widthPixels;
        float progress = Math.min(1f, Math.abs(tabSwipeLastDx) / Math.max(1f, width));

        long elapsed = Math.max(1L, System.currentTimeMillis() - tabSwipeStartTimeMs);
        float velocity = (tabSwipeLastDx / elapsed) * 1000f;
        boolean commit = progress >= 0.35f || Math.abs(velocity) >= 900f;

        tabTransitionRunning = true;

        if (commit) {
            float finalFromX = tabSwipeFromTab == TAB_STORAGE ? width : -width;
            fromView.animate()
                    .translationX(finalFromX)
                    .alpha(0.72f)
                    .setDuration(160)
                    .withEndAction(() -> {
                        fromView.setVisibility(View.GONE);
                        fromView.setTranslationX(0f);
                        fromView.setAlpha(1f);
                    })
                    .start();

            toView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(160)
                    .withEndAction(() -> {
                        currentTab = tabSwipeToTab;
                        updateTabVisualState(currentTab);
                        if (currentTab == TAB_RECENT) {
                            showRecentView();
                        } else {
                            showStorageView();
                        }
                        tabTransitionRunning = false;
                        tabSwipeActive = false;
                    })
                    .start();
        } else {
            fromView.animate().translationX(0f).alpha(1f).setDuration(160).start();
            toView.animate()
                    .translationX(tabSwipeFromTab == TAB_STORAGE ? -width : width)
                    .alpha(0.92f)
                    .setDuration(160)
                    .withEndAction(() -> {
                        toView.setVisibility(View.GONE);
                        toView.setTranslationX(0f);
                        toView.setAlpha(1f);
                        tabTransitionRunning = false;
                        tabSwipeActive = false;
                    })
                    .start();
        }
    }

    private void setClickSafe(int id, View.OnClickListener l) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(v2 -> {
            try { l.onClick(v2); } catch (Exception e) {
                Log.e(TAG, "click error " + getResources().getResourceEntryName(id), e);
                toast(getString(R.string.error_with_reason, e.getMessage()));
            }
        });
    }

    // ─────────────────────── VIEWS ───────────────────────────────────

    private void showStorageView() {
        if (viewStorage != null) viewStorage.setVisibility(View.VISIBLE);
        if (viewRecent != null)  viewRecent.setVisibility(View.GONE);
        View sortBtn = findViewById(R.id.btn_filter);
        if (sortBtn != null) sortBtn.setVisibility(View.VISIBLE);
        View trashBtn = findViewById(R.id.btn_trash);
        if (trashBtn != null) trashBtn.setVisibility(View.VISIBLE);
    }

    private void showRecentView() {
        if (viewStorage != null) viewStorage.setVisibility(View.GONE);
        if (viewRecent != null)  viewRecent.setVisibility(View.VISIBLE);
        View sortBtn = findViewById(R.id.btn_filter);
        if (sortBtn != null) sortBtn.setVisibility(View.GONE);
        View trashBtn = findViewById(R.id.btn_trash);
        if (trashBtn != null) trashBtn.setVisibility(View.GONE);
        loadRecentFiles();
    }

    private void loadRecentFiles() {
        try {
            List<RecentManager.RecentEntry> entries = RecentManager.getEntries(this);
            List<File> files = new ArrayList<>();
            recentAccessByPath.clear();
            recentPinnedByPath.clear();
            for (RecentManager.RecentEntry entry : entries) {
                File f = new File(entry.path);
                if (f.exists() && (f.isFile() || entry.isPinned)) {
                    files.add(f);
                    recentAccessByPath.put(f.getAbsolutePath(), entry.accessedAt);
                    recentPinnedByPath.put(f.getAbsolutePath(), entry.isPinned);
                }
            }

            Collections.sort(files, (a, b) -> {
                Long ta = recentAccessByPath.get(a.getAbsolutePath());
                Long tb = recentAccessByPath.get(b.getAbsolutePath());
                long va = ta != null ? ta : a.lastModified();
                long vb = tb != null ? tb : b.lastModified();
                return Long.compare(vb, va);
            });

            recentFilesCache.clear();
            recentFilesCache.addAll(files);

            String q = searchInput != null && searchBar != null && searchBar.getVisibility() == View.VISIBLE
                    ? searchInput.getText().toString().trim()
                    : "";
            if (!q.isEmpty()) {
                applyRecentFilter(q);
                return;
            }

            syncRecentItemsWithFiles(files);
            if (recentListAdapter != null) {
                recentListAdapter.setRecentMetadata(recentAccessByPath, recentPinnedByPath);
            }

            boolean empty = files.isEmpty();
            if (recyclerRecent != null)  recyclerRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (emptyRecentView != null) emptyRecentView.setVisibility(empty ? View.VISIBLE : View.GONE);
            updateClearRecentsButtonState(!files.isEmpty());
            if (!empty && recentListAdapter != null) recentListAdapter.notifyDataSetChanged();
            triggerAutoRecentDiscovery(false);
        } catch (Exception e) { Log.e(TAG, "loadRecentFiles", e); }
    }

    private void triggerAutoRecentDiscovery(boolean force) {
        if (!hasStorageReadAccess()) return;
        if (autoRecentScanRunning) return;

        long now = System.currentTimeMillis();
        if (!force && (now - lastAutoRecentScanAtMs) < AUTO_RECENT_SCAN_INTERVAL_MS) {
            return;
        }

        autoRecentScanRunning = true;
        lastAutoRecentScanAtMs = now;

        autoRecentScanThread = new Thread(() -> {
            try {
                List<File> roots = getAutoRecentScanRoots();
                if (roots.isEmpty()) return;

                List<File> candidates = new ArrayList<>();
                int[] visitedDirs = new int[]{0};
                long since = System.currentTimeMillis() - AUTO_RECENT_LOOKBACK_MS;
                for (File root : roots) {
                    collectAutoRecentCandidates(root, 0, since, visitedDirs, candidates);
                    if (candidates.size() >= AUTO_RECENT_MAX_CANDIDATES) break;
                    if (visitedDirs[0] >= AUTO_RECENT_MAX_VISITED_DIRS) break;
                }

                if (candidates.isEmpty()) return;

                Collections.sort(candidates, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                if (candidates.size() > AUTO_RECENT_MAX_CANDIDATES) {
                    candidates = new ArrayList<>(candidates.subList(0, AUTO_RECENT_MAX_CANDIDATES));
                }

                boolean changed = RecentManager.mergeAutoDiscovered(MainActivity.this, candidates);
                if (changed) {
                    mainHandler.post(() -> {
                        if (currentTab == TAB_RECENT) {
                            loadRecentFiles();
                        }
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "auto recent discovery failed", e);
            } finally {
                autoRecentScanRunning = false;
                autoRecentScanThread = null;
            }
        }, "auto-recent-scan");
        autoRecentScanThread.start();
    }

    private List<File> getAutoRecentScanRoots() {
        List<File> roots = new ArrayList<>();
        addScanRoot(roots, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        addScanRoot(roots, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));

        File storageRoot = Environment.getExternalStorageDirectory();
        if (storageRoot != null) {
            addScanRoot(roots, new File(storageRoot, "Download/WhatsApp Documents"));
            addScanRoot(roots, new File(storageRoot, "WhatsApp/Media/WhatsApp Documents"));
            addScanRoot(roots, new File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"));
        }
        return roots;
    }

    private void addScanRoot(List<File> roots, File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        String abs = dir.getAbsolutePath();
        for (File current : roots) {
            if (abs.equals(current.getAbsolutePath())) return;
        }
        roots.add(dir);
    }

    private void collectAutoRecentCandidates(File dir,
                                             int depth,
                                             long since,
                                             int[] visitedDirs,
                                             List<File> out) {
        if (dir == null || out == null || visitedDirs == null) return;
        if (depth > AUTO_RECENT_MAX_SCAN_DEPTH) return;
        if (visitedDirs[0] >= AUTO_RECENT_MAX_VISITED_DIRS) return;
        if (out.size() >= AUTO_RECENT_MAX_CANDIDATES) return;
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) return;

        visitedDirs[0]++;
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) return;

        for (File child : children) {
            if (child == null) continue;
            String name = child.getName();
            if (name == null || name.startsWith(".")) continue;

            if (child.isFile()) {
                if (child.lastModified() >= since) {
                    out.add(child);
                    if (out.size() >= AUTO_RECENT_MAX_CANDIDATES) return;
                }
                continue;
            }

            if (child.isDirectory()) {
                collectAutoRecentCandidates(child, depth + 1, since, visitedDirs, out);
                if (out.size() >= AUTO_RECENT_MAX_CANDIDATES) return;
                if (visitedDirs[0] >= AUTO_RECENT_MAX_VISITED_DIRS) return;
            }
        }
    }

    private boolean hasStorageReadAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ─────────────────────── PERMISSIONS ─────────────────────────────

    private void requestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    loadRootDirectory();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.required_permission)
                        .setMessage(R.string.full_storage_permission_message)
                        .setPositiveButton(R.string.allow, (d, w) -> {
                            try {
                                startActivityForResult(new Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName())), REQ_MANAGE_FILES);
                            } catch (Exception e) { loadRootDirectory(); }
                        })
                        .setNegativeButton(R.string.read_only, (d, w) -> loadRootDirectory())
                        .show();
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMISSION);
                } else {
                    loadRootDirectory();
                }
            }
        } catch (Exception e) { Log.e(TAG, "requestPermissions", e); loadRootDirectory(); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_MANAGE_FILES) loadRootDirectory();
        if (req == REQ_SYSTEM_TRASH) {
            if (res == RESULT_OK) {
                loadDirectory(currentDir);
                loadRecentFiles();
                toast(getString(R.string.moved_to_trash_done));
            }
            exitSelectionMode();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == REQ_PERMISSION) loadRootDirectory();
    }

    // ─────────────────────── FILE LOADING ────────────────────────────

    private void loadRootDirectory() {
        try {
            File root = Environment.getExternalStorageDirectory();
            Log.d(TAG, "root=" + root + " exists=" + root.exists() + " canRead=" + root.canRead());
            loadDirectory(root);
        } catch (Exception e) {
            Log.e(TAG, "loadRootDirectory", e);
            toast(getString(R.string.error_loading_storage, e.getMessage()));
        }
    }

    private void loadDirectory(File dir) {
        try {
            cancelRecursiveSearch(false);
            cancelDirectoryLoad();
            Log.d(TAG, "loadDirectory: " + dir.getAbsolutePath());
            currentDir = dir;

            fileItems.clear();
            if (adapter != null) adapter.notifyDataSetChanged();

            // Open immediately and stream files progressively.
            if (swipeRefresh != null) {
                swipeRefresh.setVisibility(View.VISIBLE);
                swipeRefresh.setRefreshing(true);
            }
            if (emptyView != null) emptyView.setVisibility(View.GONE);
            showLoadingStatus(getString(R.string.loading));

            updateBreadcrumb();
            updatePasteBar();
            updateSelectionActionsBar();

            final int requestId = ++directoryLoadRequestId;
            directoryLoadCancelled = false;
            directoryLoadThread = new Thread(() -> loadDirectoryIncrementally(dir, requestId), "directory-loader");
            directoryLoadThread.start();
        } catch (Exception e) {
            Log.e(TAG, "loadDirectory error", e);
            toast(getString(R.string.error_with_reason, e.getMessage()));
        }
    }

    private void loadDirectoryIncrementally(File dir, int requestId) {
        try {
            File[] files = dir.listFiles();
            Log.d(TAG, "files=" + (files == null ? "null" : files.length));
            if (directoryLoadCancelled || requestId != directoryLoadRequestId) return;

            List<FileItem> loaded = new ArrayList<>();
            final int batchSize = 80;
            List<FileItem> pendingBatch = new ArrayList<>(batchSize);

            if (files != null) {
                for (File f : files) {
                    if (directoryLoadCancelled || requestId != directoryLoadRequestId) return;
                    if (f.getName().startsWith(".")) continue;

                    FileItem item = new FileItem(f);
                    loaded.add(item);
                    pendingBatch.add(item);

                    if (pendingBatch.size() >= batchSize) {
                        List<FileItem> toPublish = new ArrayList<>(pendingBatch);
                        pendingBatch.clear();
                        publishDirectoryBatch(toPublish, requestId);
                    }
                }
            }

            if (!pendingBatch.isEmpty()) {
                publishDirectoryBatch(new ArrayList<>(pendingBatch), requestId);
            }

            if (directoryLoadCancelled || requestId != directoryLoadRequestId) return;

            // Keep user-selected sorting once full load has arrived.
            List<FileItem> sorted = new ArrayList<>(loaded);
            Comparator<File> comp = getComparator();
            Collections.sort(sorted, (a, b) -> comp.compare(a.getFile(), b.getFile()));

            mainHandler.post(() -> {
                if (directoryLoadCancelled || requestId != directoryLoadRequestId) return;
                fileItems.clear();
                fileItems.addAll(sorted);
                applyPendingLocateSelection();
                if (adapter != null) adapter.notifyDataSetChanged();
                finishDirectoryLoading();
            });
        } catch (Exception e) {
            Log.e(TAG, "loadDirectoryIncrementally", e);
            mainHandler.post(() -> {
                if (requestId == directoryLoadRequestId) {
                    finishDirectoryLoading();
                    toast(getString(R.string.error_loading_folder, e.getMessage()));
                }
            });
        }
    }

    private void publishDirectoryBatch(List<FileItem> batch, int requestId) {
        mainHandler.post(() -> {
            if (directoryLoadCancelled || requestId != directoryLoadRequestId) return;
            int start = fileItems.size();
            fileItems.addAll(batch);
            if (adapter != null) adapter.notifyItemRangeInserted(start, batch.size());
            if (emptyView != null) emptyView.setVisibility(View.GONE);
            if (swipeRefresh != null) swipeRefresh.setVisibility(View.VISIBLE);
            showLoadingStatus(getString(R.string.loading) + " " + fileItems.size());
        });
    }

    private void finishDirectoryLoading() {
        boolean empty = fileItems.isEmpty();
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
            swipeRefresh.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        if (emptyView != null) emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        hideLoadingStatus();
    }

    private void cancelDirectoryLoad() {
        directoryLoadCancelled = true;
        directoryLoadRequestId++;
        if (directoryLoadThread != null) {
            directoryLoadThread.interrupt();
            directoryLoadThread = null;
        }
        hideLoadingStatus();
    }

    private void showLoadingStatus(String text) {
        if (loadingStatusLabel == null) return;
        loadingStatusLabel.setText(text);
        loadingStatusLabel.setVisibility(View.VISIBLE);
    }

    private void hideLoadingStatus() {
        if (loadingStatusLabel == null) return;
        loadingStatusLabel.setVisibility(View.GONE);
    }

    private Comparator<File> getComparator() {
        return (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            switch (sortMode) {
                case SORT_DATE: return Long.compare(b.lastModified(), a.lastModified());
                case SORT_SIZE: return Long.compare(a.isFile() ? b.length() : 0, b.isFile() ? a.length() : 0);
                default:        return a.getName().compareToIgnoreCase(b.getName());
            }
        };
    }

    private void filterFiles(String q) {
        String query = q == null ? "" : q.trim();

        if (currentTab == TAB_RECENT) {
            cancelRecursiveSearch(false);
            if (query.isEmpty()) {
                loadRecentFiles();
            } else {
                applyRecentFilter(query);
            }
            return;
        }

        if (query.isEmpty()) {
            cancelRecursiveSearch(false);
            if (currentDir != null) loadDirectory(currentDir);
            return;
        }
        scheduleRecursiveSearch();
    }

    private void scheduleRecursiveSearch() {
        mainHandler.removeCallbacks(deferredSearch);
        mainHandler.postDelayed(deferredSearch, 250);
    }

    private void startRecursiveSearchFromInput() {
        if (currentTab != TAB_STORAGE) return;
        if (searchInput == null || currentDir == null) return;
        String query = searchInput.getText() != null ? searchInput.getText().toString().trim() : "";
        if (query.isEmpty()) {
            cancelRecursiveSearch(false);
            return;
        }
        startRecursiveSearch(query);
    }

    private void applyRecentFilter(String query) {
        if (recentListAdapter == null) return;
        String q = query.toLowerCase();
        List<File> filtered = new ArrayList<>();
        for (File f : recentFilesCache) {
            if (f.getName().toLowerCase().contains(q)) {
                filtered.add(f);
            }
        }

        Collections.sort(filtered, (a, b) -> {
            Long ta = recentAccessByPath.get(a.getAbsolutePath());
            Long tb = recentAccessByPath.get(b.getAbsolutePath());
            long va = ta != null ? ta : a.lastModified();
            long vb = tb != null ? tb : b.lastModified();
            return Long.compare(vb, va);
        });

        syncRecentItemsWithFiles(filtered);
        if (recentListAdapter != null) {
            recentListAdapter.setRecentMetadata(recentAccessByPath, recentPinnedByPath);
        }

        boolean empty = filtered.isEmpty();
        if (recyclerRecent != null) recyclerRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyRecentView != null) emptyRecentView.setVisibility(empty ? View.VISIBLE : View.GONE);
        updateClearRecentsButtonState(!recentFilesCache.isEmpty());
        if (!empty) recentListAdapter.notifyDataSetChanged();
    }

    private void updateClearRecentsButtonState(boolean hasItems) {
        if (btnClearRecent == null) return;
        btnClearRecent.setEnabled(hasItems);
        btnClearRecent.setAlpha(hasItems ? 1f : 0.35f);
    }

    private void confirmClearRecents() {
        if (recentFilesCache.isEmpty()) {
            updateClearRecentsButtonState(false);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_clear_title)
                .setMessage(R.string.confirm_clear_recent_message)
                .setPositiveButton(R.string.clear_all, (d, w) -> {
                    RecentManager.clearUnpinned(this);
                    loadRecentFiles();
                    toast(getString(R.string.recents_cleared));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startRecursiveSearch(String query) {
        cancelRecursiveSearch(false);
        cancelDirectoryLoad();
        final int requestId = ++searchRequestId;
        recursiveSearchCancelled = false;
        showSearchProgress(true, getString(R.string.searching));

        final String q = query.toLowerCase();
        final File rootDir = currentDir;
        recursiveSearchThread = new Thread(() -> {
            List<FileItem> results = new ArrayList<>();
            int[] scanned = new int[]{0};
            recursiveCollect(rootDir, q, results, scanned, requestId);
            if (recursiveSearchCancelled || requestId != searchRequestId) return;

            mainHandler.post(() -> {
                if (recursiveSearchCancelled || requestId != searchRequestId) return;
                fileItems.clear();
                fileItems.addAll(results);
                if (adapter != null) adapter.notifyDataSetChanged();
                boolean empty = fileItems.isEmpty();
                if (swipeRefresh != null) swipeRefresh.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (emptyView != null)    emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                showSearchProgress(false, "");
            });
        }, "recursive-search");
        recursiveSearchThread.start();
    }

    private void recursiveCollect(File dir, String query, List<FileItem> out, int[] scanned, int requestId) {
        if (dir == null || recursiveSearchCancelled || requestId != searchRequestId) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        Arrays.sort(files, getComparator());
        for (File f : files) {
            if (recursiveSearchCancelled || requestId != searchRequestId) return;
            if (f.getName().startsWith(".")) continue;

            scanned[0]++;
            if (f.getName().toLowerCase().contains(query)) {
                out.add(new FileItem(f));
            }

            if (scanned[0] % 120 == 0) {
                int currentScanned = scanned[0];
                mainHandler.post(() -> {
                    if (!recursiveSearchCancelled && requestId == searchRequestId) {
                        showSearchProgress(true, getString(R.string.searching) + " " + currentScanned);
                    }
                });
            }

            if (f.isDirectory()) {
                recursiveCollect(f, query, out, scanned, requestId);
            }
        }
    }

    private void showSearchProgress(boolean searching, String status) {
        if (searchProgress != null) searchProgress.setVisibility(searching ? View.VISIBLE : View.GONE);
        if (searchStatus != null) {
            searchStatus.setVisibility(searching ? View.VISIBLE : View.GONE);
            if (searching) searchStatus.setText(status);
        }
        if (cancelSearchBtn != null) cancelSearchBtn.setVisibility(searching ? View.VISIBLE : View.GONE);
    }

    private void cancelRecursiveSearch(boolean notifyUser) {
        recursiveSearchCancelled = true;
        mainHandler.removeCallbacks(deferredSearch);
        searchRequestId++;

        if (recursiveSearchThread != null) {
            recursiveSearchThread.interrupt();
            recursiveSearchThread = null;
        }

        showSearchProgress(false, "");
        if (notifyUser) toast(getString(R.string.search_cancelled));
    }

    // ─────────────────────── BREADCRUMB ──────────────────────────────

    private void updateBreadcrumb() {
        if (currentDir == null || breadcrumbScroll == null) return;
        try {
            File root = Environment.getExternalStorageDirectory();
            boolean isRoot = currentDir.equals(root);
            breadcrumbScroll.setVisibility(isRoot ? View.GONE : View.VISIBLE);
            if (isRoot || breadcrumbContainer == null) return;

            breadcrumbContainer.removeAllViews();
            List<File> parts = new ArrayList<>();
            File f = currentDir;
            while (f != null && !f.equals(root.getParentFile())) {
                parts.add(0, f);
                if (f.equals(root)) break;
                f = f.getParentFile();
            }

            breadcrumbContainer.setOrientation(LinearLayout.VERTICAL);

            final int maxLineChars = 46;
            int currentLineChars = 0;
            LinearLayout currentRow = new LinearLayout(this);
            currentRow.setOrientation(LinearLayout.HORIZONTAL);
            currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            breadcrumbContainer.addView(currentRow);

            int accent = isDark ? 0xFF8EB3D8 : 0xFF2A5B87;
            int muted = isDark ? 0xFFB7C2CF : 0xFF526478;

            for (int i = 0; i < parts.size(); i++) {
                final File part = parts.get(i);
                boolean isLast = (i == parts.size() - 1);
                String label = part.equals(root) ? getString(R.string.internal_short) : part.getName();
                String segmentText = isLast ? label : (label + "  ›  ");

                int segmentChars = segmentText.length();
                if (currentLineChars > 0 && currentLineChars + segmentChars > maxLineChars) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    breadcrumbContainer.addView(currentRow);
                    currentLineChars = 0;
                }

                TextView tv = new TextView(this);
                tv.setText(segmentText);
                tv.setTextSize(13f);
                tv.setTextColor(isLast ? muted : accent);
                tv.setSingleLine(true);
                if (!isLast) {
                    tv.setOnClickListener(v -> navigateTo(part));
                }
                currentRow.addView(tv);
                currentLineChars += segmentChars;
            }
        } catch (Exception e) { Log.e(TAG, "breadcrumb", e); }
    }

    // ─────────────────────── NAVIGATION ──────────────────────────────

    private void navigateTo(File dir) {
        backStack.push(currentDir);
        loadDirectory(dir);
        exitSelectionMode();
    }

    private void navigateToStorageRoot() {
        try {
            backStack.clear();
            File root = Environment.getExternalStorageDirectory();
            if (root != null && root.exists() && root.isDirectory()) {
                loadDirectory(root);
                exitSelectionMode();
            }
        } catch (Exception e) {
            Log.e(TAG, "navigateToStorageRoot", e);
        }
    }

    @Override
    public void onBackPressed() {
        FileAdapter activeAdapter = getActiveAdapter();
        if (activeAdapter != null && activeAdapter.isSelectionMode()) { exitSelectionMode(); return; }
        if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) { toggleSearch(); return; }
        if (!backStack.isEmpty()) { loadDirectory(backStack.pop()); return; }
        super.onBackPressed();
    }

    // ─────────────────────── ADAPTER CALLBACKS ───────────────────────

    @Override
    public void onItemClick(FileItem item) {
        try {
            if (shouldBlockItemInteraction()) {
                return;
            }
            FileAdapter activeAdapter = getActiveAdapter();
            List<FileItem> activeItems = getActiveItems();
            if (activeAdapter != null && activeAdapter.isSelectionMode()) {
                item.setSelected(!item.isSelected());
                activeAdapter.notifyDataSetChanged();
                boolean hasSelected = false;
                for (FileItem fi : activeItems) {
                    if (fi.isSelected()) {
                        hasSelected = true;
                        break;
                    }
                }
                if (!hasSelected) exitSelectionMode();
                else updateSelectionActionsBar();
                return;
            }
            if (item.isDirectory()) {
                if (currentTab == TAB_RECENT) {
                    // Pinned folder in Recents: switch to Storage and navigate into it
                    exitSelectionMode();
                    selectTab(TAB_STORAGE);
                    loadDirectory(item.getFile());
                } else {
                    navigateTo(item.getFile());
                }
            } else {
                RecentManager.add(this, item.getFile().getAbsolutePath());
                openFile(item.getFile());
            }
        } catch (Exception e) { Log.e(TAG, "onItemClick", e); toast(getString(R.string.error_with_reason, e.getMessage())); }
    }

    @Override
    public void onItemLongClick(FileItem item) {
        try {
            if (shouldBlockItemInteraction()) {
                return;
            }
            FileAdapter activeAdapter = getActiveAdapter();
            if (activeAdapter != null && !activeAdapter.isSelectionMode()) activeAdapter.setSelectionMode(true);
            item.setSelected(true);
            if (activeAdapter != null) activeAdapter.notifyDataSetChanged();
            updateSelectionActionsBar();
        } catch (Exception e) { Log.e(TAG, "onItemLongClick", e); }
    }

    @Override
    public void onMoreClick(FileItem item, View anchor) {
        // Per-item overflow actions are disabled; use selection mode menu from top overflow.
    }

    // ─────────────────────── SELECTION ───────────────────────────────

    private void exitSelectionMode() {
        clearSelectionState(adapter, fileItems);
        clearSelectionState(recentListAdapter, recentFileItems);
        updateSelectionActionsBar();
    }

    private List<File> getSelectedFiles() {
        List<File> sel = new ArrayList<>();
        for (FileItem fi : getActiveItems()) if (fi.isSelected()) sel.add(fi.getFile());
        return sel;
    }

    private void updateSelectionActionsBar() {
        FileAdapter activeAdapter = getActiveAdapter();
        List<File> sel = getSelectedFiles();
        boolean selectionMode = activeAdapter != null && activeAdapter.isSelectionMode() && !sel.isEmpty();
        boolean isRecentTab = currentTab == TAB_RECENT;
        boolean hasSingleItem = sel.size() == 1;
        boolean hasSingleFile = hasSingleItem && sel.get(0).isFile();
        boolean showPlay = sel.size() > 1 && areAllPlayableFiles(sel);
        boolean allDirs = isAllDirectories(sel);

        if (selectionActionsBar != null) {
            selectionActionsBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        }

        if (isRecentTab) {
            // RECENTES: Enviar | Abrir/Reproducir | Localizar | Mover | Copiar | Eliminar/Quitar | Renombrar | Fijar/Desfijar
            setVis(R.id.action_send,       selectionMode && !allDirs);
            setVis(R.id.action_open_with,  selectionMode && showPlay); // Reproducir: multi-playable only
            setVis(R.id.action_play,       selectionMode && hasSingleItem); // Localizar: single item only
            setVis(R.id.action_select_all, false);
            setVis(R.id.action_move,       selectionMode);
            setVis(R.id.action_copy,       selectionMode);
            setVis(R.id.action_delete,     selectionMode);
            setVis(R.id.action_rename,     selectionMode && hasSingleItem);
            setVis(R.id.action_info,       selectionMode); // Fijar/Desfijar

            if (actionSendLabel != null)     actionSendLabel.setText(R.string.send);
            if (actionSendIcon != null)      actionSendIcon.setImageResource(R.drawable.ic_action_send);

            if (actionOpenWithLabel != null) actionOpenWithLabel.setText(showPlay ? R.string.play : R.string.open);
            if (actionOpenWithIcon != null)  actionOpenWithIcon.setImageResource(showPlay ? R.drawable.ic_action_play : R.drawable.ic_action_open_with);

            if (actionPlayLabel != null)     actionPlayLabel.setText(R.string.locate);
            if (actionPlayIcon != null)      actionPlayIcon.setImageResource(R.drawable.ic_storage);

            if (actionDeleteLabel != null)   actionDeleteLabel.setText(R.string.delete);

            // Fijar/Desfijar: check pin state of all selected
            boolean allPinned = !sel.isEmpty() && isAllPinned(sel);
            if (actionInfoLabel != null)     actionInfoLabel.setText(allPinned ? R.string.unpin_from_recent : R.string.pin_to_recent);
            if (actionInfoIcon != null)      actionInfoIcon.setImageResource(R.drawable.ic_pin);

        } else {
            // STORAGE: existing behavior + pin for folder selections
            boolean showInfo   = hasSingleItem;
            boolean showRename = hasSingleItem;
            boolean showPinFolder = allDirs;

            setVis(R.id.action_send,       selectionMode && !allDirs);
            setVis(R.id.action_open_with,  selectionMode && hasSingleFile);
            setVis(R.id.action_play,       selectionMode && (showPlay || showPinFolder));
            setVis(R.id.action_select_all, false);
            setVis(R.id.action_move,       selectionMode);
            setVis(R.id.action_copy,       selectionMode);
            setVis(R.id.action_delete,     selectionMode);
            setVis(R.id.action_rename,     selectionMode && showRename);
            setVis(R.id.action_info,       selectionMode && showInfo);

            if (actionSendLabel != null)     actionSendLabel.setText(R.string.send);
            if (actionSendIcon != null)      actionSendIcon.setImageResource(R.drawable.ic_action_send);
            if (actionOpenWithLabel != null) actionOpenWithLabel.setText(R.string.open);
            if (actionOpenWithIcon != null)  actionOpenWithIcon.setImageResource(R.drawable.ic_action_open_with);

            if (showPinFolder) {
                boolean allPinned = isAllPinned(sel);
                if (actionPlayLabel != null) actionPlayLabel.setText(allPinned ? R.string.unpin_from_recent : R.string.pin_to_recent);
                if (actionPlayIcon != null)  actionPlayIcon.setImageResource(R.drawable.ic_pin);
            } else {
                if (actionPlayLabel != null) actionPlayLabel.setText(R.string.play);
                if (actionPlayIcon != null)  actionPlayIcon.setImageResource(R.drawable.ic_action_play);
            }

            if (actionInfoLabel != null)     actionInfoLabel.setText(R.string.info);
            if (actionInfoIcon != null)      actionInfoIcon.setImageResource(android.R.drawable.ic_menu_info_details);
            if (actionDeleteLabel != null)   actionDeleteLabel.setText(R.string.delete);
        }

        updateInlineSelectAllVisual();
    }

    private boolean isAllPinned(List<File> files) {
        for (File f : files) {
            if (!RecentManager.isPinned(this, f.getAbsolutePath())) return false;
        }
        return true;
    }

    private void handleDeleteAction() {
        if (currentTab == TAB_RECENT) {
            showDeleteOrRemoveMenu();
            return;
        }
        deleteSelectionToTrash();
    }

    private void showDeleteOrRemoveMenu() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setItems(new String[]{
                        getString(R.string.remove_from_recent),
                        getString(R.string.delete)
                }, (d, which) -> {
                    if (which == 0) removeSelectedFromRecent();
                    else deleteSelectionToTrash();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeSelectedFromRecent() {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) {
            toast(getString(R.string.no_items_selected));
            return;
        }

        int removed = 0;
        for (File file : sel) {
            if (file == null) continue;
            String path = file.getAbsolutePath();
            if (path == null || path.trim().isEmpty()) continue;
            RecentManager.remove(this, path);
            removed++;
        }

        if (removed == 1) {
            toast(getString(R.string.removed_from_recent));
        } else if (removed > 1) {
            toast(getString(R.string.removed_from_recent_count, removed));
        }

        loadRecentFiles();
        exitSelectionMode();
    }

    private void showInfoForSelection() {
        List<File> sel = getSelectedFiles();
        if (sel.size() != 1) {
            toast(getString(R.string.select_single_file_for_info));
            return;
        }
        showDetails(new FileItem(sel.get(0)));
    }

    private void setDefaultAppFromSelection() {
        List<File> sel = getSelectedFiles();
        if (sel.size() != 1 || !sel.get(0).isFile()) {
            toast(getString(R.string.select_single_file_for_default_app));
            return;
        }
        showSetDefaultAppDialog(sel.get(0), true);
    }

    private void markSelectionForMove() {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) {
            toast(getString(R.string.no_items_selected));
            return;
        }
        clipboardFiles.clear();
        clipboardFiles.addAll(sel);
        clipboardIsCopy = false;
        updatePasteBar();
        toast(getString(R.string.move_prepared, sel.size()));
        exitSelectionMode();
        if (currentTab == TAB_RECENT) selectTab(TAB_STORAGE);
    }

    private void copySelection() {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) {
            toast(getString(R.string.no_items_selected));
            return;
        }
        clipboardFiles.clear();
        clipboardFiles.addAll(sel);
        clipboardIsCopy = true;
        updatePasteBar();
        toast(getString(R.string.copied_count, sel.size()));
        exitSelectionMode();
        if (currentTab == TAB_RECENT) selectTab(TAB_STORAGE);
    }

    private void renameSelection() {
        List<File> sel = getSelectedFiles();
        if (sel.size() != 1) {
            toast(getString(R.string.select_single_for_rename));
            return;
        }
        showRenameDialog(sel.get(0), true);
    }

    private void deleteSelectionToTrash() {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) {
            toast(getString(R.string.no_items_selected));
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_selected_message, sel.size()))
                .setPositiveButton(R.string.delete_forever, (d, w) -> showDeleteForeverWarningForSelection(sel))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.move_to_trash, (d, w) -> {
                    List<File> publicFiles = new ArrayList<>(), privateFiles = new ArrayList<>();
                    for (File f : sel) {
                        if (isPublicStorageFile(f)) publicFiles.add(f);
                        else privateFiles.add(f);
                    }
                    if (!publicFiles.isEmpty()) {
                        android.app.PendingIntent pi = TrashManager.createSystemTrashRequest(MainActivity.this, publicFiles);
                        if (pi != null) {
                            try {
                                // Delete private files directly (no app trash)
                                if (!privateFiles.isEmpty()) runDeleteSelectionWithProgress(privateFiles, true);
                                startIntentSenderForResult(pi.getIntentSender(), REQ_SYSTEM_TRASH, null, 0, 0, 0);
                                exitSelectionMode();
                                return;
                            } catch (Exception ignored) {}
                        }
                    }
                    // System trash unavailable or all private: delete directly
                    runDeleteSelectionWithProgress(sel, true);
                })
                .create();
        dialog.setOnShowListener(d -> {
            Button deleteForever = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (deleteForever != null) deleteForever.setTextColor(0xFFD32F2F);
        });
        dialog.show();
    }

    private void showDeleteForeverWarningForSelection(List<File> sel) {
        if (sel == null || sel.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.warning_title)
                .setMessage(getString(R.string.warning_delete_forever_selected_message, sel.size()))
                .setPositiveButton(R.string.delete_forever, (d, w) -> runDeleteSelectionWithProgress(sel, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runDeleteSelectionWithProgress(List<File> sel, boolean deleteForever) {
        if (sel == null || sel.isEmpty()) return;

        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final ProgressDialogHolder holder = showProgressDialog(R.string.deleting_files, Math.max(1, sel.size()), cancelled);

        new Thread(() -> {
            int moved = 0;
            String firstError = null;
            int[] processedUnits = new int[]{0};

            int totalUnits = Math.max(1, countWorkUnits(sel));
            mainHandler.post(() -> updateProgressDialogTotal(holder, totalUnits));

            for (File f : sel) {
                if (cancelled.get()) break;
                boolean ok;
                if (deleteForever) {
                    ok = deleteWithProgress(f, cancelled, holder, processedUnits);
                } else {
                    ok = TrashManager.moveToTrash(this, f, cancelled, () -> addProgressUnits(1, holder, processedUnits));
                }
                if (ok) {
                    moved++;
                } else if (firstError == null) {
                    firstError = deleteForever ? getString(R.string.delete_failed) : TrashManager.getLastError();
                }
                if (cancelled.get()) break;
            }

            final int movedFinal = moved;
            final int processedFinal = processedUnits[0];
            final String errorFinal = firstError;
            mainHandler.post(() -> {
                dismissProgressDialog(holder);
                if (cancelled.get()) {
                    toast(getString(R.string.operation_cancelled));
                } else if (movedFinal == sel.size()) {
                    toast(getString(deleteForever ? R.string.deleted_count : R.string.moved_to_trash_count, movedFinal));
                } else {
                    String reason = (errorFinal == null || errorFinal.trim().isEmpty())
                            ? getString(R.string.unknown_reason)
                            : errorFinal;
                    toast(getString(deleteForever ? R.string.deleted_partial_error : R.string.moved_partial_error, movedFinal, sel.size(), reason));
                }
                if (processedFinal > 0) {
                    exitSelectionMode();
                    loadDirectory(currentDir);
                }
            });
        }, "delete-selection").start();
    }

    private boolean deleteWithProgress(File file,
                                       AtomicBoolean cancelled,
                                       ProgressDialogHolder holder,
                                       int[] processedUnits) {
        if (file == null || !file.exists()) return false;
        if (cancelled.get()) return false;

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (cancelled.get()) return false;
                    if (!deleteWithProgress(child, cancelled, holder, processedUnits)) {
                        return false;
                    }
                }
            }
            if (!file.delete()) return false;
            if ((children == null || children.length == 0)) {
                addProgressUnits(1, holder, processedUnits);
            }
            return true;
        }

        boolean ok = file.delete();
        if (ok) {
            addProgressUnits(1, holder, processedUnits);
        }
        return ok;
    }

    private void shareSelectedFiles() {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) {
            toast(getString(R.string.no_items_selected));
            return;
        }
        // Directories cannot be shared via standard Android intents — skip them.
        List<File> shareable = new ArrayList<>();
        for (File f : sel) { if (f.exists() && f.isFile()) shareable.add(f); }
        if (shareable.isEmpty()) {
            toast(getString(R.string.cannot_share_directories));
            return;
        }
        if (shareable.size() < sel.size()) {
            toast(getString(R.string.sharing_files_only_warning));
        }
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : shareable) {
                uris.add(FileProvider.getUriForFile(this, getPackageName() + ".provider", f));
            }
            if (uris.isEmpty()) {
                toast(getString(R.string.error_sharing_selection));
                return;
            }

            Intent i;
            if (uris.size() == 1) {
                i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                i.setType("*/*");
            } else {
                i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.setType("*/*");
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            if (!uris.isEmpty()) {
                ClipData clipData = new ClipData(
                        new ClipDescription("shared_items", new String[]{"*/*"}),
                        new ClipData.Item(uris.get(0))
                );
                for (int idx = 1; idx < uris.size(); idx++) {
                    clipData.addItem(new ClipData.Item(uris.get(idx)));
                }
                i.setClipData(clipData);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.share_via)));
        } catch (Exception e) {
            Log.e(TAG, "shareSelectedFiles", e);
            toast(getString(R.string.error_sharing_selection));
        }
    }

    private void playSelectedFiles() {
        List<File> sel = getSelectedFiles();
        if (sel.size() < 2 || !areAllPlayableFiles(sel)) {
            toast(getString(R.string.select_multiple_playable_files));
            return;
        }
        try {
            String preferredPkg = DefaultAppsManager.getPackageForExtension(this, getExtensionKey(sel.get(0)));
            String pkg = preferredPkg == null ? "" : preferredPkg.trim();

            File playlist = createOrReplaceTempPlaylist(sel, !shouldUsePlainPathsForPlaylist(pkg));
            Uri playlistUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", playlist);
            ArrayList<Uri> playlistUris = new ArrayList<>();
            playlistUris.add(playlistUri);

            String[] playlistTypes = new String[]{
                    "audio/x-mpegurl",
                    "application/x-mpegURL",
                    "application/vnd.apple.mpegurl",
                    "audio/mpegurl"
            };

            for (String playlistType : playlistTypes) {
                if (tryStartPlayIntent(buildPlaylistViewIntent(playlistUri, playlistType), pkg, playlistUris)) return;
            }
            for (String playlistType : playlistTypes) {
                if (tryStartPlayIntent(buildPlaylistViewIntent(playlistUri, playlistType), "", playlistUris)) return;
            }

            // Fallback if the target player does not support M3U.
            ArrayList<Uri> uris = new ArrayList<>();
            boolean allAudio = true;
            boolean allVideo = true;

            for (File f : sel) {
                if (!f.isFile()) continue;
                uris.add(FileProvider.getUriForFile(this, getPackageName() + ".provider", f));
                String ext = getExtensionKey(f).toLowerCase();
                if (!isAudioExt(ext)) allAudio = false;
                if (!isVideoExt(ext)) allVideo = false;
            }

            if (uris.isEmpty()) {
                toast(getString(R.string.select_multiple_playable_files));
                return;
            }

            String type = allAudio ? "audio/*" : (allVideo ? "video/*" : "*/*");

            if (tryStartPlayIntent(buildPlaySendMultipleIntent(uris, type), pkg, uris)) return;
            if (tryStartPlayIntent(buildPlaySendMultipleIntent(uris, type), "", uris)) return;
            if (tryStartPlayIntent(buildPlayViewIntent(uris, type), pkg, uris)) return;
            if (tryStartPlayIntent(buildPlayViewIntent(uris, type), "", uris)) return;

            Intent singleFallback = new Intent(Intent.ACTION_VIEW);
            singleFallback.setDataAndType(uris.get(0), type);
            singleFallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (tryStartPlayIntent(singleFallback, pkg, uris)) return;
            if (tryStartPlayIntent(singleFallback, "", uris)) return;

            toast(getString(R.string.no_app_available));
        } catch (Exception e) {
            Log.e(TAG, "playSelectedFiles", e);
            toast(getString(R.string.error_with_reason, e.getMessage()));
        }
    }

    private File createOrReplaceTempPlaylist(List<File> files, boolean useFileScheme) throws IOException {
        File baseDir = new File(getExternalFilesDir(null), "temp_playlists");
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IOException("Could not create temp playlist folder");
        }

        File[] existing = baseDir.listFiles();
        if (existing != null) {
            for (File f : existing) {
                if (f != null && f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".m3u")) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }

        File playlist = new File(baseDir, buildPlaylistFileName(files));
        if (playlist.exists() && !playlist.delete()) {
            throw new IOException("Could not replace previous playlist");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(playlist, false))) {
            writer.write("#EXTM3U");
            writer.newLine();
            for (File f : files) {
                if (f == null || !f.isFile()) continue;
                writer.write(useFileScheme ? Uri.fromFile(f).toString() : f.getAbsolutePath());
                writer.newLine();
            }
        }
        return playlist;
    }

    private String buildPlaylistFileName(List<File> files) {
        List<File> validFiles = new ArrayList<>();
        for (File f : files) {
            if (f != null && f.isFile()) validFiles.add(f);
        }

        String baseName = "playlist";
        if (!validFiles.isEmpty()) {
            String firstName = validFiles.get(0).getName();
            int dot = firstName.lastIndexOf('.');
            if (dot > 0) firstName = firstName.substring(0, dot);
            if (validFiles.size() > 1) {
                baseName = firstName + "_and_" + (validFiles.size() - 1) + "_more";
            } else {
                baseName = firstName;
            }
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return sanitizeFilename(baseName) + "_" + timestamp + ".m3u";
    }

    private String sanitizeFilename(String input) {
        if (input == null) return "file";
        String cleaned = input.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (cleaned.isEmpty()) cleaned = "file";
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
        return cleaned;
    }

    private boolean shouldUsePlainPathsForPlaylist(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String p = pkg.toLowerCase();
        return p.contains("ghisler") || p.contains("totalcmd") || p.contains("totalcommander");
    }

    private void cleanupTempPlaylist() {
        try {
            File baseDir = new File(getExternalFilesDir(null), "temp_playlists");
            File[] files = baseDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f != null && f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".m3u")) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }

            File[] leftovers = baseDir.listFiles();
            if (leftovers == null || leftovers.length == 0) {
                //noinspection ResultOfMethodCallIgnored
                baseDir.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private Intent buildPlaylistViewIntent(Uri playlistUri, String type) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(playlistUri, type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(new ClipData(
                new ClipDescription("playlist", new String[]{type}),
                new ClipData.Item(playlistUri)
        ));
        return i;
    }

    private Intent buildPlayViewIntent(ArrayList<Uri> uris, String type) {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(uris.get(0), type);
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        viewIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        viewIntent.setClipData(buildMediaClipData(uris, type));
        return viewIntent;
    }

    private Intent buildPlaySendMultipleIntent(ArrayList<Uri> uris, String type) {
        Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
        i.setType(type);
        i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    private ClipData buildMediaClipData(ArrayList<Uri> uris, String type) {
        ClipData clipData = new ClipData(
                new ClipDescription("selected-media", new String[]{type}),
                new ClipData.Item(uris.get(0))
        );
        for (int i = 1; i < uris.size(); i++) {
            clipData.addItem(new ClipData.Item(uris.get(i)));
        }
        return clipData;
    }

    private boolean tryStartPlayIntent(Intent intent, String pkg, ArrayList<Uri> uris) {
        try {
            if (pkg != null && !pkg.isEmpty()) {
                intent.setPackage(pkg);
            }
            grantUriReadPermissionForIntent(intent, uris);
            if (intent.resolveActivity(getPackageManager()) == null) {
                return false;
            }
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void grantUriReadPermissionForIntent(Intent intent, ArrayList<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        try {
            String explicitPkg = intent.getPackage();
            if (explicitPkg != null && !explicitPkg.isEmpty()) {
                for (Uri uri : uris) {
                    grantUriPermission(explicitPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                return;
            }

            PackageManager pm = getPackageManager();
            List<ResolveInfo> handlers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (handlers == null || handlers.isEmpty()) {
                handlers = pm.queryIntentActivities(intent, 0);
            }

            if (handlers == null) return;
            for (ResolveInfo info : handlers) {
                if (info == null || info.activityInfo == null) continue;
                String packageName = info.activityInfo.packageName;
                if (packageName == null || packageName.isEmpty()) continue;
                for (Uri uri : uris) {
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean areAllPlayableFiles(List<File> files) {
        if (files == null || files.isEmpty()) return false;
        for (File f : files) {
            if (f == null || !f.isFile()) return false;
            String ext = getExtensionKey(f).toLowerCase();
            if (!isAudioExt(ext) && !isVideoExt(ext)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAudioExt(String extWithDot) {
        return ".mp3".equals(extWithDot) || ".wav".equals(extWithDot) || ".flac".equals(extWithDot)
                || ".aac".equals(extWithDot) || ".ogg".equals(extWithDot) || ".m4a".equals(extWithDot);
    }

    private boolean isVideoExt(String extWithDot) {
        return ".mp4".equals(extWithDot) || ".avi".equals(extWithDot) || ".mov".equals(extWithDot)
                || ".mkv".equals(extWithDot) || ".3gp".equals(extWithDot) || ".webm".equals(extWithDot);
    }

    private void selectAllCurrentFiles() {
        FileAdapter activeAdapter = getActiveAdapter();
        List<FileItem> activeItems = getActiveItems();
        if (activeAdapter == null) return;
        activeAdapter.setSelectionMode(true);
        for (FileItem fi : activeItems) fi.setSelected(true);
        activeAdapter.notifyDataSetChanged();
        updateSelectionActionsBar();
    }

    private void toggleSelectAllInline() {
        if (currentTab != TAB_STORAGE) return;
        if (fileItems.isEmpty() || adapter == null) return;
        if (isAllCurrentSelected()) {
            exitSelectionMode();
        } else {
            selectAllCurrentFiles();
        }
    }

    private boolean isAllCurrentSelected() {
        List<FileItem> activeItems = getActiveItems();
        if (activeItems.isEmpty()) return false;
        for (FileItem fi : activeItems) {
            if (!fi.isSelected()) return false;
        }
        return true;
    }

    private void updateInlineSelectAllVisual() {
        if (btnSelectAllInline == null) return;
        FileAdapter activeAdapter = getActiveAdapter();
        boolean allSelected = isAllCurrentSelected() && activeAdapter != null && activeAdapter.isSelectionMode();
        btnSelectAllInline.setImageResource(allSelected ? R.drawable.ic_action_select_all_checked : R.drawable.ic_action_select_all);
        btnSelectAllInline.setAlpha(1.0f);

        // setImageResource can clear filters on some devices; keep same tone as new-folder button.
        ImageButton newFolderButton = findViewById(R.id.btn_new_folder_inline);
        if (newFolderButton != null) {
            if (newFolderButton.getColorFilter() != null) {
                btnSelectAllInline.setColorFilter(newFolderButton.getColorFilter());
            } else if (newFolderButton.getImageTintList() != null) {
                btnSelectAllInline.setImageTintList(newFolderButton.getImageTintList());
            }
        }
    }

    private void handlePrimaryAction() {
        shareSelectedFiles();
    }

    private void handleOpenAction() {
        if (currentTab == TAB_RECENT) {
            List<File> sel = getSelectedFiles();
            if (sel.size() == 1) {
                File f = sel.get(0);
                if (f.isDirectory()) {
                    exitSelectionMode();
                    selectTab(TAB_STORAGE);
                    loadDirectory(f);
                } else {
                    openSelectedRecentFile();
                }
            } else {
                playSelectedFiles();
            }
            return;
        }
        setDefaultAppFromSelection();
    }

    private void handleSecondaryAction() {
        if (currentTab == TAB_RECENT) {
            locateSelectedRecentFile(); // action_play is now Localizar in Recentes
            return;
        }
        // Storage: play for playable files, or pin/unpin when all selected are folders
        List<File> sel = getSelectedFiles();
        if (isAllDirectories(sel)) {
            togglePinForStorageFolders(sel);
        } else {
            playSelectedFiles();
        }
    }

    private void openSelectedRecentFile() {
        List<File> sel = getSelectedFiles();
        if (sel.size() != 1 || !sel.get(0).isFile()) {
            toast(getString(R.string.select_single_file_for_open_with));
            return;
        }
        openFile(sel.get(0));
        exitSelectionMode();
    }

    private void locateSelectedRecentFile() {
        List<File> sel = getSelectedFiles();
        if (sel.size() != 1) {
            toast(getString(R.string.select_single_file_to_locate));
            return;
        }

        File target = sel.get(0);
        if (target.isDirectory()) {
            // Locate a pinned folder: navigate to its parent and highlight it
            File parent = target.getParentFile();
            if (parent == null || !parent.exists() || !parent.isDirectory()) {
                toast(getString(R.string.could_not_locate_file));
                return;
            }
            pendingLocateFilePath = target.getAbsolutePath();
            exitSelectionMode();
            selectTab(TAB_STORAGE);
            loadDirectory(parent);
        } else {
            File parent = target.getParentFile();
            if (parent == null || !parent.exists() || !parent.isDirectory()) {
                toast(getString(R.string.could_not_locate_file));
                return;
            }
            pendingLocateFilePath = target.getAbsolutePath();
            exitSelectionMode();
            selectTab(TAB_STORAGE);
            loadDirectory(parent);
        }
    }

    private void togglePinForSelectedRecent() {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) return;

        boolean allPinned = isAllPinned(sel);
        if (allPinned) {
            for (File file : sel) RecentManager.unpin(this, file.getAbsolutePath());
            toast(getString(R.string.unpinned_from_recent));
        } else {
            for (File file : sel) RecentManager.pin(this, file.getAbsolutePath());
            toast(getString(R.string.pinned_to_recent));
        }
        loadRecentFiles();
        exitSelectionMode();
    }

    private void togglePinForStorageFolders(List<File> folders) {
        if (folders.isEmpty()) return;
        boolean allPinned = isAllPinned(folders);
        for (File folder : folders) {
            String path = folder.getAbsolutePath();
            if (allPinned) {
                RecentManager.unpin(this, path);
            } else {
                // Add to recents so the folder appears in the list, then pin it
                RecentManager.add(this, path);
                RecentManager.pin(this, path);
            }
        }
        toast(getString(allPinned ? R.string.unpinned_from_recent : R.string.pinned_to_recent));
        exitSelectionMode();
    }

    private void applyPendingLocateSelection() {
        if (pendingLocateFilePath == null || pendingLocateFilePath.trim().isEmpty()) return;
        if (currentTab != TAB_STORAGE) return;

        int targetIndex = -1;
        for (int i = 0; i < fileItems.size(); i++) {
            FileItem item = fileItems.get(i);
            if (item != null && item.getFile() != null
                    && pendingLocateFilePath.equals(item.getFile().getAbsolutePath())) {
                targetIndex = i;
                item.setSelected(true);
                break;
            }
        }

        if (targetIndex >= 0) {
            if (adapter != null) {
                adapter.setSelectionMode(true);
            }
            if (recycler != null) {
                recycler.scrollToPosition(targetIndex);
            }
            updateSelectionActionsBar();
        } else {
            toast(getString(R.string.could_not_locate_file));
        }

        pendingLocateFilePath = null;
    }

    private void syncRecentItemsWithFiles(List<File> files) {
        recentFileItems.clear();
        for (File file : files) {
            recentFileItems.add(new FileItem(file));
        }
    }

    private List<FileItem> getActiveItems() {
        return currentTab == TAB_RECENT ? recentFileItems : fileItems;
    }

    private FileAdapter getActiveAdapter() {
        return currentTab == TAB_RECENT ? recentListAdapter : adapter;
    }

    private void clearSelectionState(FileAdapter targetAdapter, List<FileItem> items) {
        if (targetAdapter == null) return;
        targetAdapter.setSelectionMode(false);
        for (FileItem fi : items) {
            fi.setSelected(false);
        }
        targetAdapter.notifyDataSetChanged();
    }

    // ─────────────────────── MENUS ───────────────────────────────────

    private void showFileMenu(FileItem item, View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        if (item.isDirectory()) {
            p.getMenu().add(0, 1, 0, getString(R.string.open));
            p.getMenu().add(0, 3, 0, getString(R.string.move));
            p.getMenu().add(0, 2, 0, getString(R.string.copy));
            p.getMenu().add(0, 5, 0, getString(R.string.rename));
            p.getMenu().add(0, 6, 0, getString(R.string.delete));
        } else {
            p.getMenu().add(0, 7, 0, getString(R.string.send));
            p.getMenu().add(0, 8, 0, getString(R.string.set_default_app));
            p.getMenu().add(0, 3, 0, getString(R.string.move));
            p.getMenu().add(0, 2, 0, getString(R.string.copy));
            p.getMenu().add(0, 5, 0, getString(R.string.rename));
            p.getMenu().add(0, 6, 0, getString(R.string.delete));
        }

        p.setOnMenuItemClickListener(mi -> {
            try {
                switch (mi.getItemId()) {
                    case 1:
                        if (item.isDirectory()) navigateTo(item.getFile());
                        else openFile(item.getFile());
                        break;
                    case 2:
                        clipboardFiles.clear();
                        clipboardFiles.add(item.getFile());
                        clipboardIsCopy = true;
                        updatePasteBar();
                        toast(getString(R.string.copied_simple));
                        break;
                    case 3:
                        clipboardFiles.clear();
                        clipboardFiles.add(item.getFile());
                        clipboardIsCopy = false;
                        updatePasteBar();
                        toast(getString(R.string.move_prepared, 1));
                        break;
                    case 5:
                        showRenameDialog(item.getFile(), false);
                        break;
                    case 6:
                        moveToTrash(item.getFile());
                        break;
                    case 7:
                        shareFile(item.getFile());
                        break;
                    case 8:
                        showSetDefaultAppDialog(item.getFile(), true);
                        break;
                }
            } catch (Exception e) { Log.e(TAG, "fileMenu", e); toast(getString(R.string.error_with_reason, e.getMessage())); }
            return true;
        });
        p.show();
    }

    private void showSelectionMenu(View anchor) {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) {
            toast(getString(R.string.no_items_selected));
            return;
        }

        boolean single = sel.size() == 1;
        boolean singleFile = single && sel.get(0).isFile();
        boolean multiPlayable = sel.size() > 1 && areAllPlayableFiles(sel);

        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(0, 1, 0, getString(R.string.send));
        if (singleFile) p.getMenu().add(0, 2, 0, getString(R.string.set_default_app));
        if (multiPlayable) p.getMenu().add(0, 7, 0, getString(R.string.play));
        p.getMenu().add(0, 3, 0, getString(R.string.move));
        p.getMenu().add(0, 4, 0, getString(R.string.copy));
        if (single) p.getMenu().add(0, 5, 0, getString(R.string.rename));
        p.getMenu().add(0, 8, 0, getString(R.string.select_all));
        p.getMenu().add(0, 6, 0, getString(R.string.delete));

        p.setOnMenuItemClickListener(mi -> {
            try {
                switch (mi.getItemId()) {
                    case 1:
                        shareSelectedFiles();
                        break;
                    case 2:
                        if (singleFile) showSetDefaultAppDialog(sel.get(0), true);
                        break;
                    case 7:
                        playSelectedFiles();
                        break;
                    case 3:
                        markSelectionForMove();
                        break;
                    case 4:
                        copySelection();
                        break;
                    case 5:
                        renameSelection();
                        break;
                    case 8:
                        selectAllCurrentFiles();
                        break;
                    case 6:
                        deleteSelectionToTrash();
                        break;
                }
            } catch (Exception e) { Log.e(TAG, "selMenu", e); }
            return true;
        });
        p.show();
    }

    private void showSortMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        String[] labels = {getString(R.string.name), getString(R.string.date), getString(R.string.size)};
        for (int i = 0; i < labels.length; i++)
            p.getMenu().add(0, i, i, (sortMode == i ? "✓ " : "") + labels[i]);
        p.setOnMenuItemClickListener(mi -> { sortMode = mi.getItemId(); if (currentDir != null) loadDirectory(currentDir); return true; });
        p.show();
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(0, 1, 0, getString(R.string.select_all));
        p.getMenu().add(0, 2, 0, getString(R.string.settings));
        p.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == 1 && adapter != null) {
                selectAllCurrentFiles();
            } else if (mi.getItemId() == 2) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });
        p.show();
    }

    // ─────────────────────── PASTE ───────────────────────────────────

    private void updatePasteBar() {
        if (pasteBar == null) return;
        Button pasteBtn = findViewById(R.id.btn_paste);
        if (!clipboardFiles.isEmpty()) {
            pasteBar.setVisibility(View.VISIBLE);
            if (pasteBtn != null) {
                pasteBtn.setText(clipboardIsCopy ? R.string.paste : R.string.move);
            }
            if (pasteLabel != null) {
                if (clipboardFiles.size() == 1) {
                    pasteLabel.setText((clipboardIsCopy ? getString(R.string.copy) + ": " : getString(R.string.move) + ": ") + clipboardFiles.get(0).getName());
                } else {
                    pasteLabel.setText((clipboardIsCopy ? getString(R.string.copy) + ": " : getString(R.string.move) + ": ") + getString(R.string.items_count, clipboardFiles.size()));
                }
            }
        } else if (!incomingSharedItems.isEmpty()) {
            pasteBar.setVisibility(View.VISIBLE);
            if (pasteBtn != null) {
                pasteBtn.setText(R.string.save_here);
            }
            if (pasteLabel != null) {
                if (incomingSharedItems.size() == 1) {
                    pasteLabel.setText(getString(R.string.shared_file_ready, incomingSharedItems.get(0).displayName));
                } else {
                    pasteLabel.setText(getString(R.string.shared_files_ready, incomingSharedItems.size()));
                }
            }
        } else if (!incomingSharedTexts.isEmpty()) {
            pasteBar.setVisibility(View.VISIBLE);
            if (pasteBtn != null) {
                pasteBtn.setText(R.string.save_here);
            }
            if (pasteLabel != null) {
                if (incomingSharedTexts.size() == 1) {
                    pasteLabel.setText(getString(R.string.shared_text_ready, incomingSharedTexts.get(0).displayName));
                } else {
                    pasteLabel.setText(getString(R.string.shared_texts_ready, incomingSharedTexts.size()));
                }
            }
        } else {
            pasteBar.setVisibility(View.GONE);
            if (pasteBtn != null) {
                pasteBtn.setText(R.string.paste);
            }
        }
    }

    private void pasteClipboard() {
        if (!incomingSharedItems.isEmpty()) {
            importSharedItemsToCurrentDirectory();
            return;
        }
        if (!incomingSharedTexts.isEmpty()) {
            importSharedTextsToCurrentDirectory();
            return;
        }

        if (clipboardFiles.isEmpty() || currentDir == null) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        // Guard: prevent copying/moving a folder into itself or one of its subdirectories
        String dstPath = currentDir.getAbsolutePath();
        for (File src : clipboardFiles) {
            if (src.isDirectory()) {
                String srcPath = src.getAbsolutePath();
                if (dstPath.equals(srcPath) || dstPath.startsWith(srcPath + java.io.File.separator)) {
                    toast(getString(R.string.cannot_paste_into_self));
                    return;
                }
            }
        }

        final List<File> sources = new ArrayList<>(clipboardFiles);
        // Paste is single-use: clear clipboard as soon as paste starts.
        clipboardFiles.clear();
        updatePasteBar();

        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final ProgressDialogHolder holder = showProgressDialog(
                clipboardIsCopy ? R.string.copying_files : R.string.moving_files,
                Math.max(1, sources.size()),
                cancelled
        );

        new Thread(() -> {
            int ok = 0;
            String firstError = null;
            int[] processedUnits = new int[]{0};

            int totalUnits = Math.max(1, countWorkUnits(sources));
            mainHandler.post(() -> updateProgressDialogTotal(holder, totalUnits));

            for (File source : sources) {
                if (cancelled.get()) break;
                try {
                    if (clipboardIsCopy) {
                        copySourceWithProgress(source, currentDir, cancelled, holder, processedUnits);
                    } else {
                        moveSourceWithProgress(source, currentDir, cancelled, holder, processedUnits);
                    }
                    ok++;
                } catch (Exception e) {
                    if (firstError == null) firstError = e.getMessage();
                }
            }

            int copiedCount = ok;
            String error = firstError;
            mainHandler.post(() -> {
                dismissProgressDialog(holder);
                if (cancelled.get()) {
                    toast(getString(R.string.operation_cancelled));
                } else {
                    if (copiedCount > 0) {
                        toast(getString(clipboardIsCopy ? R.string.copied_result : R.string.moved_result, copiedCount));
                    }
                    if (error != null) {
                        toast(getString(R.string.paste_partial_error, error));
                    }
                }
                if (copiedCount > 0) {
                    loadDirectory(currentDir);
                }
            });
        }).start();
    }

    private void handleIncomingShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return;
        }

        List<Uri> uris = extractSharedUris(intent);
        List<String> textPayloads = extractSharedTextPayloads(intent);

        incomingSharedItems.clear();
        incomingSharedTexts.clear();
        pendingSharedTextPayloads.clear();
        pendingSharedTextConfig = false;

        if (!uris.isEmpty()) {
            for (Uri uri : uris) {
                if (uri == null) continue;
                incomingSharedItems.add(new IncomingSharedItem(uri, resolveSharedDisplayName(uri)));
            }
        }

        if (incomingSharedItems.isEmpty() && !textPayloads.isEmpty()) {
            pendingSharedTextPayloads.addAll(textPayloads);
            pendingSharedTextConfig = true;
            for (int i = 0; i < textPayloads.size(); i++) {
                String text = textPayloads.get(i);
                if (text == null || text.trim().isEmpty()) continue;
                String name = buildSharedTextFilename(i + 1);
                incomingSharedTexts.add(new IncomingSharedText(text, name));
            }
        }

        if (incomingSharedItems.isEmpty() && incomingSharedTexts.isEmpty()) {
            return;
        }

        notifyIncomingSharedReady();
    }

    private void notifyIncomingSharedReady() {
        updatePasteBar();
        selectTab(TAB_STORAGE);
        int totalReceived = incomingSharedItems.size() + incomingSharedTexts.size();
        toast(getString(R.string.shared_files_received, totalReceived));
    }

    private List<String> extractSharedTextPayloads(Intent intent) {
        List<String> out = new ArrayList<>();
        if (intent == null) return out;

        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.trim().isEmpty()) out.add(text);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<CharSequence> textList = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT);
            if (textList != null) {
                for (CharSequence cs : textList) {
                    if (cs != null) {
                        String text = cs.toString();
                        if (!text.trim().isEmpty()) out.add(text);
                    }
                }
            }
        }

        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                CharSequence cs = clipData.getItemAt(i).getText();
                if (cs != null) {
                    String text = cs.toString();
                    if (!text.trim().isEmpty()) out.add(text);
                }
            }
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String text : out) {
            if (text == null) continue;
            String trimmed = text.trim();
            if (trimmed.isEmpty()) continue;
            unique.add(trimmed);
        }
        return new ArrayList<>(unique);
    }

    private void showSharedTextSaveDialog(List<String> textPayloads, Runnable onConfigured) {
        if (textPayloads == null || textPayloads.isEmpty()) return;

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(4));

        TextView nameLabel = new TextView(this);
        nameLabel.setText(getString(R.string.shared_text_name_label));
        root.addView(nameLabel);

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint(getString(R.string.shared_text_name_hint));
        nameInput.setText("shared_text_" + ts);
        root.addView(nameInput);

        TextView typeLabel = new TextView(this);
        typeLabel.setText(getString(R.string.shared_text_type_label));
        typeLabel.setPadding(0, dp(10), 0, 0);
        root.addView(typeLabel);

        EditText typeInput = new EditText(this);
        typeInput.setSingleLine(true);
        typeInput.setHint(getString(R.string.shared_text_type_hint));
        typeInput.setText("txt");
        root.addView(typeInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.shared_text_save_options_title)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save_here, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveBtn.setOnClickListener(v -> {
                String baseName = sanitizeFilename(nameInput.getText() == null ? "" : nameInput.getText().toString());
                if (baseName.trim().isEmpty()) {
                    toast(getString(R.string.shared_text_invalid_name));
                    return;
                }

                String ext = sanitizeSharedTextExtension(typeInput.getText() == null ? "" : typeInput.getText().toString());
                if (ext.isEmpty()) {
                    toast(getString(R.string.shared_text_invalid_type));
                    return;
                }

                incomingSharedTexts.clear();
                for (int i = 0; i < textPayloads.size(); i++) {
                    String text = textPayloads.get(i);
                    if (text == null || text.trim().isEmpty()) continue;
                    String suffix = textPayloads.size() > 1 ? "_" + (i + 1) : "";
                    String name = sanitizeFilename(baseName + suffix) + "." + ext;
                    incomingSharedTexts.add(new IncomingSharedText(text, name));
                }

                dialog.dismiss();
                if (!incomingSharedTexts.isEmpty()) {
                    pendingSharedTextConfig = false;
                    if (onConfigured != null) {
                        onConfigured.run();
                    } else {
                        notifyIncomingSharedReady();
                    }
                }
            });
        });

        dialog.show();
    }

    private String sanitizeSharedTextExtension(String raw) {
        if (raw == null) return "";
        String ext = raw.trim().toLowerCase(Locale.US);
        if (ext.startsWith(".")) ext = ext.substring(1);
        ext = ext.replaceAll("[^a-z0-9]", "");
        if (ext.length() > 10) ext = ext.substring(0, 10);
        return ext;
    }

    private String buildSharedTextFilename(int index) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        if (index <= 1) {
            return "shared_text_" + ts + ".txt";
        }
        return "shared_text_" + ts + "_" + index + ".txt";
    }

    private List<Uri> extractSharedUris(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (intent == null) return uris;

        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            addUriIfValid(uris, single);

            // Some providers attach the stream in clipData instead of EXTRA_STREAM.
            ClipData clipData = intent.getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                addUriIfValid(uris, clipData.getItemAt(0).getUri());
            }

            // Fallback for apps that populate data URI.
            addUriIfValid(uris, intent.getData());

            // Some apps only send text containing a local path/content URI.
            addUrisFromExtraText(uris, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) {
                for (Uri uri : many) addUriIfValid(uris, uri);
            }

            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    addUriIfValid(uris, clipData.getItemAt(i).getUri());
                }
            }

            ArrayList<CharSequence> textList = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT);
            if (textList != null) {
                for (CharSequence cs : textList) {
                    if (cs != null) {
                        addUrisFromExtraText(uris, cs.toString());
                    }
                }
            }
        }
        return uris;
    }

    private void addUrisFromExtraText(List<Uri> uris, String text) {
        if (uris == null || text == null) return;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return;

        String[] parts = trimmed.split("\\s+");
        for (String part : parts) {
            if (part == null) continue;
            String token = part.trim();
            if (token.isEmpty()) continue;

            if (token.startsWith("content://") || token.startsWith("file://")) {
                addUriIfValid(uris, Uri.parse(token));
                continue;
            }

            // Absolute filesystem path fallback.
            if (token.startsWith("/") || token.matches("^[A-Za-z]:[/\\\\].*")) {
                File f = new File(token);
                if (f.exists() && f.isFile()) {
                    addUriIfValid(uris, Uri.fromFile(f));
                }
            }
        }
    }

    private void addUriIfValid(List<Uri> uris, Uri candidate) {
        if (candidate == null || uris == null) return;
        String value = candidate.toString();
        if (value == null || value.trim().isEmpty()) return;

        for (Uri existing : uris) {
            if (existing != null && value.equals(existing.toString())) {
                return;
            }
        }
        uris.add(candidate);
    }

    private String resolveSharedDisplayName(Uri uri) {
        String fallback = uri.getLastPathSegment();
        if (fallback == null || fallback.trim().isEmpty()) {
            fallback = "shared_file";
        }

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.trim().isEmpty()) {
                        return normalizeSharedFilename(uri, sanitizeFilename(name));
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return normalizeSharedFilename(uri, sanitizeFilename(fallback));
    }

    private String normalizeSharedFilename(Uri uri, String candidateName) {
        String safeName = sanitizeFilename(candidateName);
        String mime = null;
        try {
            mime = getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }
        if (mime == null || mime.trim().isEmpty()) return safeName;

        String expectedExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.toLowerCase(Locale.US));
        if (expectedExt == null || expectedExt.trim().isEmpty()) return safeName;

        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot >= safeName.length() - 1) {
            return safeName + "." + expectedExt;
        }

        String currentExt = safeName.substring(dot + 1).toLowerCase(Locale.US);
        if ("txt".equals(currentExt) && !"text/plain".equalsIgnoreCase(mime)) {
            return safeName.substring(0, dot + 1) + expectedExt;
        }
        return safeName;
    }

    private void copySourceWithProgress(File source,
                                        File destDir,
                                        AtomicBoolean cancelled,
                                        ProgressDialogHolder holder,
                                        int[] processedUnits) throws Exception {
        if (source == null || destDir == null) return;
        if (cancelled.get()) throw new Exception("cancelled");

        File dest = buildUniqueDestination(destDir, source.getName());
        if (source.isDirectory()) {
            copyDirectoryWithProgress(source, dest, cancelled, holder, processedUnits);
        } else {
            copyFileWithProgress(source, dest, cancelled, holder, processedUnits);
        }
    }

    private void moveSourceWithProgress(File source,
                                        File destDir,
                                        AtomicBoolean cancelled,
                                        ProgressDialogHolder holder,
                                        int[] processedUnits) throws Exception {
        if (source == null || destDir == null) return;
        if (cancelled.get()) throw new Exception("cancelled");
        boolean sourceWasFile = source.isFile();
        String sourcePath = source.getAbsolutePath();

        File dest = buildUniqueDestination(destDir, source.getName());
        if (source.isFile() && source.renameTo(dest)) {
            int units = Math.max(1, countWorkUnits(dest));
            addProgressUnits(units, holder, processedUnits);
            if (sourceWasFile) {
                RecentManager.remove(this, sourcePath);
            }
            return;
        }

        if (source.isDirectory()) {
            copyDirectoryWithProgress(source, dest, cancelled, holder, processedUnits);
        } else {
            copyFileWithProgress(source, dest, cancelled, holder, processedUnits);
        }

        if (!FileOperations.delete(source)) {
            throw new Exception("Unable to delete source after move: " + source.getName());
        }
        if (sourceWasFile) {
            RecentManager.remove(this, sourcePath);
        }
    }

    private void importSharedItemsToCurrentDirectory() {
        if (currentDir == null) {
            toast(getString(R.string.open_folder_to_save_shared));
            return;
        }
        if (incomingSharedItems.isEmpty()) {
            toast(getString(R.string.no_shared_files_pending));
            return;
        }

        final List<IncomingSharedItem> items = new ArrayList<>(incomingSharedItems);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final ProgressDialogHolder holder = showProgressDialog(
                R.string.importing_shared_files,
                Math.max(1, items.size()),
                cancelled
        );

        new Thread(() -> {
            int imported = 0;
            String firstError = null;

            for (IncomingSharedItem item : items) {
                if (cancelled.get()) break;
                try {
                    File target = buildUniqueDestination(currentDir, item.displayName);
                    copyUriToFile(item.uri, target, cancelled);
                    imported++;
                    int progress = imported;
                    mainHandler.post(() -> updateProgressDialog(holder, progress));
                } catch (Exception e) {
                    if (firstError == null) firstError = e.getMessage();
                }
            }

            int importedCount = imported;
            String error = firstError;
            mainHandler.post(() -> {
                dismissProgressDialog(holder);
                if (cancelled.get()) {
                    toast(getString(R.string.operation_cancelled));
                    return;
                }

                if (importedCount > 0) {
                    if (importedCount == items.size() && error == null) {
                        toast(getString(R.string.imported_shared_result, importedCount));
                    } else {
                        toast(getString(R.string.import_shared_partial_error, importedCount, items.size(), error == null ? "unknown" : error));
                    }
                } else {
                    toast(getString(R.string.import_shared_failed));
                }

                if (importedCount > 0) {
                    incomingSharedItems.clear();
                    updatePasteBar();
                    loadDirectory(currentDir);
                }
            });
        }).start();
    }

    private void importSharedTextsToCurrentDirectory() {
        if (currentDir == null) {
            toast(getString(R.string.open_folder_to_save_shared));
            return;
        }
        if (incomingSharedTexts.isEmpty()) {
            toast(getString(R.string.no_shared_files_pending));
            return;
        }

        if (pendingSharedTextConfig) {
            showSharedTextSaveDialog(new ArrayList<>(pendingSharedTextPayloads), this::importSharedTextsToCurrentDirectory);
            return;
        }

        final List<IncomingSharedText> items = new ArrayList<>(incomingSharedTexts);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final ProgressDialogHolder holder = showProgressDialog(
                R.string.importing_shared_files,
                Math.max(1, items.size()),
                cancelled
        );

        new Thread(() -> {
            int imported = 0;
            String firstError = null;

            for (IncomingSharedText item : items) {
                if (cancelled.get()) break;
                try {
                    File target = buildUniqueDestination(currentDir, sanitizeFilename(item.displayName));
                    writeTextToFile(item.text, target, cancelled);
                    imported++;
                    int progress = imported;
                    mainHandler.post(() -> updateProgressDialog(holder, progress));
                } catch (Exception e) {
                    if (firstError == null) firstError = e.getMessage();
                }
            }

            int importedCount = imported;
            String error = firstError;
            mainHandler.post(() -> {
                dismissProgressDialog(holder);
                if (cancelled.get()) {
                    toast(getString(R.string.operation_cancelled));
                    return;
                }

                if (importedCount > 0) {
                    if (importedCount == items.size() && error == null) {
                        toast(getString(R.string.imported_shared_result, importedCount));
                    } else {
                        toast(getString(R.string.import_shared_partial_error, importedCount, items.size(), error == null ? "unknown" : error));
                    }
                } else {
                    toast(getString(R.string.import_shared_failed));
                }

                if (importedCount > 0) {
                    incomingSharedTexts.clear();
                    pendingSharedTextPayloads.clear();
                    pendingSharedTextConfig = false;
                    updatePasteBar();
                    loadDirectory(currentDir);
                }
            });
        }).start();
    }

    private void writeTextToFile(String text, File target, AtomicBoolean cancelled) throws Exception {
        if (cancelled.get()) throw new Exception("cancelled");
        try (FileOutputStream out = new FileOutputStream(target)) {
            String safeText = text == null ? "" : text;
            byte[] bytes = safeText.getBytes();
            out.write(bytes);
            out.flush();
        }
    }

    private void copyUriToFile(Uri uri, File target, AtomicBoolean cancelled) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("Unable to read shared item");
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled.get()) throw new Exception("cancelled");
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private void copyDirectoryWithProgress(File src,
                                           File dst,
                                           AtomicBoolean cancelled,
                                           ProgressDialogHolder holder,
                                           int[] processedUnits) throws Exception {
        if (cancelled.get()) throw new Exception("cancelled");
        if (!dst.exists() && !dst.mkdirs()) {
            throw new Exception("Cannot create destination directory: " + dst.getAbsolutePath());
        }

        File[] children = src.listFiles();
        if (children == null || children.length == 0) {
            addProgressUnits(1, holder, processedUnits);
            return;
        }

        for (File child : children) {
            if (cancelled.get()) throw new Exception("cancelled");
            File target = new File(dst, child.getName());
            if (child.isDirectory()) {
                copyDirectoryWithProgress(child, target, cancelled, holder, processedUnits);
            } else {
                copyFileWithProgress(child, target, cancelled, holder, processedUnits);
            }
        }
    }

    private void copyFileWithProgress(File src,
                                      File dst,
                                      AtomicBoolean cancelled,
                                      ProgressDialogHolder holder,
                                      int[] processedUnits) throws Exception {
        if (cancelled.get()) throw new Exception("cancelled");
        try (FileChannel in = new FileInputStream(src).getChannel();
             FileChannel out = new FileOutputStream(dst).getChannel()) {
            long size = in.size();
            long position = 0;
            while (position < size) {
                if (cancelled.get()) throw new Exception("cancelled");
                long transferred = out.transferFrom(in, position, Math.min(8L * 1024L * 1024L, size - position));
                if (transferred <= 0) break;
                position += transferred;
            }
        }
        addProgressUnits(1, holder, processedUnits);
    }

    private void addProgressUnits(int units, ProgressDialogHolder holder, int[] processedUnits) {
        int safeUnits = Math.max(0, units);
        processedUnits[0] += safeUnits;
        int done = processedUnits[0];
        mainHandler.post(() -> updateProgressDialog(holder, done));
    }

    private File buildUniqueDestination(File destDir, String sourceName) {
        File dest = new File(destDir, sourceName);
        if (!dest.exists()) return dest;

        String base = sourceName;
        String ext = "";
        int dot = sourceName.lastIndexOf('.');
        if (dot > 0 && dot < sourceName.length() - 1) {
            base = sourceName.substring(0, dot);
            ext = sourceName.substring(dot);
        }

        int index = 1;
        while (dest.exists()) {
            dest = new File(destDir, base + " (" + index + ")" + ext);
            index++;
        }
        return dest;
    }

    private static class ProgressDialogHolder {
        final AlertDialog dialog;
        final TextView status;
        final ProgressBar progress;
        int total;

        ProgressDialogHolder(AlertDialog dialog, TextView status, ProgressBar progress, int total) {
            this.dialog = dialog;
            this.status = status;
            this.progress = progress;
            this.total = total;
        }
    }

    private ProgressDialogHolder showProgressDialog(int titleRes, int total, AtomicBoolean cancelled) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(12), p, dp(4));

        TextView status = new TextView(this);
        status.setText(getString(R.string.operation_progress, 0, Math.max(1, total)));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(Math.max(1, total));
        bar.setProgress(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(bar, lp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(root)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    cancelled.set(true);
                    status.setText(getString(R.string.cancelling));
                })
                .create();
        dialog.show();
        return new ProgressDialogHolder(dialog, status, bar, Math.max(1, total));
    }

    private void updateProgressDialog(ProgressDialogHolder holder, int done) {
        if (holder == null) return;
        int clamped = Math.max(0, Math.min(done, holder.total));
        holder.progress.setProgress(clamped);
        holder.status.setText(getString(R.string.operation_progress, clamped, holder.total));
    }

    private void updateProgressDialogTotal(ProgressDialogHolder holder, int total) {
        if (holder == null) return;
        int safeTotal = Math.max(1, total);
        holder.total = safeTotal;
        holder.progress.setMax(safeTotal);
        int current = holder.progress.getProgress();
        int clamped = Math.max(0, Math.min(current, safeTotal));
        holder.progress.setProgress(clamped);
        holder.status.setText(getString(R.string.operation_progress, clamped, safeTotal));
    }

    private void dismissProgressDialog(ProgressDialogHolder holder) {
        if (holder == null || holder.dialog == null) return;
        if (holder.dialog.isShowing()) holder.dialog.dismiss();
    }

    private int countWorkUnits(List<File> files) {
        if (files == null || files.isEmpty()) return 0;
        int total = 0;
        for (File f : files) {
            total += countWorkUnits(f);
        }
        return total;
    }

    private int countWorkUnits(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return 1;

        File[] children = file.listFiles();
        if (children == null || children.length == 0) return 1;

        int total = 0;
        for (File child : children) {
            total += countWorkUnits(child);
        }
        return Math.max(1, total);
    }

    // ─────────────────────── FILE OPS ────────────────────────────────

    private boolean isPublicStorageFile(File file) {
        if (file == null) return false;
        String extRoot = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String path = file.getAbsolutePath();
        if (!path.startsWith(extRoot)) return false;
        java.io.File appDir = getExternalFilesDir(null);
        if (appDir != null && path.startsWith(appDir.getAbsolutePath())) return false;
        return true;
    }

    private boolean isAllDirectories(List<File> files) {
        if (files == null || files.isEmpty()) return false;
        for (File f : files) if (!f.isDirectory()) return false;
        return true;
    }

    private void setVis(int id, boolean show) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void moveToTrash(File file) {
        // When buttons stack vertically the order is Positive (top) → Negative → Neutral (bottom).
        // Desired order: Eliminar definitivamente / Cancelar / Mover a la papelera.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_single_message, file.getName()))
                .setPositiveButton(R.string.delete_forever, (d, w) -> showDeleteForeverWarningForSingle(file))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.move_to_trash, (d, w) -> {
                    android.app.PendingIntent pi = TrashManager.createSystemTrashRequest(this, Collections.singletonList(file));
                    if (pi != null) {
                        try {
                            startIntentSenderForResult(pi.getIntentSender(), REQ_SYSTEM_TRASH, null, 0, 0, 0);
                            return;
                        } catch (Exception ignored) {}
                    }
                    // System trash unavailable: delete directly
                    if (FileOperations.delete(file)) {
                        loadDirectory(currentDir);
                        toast(getString(R.string.deleted_done));
                    } else {
                        toast(getString(R.string.error_deleting_item));
                    }
                })
                .create();
        dialog.setOnShowListener(d -> {
            Button deleteForever = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (deleteForever != null) deleteForever.setTextColor(0xFFD32F2F);
        });
        dialog.show();
    }

    private void showDeleteForeverWarningForSingle(File file) {
        if (file == null) return;
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.warning_title)
                .setMessage(getString(R.string.warning_delete_forever_single_message, file.getName()))
                .setPositiveButton(R.string.delete_forever, (d, w) -> {
                    if (FileOperations.delete(file)) {
                        loadDirectory(currentDir);
                        toast(getString(R.string.deleted_done));
                    } else {
                        toast(getString(R.string.error_deleting_item));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openFile(File file) {
        try {
            if (".apk".equals(getExtensionKey(file))) {
                confirmAndInstallApk(file);
                return;
            }

            String mime = getMimeType(file);
            String ext = getExtensionKey(file);

            // For images, prefer a MediaStore URI so apps like Google Photos can
            // navigate to adjacent images in the same album/folder.
            Uri mediaStoreUri = mime.startsWith("image/") ? getMediaStoreUri(file) : null;
            Uri uri = mediaStoreUri != null
                    ? mediaStoreUri
                    : FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            int grantFlags = mediaStoreUri != null ? 0 : Intent.FLAG_GRANT_READ_URI_PERMISSION;

            String preferredPackage = DefaultAppsManager.getPackageForExtension(this, ext);
            if (preferredPackage != null && !preferredPackage.trim().isEmpty()) {
                Intent preferred = new Intent(Intent.ACTION_VIEW);
                preferred.setDataAndType(uri, mime);
                preferred.setPackage(preferredPackage);
                if (grantFlags != 0) preferred.addFlags(grantFlags);
                if (mediaStoreUri == null) {
                    Uri providerUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                    attachSiblingImagesIfNeeded(preferred, file, providerUri, mime);
                }
                try {
                    startActivity(preferred);
                    return;
                } catch (Exception ignored) {
                    // Fallback to normal resolver when the preferred app is not available/compatible.
                }
            }

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            if (grantFlags != 0) i.addFlags(grantFlags);
            if (mediaStoreUri == null) {
                Uri providerUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                attachSiblingImagesIfNeeded(i, file, providerUri, mime);
            }

            captureDefaultHandler(i, file);
            startActivity(i);
        } catch (Exception e) { Log.e(TAG, "openFile", e); toast(getString(R.string.cannot_open_file, file.getName())); }
    }

    /**
     * Looks up the MediaStore URI for an image file by its absolute path.
     * Returns null if the file is not indexed in MediaStore yet.
     */
    private Uri getMediaStoreUri(File file) {
        String[] projection = {MediaStore.Images.Media._ID};
        String selection = MediaStore.Images.Media.DATA + "=?";
        String[] selectionArgs = {file.getAbsolutePath()};
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception e) {
            Log.w(TAG, "getMediaStoreUri", e);
        }
        return null;
    }

    /**
     * When opening an image file, attaches all sibling images from the same directory
     * as ClipData extras. Gallery apps that support this pattern (Simple Gallery, etc.)
     * use it to enable swiping between images. Apps that don't support it open only the
     * primary URI, so there is no regression for other apps.
     */
    private void attachSiblingImagesIfNeeded(Intent intent, File file, Uri primaryUri, String mime) {
        if (mime == null || !mime.startsWith("image/")) return;
        File dir = file.getParentFile();
        if (dir == null) return;

        File[] siblings = dir.listFiles(f -> {
            if (!f.isFile()) return false;
            String m = getMimeType(f);
            return m.startsWith("image/");
        });
        if (siblings == null || siblings.length <= 1) return;

        java.util.Arrays.sort(siblings, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        ClipData clip = new ClipData(new ClipDescription("images", new String[]{"image/*"}),
                new ClipData.Item(primaryUri));
        for (File sibling : siblings) {
            if (sibling.getName().equals(file.getName())) continue;
            try {
                Uri sibUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", sibling);
                clip.addItem(new ClipData.Item(sibUri));
            } catch (Exception ignored) {}
        }
        intent.setClipData(clip);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private void confirmAndInstallApk(File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.apk_install_warning_title)
                .setMessage(R.string.apk_install_warning_message)
                .setPositiveButton(R.string.continue_action, (d, w) -> openApkInstaller(file))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openApkInstaller(File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
                toast(getString(R.string.allow_install_unknown_apps));
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settingsIntent);
                return;
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "openApkInstaller", e);
            toast(getString(R.string.cannot_open_file, file.getName()));
        }
    }

    private void openFileWithSystemResolver(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, getMimeType(file));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "openFileWithSystemResolver", e);
            toast(getString(R.string.cannot_open_file, file.getName()));
        }
    }

    private void showSetDefaultAppDialog(File file, boolean openAfterSelection) {
        List<AppChoice> candidates = getAppsForFileDefaultPicker(file);
        List<AppChoice> moreUserApps = getAllAppsForDefaultPicker(true);
        if (candidates.isEmpty()) {
            toast(getString(R.string.no_apps_found));
            return;
        }
        String ext = getExtensionKey(file);
        String currentPackage = DefaultAppsManager.getPackageForExtension(this, ext);
        showAppChoiceDialog(
                getString(R.string.open),
                candidates,
                moreUserApps,
                currentPackage,
                selected -> {
                    DefaultAppsManager.add(this, ext, selected.packageName, selected.label);
                    toast(getString(R.string.default_app_set_for_extension, ext));
                    if (openAfterSelection) {
                        openFileWithSelectedPackage(file, selected.packageName);
                    }
                    if (adapter != null && adapter.isSelectionMode()) {
                        exitSelectionMode();
                    }
                }
        );
    }

    private List<AppChoice> getAppsForFileDefaultPicker(File file) {
        Map<String, AppChoice> dedup = new LinkedHashMap<>();
        try {
            PackageManager pm = getPackageManager();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(uri, getMimeType(file));
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            List<ResolveInfo> apps = pm.queryIntentActivities(viewIntent, 0);
            for (ResolveInfo info : apps) {
                if (info == null || info.activityInfo == null || info.activityInfo.applicationInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || pkg.trim().isEmpty()) continue;
                if (pkg.equals(getPackageName())) continue;

                int flags = info.activityInfo.applicationInfo.flags;
                boolean isSystem = (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        || (flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (isSystem) continue;

                CharSequence rawLabel = info.loadLabel(pm);
                String label = rawLabel == null ? pkg : rawLabel.toString().trim();
                if (label.isEmpty()) label = pkg;
                Drawable icon;
                try {
                    icon = info.loadIcon(pm);
                } catch (Exception ignored) {
                    icon = null;
                }
                if (!dedup.containsKey(pkg)) {
                    dedup.put(pkg, new AppChoice(pkg, label, icon));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getAppsForFileDefaultPicker", e);
        }

        List<AppChoice> out = new ArrayList<>(dedup.values());
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private List<AppChoice> getAllAppsForDefaultPicker(boolean includeSystemApps) {
        Map<String, AppChoice> dedup = new LinkedHashMap<>();
        try {
            PackageManager pm = getPackageManager();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);

            for (ResolveInfo info : apps) {
                if (info == null || info.activityInfo == null || info.activityInfo.applicationInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || pkg.trim().isEmpty()) continue;
                if (pkg.equals(getPackageName())) continue;

                int flags = info.activityInfo.applicationInfo.flags;
                boolean isSystem = (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    || (flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (!includeSystemApps && isSystem) continue;

                CharSequence rawLabel = info.loadLabel(pm);
                String label = rawLabel == null ? pkg : rawLabel.toString().trim();
                if (label.isEmpty()) label = pkg;

                Drawable icon;
                try {
                    icon = info.loadIcon(pm);
                } catch (Exception ignored) {
                    icon = null;
                }

                if (!dedup.containsKey(pkg)) {
                    dedup.put(pkg, new AppChoice(pkg, label, icon));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getAllAppsForDefaultPicker", e);
        }

        List<AppChoice> out = new ArrayList<>(dedup.values());
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private void openFileWithSelectedPackage(File file, String packageName) {
        if (file == null || packageName == null || packageName.trim().isEmpty()) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, getMimeType(file));
            i.setPackage(packageName);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "openFileWithSelectedPackage", e);
            toast(getString(R.string.cannot_open_file, file.getName()));
        }
    }

    private void showAppChoiceDialog(String title,
                                     List<AppChoice> candidates,
                                     List<AppChoice> moreCandidates,
                                     String selectedPackage,
                                     java.util.function.Consumer<AppChoice> onSelected) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int hp = dp(16);
        root.setPadding(hp, dp(10), hp, 0);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(getString(R.string.search_apps_hint));
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(420));
        listParams.topMargin = dp(8);
        root.addView(listView, listParams);

        AppChoiceAdapter adapter = new AppChoiceAdapter(candidates, selectedPackage);
        listView.setAdapter(adapter);

        final boolean canShowMore = moreCandidates != null && !moreCandidates.isEmpty()
            && moreCandidates.size() > candidates.size();

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(root)
            .setNegativeButton(R.string.cancel, null);
        if (canShowMore) {
            dialogBuilder.setNeutralButton(R.string.show_more_apps, null);
        }
        AlertDialog dialog = dialogBuilder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppChoice selected = adapter.getItem(position);
            if (selected != null) {
                dialog.dismiss();
                onSelected.accept(selected);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.show();

        if (canShowMore) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                showExpandedAppChoiceDialog(
                        title,
                        moreCandidates,
                        selectedPackage,
                        onSelected
                );
            });
        }
    }

    private void showExpandedAppChoiceDialog(String title,
                                             List<AppChoice> expandedCandidates,
                                             String selectedPackage,
                                             java.util.function.Consumer<AppChoice> onSelected) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int hp = dp(16);
        root.setPadding(hp, dp(10), hp, 0);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(getString(R.string.search_apps_hint));
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(420));
        listParams.topMargin = dp(8);
        root.addView(listView, listParams);

        AppChoiceAdapter adapter = new AppChoiceAdapter(expandedCandidates, selectedPackage);
        listView.setAdapter(adapter);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(root)
                .setPositiveButton(R.string.cancel, null);
        AlertDialog dialog = dialogBuilder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppChoice selected = adapter.getItem(position);
            if (selected != null) {
                dialog.dismiss();
                onSelected.accept(selected);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.show();
    }

    private void alignDialogButtonStart(AlertDialog dialog, int whichButton) {
        if (dialog == null) return;
        Button button = dialog.getButton(whichButton);
        if (button == null) return;
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    }

    private class AppChoiceAdapter extends BaseAdapter {
        private final List<AppChoice> all;
        private final List<AppChoice> filtered;
        private final String selectedPackage;

        AppChoiceAdapter(List<AppChoice> initial, String selectedPackage) {
            this.all = new ArrayList<>(initial);
            this.filtered = new ArrayList<>(initial);
            this.selectedPackage = selectedPackage;
        }

        void filter(String query) {
            String q = query == null ? "" : query.trim().toLowerCase();
            filtered.clear();
            if (q.isEmpty()) {
                filtered.addAll(all);
            } else {
                for (AppChoice c : all) {
                    String label = c.label == null ? "" : c.label.toLowerCase();
                    String pkg = c.packageName == null ? "" : c.packageName.toLowerCase();
                    if (label.contains(q) || pkg.contains(q)) {
                        filtered.add(c);
                    }
                }
            }
            notifyDataSetChanged();
        }

        void replaceAll(List<AppChoice> replacement) {
            all.clear();
            if (replacement != null) {
                all.addAll(replacement);
            }
            filter("");
        }

        @Override
        public int getCount() {
            return filtered.size();
        }

        @Override
        public AppChoice getItem(int position) {
            return (position >= 0 && position < filtered.size()) ? filtered.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_app_choice, parent, false);
            }

            AppChoice item = getItem(position);
            if (item == null) return v;

            ImageView icon = v.findViewById(R.id.app_icon);
            TextView label = v.findViewById(R.id.app_label);
            TextView pkg = v.findViewById(R.id.app_package);

            if (item.icon != null) icon.setImageDrawable(item.icon);
            else icon.setImageDrawable(null);

            boolean selected = selectedPackage != null && selectedPackage.equals(item.packageName);
            label.setText(selected ? ("✓ " + item.label) : item.label);
            pkg.setText(item.packageName);

            return v;
        }
    }

    private void captureDefaultHandler(Intent viewIntent, File sourceFile) {
        PackageManager pm = getPackageManager();

        ResolveInfo resolvedDefault = pm.resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (isUsableExternalHandler(resolvedDefault)) {
            saveResolvedHandler(pm, resolvedDefault, sourceFile);
            return;
        }

        // Some preferred handlers may resolve without MATCH_DEFAULT_ONLY.
        ResolveInfo resolvedPreferred = pm.resolveActivity(viewIntent, 0);
        if (isUsableExternalHandler(resolvedPreferred)) {
            saveResolvedHandler(pm, resolvedPreferred, sourceFile);
            return;
        }

        // Fallback: if there is only one real handler, treat it as effective default.
        List<ResolveInfo> handlers = pm.queryIntentActivities(viewIntent, 0);
        ResolveInfo singleUsable = null;
        for (ResolveInfo info : handlers) {
            if (!isUsableExternalHandler(info)) continue;
            if (singleUsable != null) {
                // More than one candidate and no explicit default => unknown selection.
                return;
            }
            singleUsable = info;
        }
        if (singleUsable != null) {
            saveResolvedHandler(pm, singleUsable, sourceFile);
        }
    }

    private boolean isUsableExternalHandler(ResolveInfo info) {
        if (info == null || info.activityInfo == null) return false;
        String pkg = info.activityInfo.packageName;
        if (pkg == null || pkg.trim().isEmpty()) return false;
        if (pkg.equals(getPackageName())) return false;

        String className = info.activityInfo.name == null ? "" : info.activityInfo.name;
        String lowerClass = className.toLowerCase();
        String lowerPkg = pkg.toLowerCase();

        // Ignore system choosers/resolvers.
        if ("android".equals(lowerPkg)) return false;
        if (lowerClass.contains("resolveractivity") || lowerClass.contains("chooseractivity")) return false;
        return true;
    }

    private void saveResolvedHandler(PackageManager pm, ResolveInfo info, File sourceFile) {
        String pkg = info.activityInfo.packageName;
        CharSequence label = info.loadLabel(pm);
        DefaultAppsManager.add(this, getExtensionKey(sourceFile), pkg, label != null ? label.toString() : pkg);
    }

    private String getExtensionKey(File file) {
        if (file == null) return "*";
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) return "*";
        return ("." + name.substring(dot + 1)).toLowerCase();
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(getMimeType(file));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.share_via)));
        } catch (Exception e) { Log.e(TAG, "shareFile", e); toast(getString(R.string.error_sharing)); }
    }

    private String getMimeType(File file) {
        String ext = file.getName().contains(".")
                ? file.getName().substring(file.getName().lastIndexOf('.') + 1) : "";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return m != null ? m : "*/*";
    }

    private void showRenameDialog(File file) {
        showRenameDialog(file, false);
    }

    private void showRenameDialog(File file, boolean clearSelectionOnSuccess) {
        EditText et = new EditText(this);
        String originalName = file.getName();
        et.setText(originalName);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.bg_prompt_underline);
        int pad = dp(8);
        et.setPadding(0, pad, 0, pad);
        et.setSelection(originalName.length());
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int margin = dp(20);
        wrap.setPadding(margin, dp(6), margin, 0);
        wrap.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(R.string.rename_title).setView(wrap)
            .setPositiveButton(R.string.accept, (d, w) -> {
                String n = et.getText().toString().trim();
                if (!n.isEmpty() && !n.equals(file.getName())) {
                    String oldExt = getExtensionPart(file.getName());
                    String newExt = getExtensionPart(n);
                    if (!oldExt.isEmpty() && !oldExt.equalsIgnoreCase(newExt)) {
                        String onlyName = replaceNameKeepingOriginalExtension(n, oldExt);
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.extension_changed_title)
                                .setMessage(R.string.extension_changed_message)
                                .setPositiveButton(R.string.rename_name_and_extension, (d2, w2) ->
                                        applyRename(file, n, clearSelectionOnSuccess))
                                .setNeutralButton(R.string.rename_only_name, (d2, w2) ->
                                        applyRename(file, onlyName, clearSelectionOnSuccess))
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    } else {
                        applyRename(file, n, clearSelectionOnSuccess);
                    }
                }
            }).setNegativeButton(R.string.cancel, null).show();
    }

    private void applyRename(File source, String newName, boolean clearSelectionOnSuccess) {
        String oldPath = source.getAbsolutePath();
        if (FileOperations.rename(source, newName)) {
            String newPath = new File(source.getParentFile(), newName).getAbsolutePath();
            RecentManager.renamePath(this, oldPath, newPath);
            if (clearSelectionOnSuccess) {
                exitSelectionMode();
            }
            if (!updateRenamedItemInCurrentList(source, newName) && currentDir != null) {
                loadDirectory(currentDir);
            }
            if (currentTab == TAB_RECENT) loadRecentFiles();
            toast(getString(R.string.renamed));
        } else {
            toast(getString(R.string.rename_error));
        }
    }

    private boolean updateRenamedItemInCurrentList(File source, String newName) {
        if (source == null || newName == null || currentDir == null) return false;
        File parent = source.getParentFile();
        if (parent == null || !parent.equals(currentDir)) return false;

        String oldPath = source.getAbsolutePath();
        int oldIndex = -1;
        boolean wasSelected = false;
        for (int i = 0; i < fileItems.size(); i++) {
            FileItem item = fileItems.get(i);
            if (item != null && item.getFile() != null && oldPath.equals(item.getFile().getAbsolutePath())) {
                oldIndex = i;
                wasSelected = item.isSelected();
                break;
            }
        }
        if (oldIndex < 0) return false;

        File renamedFile = new File(parent, newName);
        FileItem replacement = new FileItem(renamedFile);
        replacement.setSelected(wasSelected);

        fileItems.remove(oldIndex);

        Comparator<File> comp = getComparator();
        int insertIndex = fileItems.size();
        for (int i = 0; i < fileItems.size(); i++) {
            FileItem candidate = fileItems.get(i);
            if (candidate == null || candidate.getFile() == null) continue;
            if (comp.compare(renamedFile, candidate.getFile()) < 0) {
                insertIndex = i;
                break;
            }
        }

        fileItems.add(insertIndex, replacement);
        if (adapter != null) adapter.notifyDataSetChanged();
        return true;
    }

    private String getExtensionPart(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "";
    }

    private String replaceNameKeepingOriginalExtension(String typedName, String originalExt) {
        int dot = typedName.lastIndexOf('.');
        String base = dot > 0 ? typedName.substring(0, dot) : typedName;
        if (originalExt == null || originalExt.isEmpty()) return base;
        return base + "." + originalExt;
    }

    private void showDetails(FileItem item) {
        File f = item.getFile();
        String type = getDetailsTypeText(f);
        String createdAt = getFileCreatedAt(f);
        String modifiedAt = item.getFormattedDate();
        new AlertDialog.Builder(this).setTitle(R.string.details_title)
            .setMessage(getString(R.string.details_message,
                    f.getName(),
                    type,
                    item.getFormattedSize(this),
                    createdAt,
                    modifiedAt,
                    f.getAbsolutePath()))
            .setPositiveButton(R.string.close, null).show();
    }

    private String getDetailsTypeText(File file) {
        if (file == null) return getString(R.string.file);
        if (file.isDirectory()) return getString(R.string.folder);

        String ext = getExtensionKey(file);
        String extPart = "";
        if (ext != null && !"*".equals(ext)) {
            extPart = ext;
        }

        String mime = getMimeType(file);
        boolean hasMime = mime != null && !mime.trim().isEmpty() && !"*/*".equals(mime);

        if (hasMime && !extPart.isEmpty()) {
            return mime + " (" + extPart + ")";
        }
        if (hasMime) {
            return mime;
        }
        if (!extPart.isEmpty()) {
            return extPart;
        }
        return getString(R.string.file);
    }

    private String getFileCreatedAt(File file) {
        if (file == null) return "-";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                long created = attrs.creationTime().toMillis();
                if (created > 0) {
                    return formatTimestamp(created);
                }
            }
        } catch (Exception ignored) {
        }
        return formatTimestamp(file.lastModified());
    }

    private String formatTimestamp(long timeMillis) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(timeMillis));
    }

    private void showNewFolderDialog() {
        EditText et = new EditText(this);
        et.setHint(R.string.folder_name_hint);
        new AlertDialog.Builder(this).setTitle(R.string.new_folder).setView(et)
            .setPositiveButton(R.string.create, (d, w) -> {
                String n = et.getText().toString().trim();
                if (!n.isEmpty()) {
                    if (FileOperations.createDirectory(currentDir, n)) { loadDirectory(currentDir); toast(getString(R.string.folder_created)); }
                    else toast(getString(R.string.folder_create_error));
                }
            }).setNegativeButton(R.string.cancel, null).show();
    }

    private void toggleSearch() {
        if (searchBar == null) return;
        if (searchBar.getVisibility() == View.VISIBLE) {
            cancelRecursiveSearch(false);
            searchBar.setVisibility(View.GONE);
            if (searchInput != null) searchInput.setText("");
            // Hide keyboard
            if (searchInput != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
            if (currentTab == TAB_RECENT) {
                loadRecentFiles();
            } else if (currentDir != null) {
                loadDirectory(currentDir);
            }
        } else {
            searchBar.setVisibility(View.VISIBLE);
            if (searchInput != null) {
                searchInput.requestFocus();
                // Show keyboard automatically
                searchInput.postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
                }, 100);
            }
        }
    }

    @Override
    protected void onDestroy() {
        cancelRecursiveSearch(false);
        cancelDirectoryLoad();
        if (autoRecentScanThread != null) {
            autoRecentScanThread.interrupt();
            autoRecentScanThread = null;
        }
        if (isFinishing()) {
            cleanupTempPlaylist();
        }
        super.onDestroy();
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}


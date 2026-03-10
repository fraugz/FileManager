package com.fraugz.filemanager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

import android.content.res.ColorStateList;

public class MainActivity extends AppCompatActivity implements FileAdapter.Listener {

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSION = 100;
    private static final int REQ_MANAGE_FILES = 101;
    private static final int SORT_NAME = 0, SORT_DATE = 1, SORT_SIZE = 2;
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
    private TextView pasteLabel, sectionHeaderLabel, pageTitle, recentTitle, searchStatus, cancelSearchBtn, loadingStatusLabel;
    private TextView actionSendLabel, actionMoveLabel, actionCopyLabel, actionDeleteLabel, actionRenameLabel;
    private ImageView actionSendIcon, actionMoveIcon, actionCopyIcon, actionDeleteIcon, actionRenameIcon;
    private ProgressBar searchProgress;
    private SwipeRefreshLayout swipeRefresh;

    // Custom bottom nav
    private LinearLayout bottomNav, tabRecent, tabStorage;
    private ImageView tabRecentIcon, tabStorageIcon;
    private TextView tabRecentLabel, tabStorageLabel;

    // Adapters
    private FileAdapter adapter;
    private RecentAdapter recentAdapter;
    private final List<FileItem> fileItems = new ArrayList<>();
    private final List<File> recentFilesCache = new ArrayList<>();

    // State
    private File currentDir;
    private final Stack<File> backStack = new Stack<>();
    private final List<File> clipboardFiles = new ArrayList<>();
    private boolean clipboardIsCopy = false;
    private volatile boolean recursiveSearchCancelled = false;
    private Thread recursiveSearchThread;
    private int searchRequestId = 0;
    private volatile boolean directoryLoadCancelled = false;
    private Thread directoryLoadThread;
    private int directoryLoadRequestId = 0;
    private final Runnable deferredSearch = this::startRecursiveSearchFromInput;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GestureDetector gestureDetector;

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
        requestPermissions();
        Log.d(TAG, "onCreate complete");
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
        sectionHeaderLabel  = findViewById(R.id.section_header_label);
        pageTitle           = findViewById(R.id.page_title);
        recentTitle         = findViewById(R.id.recent_title);
        searchProgress      = findViewById(R.id.search_progress);
        searchStatus        = findViewById(R.id.search_status);
        cancelSearchBtn     = findViewById(R.id.btn_cancel_search);
        loadingStatusLabel  = findViewById(R.id.loading_status_label);
        actionSendLabel     = findViewById(R.id.action_send_label);
        actionMoveLabel     = findViewById(R.id.action_move_label);
        actionCopyLabel     = findViewById(R.id.action_copy_label);
        actionDeleteLabel   = findViewById(R.id.action_delete_label);
        actionRenameLabel   = findViewById(R.id.action_rename_label);
        actionSendIcon      = findViewById(R.id.action_send_icon);
        actionMoveIcon      = findViewById(R.id.action_move_icon);
        actionCopyIcon      = findViewById(R.id.action_copy_icon);
        actionDeleteIcon    = findViewById(R.id.action_delete_icon);
        actionRenameIcon    = findViewById(R.id.action_rename_icon);
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
        if (sectionHeaderLabel != null) sectionHeaderLabel.setTextColor(accent);

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
        if (actionMoveLabel != null) actionMoveLabel.setTextColor(iconTint);
        if (actionCopyLabel != null) actionCopyLabel.setTextColor(iconTint);
        if (actionDeleteLabel != null) actionDeleteLabel.setTextColor(iconTint);
        if (actionRenameLabel != null) actionRenameLabel.setTextColor(iconTint);
        if (actionSendIcon != null) actionSendIcon.setColorFilter(iconTint);
        if (actionMoveIcon != null) actionMoveIcon.setColorFilter(iconTint);
        if (actionCopyIcon != null) actionCopyIcon.setColorFilter(iconTint);
        if (actionDeleteIcon != null) actionDeleteIcon.setColorFilter(iconTint);
        if (actionRenameIcon != null) actionRenameIcon.setColorFilter(iconTint);

        int[] iconBtns = {R.id.btn_search, R.id.btn_filter, R.id.btn_trash, R.id.btn_overflow, R.id.btn_new_folder_inline};
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
        if (pageTitle != null) pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f * uiScale);
        if (recentTitle != null) recentTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f * uiScale);
        if (sectionHeaderLabel != null) sectionHeaderLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * uiScale);
        if (searchInput != null) searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * uiScale);
        if (pasteLabel != null) pasteLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * uiScale);
        if (tabRecentLabel != null) tabRecentLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * uiScale);
        if (tabStorageLabel != null) tabStorageLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * uiScale);
        if (searchStatus != null) searchStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (loadingStatusLabel != null) loadingStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (cancelSearchBtn != null) cancelSearchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * uiScale);
        if (actionSendLabel != null) actionSendLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (actionMoveLabel != null) actionMoveLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (actionCopyLabel != null) actionCopyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (actionDeleteLabel != null) actionDeleteLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);
        if (actionRenameLabel != null) actionRenameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * uiScale);

        int iconSize = dp(44f * uiScale);
        int[] topIconButtons = {R.id.btn_search, R.id.btn_filter, R.id.btn_trash, R.id.btn_overflow};
        for (int id : topIconButtons) {
            View v = findViewById(id);
            setSquareSize(v, iconSize);
        }

        setSquareSize(findViewById(R.id.btn_new_folder_inline), dp(38f * uiScale));
        setSquareSize(findViewById(R.id.btn_paste_dismiss), dp(36f * uiScale));
        setSquareSize(actionSendIcon, dp(24f * uiScale));
        setSquareSize(actionMoveIcon, dp(24f * uiScale));
        setSquareSize(actionCopyIcon, dp(24f * uiScale));
        setSquareSize(actionDeleteIcon, dp(24f * uiScale));
        setSquareSize(actionRenameIcon, dp(24f * uiScale));

        if (adapter != null) adapter.setUiScale(uiScale);
        if (recentAdapter != null) recentAdapter.setUiScale(uiScale);
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

        if (storageActive) showStorageView();
        else               showRecentView();
    }

    // ─────────────────────── SETUP ───────────────────────────────────

    private void setupRecyclerViews() {
        if (recycler != null) {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
            adapter = new FileAdapter(fileItems, this);
            adapter.setDarkTheme(isDark);
            adapter.setUiScale(uiScale);
            recycler.setAdapter(adapter);
        }

        if (recyclerRecent != null) {
            GridLayoutManager glm = new GridLayoutManager(this, 3);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int pos) {
                    return (recentAdapter != null && recentAdapter.getItemViewType(pos) == 0) ? 3 : 1;
                }
            });
            recyclerRecent.setLayoutManager(glm);
            recentAdapter = new RecentAdapter(this, new ArrayList<>(), file -> {
                RecentManager.add(this, file.getAbsolutePath());
                openFile(file);
            });
            recentAdapter.setDarkTheme(isDark);
            recentAdapter.setUiScale(uiScale);
            recyclerRecent.setAdapter(recentAdapter);
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
        setClickSafe(R.id.btn_new_folder_inline, v -> showNewFolderDialog());
        setClickSafe(R.id.btn_paste,             v -> pasteClipboard());
        setClickSafe(R.id.btn_paste_dismiss,     v -> {
            clipboardFiles.clear();
            updatePasteBar();
        });
        setClickSafe(R.id.action_send,           v -> shareSelectedFiles());
        setClickSafe(R.id.action_move,           v -> markSelectionForMove());
        setClickSafe(R.id.action_copy,           v -> copySelection());
        setClickSafe(R.id.action_delete,         v -> deleteSelectionToTrash());
        setClickSafe(R.id.action_rename,         v -> renameSelection());

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
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 80;
            private static final int SWIPE_MIN_VELOCITY = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < Math.abs(dy)) return false;
                if (Math.abs(dx) < SWIPE_MIN_DISTANCE || Math.abs(velocityX) < SWIPE_MIN_VELOCITY) return false;

                if (dx < 0 && currentTab != TAB_STORAGE) {
                    selectTab(TAB_STORAGE);
                    return true;
                }
                if (dx > 0 && currentTab != TAB_RECENT) {
                    selectTab(TAB_RECENT);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
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
    }

    private void showRecentView() {
        if (viewStorage != null) viewStorage.setVisibility(View.GONE);
        if (viewRecent != null)  viewRecent.setVisibility(View.VISIBLE);
        View sortBtn = findViewById(R.id.btn_filter);
        if (sortBtn != null) sortBtn.setVisibility(View.GONE);
        loadRecentFiles();
    }

    private void loadRecentFiles() {
        try {
            List<String> paths = RecentManager.get(this);
            List<File> files = new ArrayList<>();
            for (String p : paths) {
                File f = new File(p);
                if (f.exists() && f.isFile()) files.add(f);
            }

            recentFilesCache.clear();
            recentFilesCache.addAll(files);

            String q = searchInput != null && searchBar != null && searchBar.getVisibility() == View.VISIBLE
                    ? searchInput.getText().toString().trim()
                    : "";
            if (!q.isEmpty()) {
                applyRecentFilter(q);
                return;
            }

            boolean empty = files.isEmpty();
            if (recyclerRecent != null)  recyclerRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (emptyRecentView != null) emptyRecentView.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (!empty && recentAdapter != null) recentAdapter.setFiles(files);
        } catch (Exception e) { Log.e(TAG, "loadRecentFiles", e); }
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
        if (recentAdapter == null) return;
        String q = query.toLowerCase();
        List<File> filtered = new ArrayList<>();
        for (File f : recentFilesCache) {
            if (f.getName().toLowerCase().contains(q)) {
                filtered.add(f);
            }
        }

        boolean empty = filtered.isEmpty();
        if (recyclerRecent != null) recyclerRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyRecentView != null) emptyRecentView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) recentAdapter.setFiles(filtered);
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
            if (sectionHeaderLabel != null)
                sectionHeaderLabel.setText(isRoot ? getString(R.string.internal_storage)
                        : currentDir.getAbsolutePath().replace(root.getAbsolutePath() + "/", ""));
            if (isRoot || breadcrumbContainer == null) return;

            breadcrumbContainer.removeAllViews();
            List<File> parts = new ArrayList<>();
            File f = currentDir;
            while (f != null && !f.equals(root.getParentFile())) {
                parts.add(0, f);
                if (f.equals(root)) break;
                f = f.getParentFile();
            }
            int accent = isDark ? 0xFF1E88E5 : 0xFF1565C0;
            int muted  = isDark ? 0xFF666666 : 0xFF999999;
            for (int i = 0; i < parts.size(); i++) {
                final File part = parts.get(i);
                boolean isLast = (i == parts.size() - 1);
                TextView tv = new TextView(this);
                String label = part.equals(root) ? getString(R.string.internal_short) : part.getName();
                tv.setText(isLast ? label : label + "  ›  ");
                tv.setTextSize(12);
                tv.setTextColor(isLast ? muted : accent);
                if (!isLast) tv.setOnClickListener(v -> navigateTo(part));
                breadcrumbContainer.addView(tv);
            }
            breadcrumbScroll.post(() -> breadcrumbScroll.fullScroll(View.FOCUS_RIGHT));
        } catch (Exception e) { Log.e(TAG, "breadcrumb", e); }
    }

    // ─────────────────────── NAVIGATION ──────────────────────────────

    private void navigateTo(File dir) {
        backStack.push(currentDir);
        loadDirectory(dir);
        exitSelectionMode();
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isSelectionMode()) { exitSelectionMode(); return; }
        if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) { toggleSearch(); return; }
        if (!backStack.isEmpty()) { loadDirectory(backStack.pop()); return; }
        super.onBackPressed();
    }

    // ─────────────────────── ADAPTER CALLBACKS ───────────────────────

    @Override
    public void onItemClick(FileItem item) {
        try {
            if (adapter != null && adapter.isSelectionMode()) {
                item.setSelected(!item.isSelected());
                adapter.notifyDataSetChanged();
                if (fileItems.stream().noneMatch(FileItem::isSelected)) exitSelectionMode();
                else updateSelectionActionsBar();
                return;
            }
            if (item.isDirectory()) {
                navigateTo(item.getFile());
            } else {
                RecentManager.add(this, item.getFile().getAbsolutePath());
                openFile(item.getFile());
            }
        } catch (Exception e) { Log.e(TAG, "onItemClick", e); toast(getString(R.string.error_with_reason, e.getMessage())); }
    }

    @Override
    public void onItemLongClick(FileItem item) {
        try {
            if (adapter != null && !adapter.isSelectionMode()) adapter.setSelectionMode(true);
            item.setSelected(true);
            if (adapter != null) adapter.notifyDataSetChanged();
            updateSelectionActionsBar();
        } catch (Exception e) { Log.e(TAG, "onItemLongClick", e); }
    }

    @Override
    public void onMoreClick(FileItem item, View anchor) {
        // Per-item overflow is intentionally hidden in favor of the bottom selection action bar.
    }

    // ─────────────────────── SELECTION ───────────────────────────────

    private void exitSelectionMode() {
        if (adapter == null) return;
        adapter.setSelectionMode(false);
        for (FileItem fi : fileItems) fi.setSelected(false);
        adapter.notifyDataSetChanged();
        updateSelectionActionsBar();
    }

    private List<File> getSelectedFiles() {
        List<File> sel = new ArrayList<>();
        for (FileItem fi : fileItems) if (fi.isSelected()) sel.add(fi.getFile());
        return sel;
    }

    private void updateSelectionActionsBar() {
        boolean selectionMode = adapter != null && adapter.isSelectionMode() && !getSelectedFiles().isEmpty();
        if (selectionActionsBar != null) {
            selectionActionsBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        }
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
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_selected_message, sel.size()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    int moved = 0;
                    String firstError = null;
                    for (File f : sel) {
                        if (TrashManager.moveToTrash(this, f)) {
                            moved++;
                        } else if (firstError == null) {
                            firstError = TrashManager.getLastError();
                        }
                    }
                    if (moved == sel.size()) {
                        toast(getString(R.string.moved_to_trash_count, moved));
                    } else {
                        String reason = (firstError == null || firstError.trim().isEmpty())
                            ? getString(R.string.unknown_reason)
                                : firstError;
                        toast(getString(R.string.moved_partial_error, moved, sel.size(), reason));
                    }
                    exitSelectionMode();
                    loadDirectory(currentDir);
                })
                    .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareSelectedFiles() {
        List<File> sel = getSelectedFiles();
        if (sel.isEmpty()) {
            toast(getString(R.string.no_items_selected));
            return;
        }
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : sel) {
                if (f.isFile()) {
                    uris.add(FileProvider.getUriForFile(this, getPackageName() + ".provider", f));
                }
            }
            if (uris.isEmpty()) {
                toast(getString(R.string.only_files_can_be_shared));
                return;
            }

            Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.setType("*/*");
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.share_via)));
        } catch (Exception e) {
            Log.e(TAG, "shareSelectedFiles", e);
            toast(getString(R.string.error_sharing_selection));
        }
    }

    // ─────────────────────── MENUS ───────────────────────────────────

    private void showFileMenu(FileItem item, View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(0, 1, 0, getString(R.string.open));
        p.getMenu().add(0, 2, 0, getString(R.string.copy));
        p.getMenu().add(0, 3, 0, getString(R.string.cut));
        p.getMenu().add(0, 4, 0, getString(R.string.rename));
        p.getMenu().add(0, 5, 0, getString(R.string.share));
        p.getMenu().add(0, 6, 0, getString(R.string.details));
        p.getMenu().add(0, 7, 0, getString(R.string.move_to_trash));
        p.setOnMenuItemClickListener(mi -> {
            try {
                switch (mi.getItemId()) {
                    case 1: if (item.isDirectory()) navigateTo(item.getFile()); else openFile(item.getFile()); break;
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
                        toast(getString(R.string.cut_simple));
                        break;
                    case 4: showRenameDialog(item.getFile(), false); break;
                    case 5: shareFile(item.getFile()); break;
                    case 6: showDetails(item); break;
                    case 7: moveToTrash(item.getFile()); break;
                }
            } catch (Exception e) { Log.e(TAG, "fileMenu", e); toast(getString(R.string.error_with_reason, e.getMessage())); }
            return true;
        });
        p.show();
    }

    private void showSelectionMenu(View anchor) {
        List<File> sel = getSelectedFiles();
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(0, 1, 0, getString(R.string.copy) + " (" + sel.size() + ")");
        p.getMenu().add(0, 2, 0, getString(R.string.cut) + " (" + sel.size() + ")");
        p.getMenu().add(0, 3, 0, getString(R.string.move_to_trash) + " (" + sel.size() + ")");
        p.getMenu().add(0, 4, 0, getString(R.string.share));
        p.setOnMenuItemClickListener(mi -> {
            try {
                switch (mi.getItemId()) {
                    case 1:
                        if (!sel.isEmpty()) {
                            clipboardFiles.clear();
                            clipboardFiles.addAll(sel);
                            clipboardIsCopy = true;
                            updatePasteBar();
                        }
                            toast(getString(R.string.copied_count, sel.size())); exitSelectionMode(); break;
                    case 2:
                        if (!sel.isEmpty()) {
                            clipboardFiles.clear();
                            clipboardFiles.addAll(sel);
                            clipboardIsCopy = false;
                            updatePasteBar();
                        }
                            toast(getString(R.string.cut_count, sel.size())); exitSelectionMode(); break;
                    case 3:
                        int n = 0;
                        String firstError = null;
                        for (File f : sel) {
                            if (TrashManager.moveToTrash(this, f)) {
                                n++;
                            } else if (firstError == null) {
                                firstError = TrashManager.getLastError();
                            }
                        }
                        if (n == sel.size()) {
                                toast(getString(R.string.moved_count, n));
                        } else {
                            String reason = (firstError == null || firstError.trim().isEmpty())
                                    ? getString(R.string.unknown_reason)
                                    : firstError;
                                toast(getString(R.string.moved_partial_error, n, sel.size(), reason));
                        }
                        exitSelectionMode(); loadDirectory(currentDir); break;
                    case 4: if (!sel.isEmpty()) shareFile(sel.get(0)); break;
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
                adapter.setSelectionMode(true);
                for (FileItem fi : fileItems) fi.setSelected(true);
                adapter.notifyDataSetChanged();
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
        if (!clipboardFiles.isEmpty()) {
            pasteBar.setVisibility(View.VISIBLE);
            if (pasteLabel != null) {
                if (clipboardFiles.size() == 1) {
                    pasteLabel.setText((clipboardIsCopy ? getString(R.string.copy) + ": " : getString(R.string.move) + ": ") + clipboardFiles.get(0).getName());
                } else {
                    pasteLabel.setText((clipboardIsCopy ? getString(R.string.copy) + ": " : getString(R.string.move) + ": ") + getString(R.string.items_count, clipboardFiles.size()));
                }
            }
        } else {
            pasteBar.setVisibility(View.GONE);
        }
    }

    private void pasteClipboard() {
        if (clipboardFiles.isEmpty() || currentDir == null) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        final List<File> sources = new ArrayList<>(clipboardFiles);
        new Thread(() -> {
            int ok = 0;
            String firstError = null;
            for (File source : sources) {
                try {
                    if (clipboardIsCopy) FileOperations.copySync(source, currentDir);
                    else FileOperations.moveSync(source, currentDir);
                    ok++;
                } catch (Exception e) {
                    if (firstError == null) firstError = e.getMessage();
                }
            }

            int copiedCount = ok;
            String error = firstError;
            mainHandler.post(() -> {
                loadDirectory(currentDir);
                clipboardFiles.clear();
                updatePasteBar();
                if (copiedCount > 0) {
                    toast(getString(clipboardIsCopy ? R.string.copied_result : R.string.moved_result, copiedCount));
                }
                if (error != null) {
                    toast(getString(R.string.paste_partial_error, error));
                }
            });
        }).start();
    }

    // ─────────────────────── FILE OPS ────────────────────────────────

    private void moveToTrash(File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_single_message, file.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (TrashManager.moveToTrash(this, file)) {
                        loadDirectory(currentDir);
                        toast(getString(R.string.moved_to_trash_done));
                    } else {
                        String reason = TrashManager.getLastError();
                        if (reason == null || reason.trim().isEmpty()) reason = getString(R.string.unknown_reason);
                        toast(getString(R.string.error_moving_to_trash, reason));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, getMimeType(file));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Capture the currently resolved default app to expose it in settings.
            ResolveInfo resolved = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null) {
                String pkg = resolved.activityInfo.packageName;
                if (pkg != null && !pkg.equals(getPackageName())) {
                    CharSequence label = resolved.loadLabel(getPackageManager());
                    DefaultAppsManager.add(this, pkg, label != null ? label.toString() : pkg);
                }
            }

            startActivity(i);
        } catch (Exception e) { Log.e(TAG, "openFile", e); toast(getString(R.string.cannot_open_file, file.getName())); }
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
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            et.setSelection(0, dot);
        } else {
            et.selectAll();
        }
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
        if (FileOperations.rename(source, newName)) {
            if (clearSelectionOnSuccess) {
                exitSelectionMode();
            }
            loadDirectory(currentDir);
            toast(getString(R.string.renamed));
        } else {
            toast(getString(R.string.rename_error));
        }
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
        String type = f.isDirectory() ? getString(R.string.folder) : getString(R.string.file);
        new AlertDialog.Builder(this).setTitle(R.string.details_title)
            .setMessage(getString(R.string.details_message,
                    f.getName(),
                    type,
                    item.getFormattedSize(this),
                    item.getFormattedDate(),
                    f.getAbsolutePath()))
            .setPositiveButton(R.string.close, null).show();
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
            if (currentTab == TAB_RECENT) {
                loadRecentFiles();
            } else if (currentDir != null) {
                loadDirectory(currentDir);
            }
        } else {
            searchBar.setVisibility(View.VISIBLE);
            if (searchInput != null) searchInput.requestFocus();
        }
    }

    @Override
    protected void onDestroy() {
        cancelRecursiveSearch(false);
        cancelDirectoryLoad();
        super.onDestroy();
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}


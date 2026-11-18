package com.ibrata2.player;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ibra Player Kha for phones and tablets.
 * Layout adapts to any screen: 7" 1024×600, 10" 1280×720, ultra-wide 1920×720,
 * portrait "Tesla-style" screens and phones. Touch, rotary/D-pad and steering-wheel keys.
 */
@OptIn(markerClass = UnstableApi.class)
public final class MainActivity extends Activity {

    static final String EXTRA_AUTOPLAY = "autoplay";
    static final String EXTRA_TAB = "tab";
    private static final int REQ_PERMS = 7;

    private Prefs prefs;
    private Glass g;
    private float dp;
    private final MediaRepo repo = new MediaRepo();
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private boolean scanned, scanning, pendingAutoplay, hadFullAccess;
    private Uri pendingViewUri;

    // shell
    private FrameLayout root, content;
    private LinearLayout nav;
    private String tab = "videos";
    private MediaRepo.Folder openFolder;
    private String query = "";
    // library
    private RecyclerView list;
    private RowAdapter adapter;
    private TextView header, emptyView;
    private EditText search;
    // mini player
    private LinearLayout mini;
    private ImageView miniArt;
    private TextView miniTitle, miniSub;
    private ImageButton miniPlay;
    // full player
    private FrameLayout playerPage;
    private PlayerView playerView;
    private ImageView audioArt;
    private LinearLayout pTop, pBottom;
    private TextView pTitle, pSub, pCur, pDur;
    private SeekBar pSeek;
    private ImageButton pPlay, pRepeat, pShuffle, pFav;
    private boolean playerVisible, seeking;
    private AlertDialog learnDialog;
    private byte[] lastArtData;
    private Bitmap lastArt;
    private String lastArtId;

    private TextView lastKeyView;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            if (lastKeyView != null && lastKeyView.isAttachedToWindow() && !KeyBus.lastEvent.isEmpty())
                lastKeyView.setText(KeyBus.lastEvent);
            tickDownloads();
            ui.postDelayed(this, 500);
        }
    };
    private final Runnable hideControls = () -> setControlsVisible(false);

    // ------------------------------------------------------------------ lifecycle
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        installCrashLog();
        prefs = new Prefs(this);
        Tr.setArabic(prefs.arabic());
        pendingAutoplay = getIntent().getBooleanExtra(EXTRA_AUTOPLAY, false);
        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) pendingViewUri = getIntent().getData();
        takeShared(getIntent());
        buildUi();
        requestPermissionsIfNeeded();
        showLastCrash();
    }

    /** If the app ever closes by itself, keep the reason and show it on the next start. */
    private void installCrashLog() {
        java.io.File f = new java.io.File(getFilesDir(), "crash.txt");
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        if (prev != null && prev.getClass().getName().contains("MainActivity")) return;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(f))) {
                String ver = "";
                try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
                w.println("Ibra Player Kha " + ver + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · " + Build.MANUFACTURER + " " + Build.MODEL);
                e.printStackTrace(w);
            } catch (Exception ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private void showLastCrash() {
        java.io.File f = new java.io.File(getFilesDir(), "crash.txt");
        if (!f.exists()) return;
        String txt;
        try { txt = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"); } catch (Exception e) { txt = ""; }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        if (txt.isEmpty()) return;
        final String report = txt.length() > 3000 ? txt.substring(0, 3000) : txt;
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        tv.setPadding(px(16), px(8), px(16), px(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Tr.t("crashTitle"))
                .setView(sv)
                .setPositiveButton(Tr.t("crashCopy"), (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", report));
                    toast(Tr.t("crashCopied"));
                })
                .setNegativeButton(Tr.t("close"), null)
                .show();
    }

    /** "Share" from YouTube / browser, or a tap on the download notification. */
    private boolean takeShared(Intent i) {
        if (i == null) return false;
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            String u = YtDl.extractUrl(i.getStringExtra(Intent.EXTRA_TEXT));
            if (u != null) { dlUrl = u; dlAutoFetch = true; dlInfo = null; tab = "downloads"; return true; }
        }
        if ("downloads".equals(i.getStringExtra(EXTRA_TAB))) { tab = "downloads"; return true; }
        return false;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (takeShared(intent)) { if (playerVisible) showPlayer(false); selectTab("downloads"); return; }
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            pendingViewUri = intent.getData();
            if (controller != null) playPendingUri();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
            } catch (Exception e) {
                return;
            }
            controller.addListener(playerListener);
            playerView.setPlayer(controller);
            onControllerReady();
        }, ContextCompat.getMainExecutor(this));
        ui.post(ticker);
    }

    @Override protected void onStop() {
        ui.removeCallbacks(ticker);
        if (playerView != null) playerView.setPlayer(null);
        if (controller != null) controller.removeListener(playerListener);
        MediaController.releaseFuture(controllerFuture);
        controller = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(@NonNull Configuration c) {
        super.onConfigurationChanged(c);
        boolean showPlayer = playerVisible;
        buildUi();
        if (controller != null) { playerView.setPlayer(controller); refreshPlayerState(); }
        if (showPlayer) showPlayer(true);
    }

    @Override public void onBackPressed() {
        if (playerVisible) { showPlayer(false); return; }
        if (web != null) { if (web.canGoBack()) web.goBack(); else closeBrowser(); return; }
        if (openFolder != null) { openFolder = null; refreshList(); return; }
        if (openList != null) { openList = null; refreshList(); return; }
        if (!"videos".equals(tab)) { selectTab("videos"); return; }
        moveTaskToBack(true);   // keep music playing in the background
    }

    // ------------------------------------------------------------------ permissions & scan
    private void requestPermissionsIfNeeded() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            need.add(Manifest.permission.READ_MEDIA_VIDEO);
            need.add(Manifest.permission.READ_MEDIA_AUDIO);
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT >= 23) {
            need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        List<String> missing = new ArrayList<>();
        for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (missing.isEmpty()) scan();
        else requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
    }

    @Override public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        scan();
    }

    private void scan() {
        if (scanning) return;
        scanning = true;
        header.setText(Tr.t("scanning"));
        MediaRepo.progress = 0;
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!scanning) return;
                header.setText(Tr.t("scanning") + "  " + MediaRepo.progress);
                ui.postDelayed(this, 400);
            }
        };
        ui.postDelayed(tick, 400);
        io.execute(() -> {
            MediaRepo fresh = new MediaRepo();
            fresh.scan(this);
            ui.post(() -> {
                hadFullAccess = MediaRepo.hasFullAccess(this);
                repo.videos.clear(); repo.videos.addAll(fresh.videos);
                repo.music.clear(); repo.music.addAll(fresh.music);
                repo.folders.clear(); repo.folders.addAll(fresh.folders);
                scanned = true;
                scanning = false;
                refreshList();
            });
        });
    }

    // ------------------------------------------------------------------ UI construction
    private int px(float v) { return Math.round(v * dp); }

    private void buildUi() {
        g = "system".equals(prefs.theme()) ? Glass.system(this) : Glass.of(prefs.theme());
        DisplayMetrics m = getResources().getDisplayMetrics();
        dp = m.density;
        float wDp = m.widthPixels / dp, hDp = m.heightPixels / dp;
        boolean landscape = wDp >= hDp;
        boolean compact = Math.min(wDp, hDp) < 420;   // 1024×600 @ 1.5x etc.

        try { edgeToEdge(); } catch (Exception ignored) { }
        getWindow().getDecorView().setLayoutDirection(Tr.isArabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        settingsView = null;
        dlView = null;
        if (web != null) { web.destroy(); web = null; browserView = null; }
        lastArtId = null;
        root = new FrameLayout(this);
        root.setBackground(g.backdrop());
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        this.shell = shell;
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets sb = in.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = in.getInsets(android.view.WindowInsets.Type.ime());
                l = sb.left; t = sb.top; r = sb.right; b = Math.max(sb.bottom, ime.bottom);
            } else {
                l = in.getSystemWindowInsetLeft(); t = in.getSystemWindowInsetTop();
                r = in.getSystemWindowInsetRight(); b = in.getSystemWindowInsetBottom();
            }
            insets[0] = l; insets[1] = t; insets[2] = r; insets[3] = b;
            applyInsets();
            return in;
        });

        // ---- navigation (rail on the side in landscape, bar at the bottom in portrait)
        nav = new LinearLayout(this);
        nav.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(g.panel(px(18), dp, 20));
        int pad = px(compact ? 4 : 8);
        nav.setPadding(pad, pad, pad, pad);
        if (landscape) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.logo_big);
            int ls = px(compact ? 48 : 64);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ls, ls);
            lp.bottomMargin = px(compact ? 4 : 12);
            nav.addView(logo, lp);
        }
        String[][] tabs = {{"videos", "video"}, {"music", "music"}, {"downloads", "download"}, {"folders", "folder"},
                {"playlists", "queue"}, {"settings", "settings"}};
        for (String[] t : tabs) nav.addView(navButton(t[0], t[1], landscape, compact), navParams(landscape));

        content = new FrameLayout(this);
        LinearLayout.LayoutParams navLp = landscape
                ? new LinearLayout.LayoutParams(px(compact ? 88 : 108), -1)
                : new LinearLayout.LayoutParams(-1, px(compact ? 64 : 76));
        int gap = px(compact ? 6 : 10);
        navLp.setMargins(gap, gap, landscape ? 0 : gap, gap);
        LinearLayout.LayoutParams contentLp = landscape ? new LinearLayout.LayoutParams(0, -1, 1f) : new LinearLayout.LayoutParams(-1, 0, 1f);
        if (landscape) { shell.addView(nav, navLp); shell.addView(content, contentLp); }
        else { shell.addView(content, contentLp); shell.addView(nav, navLp); }

        buildLibrary(compact, wDp);
        buildPlayer(compact);
        setContentView(root);
        try { edgeToEdge(); } catch (Exception ignored) { }
        root.requestApplyInsets();
        applyInsets();
        selectTab(tab);
        if (scanned) refreshList();
        if (controller != null) refreshPlayerState();
    }

    private LinearLayout shell;
    private final int[] insets = new int[4];

    /** Draw behind the status / navigation bars, then keep our own content clear of them. */
    private void edgeToEdge() {
        android.view.Window w = getWindow();
        w.getDecorView();   // the window's views must exist before the bar controller is asked for
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) { w.setNavigationBarContrastEnforced(false); w.setStatusBarContrastEnforced(false); }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = null;
            try { c = w.getDecorView().getWindowInsetsController(); } catch (Exception ignored) { }
            if (c != null) {
                int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(g.dark ? 0 : light, light);
            }
        } else {
            int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (!g.dark) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (!g.dark && Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(f);
        }
    }

    private void applyInsets() {
        if (shell != null) shell.setPadding(insets[0], insets[1], insets[2], insets[3]);
        if (pTop != null && pTop.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pTop.getLayoutParams();
            lp.setMargins(px(10) + insets[0], px(10) + insets[1], px(10) + insets[2], 0);
            pTop.setLayoutParams(lp);
        }
        if (pBottom != null && pBottom.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pBottom.getLayoutParams();
            lp.setMargins(px(10) + insets[0], 0, px(10) + insets[2], px(10) + insets[3]);
            pBottom.setLayoutParams(lp);
        }
    }

    private LinearLayout.LayoutParams navParams(boolean landscape) {
        return landscape ? new LinearLayout.LayoutParams(-1, 0, 1f) : new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private View navButton(String id, String icon, boolean landscape, boolean compact) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setTag(id);
        b.setFocusable(true);
        b.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icon(icon, g.text));
        int s = px(compact ? 26 : 30);
        b.addView(iv, new LinearLayout.LayoutParams(s, s));
        TextView tv = text(Tr.t(id), compact ? 11 : 12, g.text, false);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        b.addView(tv);
        b.setOnClickListener(v -> selectTab(id));
        return b;
    }

    private void selectTab(String id) {
        if (web != null) closeBrowser();
        if (!id.equals(tab)) openList = null;
        tab = id;
        openFolder = null;
        for (int i = 0; i < nav.getChildCount(); i++) {
            View v = nav.getChildAt(i);
            boolean on = id.equals(v.getTag());
            if (v.getTag() == null) continue;
            v.setBackground(on ? g.pill(g.accent, px(14)) : null);
            if (v instanceof LinearLayout) {
                LinearLayout l = (LinearLayout) v;
                ((ImageView) l.getChildAt(0)).setImageDrawable(new Icon(navIcon((String) v.getTag()), on ? g.onAccent : g.text));
                ((TextView) l.getChildAt(1)).setTextColor(on ? g.onAccent : g.text);
            }
        }
        if ("settings".equals(id)) showSettings();
        else if ("downloads".equals(id)) showDownloads();
        else { showLibrary(); refreshList(); }
    }

    private String navIcon(String id) {
        switch (id) {
            case "music": return "music";
            case "folders": return "folder";
            case "recent": return "recent";
            case "favorites": return "star";
            case "settings": return "settings";
            case "downloads": return "download";
            case "playlists": return "queue";
            default: return "video";
        }
    }

    private LinearLayout library;
    private ScrollView settingsView;

    private void buildLibrary(boolean compact, float wDp) {
        library = new LinearLayout(this);
        library.setOrientation(LinearLayout.VERTICAL);
        int gap = px(compact ? 6 : 10);
        library.setPadding(gap, gap, gap, gap);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        header = text("", compact ? 20 : 24, g.text, true);
        top.addView(header, new LinearLayout.LayoutParams(0, -2, 1f));
        search = new EditText(this);
        search.setHint(Tr.t("search"));
        search.setSingleLine(true);
        search.setTextColor(g.text);
        search.setHintTextColor(g.muted);
        search.setTextSize(16);
        search.setBackground(g.pill(g.field(), px(22)));
        search.setPadding(px(16), 0, px(16), 0);
        search.setCompoundDrawablePadding(px(8));
        Drawable si = new Icon("search", g.muted);
        si.setBounds(0, 0, px(20), px(20));
        search.setCompoundDrawablesRelative(si, null, null, null);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { query = e.toString(); refreshList(); }
        });
        top.addView(search, new LinearLayout.LayoutParams(px(compact ? 200 : 280), px(compact ? 44 : 50)));
        ImageButton rescan = iconButton("refresh", g.text, compact ? 44 : 50);
        rescan.setContentDescription(Tr.t("rescan"));
        rescan.setOnClickListener(v -> scan());
        top.addView(rescan);
        library.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout listFrame = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setClipToPadding(false);
        list.setPadding(0, gap, 0, gap);
        int cols = Math.max(1, (int) ((wDp - (compact ? 100 : 130)) / (compact ? 210 : 240)));
        GridLayoutManager glm = new GridLayoutManager(this, cols);
        list.setLayoutManager(glm);
        adapter = new RowAdapter(cols);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int pos) { return adapter.isCard(pos) ? 1 : (cols >= 4 ? cols / 2 : cols); }
        });
        list.setAdapter(adapter);
        listFrame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        emptyView = text("", 16, g.muted, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        listFrame.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        library.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        // mini player
        mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setBackground(g.panel(px(18), dp, 30));
        mini.setPadding(px(8), px(6), px(8), px(6));
        mini.setClickable(true);
        mini.setFocusable(true);
        mini.setOnClickListener(v -> showPlayer(true));
        miniArt = new ImageView(this);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int ms = px(compact ? 48 : 56);
        mini.addView(miniArt, new LinearLayout.LayoutParams(ms, ms));
        LinearLayout mt = new LinearLayout(this);
        mt.setOrientation(LinearLayout.VERTICAL);
        mt.setPadding(px(12), 0, px(12), 0);
        miniTitle = text("", 16, g.text, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniSub = text("", 13, g.muted, false);
        miniSub.setSingleLine(true);
        mt.addView(miniTitle);
        mt.addView(miniSub);
        mini.addView(mt, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton mPrev = iconButton("prev", g.text, compact ? 52 : 60);
        miniPlay = iconButton("play", g.onAccent, compact ? 52 : 60);
        miniPlay.setBackground(g.pill(g.accent, px(30)));
        ImageButton mNext = iconButton("next", g.text, compact ? 52 : 60);
        mPrev.setOnClickListener(v -> act("previous"));
        miniPlay.setOnClickListener(v -> act("playPause"));
        mNext.setOnClickListener(v -> act("next"));
        ImageButton mClose = iconButton("close", g.muted, compact ? 44 : 50);
        mClose.setContentDescription(Tr.t("stopClose"));
        mClose.setOnClickListener(v -> closePlayback());
        mini.addView(mPrev); mini.addView(miniPlay); mini.addView(mNext); mini.addView(mClose);
        mini.setVisibility(View.GONE);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = gap;
        library.addView(mini, mlp);
        content.addView(library, new FrameLayout.LayoutParams(-1, -1));
    }

    // ------------------------------------------------------------------ list content
    private void refreshList() {
        if (adapter == null || "settings".equals(tab) || "downloads".equals(tab)) return;
        List<Row> rows = new ArrayList<>();
        String title;
        switch (tab) {
            case "music":
                title = Tr.t("music");
                List<MediaRepo.Item> ms = MediaRepo.filter(repo.music, query);
                for (int i = 0; i < ms.size(); i++) rows.add(Row.item(ms.get(i), ms, i, false));
                break;
            case "folders":
                if (openFolder != null) {
                    title = openFolder.name;
                    rows.add(Row.back());
                    List<MediaRepo.Item> fs = MediaRepo.filter(openFolder.items, query);
                    for (int i = 0; i < fs.size(); i++) rows.add(Row.item(fs.get(i), fs, i, false));
                } else {
                    title = Tr.t("folders") + "  (" + repo.folders.size() + ")";
                    if (!MediaRepo.hasFullAccess(this)) rows.add(Row.grant());
                    for (MediaRepo.Folder f : repo.folders)
                        if (query.isEmpty() || f.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) rows.add(Row.folder(f));
                }
                break;
            case "playlists": {
                if (openList != null) {
                    title = listTitle(openList);
                    rows.add(Row.back());
                    List<MediaRepo.Item> f = MediaRepo.filter(listItems(openList), query);
                    for (int i = 0; i < f.size(); i++) { Row r = Row.item(f.get(i), f, i, false); r.owner = openList; rows.add(r); }
                } else {
                    title = Tr.t("playlists");
                    rows.add(Row.newList());
                    rows.add(Row.list(FAV));
                    rows.add(Row.list(RECENT));
                    for (String n : prefs.playlistNames())
                        if (query.isEmpty() || n.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) rows.add(Row.list(n));
                }
                break;
            }
            default:
                title = Tr.t("videos");
                List<MediaRepo.Item> vs = MediaRepo.filter(repo.videos, query);
                for (int i = 0; i < vs.size(); i++) rows.add(Row.item(vs.get(i), vs, i, true));
        }
        header.setText(title);
        adapter.set(rows);
        boolean empty = (rows.isEmpty() || (rows.size() == 1 && rows.get(0).back)) && !("playlists".equals(tab) && openList == null);
        emptyView.setVisibility(empty && scanned ? View.VISIBLE : View.GONE);
        emptyView.setText(Tr.t("empty"));
    }

    private MediaRepo.Item find(String uri) {
        for (MediaRepo.Item i : repo.videos) if (i.uri.equals(uri)) return i;
        for (MediaRepo.Item i : repo.music) if (i.uri.equals(uri)) return i;
        return null;
    }

    static final class Row {
        MediaRepo.Item item;
        List<MediaRepo.Item> queue;
        int index;
        MediaRepo.Folder folder;
        boolean card, back;
        static Row item(MediaRepo.Item it, List<MediaRepo.Item> q, int i, boolean card) {
            Row r = new Row(); r.item = it; r.queue = q; r.index = i; r.card = card; return r;
        }
        static Row folder(MediaRepo.Folder f) { Row r = new Row(); r.folder = f; return r; }
        static Row back() { Row r = new Row(); r.back = true; return r; }
        boolean grant;
        static Row grant() { Row r = new Row(); r.grant = true; return r; }
        String owner, listName;
        boolean newList;
        static Row list(String name) { Row r = new Row(); r.listName = name; return r; }
        static Row newList() { Row r = new Row(); r.newList = true; return r; }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Row> rows = new ArrayList<>();
        private final int cols;
        RowAdapter(int cols) { this.cols = cols; }
        @SuppressLint("NotifyDataSetChanged")
        void set(List<Row> r) { rows.clear(); rows.addAll(r); notifyDataSetChanged(); }
        boolean isCard(int pos) { return pos < rows.size() && rows.get(pos).card; }
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int pos) { return rows.get(pos).card ? 1 : 0; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout v = new LinearLayout(MainActivity.this);
            v.setFocusable(true);
            v.setClickable(true);
            v.setBackground(g.panel(px(16), dp, 0));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.setMargins(px(5), px(5), px(5), px(5));
            v.setLayoutParams(lp);
            ImageView img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setClipToOutline(true);
            TextView t1 = text("", 15, g.text, true);
            TextView t2 = text("", 12, g.muted, false);
            t1.setMaxLines(2);
            t1.setEllipsize(TextUtils.TruncateAt.END);
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            ImageView more = new ImageView(MainActivity.this);
            more.setImageDrawable(new Icon("more", g.text));
            more.setPadding(px(10), px(10), px(10), px(10));
            more.setBackground(g.pill(type == 1 ? 0x66000000 : 0, px(20)));
            more.setFocusable(true);
            more.setClickable(true);
            more.setContentDescription(Tr.t("f.more"));
            if (type == 1) more.setImageDrawable(new Icon("more", 0xFFFFFFFF));
            if (type == 1) {
                v.setOrientation(LinearLayout.VERTICAL);
                v.setPadding(px(6), px(6), px(6), px(10));
                FrameLayout thumbFrame = new FrameLayout(MainActivity.this);
                thumbFrame.addView(img, new FrameLayout.LayoutParams(-1, -1));
                thumbFrame.addView(more, new FrameLayout.LayoutParams(px(40), px(40), Gravity.TOP | Gravity.END));
                v.addView(thumbFrame, new LinearLayout.LayoutParams(-1, px(118)));
                t1.setPadding(px(4), px(8), px(4), 0);
                t2.setPadding(px(4), px(2), px(4), 0);
                v.addView(t1);
                v.addView(t2);
            } else {
                v.setOrientation(LinearLayout.HORIZONTAL);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(px(8), px(8), px(12), px(8));
                v.setMinimumHeight(px(72));
                v.addView(img, new LinearLayout.LayoutParams(px(56), px(56)));
                LinearLayout tx = new LinearLayout(MainActivity.this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(px(12), 0, px(8), 0);
                tx.addView(t1);
                tx.addView(t2);
                v.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
                v.addView(more, new LinearLayout.LayoutParams(px(44), px(44)));
            }
            v.setTag(new View[]{img, t1, t2, more});
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Row r = rows.get(pos);
            View[] parts = (View[]) h.itemView.getTag();
            ImageView img = (ImageView) parts[0];
            TextView t1 = (TextView) parts[1], t2 = (TextView) parts[2];
            View more = parts[3];
            more.setVisibility(View.GONE);
            more.setOnClickListener(null);
            h.itemView.setOnLongClickListener(null);
            img.setTag(null);
            img.setBackground(g.pill(g.field(), px(10)));
            int pad = r.card ? px(30) : px(14);
            img.setPadding(pad, pad, pad, pad);
            if (r.grant) {
                img.setImageDrawable(new Icon("folder", g.onAccent));
                img.setBackground(g.pill(g.accent, px(10)));
                t1.setText(Tr.t("grantAll"));
                t2.setText(Tr.t("grantAllHint"));
                h.itemView.setOnClickListener(v -> requestAllFiles());
                return;
            }
            if (r.back) {
                img.setImageDrawable(new Icon("back", g.text));
                t1.setText(Tr.t("back"));
                t2.setText("");
                h.itemView.setOnClickListener(v -> { openFolder = null; refreshList(); });
                return;
            }
            if (r.newList) {
                img.setImageDrawable(new Icon("plus", g.onAccent));
                img.setBackground(g.pill(g.accent, px(10)));
                t1.setText(Tr.t("pl.new"));
                t2.setText(Tr.t("pl.newHint"));
                h.itemView.setOnClickListener(v -> nameDialog(Tr.t("pl.new"), "", n -> {
                    if (!prefs.createPlaylist(n)) { toast(Tr.t("pl.exists")); return; }
                    refreshList();
                }));
                return;
            }
            if (r.listName != null) {
                String n = r.listName;
                img.setImageDrawable(new Icon(FAV.equals(n) ? "starFill" : RECENT.equals(n) ? "recent" : "queue", g.accent));
                t1.setText(listTitle(n));
                int count = FAV.equals(n) ? prefs.list("fav").size() : RECENT.equals(n) ? prefs.list("recent").size() : prefs.playlist(n).size();
                t2.setText(String.format(Locale.getDefault(), Tr.t("items"), count));
                h.itemView.setOnClickListener(v -> { openList = n; refreshList(); list.scrollToPosition(0); });
                more.setVisibility(View.VISIBLE);
                more.setOnClickListener(v -> listMenu(n));
                h.itemView.setOnLongClickListener(v -> { listMenu(n); return true; });
                return;
            }
            if (r.folder != null) {
                img.setImageDrawable(new Icon("folder", g.accent));
                t1.setText(r.folder.name);
                String vol = TextUtils.isEmpty(r.folder.volume) ? "" : "  ·  " + r.folder.volume;
                t2.setText(String.format(Locale.getDefault(), Tr.t("items"), r.folder.items.size()) + vol);
                h.itemView.setOnClickListener(v -> { openFolder = r.folder; refreshList(); list.scrollToPosition(0); });
                return;
            }
            MediaRepo.Item it = r.item;
            img.setImageDrawable(new Icon(it.video ? "video" : "music", g.muted));
            t1.setText(it.title);
            String dur = it.duration > 0 ? time(it.duration) : "";
            t2.setText(TextUtils.isEmpty(it.sub) ? dur : (dur.isEmpty() ? it.sub : dur + "  ·  " + it.sub));
            h.itemView.setOnClickListener(v -> play(r.queue, r.index));
            more.setVisibility(View.VISIBLE);
            more.setOnClickListener(v -> itemMenu(it, r.queue, r.index, r.owner));
            h.itemView.setOnLongClickListener(v -> { itemMenu(it, r.queue, r.index, r.owner); return true; });
            if (it.id > 0 || it.path != null) {
                img.setTag(it.uri);
                io.execute(() -> {
                    Bitmap b = MediaRepo.thumbnail(MainActivity.this, it, r.card ? 384 : 160, r.card ? 216 : 160);
                    if (b == null) return;
                    ui.post(() -> {
                        if (it.uri.equals(img.getTag())) { img.setPadding(0, 0, 0, 0); img.setImageBitmap(b); }
                    });
                });
            }
        }
    }

    // ------------------------------------------------------------------ player page
    private void buildPlayer(boolean compact) {
        playerPage = new FrameLayout(this);
        playerPage.setBackgroundColor(Color.BLACK);
        playerPage.setVisibility(View.GONE);
        playerPage.setClickable(true);

        audioArt = new ImageView(this);
        audioArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        audioArt.setBackground(g.backdrop());
        int ap = px(compact ? 50 : 90);
        audioArt.setPadding(ap, ap, ap, ap + px(110));
        playerPage.addView(audioArt, new FrameLayout.LayoutParams(-1, -1));

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(true);
        applySubtitleStyle();
        playerPage.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        playerView.setOnClickListener(v -> setControlsVisible(pBottom.getVisibility() != View.VISIBLE));
        audioArt.setOnClickListener(v -> setControlsVisible(true));

        int btn = compact ? 56 : 66;
        // top bar
        pTop = new LinearLayout(this);
        pTop.setGravity(Gravity.CENTER_VERTICAL);
        pTop.setBackground(g.panel(px(20), dp, 40));
        pTop.setPadding(px(6), px(4), px(14), px(4));
        ImageButton back = iconButton("back", g.text, btn);
        back.setContentDescription(Tr.t("back"));
        back.setOnClickListener(v -> showPlayer(false));
        if (Tr.isArabic()) back.setScaleX(-1);
        pTop.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        pTitle = text("", compact ? 17 : 20, g.text, true);
        pTitle.setSingleLine(true);
        pTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        pTitle.setMarqueeRepeatLimit(-1);
        pTitle.setSelected(true);
        pSub = text("", 13, g.muted, false);
        pSub.setSingleLine(true);
        titles.addView(pTitle);
        titles.addView(pSub);
        pTop.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        pFav = iconButton("star", g.text, btn);
        pFav.setOnClickListener(v -> toggleFavorite());
        pTop.addView(pFav);
        ImageButton pClose = iconButton("close", g.text, btn);
        pClose.setContentDescription(Tr.t("stopClose"));
        pClose.setOnClickListener(v -> closePlayback());
        pTop.addView(pClose);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        tlp.setMargins(px(10), px(10), px(10), 0);
        playerPage.addView(pTop, tlp);

        // bottom controls
        pBottom = new LinearLayout(this);
        pBottom.setOrientation(LinearLayout.VERTICAL);
        pBottom.setBackground(g.panel(px(24), dp, 45));
        pBottom.setPadding(px(14), px(8), px(14), px(8));
        pBottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);   // media transport is never mirrored
        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        pCur = text("0:00", 14, g.text, true);
        pDur = text("0:00", 14, g.text, true);
        pSeek = new SeekBar(this);
        pSeek.setMax(1000);
        pSeek.setMinimumHeight(px(40));
        pSeek.getProgressDrawable().setTint(g.accent);
        pSeek.getThumb().setTint(g.accent);
        pSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && controller != null) pCur.setText(time(controller.getDuration() * p / 1000));
            }
            public void onStartTrackingTouch(SeekBar s) { seeking = true; ui.removeCallbacks(hideControls); }
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                if (controller != null && controller.getDuration() > 0) controller.seekTo(controller.getDuration() * s.getProgress() / 1000);
                scheduleHide();
            }
        });
        seekRow.addView(pCur);
        seekRow.addView(pSeek, new LinearLayout.LayoutParams(0, px(48), 1f));
        seekRow.addView(pDur);
        pBottom.addView(seekRow);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        pRepeat = iconButton("repeat", g.text, btn);
        pRepeat.setOnClickListener(v -> { act("repeat"); refreshPlayerState(); });
        ImageButton prev = iconButton("prev", g.text, btn);
        prev.setOnClickListener(v -> act("previous"));
        ImageButton rew = iconButton("rewind", g.text, btn);
        rew.setOnClickListener(v -> act("rewind"));
        pPlay = iconButton("play", g.onAccent, btn + 16);
        pPlay.setBackground(g.pill(g.accent, px(btn + 16)));
        pPlay.setOnClickListener(v -> act("playPause"));
        ImageButton fwd = iconButton("forward", g.text, btn);
        fwd.setOnClickListener(v -> act("forward"));
        ImageButton next = iconButton("next", g.text, btn);
        next.setOnClickListener(v -> act("next"));
        pShuffle = iconButton("shuffle", g.text, btn);
        pShuffle.setOnClickListener(v -> { act("shuffle"); refreshPlayerState(); });
        ImageButton audio = iconButton("audio", g.text, btn);
        audio.setOnClickListener(v -> chooseTrack(C.TRACK_TYPE_AUDIO));
        ImageButton subs = iconButton("subtitles", g.text, btn);
        subs.setOnClickListener(v -> chooseTrack(C.TRACK_TYPE_TEXT));
        ImageButton resize = iconButton("resize", g.text, btn);
        resize.setOnClickListener(v -> cycleResize());
        ImageButton speed = iconButton("speed", g.text, btn);
        speed.setOnClickListener(v -> chooseSpeed());
        for (View v : new View[]{pRepeat, prev, rew, pPlay, fwd, next, pShuffle}) { space(v); row.addView(v); }
        LinearLayout extra = new LinearLayout(this);
        extra.setGravity(Gravity.CENTER);
        for (View v : new View[]{audio, subs, resize, speed}) { space(v); extra.addView(v); }
        DisplayMetrics m = getResources().getDisplayMetrics();
        boolean wide = m.widthPixels / dp >= 760;
        if (wide) {
            LinearLayout both = new LinearLayout(this);
            both.setGravity(Gravity.CENTER_VERTICAL);
            both.addView(row, new LinearLayout.LayoutParams(0, -2, 1f));
            both.addView(extra);
            pBottom.addView(both);
        } else {
            pBottom.addView(row);
            pBottom.addView(extra);
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        blp.setMargins(px(10), 0, px(10), px(10));
        playerPage.addView(pBottom, blp);
        root.addView(playerPage, new FrameLayout.LayoutParams(-1, -1));
    }

    private void space(View v) {
        ((LinearLayout.LayoutParams) v.getLayoutParams()).setMargins(px(3), 0, px(3), 0);
    }

    private void showPlayer(boolean show) {
        playerVisible = show;
        playerPage.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) { setControlsVisible(true); refreshPlayerState(); }
        updateKeepScreen();
    }

    private void setControlsVisible(boolean v) {
        pTop.setVisibility(v ? View.VISIBLE : View.GONE);
        pBottom.setVisibility(v ? View.VISIBLE : View.GONE);
        if (v) scheduleHide();
    }

    private void scheduleHide() {
        ui.removeCallbacks(hideControls);
        if (controller != null && controller.isPlaying() && isVideo()) ui.postDelayed(hideControls, 4500);
    }

    private boolean isVideo() {
        if (controller == null) return false;
        Tracks t = controller.getCurrentTracks();
        if (t.isTypeSupported(C.TRACK_TYPE_VIDEO)) {
            for (Tracks.Group gr : t.getGroups())
                if (gr.getType() == C.TRACK_TYPE_VIDEO && gr.isSelected()) {
                    // album art in audio files is reported as an image, not video
                    String mime = gr.getMediaTrackGroup().getFormat(0).sampleMimeType;
                    if (mime == null || !mime.startsWith("image/")) return true;
                }
        }
        MediaItem cur = controller.getCurrentMediaItem();
        Bundle ex = cur == null ? null : cur.mediaMetadata.extras;
        return ex != null && ex.getBoolean(PlaybackService.EXTRA_VIDEO) && t.getGroups().isEmpty();
    }

    // ------------------------------------------------------------------ playlists & file actions
    private static final String FAV = "★fav", RECENT = "★recent";
    private static final int REQ_DELETE = 41, REQ_WRITE = 42;
    private String openList;                 // playlist being shown in the Playlists tab
    private MediaRepo.Item pendingItem;      // waiting for Android's "allow?" answer
    private String pendingNewName;

    private String listTitle(String name) {
        if (FAV.equals(name)) return Tr.t("favorites");
        if (RECENT.equals(name)) return Tr.t("recent");
        return name;
    }

    private List<MediaRepo.Item> listItems(String name) {
        List<String[]> src = FAV.equals(name) ? prefs.list("fav") : RECENT.equals(name) ? prefs.list("recent") : prefs.playlist(name);
        List<MediaRepo.Item> items = new ArrayList<>();
        for (String[] r : src) {
            MediaRepo.Item it = find(r[0]);
            if (it == null && r.length > 3 && !r[3].isEmpty()) it = findPath(r[3]);
            if (it == null) {
                it = new MediaRepo.Item();
                it.uri = r[0]; it.title = r[1]; it.sub = ""; it.video = "1".equals(r[2]);
                it.path = r.length > 3 && !r[3].isEmpty() ? r[3] : null;
            }
            items.add(it);
        }
        return items;
    }

    private MediaRepo.Item findPath(String path) {
        for (MediaRepo.Item i : repo.videos) if (path.equals(i.path)) return i;
        for (MediaRepo.Item i : repo.music) if (path.equals(i.path)) return i;
        return null;
    }

    private void itemMenu(MediaRepo.Item it, List<MediaRepo.Item> queue, int index, String owner) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add("▶  " + Tr.t("a.play")); acts.add(() -> play(queue, index));
        labels.add("＋  " + Tr.t("pl.addTo")); acts.add(() -> chooseList(it));
        boolean fav = prefs.isFavorite(it.uri);
        labels.add("★  " + Tr.t(fav ? "pl.unfav" : "pl.fav"));
        acts.add(() -> { toast(Tr.t(prefs.toggleFavorite(it.uri, it.title, it.video) ? "addFav" : "removeFav")); refreshList(); });
        if (owner != null && !RECENT.equals(owner)) {
            labels.add("－  " + Tr.t("pl.removeFrom"));
            acts.add(() -> {
                if (FAV.equals(owner)) prefs.toggleFavorite(it.uri, it.title, it.video);
                else prefs.removeFromPlaylist(owner, it.uri);
                refreshList();
            });
        }
        labels.add("✎  " + Tr.t("f.rename")); acts.add(() -> renameDialog(it));
        labels.add("⇪  " + Tr.t("f.share")); acts.add(() -> shareItem(it));
        labels.add("🗑  " + Tr.t("f.delete")); acts.add(() -> confirmDelete(it));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(it.title)
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    private void chooseList(MediaRepo.Item it) {
        List<String> names = prefs.playlistNames();
        List<String> labels = new ArrayList<>();
        labels.add("＋  " + Tr.t("pl.new"));
        labels.addAll(names);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Tr.t("pl.addTo"))
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    if (w == 0) nameDialog(Tr.t("pl.new"), "", n -> {
                        if (!prefs.createPlaylist(n)) { toast(Tr.t("pl.exists")); return; }
                        prefs.addToPlaylist(n, it.uri, it.title, it.video, it.path);
                        toast(String.format(Tr.t("pl.added"), n));
                        refreshList();
                    });
                    else {
                        String n = names.get(w - 1);
                        toast(prefs.addToPlaylist(n, it.uri, it.title, it.video, it.path)
                                ? String.format(Tr.t("pl.added"), n) : Tr.t("pl.already"));
                        refreshList();
                    }
                })
                .show();
    }

    interface NameAction { void run(String name); }

    private void nameDialog(String title, String current, NameAction then) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(current);
        e.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(px(20), px(8), px(20), 0);
        box.addView(e);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(Tr.t("ok"), (x, w) -> {
                    String n = e.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", " ").trim();
                    if (n.isEmpty()) { toast(Tr.t("f.emptyName")); return; }
                    then.run(n);
                })
                .setNegativeButton(Tr.t("dl.cancel"), null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        e.requestFocus();
    }

    private void listMenu(String name) {
        boolean user = !FAV.equals(name) && !RECENT.equals(name);
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add("▶  " + Tr.t("playAll"));
        acts.add(() -> { List<MediaRepo.Item> l = listItems(name); if (!l.isEmpty()) play(l, 0); });
        if (user) {
            labels.add("✎  " + Tr.t("pl.rename"));
            acts.add(() -> nameDialog(Tr.t("pl.rename"), name, n -> {
                if (!prefs.renamePlaylist(name, n)) { toast(Tr.t("pl.exists")); return; }
                if (name.equals(openList)) openList = n;
                refreshList();
            }));
            labels.add("🗑  " + Tr.t("pl.delete"));
            acts.add(() -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(Tr.t("pl.delete"))
                    .setMessage(String.format(Tr.t("pl.deleteQ"), name))
                    .setPositiveButton(Tr.t("f.delete"), (d, w) -> {
                        prefs.deletePlaylist(name);
                        if (name.equals(openList)) openList = null;
                        refreshList();
                    })
                    .setNegativeButton(Tr.t("dl.cancel"), null).show());
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(listTitle(name))
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    private void shareItem(MediaRepo.Item it) {
        try {
            Uri u = Uri.parse(it.uri);
            if (!"content".equals(u.getScheme())) { toast(Tr.t("f.cantShare")); return; }
            Intent s = new Intent(Intent.ACTION_SEND).setType(it.video ? "video/*" : "audio/*")
                    .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(s, it.title));
        } catch (Exception e) { toast(Tr.t("f.cantShare")); }
    }

    // ---- delete & rename (the file itself, on the phone)
    /** Can we change the file directly, without asking Android for permission each time? */
    private boolean direct(MediaRepo.Item it) {
        if (it.path == null) return false;
        if (Build.VERSION.SDK_INT <= 29) return true;
        if (Environment.isExternalStorageManager()) return true;
        return it.path.startsWith(YtDl.outDir().getAbsolutePath());   // our own downloads
    }

    private boolean needsLegacyWrite() {
        return Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    private void confirmDelete(MediaRepo.Item it) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Tr.t("f.delete"))
                .setMessage(String.format(Tr.t("f.deleteQ"), it.title))
                .setPositiveButton(Tr.t("f.delete"), (d, w) -> deleteItem(it))
                .setNegativeButton(Tr.t("dl.cancel"), null)
                .show();
    }

    private void deleteItem(MediaRepo.Item it) {
        if (needsLegacyWrite()) { requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMS + 1); return; }
        stopIfPlaying(it);
        if (direct(it)) {
            java.io.File f = new java.io.File(it.path);
            if (f.delete() || !f.exists()) {
                try { if (it.id > 0) getContentResolver().delete(Uri.parse(it.uri), null, null); } catch (Exception ignored) { }
                MediaScannerConnectionScan(it.path);
                afterDelete(it);
            } else toast(Tr.t("f.failed"));
            return;
        }
        Uri u = Uri.parse(it.uri);
        if (Build.VERSION.SDK_INT >= 30 && "content".equals(u.getScheme())) {
            try {
                android.app.PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), java.util.Collections.singletonList(u));
                pendingItem = it;
                startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE, null, 0, 0, 0);
            } catch (Exception e) { toast(Tr.t("f.failed")); }
            return;
        }
        toast(Tr.t("f.failed"));
    }

    private void afterDelete(MediaRepo.Item it) {
        prefs.replaceEverywhere(it.uri, null, null, null);
        repo.videos.remove(it);
        repo.music.remove(it);
        for (MediaRepo.Folder f : repo.folders) f.items.remove(it);
        toast(Tr.t("f.deleted"));
        refreshList();
    }

    private void renameDialog(MediaRepo.Item it) {
        nameDialog(Tr.t("f.rename"), it.title, n -> renameItem(it, n));
    }

    private static String ext(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.'), slash = path.lastIndexOf('/');
        return dot > slash ? path.substring(dot) : "";
    }

    private void renameItem(MediaRepo.Item it, String newName) {
        if (needsLegacyWrite()) { requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMS + 1); return; }
        if (direct(it)) {
            java.io.File f = new java.io.File(it.path);
            java.io.File to = new java.io.File(f.getParentFile(), newName + ext(it.path));
            if (to.exists()) { toast(Tr.t("f.exists")); return; }
            stopIfPlaying(it);
            if (!f.renameTo(to)) { toast(Tr.t("f.failed")); return; }
            MediaScannerConnectionScan(f.getAbsolutePath());
            MediaScannerConnectionScan(to.getAbsolutePath());
            String newUri = it.id > 0 ? it.uri : Uri.fromFile(to).toString();
            if (it.id > 0) {
                try {
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, to.getName());
                    if (Build.VERSION.SDK_INT < 29) cv.put(MediaStore.MediaColumns.DATA, to.getAbsolutePath());
                    getContentResolver().update(Uri.parse(it.uri), cv, null, null);
                } catch (Exception ignored) { }
            }
            afterRename(it, newName, to.getAbsolutePath(), newUri);
            return;
        }
        Uri u = Uri.parse(it.uri);
        if (Build.VERSION.SDK_INT >= 30 && "content".equals(u.getScheme())) {
            try {
                android.app.PendingIntent pi = MediaStore.createWriteRequest(getContentResolver(), java.util.Collections.singletonList(u));
                pendingItem = it;
                pendingNewName = newName;
                startIntentSenderForResult(pi.getIntentSender(), REQ_WRITE, null, 0, 0, 0);
            } catch (Exception e) { toast(Tr.t("f.failed")); }
            return;
        }
        toast(Tr.t("f.failed"));
    }

    private void renameThroughMediaStore(MediaRepo.Item it, String newName) {
        try {
            String file = newName + ext(it.path != null ? it.path : it.title);
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, file);
            stopIfPlaying(it);
            int n = getContentResolver().update(Uri.parse(it.uri), cv, null, null);
            if (n <= 0) { toast(Tr.t("f.failed")); return; }
            String newPath = it.path == null ? null : new java.io.File(new java.io.File(it.path).getParentFile(), file).getAbsolutePath();
            afterRename(it, newName, newPath, it.uri);
        } catch (Exception e) { toast(Tr.t("f.failed")); }
    }

    private void afterRename(MediaRepo.Item it, String newName, String newPath, String newUri) {
        prefs.replaceEverywhere(it.uri, newUri, newName, newPath);
        it.title = newName;
        it.path = newPath;
        it.uri = newUri;
        toast(Tr.t("f.renamed"));
        refreshList();
        ui.postDelayed(this::quietRescan, 1500);
    }

    private void stopIfPlaying(MediaRepo.Item it) {
        if (controller == null) return;
        MediaItem cur = controller.getCurrentMediaItem();
        if (cur != null && it.uri.equals(cur.mediaId)) { controller.stop(); controller.clearMediaItems(); showPlayer(false); mini.setVisibility(View.GONE); }
    }

    private void MediaScannerConnectionScan(String path) {
        try { android.media.MediaScannerConnection.scanFile(this, new String[]{path}, null, null); } catch (Exception ignored) { }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        MediaRepo.Item it = pendingItem;
        String name = pendingNewName;
        pendingItem = null;
        pendingNewName = null;
        if (it == null) return;
        if (result != RESULT_OK) { toast(Tr.t("f.notAllowed")); return; }
        if (code == REQ_DELETE) afterDelete(it);
        else if (code == REQ_WRITE && name != null) renameThroughMediaStore(it, name);
    }

    // ------------------------------------------------------------------ playback actions
    private void play(List<MediaRepo.Item> items, int index) {
        if (controller == null) return;
        List<MediaItem> mi = new ArrayList<>();
        int start = 0, from = 0, to = items.size();
        if (items.size() > 400) { from = Math.max(0, index - 200); to = Math.min(items.size(), from + 400); }
        for (int i = from; i < to; i++) {
            MediaRepo.Item it = items.get(i);
            mi.add(PlaybackService.build(it.uri, it.title, it.sub, it.path, it.video));
            if (i == index) start = i - from;
        }
        controller.setMediaItems(mi, start, C.TIME_UNSET);
        controller.prepare();
        controller.play();
        if (items.get(index).video) showPlayer(true);
        else mini.setVisibility(View.VISIBLE);
    }

    private void act(String action) {
        if (controller == null) return;
        if ("openPlayer".equals(action)) { if (controller.getMediaItemCount() > 0) showPlayer(true); return; }
        if ("ignore".equals(action)) return;
        PlaybackService.perform(controller, action);
        if ("repeat".equals(action)) {
            int r = controller.getRepeatMode();
            toast(Tr.t(r == Player.REPEAT_MODE_ONE ? "repeatOne" : r == Player.REPEAT_MODE_ALL ? "repeatAll" : "repeatOff"));
        } else if ("shuffle".equals(action)) {
            toast(Tr.t(controller.getShuffleModeEnabled() ? "shuffleOn" : "shuffleOff"));
        }
        if (playerVisible) setControlsVisible(true);
    }

    private void toggleFavorite() {
        if (controller == null) return;
        MediaItem cur = controller.getCurrentMediaItem();
        if (cur == null) return;
        Bundle ex = cur.mediaMetadata.extras;
        boolean added = prefs.toggleFavorite(cur.mediaId, String.valueOf(cur.mediaMetadata.title),
                ex != null && ex.getBoolean(PlaybackService.EXTRA_VIDEO));
        toast(Tr.t(added ? "addFav" : "removeFav"));
        refreshPlayerState();
        if ("favorites".equals(tab)) refreshList();
    }

    private void chooseTrack(int type) {
        if (controller == null) return;
        List<String> labels = new ArrayList<>();
        List<Object[]> choices = new ArrayList<>();
        if (type == C.TRACK_TYPE_TEXT) { labels.add(Tr.t("off")); choices.add(null); }
        int checked = type == C.TRACK_TYPE_TEXT ? 0 : -1;
        for (Tracks.Group gr : controller.getCurrentTracks().getGroups()) {
            if (gr.getType() != type) continue;
            for (int i = 0; i < gr.length; i++) {
                if (!gr.isTrackSupported(i)) continue;
                androidx.media3.common.Format f = gr.getTrackFormat(i);
                StringBuilder sb = new StringBuilder();
                if (f.label != null) sb.append(f.label).append(' ');
                if (f.language != null && !"und".equals(f.language)) sb.append('[').append(f.language).append("] ");
                if (f.channelCount > 0) sb.append(f.channelCount).append("ch ");
                if (f.sampleMimeType != null) sb.append('(').append(f.sampleMimeType.replaceAll(".*/", "")).append(')');
                labels.add(sb.length() == 0 ? "#" + (labels.size() + 1) : sb.toString().trim());
                choices.add(new Object[]{gr, i});
                if (gr.isTrackSelected(i)) checked = labels.size() - 1;
            }
        }
        if (choices.isEmpty() || (type == C.TRACK_TYPE_TEXT && choices.size() == 1)) {
            toast(Tr.t(type == C.TRACK_TYPE_AUDIO ? "audio" : "subtitles") + ": —");
            return;
        }
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Tr.t(type == C.TRACK_TYPE_AUDIO ? "audio" : "subtitles"))
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    Object[] ch = choices.get(which);
                    androidx.media3.common.TrackSelectionParameters.Builder b = controller.getTrackSelectionParameters().buildUpon();
                    if (ch == null) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
                    else {
                        Tracks.Group gr = (Tracks.Group) ch[0];
                        b.setTrackTypeDisabled(type, false);
                        b.setOverrideForType(new TrackSelectionOverride(gr.getMediaTrackGroup(), (int) ch[1]));
                    }
                    controller.setTrackSelectionParameters(b.build());
                    d.dismiss();
                }).show();
    }

    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

    private void cycleResize() {
        int[] modes = {AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL};
        String[] names = {"fit", "fill", "stretch"};
        int idx = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == resizeMode) idx = i;
        idx = (idx + 1) % modes.length;
        resizeMode = modes[idx];
        playerView.setResizeMode(resizeMode);
        toast(Tr.t("resize") + ": " + Tr.t(names[idx]));
    }

    private void chooseSpeed() {
        if (controller == null) return;
        final float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = new String[speeds.length];
        int checked = 2;
        for (int i = 0; i < speeds.length; i++) {
            labels[i] = speeds[i] + "×";
            if (Math.abs(controller.getPlaybackParameters().speed - speeds[i]) < 0.01f) checked = i;
        }
        new AlertDialog.Builder(this, dialogTheme()).setTitle(Tr.t("speed"))
                .setSingleChoiceItems(labels, checked, (d, w) -> { controller.setPlaybackSpeed(speeds[w]); d.dismiss(); }).show();
    }

    private int dialogTheme() {
        return g.dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
    }

    // ------------------------------------------------------------------ controller state
    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onEvents(@NonNull Player player, @NonNull Player.Events events) { refreshPlayerState(); }
        @Override public void onPlayerError(@NonNull PlaybackException error) {
            toast(Tr.t("error") + " (" + error.getErrorCodeName() + ")");
            if (controller != null && controller.hasNextMediaItem()) ui.postDelayed(() -> { if (controller != null) controller.seekToNextMediaItem(); }, 1500);
        }
    };

    private boolean updateChecked;

    private void onControllerReady() {
        if (!updateChecked && prefs.bool("autoUpdate", true)) {
            updateChecked = true;
            long last = prefs.integer("lastUpdateCheckDay", 0);
            long today = System.currentTimeMillis() / 86_400_000L;
            if (today != last) {
                prefs.setInt("lastUpdateCheckDay", (int) today);
                ui.postDelayed(() -> Updater.check(this, true), 4000);
            }
        }
        refreshPlayerState();
        if (pendingViewUri != null) { playPendingUri(); return; }
        // Nothing plays by itself when the app opens, unless the user turned that on in Settings.
        if (controller.getMediaItemCount() == 0 && prefs.bool("autoResume", false)) restoreSession(true);
        pendingAutoplay = false;
    }

    private void playPendingUri() {
        Uri u = pendingViewUri;
        pendingViewUri = null;
        if (u == null || controller == null) return;
        String name = u.getLastPathSegment() == null ? u.toString() : u.getLastPathSegment();
        String type = getContentResolver().getType(u);
        boolean video = type == null || type.startsWith("video");
        String path = "file".equals(u.getScheme()) ? u.getPath() : null;
        controller.setMediaItem(PlaybackService.build(u.toString(), MediaRepo.stripExt(name), "", path, video));
        controller.prepare();
        controller.play();
        showPlayer(true);
    }

    /** Stop playing and forget the queue, so nothing comes back next time the app opens. */
    private void closePlayback() {
        if (controller != null) {
            controller.stop();
            controller.clearMediaItems();
        }
        prefs.clearSession();
        showPlayer(false);
        mini.setVisibility(View.GONE);
        toast(Tr.t("stopped"));
    }

    private void restoreSession(boolean autoplay) {
        JSONObject s = prefs.session();
        JSONArray uris = s.optJSONArray("uris"), titles = s.optJSONArray("titles");
        if (uris == null || uris.length() == 0) return;
        List<MediaItem> items = new ArrayList<>();
        for (int i = 0; i < uris.length(); i++) {
            String[] meta = (titles != null ? titles.optString(i, "") : "").split("\u0001", -1);
            String title = meta.length > 0 ? meta[0] : "";
            String path = meta.length > 1 ? meta[1] : null;
            boolean video = meta.length > 2 && "1".equals(meta[2]);
            items.add(PlaybackService.build(uris.optString(i), title, "", path, video));
        }
        int idx = Math.min(Math.max(0, s.optInt("index", 0)), items.size() - 1);
        controller.setMediaItems(items, idx, Math.max(0, s.optLong("pos", 0)));
        controller.prepare();
        if (autoplay) controller.play();
        mini.setVisibility(View.VISIBLE);
    }

    private void refreshPlayerState() {
        if (controller == null || pPlay == null) return;
        boolean has = controller.getMediaItemCount() > 0;
        mini.setVisibility(has ? View.VISIBLE : View.GONE);
        boolean playing = controller.isPlaying();
        pPlay.setImageDrawable(new Icon(playing ? "pause" : "play", g.onAccent));
        miniPlay.setImageDrawable(new Icon(playing ? "pause" : "play", g.onAccent));
        MediaItem cur = controller.getCurrentMediaItem();
        MediaMetadata md = controller.getMediaMetadata();
        String title = md.title != null ? md.title.toString() : (cur != null && cur.mediaMetadata.title != null ? cur.mediaMetadata.title.toString() : Tr.t("nothing"));
        String sub = md.artist != null ? md.artist.toString() : "";
        pTitle.setText(title);
        pSub.setText(sub);
        miniTitle.setText(title);
        miniSub.setText(sub.isEmpty() ? (controller.getMediaItemCount() > 1
                ? (controller.getCurrentMediaItemIndex() + 1) + " / " + controller.getMediaItemCount() : "") : sub);
        boolean fav = cur != null && prefs.isFavorite(cur.mediaId);
        pFav.setImageDrawable(new Icon(fav ? "starFill" : "star", fav ? g.accent : g.text));
        int rep = controller.getRepeatMode();
        pRepeat.setImageDrawable(new Icon(rep == Player.REPEAT_MODE_ONE ? "repeat1" : "repeat", rep == Player.REPEAT_MODE_OFF ? g.muted : g.accent));
        pShuffle.setImageDrawable(new Icon("shuffle", controller.getShuffleModeEnabled() ? g.accent : g.muted));

        boolean video = isVideo();
        playerView.setVisibility(video ? View.VISIBLE : View.INVISIBLE);
        audioArt.setVisibility(video ? View.GONE : View.VISIBLE);
        String curId = cur == null ? null : cur.mediaId;
        boolean artChanged = md.artworkData != lastArtData || (curId != null && !curId.equals(lastArtId));
        if (artChanged) {
            lastArtId = curId;
            lastArtData = md.artworkData;
            lastArt = md.artworkData == null ? null
                    : android.graphics.BitmapFactory.decodeByteArray(md.artworkData, 0, md.artworkData.length);
            if (lastArt != null) { audioArt.setImageBitmap(lastArt); miniArt.setImageBitmap(lastArt); }
            else {
                audioArt.setImageResource(R.drawable.logo_big);
                MediaRepo.Item it = cur == null ? null : find(cur.mediaId);
                miniArt.setImageResource(R.drawable.logo_big);
                if (it != null) io.execute(() -> {
                    Bitmap bmp = MediaRepo.thumbnail(this, it, 160, 160);
                    if (bmp != null) ui.post(() -> { if (it.uri.equals(lastArtId)) miniArt.setImageBitmap(bmp); });
                });
            }
        }
        if (!video && playerVisible) setControlsVisible(true);
        else if (playerVisible && playing) scheduleHide();
        updateKeepScreen();
        updateProgress();
    }

    private void updateProgress() {
        if (controller == null || seeking || pSeek == null) return;
        long d = controller.getDuration(), p = controller.getCurrentPosition();
        if (d > 0 && d != C.TIME_UNSET) {
            pSeek.setProgress((int) (p * 1000 / d));
            pDur.setText(time(d));
        } else {
            pSeek.setProgress(0);
            pDur.setText("--:--");
        }
        pCur.setText(time(p));
    }

    private void updateKeepScreen() {
        boolean keep = controller != null && controller.isPlaying() && isVideo() && prefs.bool("keepScreen", true);
        if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void applySubtitleStyle() {
        if (playerView == null || playerView.getSubtitleView() == null) return;
        int size = prefs.integer("subSize", 2);
        float[] sizes = {18, 22, 28, 36};
        playerView.getSubtitleView().setFixedTextSize(TypedValue.COMPLEX_UNIT_DIP, sizes[Math.max(0, Math.min(3, size))]);
        boolean bg = prefs.bool("subBg", false);
        playerView.getSubtitleView().setStyle(new CaptionStyleCompat(Color.WHITE, bg ? 0x99000000 : Color.TRANSPARENT,
                Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, Typeface.DEFAULT_BOLD));
        playerView.getSubtitleView().setApplyEmbeddedStyles(true);
        playerView.getSubtitleView().setBottomPaddingFraction(0.12f);
    }

    // ------------------------------------------------------------------ keys (steering wheel, remote, rotary)
    @Override protected void onResume() {
        super.onResume();
        KeyBus.uiVisible = true;
        // Rescan once the user comes back after granting "all files" access
        if (scanned && !hadFullAccess && MediaRepo.hasFullAccess(this)) scan();
    }
    @Override protected void onPause() { KeyBus.uiVisible = false; super.onPause(); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (KeyBus.learn != null) {
            if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
            if (KeyBus.accept(e)) KeyBus.report(code, "key event");
            return true;
        }
        Map<Integer, String> map = prefs.keyMap();
        String action = map.get(code);
        if (action != null && controller != null) {
            if (KeyBus.accept(e)) {
                KeyBus.lastEvent = KeyBus.name(code) + "  ·  key event";
                act(action);
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) KeyBus.lastEvent = KeyBus.name(code) + "  ·  key event";
        if (playerVisible && e.getAction() == KeyEvent.ACTION_DOWN) setControlsVisible(true);
        return super.dispatchKeyEvent(e);
    }

    private void requestAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception ex) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                catch (Exception ex2) { toast(Tr.t("grantAllHint")); }
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMS);
        }
    }

    private void startLearning() {
        TextView msg = text(Tr.t("pressNow"), 20, g.text, true);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(px(24), px(40), px(24), px(40));
        learnDialog = new AlertDialog.Builder(this, dialogTheme()).setTitle(Tr.t("learn")).setView(msg)
                .setNegativeButton(Tr.t("close"), null).create();
        learnDialog.setOnDismissListener(d -> KeyBus.learn = null);
        KeyBus.learn = (code, source) -> onKeyLearned(code, source);
        learnDialog.show();
    }

    private void onKeyLearned(int code, String source) {
        KeyBus.learn = null;
        if (learnDialog != null) { learnDialog.dismiss(); learnDialog = null; }
        String[] labels = new String[Prefs.ACTIONS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = Tr.t("a." + Prefs.ACTIONS[i]);
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Tr.t("chooseAction") + "\n" + KeyBus.name(code) + "  (" + source + ")")
                .setItems(labels, (d, w) -> { prefs.setKey(code, Prefs.ACTIONS[w]); showSettings(); })
                .show();
    }

    // ------------------------------------------------------------------ settings
    private void showLibrary() {
        if (settingsView != null) settingsView.setVisibility(View.GONE);
        if (dlView != null) { content.removeView(dlView); dlView = null; }
        library.setVisibility(View.VISIBLE);
    }

    private void showSettings() {
        library.setVisibility(View.GONE);
        if (dlView != null) { content.removeView(dlView); dlView = null; }
        if (settingsView != null) content.removeView(settingsView);
        settingsView = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(12), px(12), px(12), px(24));
        settingsView.addView(col);
        content.addView(settingsView, new FrameLayout.LayoutParams(-1, -1));

        col.addView(text(Tr.t("settings"), 24, g.text, true));

        LinearLayout lang = section(col, Tr.t("language"));
        lang.addView(chipRow(new String[]{"العربية", "English"}, Tr.isArabic() ? 0 : 1, i -> {
            prefs.setArabic(i == 0);
            Tr.setArabic(i == 0);
            rebuildKeepingTab();
        }));

        LinearLayout theme = section(col, Tr.t("theme"));
        String[] names = new String[Glass.IDS.length];
        int cur = 0;
        for (int i = 0; i < names.length; i++) { names[i] = Tr.t("t." + Glass.IDS[i]); if (Glass.IDS[i].equals(prefs.theme())) cur = i; }
        theme.addView(chipRow(names, cur, i -> { prefs.setTheme(Glass.IDS[i]); rebuildKeepingTab(); }));

        LinearLayout play = section(col, Tr.t("playback"));
        play.addView(toggle(Tr.t("autoResume"), "autoResume", false));
        play.addView(toggle(Tr.t("rememberPos"), "rememberPos", true));
        play.addView(toggle(Tr.t("keepScreen"), "keepScreen", true));

        LinearLayout subs = section(col, Tr.t("subtitles"));
        subs.addView(text(Tr.t("subSize"), 15, g.text, false));
        subs.addView(chipRow(new String[]{Tr.t("small"), Tr.t("medium"), Tr.t("large"), Tr.t("xlarge")}, prefs.integer("subSize", 2),
                i -> { prefs.setInt("subSize", i); applySubtitleStyle(); showSettings(); }));
        subs.addView(toggle(Tr.t("subBg"), "subBg", false));

        LinearLayout st = section(col, Tr.t("storage"));
        LinearLayout sb = new LinearLayout(this);
        sb.addView(button(Tr.t("rescan"), true, v -> { scan(); selectTab("videos"); }));
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            sb.addView(button(Tr.t("allFiles"), false, v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                } catch (Exception ex) {
                    try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); } catch (Exception ignored) { }
                }
            }));
        }
        st.addView(sb);

        LinearLayout about = section(col, Tr.t("about"));
        LinearLayout ab = new LinearLayout(this);
        ab.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_big);
        ab.addView(logo, new LinearLayout.LayoutParams(px(84), px(84)));
        LinearLayout at = new LinearLayout(this);
        at.setOrientation(LinearLayout.VERTICAL);
        at.setPadding(px(14), 0, px(14), 0);
        at.addView(text("Ibra Player Kha", 22, g.text, true));
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        at.addView(text(Tr.t("version") + " " + ver, 14, g.muted, false));
        at.addView(text(Tr.t("credit"), 15, g.text, true));
        TextView site = text(Tr.t("appsSite") + "\nibraprogram.netlify.app", 16, g.accent, true);
        site.setPadding(0, px(6), 0, px(4));
        site.setOnClickListener(v -> openLink("https://ibraprogram.netlify.app/"));
        at.addView(site);
        TextView tg = text("Telegram: t.me/ibraprogram", 15, g.accent, true);
        tg.setOnClickListener(v -> openLink("https://t.me/ibraprogram"));
        at.addView(tg);
        tg = text("TikTok: @ibraprogram", 15, g.accent, true);
        tg.setOnClickListener(v -> openLink("https://www.tiktok.com/@ibraprogram"));
        at.addView(tg);
        ab.addView(at, new LinearLayout.LayoutParams(0, -2, 1f));
        about.addView(ab);
        about.addView(text(Tr.t("codecNote"), 13, g.muted, false));
        about.addView(button(Tr.t("updCheck"), true, v -> Updater.check(this, false)));
        about.addView(toggle(Tr.t("updAuto"), "autoUpdate", true));
    }

    // ------------------------------------------------------------------ YouTube downloads
    private ScrollView dlView;
    private String dlUrl = "";
    private boolean dlAutoFetch, dlFetching, dlAudio = true;
    private YtDl.Info dlInfo;
    private String dlError = "";
    private Bitmap dlThumb;
    private int dlSeen = -1;
    private LinearLayout dlJobsBox;
    private TextView dlEngine;
    private final Map<Integer, Object[]> dlRows = new java.util.HashMap<>();

    private void showDownloads() {
        library.setVisibility(View.GONE);
        if (settingsView != null) settingsView.setVisibility(View.GONE);
        if (dlView != null) content.removeView(dlView);
        YtDl.prepare(this);
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMS + 1);

        dlView = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(12), px(12), px(12), px(24));
        dlView.addView(col);
        content.addView(dlView, new FrameLayout.LayoutParams(-1, -1));

        col.addView(text(Tr.t("dl.title"), 24, g.text, true));
        dlEngine = text("", 13, g.muted, false);
        col.addView(dlEngine);

        // ---- ready buttons: YouTube first, then music sites
        sitesBox(col);

        // ---- link box
        LinearLayout box = section(col, Tr.t("dl.link"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText url = new EditText(this);
        url.setHint("https://youtu.be/…");
        url.setSingleLine(true);
        url.setText(dlUrl);
        url.setTextColor(g.text);
        url.setHintTextColor(g.muted);
        url.setTextSize(15);
        url.setTextDirection(View.TEXT_DIRECTION_LTR);
        url.setBackground(g.pill(g.field(), px(22)));
        url.setPadding(px(16), 0, px(16), 0);
        url.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI | android.text.InputType.TYPE_CLASS_TEXT);
        url.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        url.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { dlUrl = e.toString().trim(); }
        });
        url.setOnEditorActionListener((v, id, ev) -> { dlFetch(); return true; });
        row.addView(url, new LinearLayout.LayoutParams(0, px(52), 1f));
        ImageButton paste = iconButton("paste", g.text, 52);
        paste.setContentDescription(Tr.t("dl.paste"));
        paste.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            String clip = null;
            try {
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0)
                    clip = String.valueOf(cm.getPrimaryClip().getItemAt(0).coerceToText(this));
            } catch (Exception ignored) { }
            String u = YtDl.extractUrl(clip);
            if (u == null) { toast(Tr.t("dl.noLink")); return; }
            dlUrl = u;
            dlInfo = null;
            dlFetch();
        });
        row.addView(paste);
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout btns = new LinearLayout(this);
        btns.addView(button(dlFetching ? Tr.t("dl.fetching") : Tr.t("dl.fetch"), true, v -> dlFetch()));
        if (dlInfo != null || !dlUrl.isEmpty())
            btns.addView(button(Tr.t("dl.clear"), false, v -> { dlUrl = ""; dlInfo = null; dlError = ""; dlThumb = null; showDownloads(); }));
        box.addView(btns);
        box.addView(text(Tr.t("dl.shareHint"), 13, g.muted, false));
        if (!dlError.isEmpty()) {
            TextView err = text(Tr.t("dl.fetchFailed") + "\n" + dlError, 14, g.accent, false);
            err.setPadding(0, px(8), 0, 0);
            box.addView(err);
        }

        // ---- the video and its qualities
        if (dlInfo != null) {
            LinearLayout card = section(col, Tr.t("dl.choose"));
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView th = new ImageView(this);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (dlThumb != null) th.setImageBitmap(dlThumb); else th.setImageResource(R.drawable.logo_big);
            th.setBackground(g.pill(g.field(), px(10)));
            th.setClipToOutline(true);
            head.addView(th, new LinearLayout.LayoutParams(px(128), px(72)));
            LinearLayout ht = new LinearLayout(this);
            ht.setOrientation(LinearLayout.VERTICAL);
            ht.setPadding(px(12), 0, px(4), 0);
            TextView tt = text(dlInfo.title, 16, g.text, true);
            tt.setMaxLines(2);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            ht.addView(tt);
            String meta = dlInfo.uploader + (dlInfo.duration > 0 ? "  •  " + time(dlInfo.duration * 1000L) : "");
            ht.addView(text(meta, 13, g.muted, false));
            head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(head);

            card.addView(chipRow(new String[]{"♪  " + Tr.t("dl.audio"), "▶  " + Tr.t("dl.video")}, dlAudio ? 0 : 1, i -> {
                dlAudio = i == 0;
                showDownloads();
            }));
            for (YtDl.Choice c : dlAudio ? dlInfo.audio : dlInfo.video) card.addView(choiceRow(c));
        }

        // ---- downloads list
        LinearLayout jobs = section(col, Tr.t("downloads"));
        dlJobsBox = new LinearLayout(this);
        dlJobsBox.setOrientation(LinearLayout.VERTICAL);
        jobs.addView(dlJobsBox);
        jobs.addView(button(Tr.t("dl.clearDone"), false, v -> YtDl.clearFinished()));
        jobs.addView(text(Tr.t("dl.saveTo") + "\n" + YtDl.outDir().getAbsolutePath(), 13, g.muted, false));

        // ---- engine
        LinearLayout eng = section(col, Tr.t("dl.engine"));
        eng.addView(text(Tr.t("dl.engineHint"), 13, g.muted, false));
        eng.addView(button(Tr.t("dl.update"), false, v -> {
            if (YtDl.state != YtDl.READY) { toast(Tr.t("dl.preparing")); return; }
            toast(Tr.t("dl.updating"));
            YtDl.updateEngine(this, (ver, err) -> {
                toast(err == null ? Tr.t("dl.updated") + " " + ver : Tr.t("updFailed"));
                dlSeen = -1;
            });
        }));

        dlSeen = -1;
        tickDownloads();
        if (dlAutoFetch) { dlAutoFetch = false; dlFetch(); }
    }

    // ------------------------------------------------------------------ built-in browser for YouTube & music sites
    /** id, name, home page, colour, glyph */
    private static final Object[][] SITES = {
            {"youtube", "YouTube", "https://m.youtube.com/", 0xFFFF0033, "play"},
            {"ytmusic", "YouTube Music", "https://music.youtube.com/", 0xFFC4122F, "music"},
            {"soundcloud", "SoundCloud", "https://m.soundcloud.com/", 0xFFFF5500, "music"},
            {"audiomack", "Audiomack", "https://audiomack.com/", 0xFFF29E0C, "music"},
            {"bandcamp", "Bandcamp", "https://bandcamp.com/", 0xFF1A9AB8, "music"},
            {"mixcloud", "Mixcloud", "https://www.mixcloud.com/", 0xFF5B3CF5, "music"},
    };

    private LinearLayout browserView;
    private android.webkit.WebView web;
    private TextView webTitle;
    private TextView webGrab;

    private View sitesBox(LinearLayout col) {
        LinearLayout box = section(col, Tr.t("dl.open"));
        // YouTube first: one big tile
        box.addView(siteTile(SITES[0], true));
        // other music sites: two per row
        LinearLayout row = null;
        for (int i = 1; i < SITES.length; i++) {
            if (row == null || row.getChildCount() == 2) {
                row = new LinearLayout(this);
                box.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            View t = siteTile(SITES[i], false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(row.getChildCount() == 0 ? 0 : px(4), px(8), row.getChildCount() == 0 ? px(4) : 0, 0);
            row.addView(t, lp);
        }
        box.addView(text(Tr.t("dl.openHint"), 13, g.muted, false));
        return box;
    }

    private View siteTile(Object[] site, boolean big) {
        int color = (int) site[3];
        LinearLayout t = new LinearLayout(this);
        // big YouTube tile: icon + text side by side; small tiles: icon on top, name under it (never cut)
        if (!big) t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(big ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(px(big ? 16 : 10), px(big ? 12 : 8), px(12), px(big ? 12 : 8));
        t.setMinimumHeight(px(big ? 72 : 56));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{color, (color & 0x00FFFFFF) | 0xB0000000});
        bg.setCornerRadius(px(18));
        t.setBackground(bg);
        ImageView badge = new ImageView(this);
        badge.setImageDrawable(new Icon((String) site[4], color));
        badge.setBackground(g.pill(0xFFFFFFFF, px(big ? 20 : 16)));
        int p = px(big ? 9 : 7);
        badge.setPadding(p, p, p, p);
        int bs = px(big ? 44 : 30);
        t.addView(badge, new LinearLayout.LayoutParams(bs, bs));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(px(big ? 12 : 0), px(big ? 0 : 6), px(big ? 2 : 0), 0);
        if (!big) tx.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = text((String) site[1], big ? 20 : 14, 0xFFFFFFFF, true);
        name.setMaxLines(1);
        if (!big) { name.setGravity(Gravity.CENTER); name.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); }
        name.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(name);
        if (big) tx.addView(text(Tr.t("dl.ytSub"), 13, 0xE6FFFFFF, false));
        t.addView(tx, big ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2));
        if (big) {
            ImageView arrow = new ImageView(this);
            arrow.setImageDrawable(new Icon("download", 0xFFFFFFFF));
            t.addView(arrow, new LinearLayout.LayoutParams(px(28), px(28)));
        }
        t.setOnClickListener(v -> openBrowser((String) site[2], (String) site[1], color));
        return t;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openBrowser(String url, String name, int color) {
        closeBrowser();
        if (dlView != null) dlView.setVisibility(View.GONE);
        browserView = new LinearLayout(this);
        browserView.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(g.panel(px(18), dp, 30));
        bar.setPadding(px(4), px(4), px(6), px(4));
        ImageButton close = iconButton("close", g.text, 48);
        close.setContentDescription(Tr.t("close"));
        close.setOnClickListener(v -> closeBrowser());
        bar.addView(close);
        ImageButton back = iconButton("back", g.text, 48);
        back.setContentDescription(Tr.t("back"));
        if (Tr.isArabic()) back.setRotationY(180);
        back.setOnClickListener(v -> { if (web != null && web.canGoBack()) web.goBack(); });
        bar.addView(back);
        webTitle = text(name, 15, g.text, true);
        webTitle.setSingleLine(true);
        webTitle.setEllipsize(TextUtils.TruncateAt.END);
        webTitle.setPadding(px(6), 0, px(6), 0);
        bar.addView(webTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        webGrab = text("⬇  " + Tr.t("dl.grab"), 15, 0xFFFFFFFF, true);
        webGrab.setGravity(Gravity.CENTER);
        webGrab.setMinHeight(px(46));
        webGrab.setPadding(px(16), 0, px(16), 0);
        webGrab.setBackground(g.pill(color, px(23)));
        webGrab.setFocusable(true);
        webGrab.setClickable(true);
        webGrab.setOnClickListener(v -> grabFromBrowser());
        bar.addView(webGrab);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.setMargins(px(8), px(8), px(8), px(6));
        browserView.addView(bar, blp);

        web = new android.webkit.WebView(this);
        android.webkit.WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        web.setBackgroundColor(Color.BLACK);
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v, android.webkit.WebResourceRequest r) {
                String s = r.getUrl().getScheme();
                // stay inside the app: ignore "open in YouTube app" / intent:// links
                return !("http".equals(s) || "https".equals(s));
            }
            @Override public void doUpdateVisitedHistory(android.webkit.WebView v, String u, boolean reload) { updateGrab(); }
            @Override public void onPageFinished(android.webkit.WebView v, String u) { updateGrab(); }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onReceivedTitle(android.webkit.WebView v, String title) {
                if (webTitle != null && title != null && !title.isEmpty()) webTitle.setText(title);
            }
        });
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        wlp.setMargins(px(8), 0, px(8), px(8));
        webFrame = new FrameLayout(this);
        webFrame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        browserView.addView(webFrame, wlp);
        webColor = color;
        askKey = null;
        askCard = null;
        content.addView(browserView, new FrameLayout.LayoutParams(-1, -1));
        web.loadUrl(url);
        updateGrab();
    }

    /** Is the page a single video / song (not the home page or a search)? */
    private static boolean isMediaPage(String url) {
        if (url == null) return false;
        Uri u = Uri.parse(url);
        String host = u.getHost() == null ? "" : u.getHost();
        String path = u.getPath() == null ? "" : u.getPath();
        if (host.contains("youtube.com") || host.contains("youtu.be")) {
            return host.contains("youtu.be") || u.getQueryParameter("v") != null || path.startsWith("/shorts/") || path.startsWith("/live/");
        }
        if (host.contains("bandcamp.com")) return path.contains("/track/") || path.contains("/album/");
        int depth = 0;
        for (String seg : path.split("/")) if (!seg.isEmpty()) depth++;
        return depth >= 2 && !path.startsWith("/search") && !path.startsWith("/discover");
    }

    private void updateGrab() {
        if (webGrab == null || web == null) return;
        // ask once the page has had a moment to show the video
        ui.removeCallbacks(askLater);
        if (isMediaPage(web.getUrl())) ui.postDelayed(askLater, 1200);
        else hideAsk();
        boolean ok = isMediaPage(web.getUrl());
        webGrab.setAlpha(ok ? 1f : 0.45f);
    }

    private void grabFromBrowser() {
        if (web == null) return;
        String u = web.getUrl();
        if (!isMediaPage(u)) { toast(Tr.t("dl.openVideoFirst")); return; }
        askKey = mediaKey(u);
        askDeclined.remove(askKey);
        showAskChoices(u);
    }

    // ---- "Download this?" card that floats over the page while the video keeps playing
    private FrameLayout webFrame;
    private LinearLayout askCard;
    private String askKey;                 // the video the card is about
    private final java.util.Set<String> askDeclined = new java.util.HashSet<>();
    private int webColor;
    private boolean askAudio = true;
    private final Runnable askLater = this::maybeAsk;

    /** Same video = same key, whatever extra parameters the site adds (time, playlist…). */
    private static String mediaKey(String url) {
        if (url == null) return "";
        Uri u = Uri.parse(url);
        String host = u.getHost() == null ? "" : u.getHost();
        String v = null;
        try { v = u.getQueryParameter("v"); } catch (Exception ignored) { }
        if (v != null) return "yt:" + v;
        String path = u.getPath() == null ? "" : u.getPath();
        if (host.contains("youtu.be")) return "yt:" + path.replace("/", "");
        if (path.startsWith("/shorts/") || path.startsWith("/live/")) return "yt:" + path.substring(path.indexOf('/', 1) + 1);
        return host + path;
    }

    private void maybeAsk() {
        if (web == null || webFrame == null) return;
        String url = web.getUrl();
        if (!isMediaPage(url)) { hideAsk(); return; }
        String key = mediaKey(url);
        if (key.equals(askKey) || askDeclined.contains(key)) return;
        askKey = key;
        showAskQuestion(url);
    }

    private void hideAsk() {
        if (askCard != null && webFrame != null) webFrame.removeView(askCard);
        askCard = null;
    }

    private LinearLayout newAskCard() {
        hideAsk();
        askCard = new LinearLayout(this);
        askCard.setOrientation(LinearLayout.VERTICAL);
        askCard.setBackground(g.panel(px(22), dp, 120));
        askCard.setPadding(px(18), px(14), px(18), px(14));
        askCard.setElevation(px(12));
        askCard.setClickable(true);   // touches on the card stay on the card; the rest of the page stays usable
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.setMargins(px(18), 0, px(18), 0);
        webFrame.addView(askCard, lp);
        return askCard;
    }

    private void showAskQuestion(String url) {
        LinearLayout c = newAskCard();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(new Icon("download", 0xFFFFFFFF));
        ic.setBackground(g.pill(webColor, px(20)));
        int p = px(8);
        ic.setPadding(p, p, p, p);
        head.addView(ic, new LinearLayout.LayoutParams(px(40), px(40)));
        TextView q = text(Tr.t("ask.q"), 18, g.text, true);
        q.setPadding(px(12), 0, 0, 0);
        head.addView(q, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(head);
        c.addView(text(Tr.t("ask.hint"), 13, g.muted, false));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        View yes = button(Tr.t("ask.yes"), true, v -> showAskChoices(url));
        View no = button(Tr.t("ask.no"), false, v -> { askDeclined.add(mediaKey(url)); hideAsk(); });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f);
        l1.setMargins(0, px(12), px(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1f);
        l2.setMargins(px(6), px(12), 0, 0);
        row.addView(yes, l1);
        row.addView(no, l2);
        c.addView(row);
    }

    private void showAskChoices(String url) {
        LinearLayout c = newAskCard();
        TextView wait = text(Tr.t("dl.fetching"), 16, g.text, true);
        c.addView(wait);
        android.widget.ProgressBar spin = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        spin.setIndeterminate(true);
        spin.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(webColor));
        c.addView(spin, new LinearLayout.LayoutParams(-1, px(10)));
        c.addView(button(Tr.t("dl.cancel"), false, v -> hideAsk()));
        final LinearLayout mine = c;
        YtDl.prepare(this);
        Runnable go = () -> YtDl.fetch(url, (info, err) -> {
            if (askCard != mine || web == null) return;   // user moved on
            if (info == null) {
                LinearLayout e = newAskCard();
                e.addView(text(Tr.t("dl.fetchFailed"), 16, g.text, true));
                e.addView(text(err == null ? "" : shortErr(err), 13, g.accent, false));
                e.addView(button(Tr.t("close"), false, v -> hideAsk()));
                return;
            }
            renderAskChoices(info);
        });
        if (YtDl.state == YtDl.READY) go.run(); else waitEngine(go, 0);
    }

    private void renderAskChoices(YtDl.Info info) {
        LinearLayout c = newAskCard();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(info.title, 15, g.text, true);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton x = iconButton("close", g.text, 40);
        x.setOnClickListener(v -> hideAsk());
        head.addView(x);
        c.addView(head);
        c.addView(chipRow(new String[]{"♪  " + Tr.t("dl.audio"), "▶  " + Tr.t("dl.video")}, askAudio ? 0 : 1, i -> {
            askAudio = i == 0;
            renderAskChoices(info);
        }));
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        for (YtDl.Choice ch : askAudio ? info.audio : info.video) {
            View r = choiceRow(ch);
            r.setOnClickListener(v -> {
                YtDl.enqueue(this, info, ch);
                LinearLayout done = newAskCard();
                TextView ok = text("✓  " + Tr.t("dl.started"), 17, g.text, true);
                done.addView(ok);
                done.addView(text(Tr.t("ask.started"), 13, g.muted, false));
                ui.postDelayed(() -> { if (askCard == done) hideAsk(); }, 2500);
            });
            list.addView(r);
        }
        int maxH = webFrame.getHeight() > 0 ? (int) (webFrame.getHeight() * 0.55f) : px(320);
        int rows = (askAudio ? info.audio : info.video).size();
        c.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(maxH, rows * px(64))));
    }

    private void closeBrowser() {
        if (browserView != null) content.removeView(browserView);
        if (web != null) {
            web.stopLoading();
            web.onPause();
            web.destroy();
        }
        web = null;
        browserView = null;
        webTitle = null;
        webGrab = null;
        ui.removeCallbacks(askLater);
        askCard = null;
        webFrame = null;
        if (dlView != null) dlView.setVisibility(View.VISIBLE);
    }

    private View choiceRow(YtDl.Choice c) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(g.pill(g.field(), px(16)));
        r.setPadding(px(16), px(4), px(8), px(4));
        r.setMinimumHeight(px(56));
        r.setFocusable(true);
        r.setClickable(true);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(new Icon(c.audio ? "music" : "video", g.accent));
        r.addView(ic, new LinearLayout.LayoutParams(px(24), px(24)));
        TextView l = text(c.label, 16, g.text, true);
        l.setPadding(px(12), 0, px(8), 0);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(c.sub, 13, g.muted, false));
        ImageView dl = new ImageView(this);
        dl.setImageDrawable(new Icon("download", g.onAccent));
        dl.setBackground(g.pill(g.accent, px(20)));
        int p = px(8);
        dl.setPadding(p, p, p, p);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(40), px(40));
        dlp.setMarginStart(px(10));
        r.addView(dl, dlp);
        r.setOnClickListener(v -> {
            if (YtDl.state != YtDl.READY) { toast(Tr.t("dl.preparing")); return; }
            YtDl.enqueue(this, dlInfo, c);
            toast(Tr.t("dl.started"));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(8);
        r.setLayoutParams(lp);
        return r;
    }

    private void dlFetch() {
        if (dlFetching) return;
        String u = YtDl.extractUrl(dlUrl);
        if (u == null) { toast(Tr.t("dl.noLink")); return; }
        dlUrl = u;
        dlError = "";
        dlFetching = true;
        dlInfo = null;
        dlThumb = null;
        if ("downloads".equals(tab)) showDownloads();
        Runnable go = () -> YtDl.fetch(u, (info, err) -> {
            dlFetching = false;
            dlInfo = info;
            dlError = err == null ? "" : shortErr(err);
            if (info != null && info.thumbnail != null) loadThumb(info.thumbnail);
            if ("downloads".equals(tab) && !isFinishing()) showDownloads();
        });
        if (YtDl.state == YtDl.READY) go.run();
        else waitEngine(go, 0);
    }

    private void waitEngine(Runnable then, int tries) {
        if (YtDl.state == YtDl.READY) { then.run(); return; }
        if (YtDl.state == YtDl.FAILED || tries > 240) {
            dlFetching = false;
            dlError = YtDl.stateError;
            if ("downloads".equals(tab) && web == null) showDownloads();
            return;
        }
        ui.postDelayed(() -> waitEngine(then, tries + 1), 500);
    }

    private static String shortErr(String m) {
        String[] lines = m.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) if (lines[i].contains("ERROR")) return lines[i].replace("ERROR:", "").trim();
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    private void loadThumb(String url) {
        io.execute(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                Bitmap b;
                try (java.io.InputStream in = c.getInputStream()) { b = android.graphics.BitmapFactory.decodeStream(in); }
                ui.post(() -> {
                    dlThumb = b;
                    if ("downloads".equals(tab) && dlInfo != null && !isFinishing()) showDownloads();
                });
            } catch (Exception ignored) { }
        });
    }

    /** Called by the ticker every half second while the app is open. */
    private void tickDownloads() {
        if (dlView == null || dlJobsBox == null) return;
        if (dlEngine != null) {
            String st;
            switch (YtDl.state) {
                case YtDl.READY: st = YtDl.engineVersion(this); break;
                case YtDl.FAILED: st = Tr.t("dl.engineFailed") + " " + YtDl.stateError; break;
                default: st = Tr.t("dl.preparing");
            }
            if (!st.contentEquals(dlEngine.getText())) dlEngine.setText(st);
        }
        if (YtDl.version != dlSeen) {
            dlSeen = YtDl.version;
            boolean finishedSomething = false;
            dlJobsBox.removeAllViews();
            dlRows.clear();
            List<YtDl.Job> snapshot;
            synchronized (YtDl.jobs) { snapshot = new ArrayList<>(YtDl.jobs); }
            if (snapshot.isEmpty()) dlJobsBox.addView(text(Tr.t("dl.none"), 14, g.muted, false));
            for (YtDl.Job j : snapshot) {
                dlJobsBox.addView(jobRow(j));
                if ("done".equals(j.status)) finishedSomething = true;
            }
            if (finishedSomething && !scanning) ui.postDelayed(this::quietRescan, 1500);
        }
        for (Map.Entry<Integer, Object[]> e : dlRows.entrySet()) {
            YtDl.Job j = (YtDl.Job) e.getValue()[0];
            android.widget.ProgressBar bar = (android.widget.ProgressBar) e.getValue()[1];
            TextView st = (TextView) e.getValue()[2];
            if ("merging".equals(j.status)) { bar.setIndeterminate(true); st.setText(Tr.t("dl.processing")); }
            else {
                bar.setIndeterminate(false);
                bar.setProgress(Math.round(j.progress));
                st.setText(String.format(Locale.US, "%.0f%%", j.progress) + (j.eta > 0 ? "  •  " + time(j.eta * 1000L) : ""));
            }
        }
    }

    private int lastRescanVersion = -1;
    private void quietRescan() {
        if (lastRescanVersion == YtDl.version || scanning) return;
        lastRescanVersion = YtDl.version;
        io.execute(() -> {
            MediaRepo fresh = new MediaRepo();
            fresh.scan(this);
            ui.post(() -> {
                repo.videos.clear(); repo.videos.addAll(fresh.videos);
                repo.music.clear(); repo.music.addAll(fresh.music);
                repo.folders.clear(); repo.folders.addAll(fresh.folders);
                scanned = true;
                refreshList();
            });
        });
    }

    private View jobRow(YtDl.Job j) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackground(g.pill(g.field(), px(16)));
        r.setPadding(px(14), px(10), px(10), px(10));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(new Icon(j.choice.audio ? "music" : "video", g.accent));
        top.addView(ic, new LinearLayout.LayoutParams(px(22), px(22)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(px(10), 0, px(6), 0);
        TextView t = text(j.title, 15, g.text, true);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(t);
        TextView st = text("", 13, g.muted, false);
        tx.addView(st);
        top.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        if (j.active()) {
            ImageButton x = iconButton("close", g.text, 44);
            x.setContentDescription(Tr.t("dl.cancel"));
            x.setOnClickListener(v -> YtDl.cancel(j));
            top.addView(x);
        } else if ("done".equals(j.status) && !j.file.isEmpty()) {
            ImageButton play = iconButton("play", g.onAccent, 44);
            play.setBackground(g.pill(g.accent, px(22)));
            play.setOnClickListener(v -> playFile(new java.io.File(j.file), !j.choice.audio));
            top.addView(play);
        }
        r.addView(top);
        if (j.active()) {
            android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(g.accent));
            bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(g.accent));
            r.addView(bar, new LinearLayout.LayoutParams(-1, px(10)));
            st.setText(j.label + "  •  " + Tr.t("queued".equals(j.status) ? "dl.queued" : "dl.running"));
            if (!"queued".equals(j.status)) dlRows.put(j.id, new Object[]{j, bar, st});
        } else {
            String s = j.label + "  •  " + Tr.t("dl." + j.status);
            if ("failed".equals(j.status) && !j.error.isEmpty()) s += "\n" + j.error;
            st.setText(s);
            st.setTextColor("failed".equals(j.status) ? g.accent : g.muted);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(8);
        r.setLayoutParams(lp);
        return r;
    }

    private void playFile(java.io.File f, boolean video) {
        if (controller == null || !f.exists()) { toast(Tr.t("error")); return; }
        Uri u = Uri.fromFile(f);
        controller.setMediaItem(PlaybackService.build(u.toString(), MediaRepo.stripExt(f.getName()), "", f.getAbsolutePath(), video));
        controller.prepare();
        controller.play();
        prefs.addRecent(u.toString(), MediaRepo.stripExt(f.getName()), video);
        showPlayer(true);
    }

    private void rebuildKeepingTab() {
        String t = tab;
        buildUi();
        tab = t;
        selectTab(t);
        if (controller != null) { playerView.setPlayer(controller); refreshPlayerState(); }
    }

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setBackground(g.panel(px(18), dp, 10));
        s.setPadding(px(16), px(12), px(16), px(14));
        TextView t = text(title, 18, g.text, true);
        t.setPadding(0, 0, 0, px(8));
        s.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(12);
        parent.addView(s, lp);
        return s;
    }

    interface IntAction { void run(int i); }

    private View chipRow(String[] labels, int selected, IntAction onPick) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView c = text(labels[i], 15, i == selected ? g.onAccent : g.text, i == selected);
            c.setGravity(Gravity.CENTER);
            c.setMinHeight(px(52));
            c.setPadding(px(18), 0, px(18), 0);
            c.setBackground(g.pill(i == selected ? g.accent : g.field(), px(26)));
            c.setFocusable(true);
            c.setClickable(true);
            c.setOnClickListener(v -> onPick.run(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, px(4), px(8), px(4));
            row.addView(c, lp);
        }
        hs.addView(row);
        return hs;
    }

    private View toggle(String label, String key, boolean def) {
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText(label);
        sw.setTextColor(g.text);
        sw.setTextSize(16);
        sw.setMinHeight(px(56));
        sw.setChecked(prefs.bool(key, def));
        sw.setOnCheckedChangeListener((b, on) -> prefs.setBool(key, on));
        return sw;
    }

    private View button(String label, boolean accent, View.OnClickListener l) {
        TextView b = text(label, 15, accent ? g.onAccent : g.text, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(px(52));
        b.setPadding(px(20), 0, px(20), 0);
        b.setBackground(g.pill(accent ? g.accent : g.field(), px(26)));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, px(10), px(10), 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ------------------------------------------------------------------ helpers
    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private ImageButton iconButton(String icon, int color, int sizeDp) {
        ImageButton b = new ImageButton(this);
        b.setImageDrawable(new Icon(icon, color));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = px(sizeDp * 0.24f);
        b.setPadding(p, p, p, p);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, g.pill(g.pressed(), px(sizeDp)));
        bg.addState(new int[]{android.R.attr.state_focused}, g.pill(g.pressed(), px(sizeDp)));
        bg.addState(new int[]{}, g.pill(Color.TRANSPARENT, px(sizeDp)));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(px(sizeDp), px(sizeDp)));
        b.setFocusable(true);
        return b;
    }

    private void openLink(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, ss = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, ss) : String.format(Locale.US, "%d:%02d", m, ss);
    }
}

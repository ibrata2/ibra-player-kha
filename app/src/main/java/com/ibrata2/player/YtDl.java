package com.ibrata2.player;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoFormat;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * YouTube (and other sites) downloader built on yt-dlp + ffmpeg.
 * State lives here (not in the activity) so downloads keep going when the screen rotates
 * or the user switches tabs; {@link DownloadService} keeps the process alive meanwhile.
 */
final class YtDl {

    // ---- engine state
    static final int NOT_READY = 0, PREPARING = 1, READY = 2, FAILED = -1;
    static volatile int state = NOT_READY;
    static volatile String stateError = "";
    /** Bumped whenever the job list or a job's status changes, so the UI knows to redraw. */
    static volatile int version;

    private static final ExecutorService setup = Executors.newSingleThreadExecutor();
    private static final ExecutorService queue = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    static final List<Job> jobs = Collections.synchronizedList(new ArrayList<>());
    private static int nextId = 1;

    interface Done<T> { void run(T value, String error); }

    static final class Choice {
        final String label, sub;
        final boolean audio;
        final String kind;   // mp3 | m4a | video
        final int value;     // audio kbps or video height (0 = best)
        Choice(String label, String sub, boolean audio, String kind, int value) {
            this.label = label; this.sub = sub; this.audio = audio; this.kind = kind; this.value = value;
        }
    }

    static final class Info {
        String url, title, uploader, thumbnail;
        int duration;
        final List<Choice> video = new ArrayList<>();
        final List<Choice> audio = new ArrayList<>();
    }

    static final class Job {
        final int id;
        final String url, title, label;
        final Choice choice;
        volatile String status = "queued";   // queued | running | merging | done | failed | canceled
        volatile float progress;
        volatile long eta;
        volatile String file = "", error = "";
        Job(int id, String url, String title, Choice c) {
            this.id = id; this.url = url; this.title = title; this.choice = c; this.label = c.label;
        }
        boolean active() { return "queued".equals(status) || "running".equals(status) || "merging".equals(status); }
    }

    // ------------------------------------------------------------------ setup
    static void prepare(Context ctx) {
        if (state == READY || state == PREPARING) return;
        state = PREPARING;
        version++;
        Context app = ctx.getApplicationContext();
        setup.execute(() -> {
            try {
                YoutubeDL.getInstance().init(app);
                FFmpeg.getInstance().init(app);
                state = READY;
            } catch (Throwable e) {
                stateError = String.valueOf(e.getMessage());
                state = FAILED;
            }
            version++;
            if (state == READY) autoUpdate(app);
        });
    }

    /** YouTube changes often; refresh yt-dlp quietly once a day so downloads keep working. */
    private static void autoUpdate(Context app) {
        long last = app.getSharedPreferences("ibra", Context.MODE_PRIVATE).getLong("ytdlpCheck", 0);
        if (System.currentTimeMillis() - last < 24L * 3600_000L) return;
        try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE);
            app.getSharedPreferences("ibra", Context.MODE_PRIVATE).edit().putLong("ytdlpCheck", System.currentTimeMillis()).apply();
        } catch (Throwable ignored) { }
    }

    static void updateEngine(Context ctx, Done<String> done) {
        Context app = ctx.getApplicationContext();
        setup.execute(() -> {
            try {
                YoutubeDL.UpdateStatus s = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE);
                String v = YoutubeDL.getInstance().versionName(app);
                UI.post(() -> done.run((s == YoutubeDL.UpdateStatus.DONE ? "✓ " : "") + (v == null ? "" : v), null));
            } catch (Throwable e) {
                UI.post(() -> done.run(null, String.valueOf(e.getMessage())));
            }
        });
    }

    static String engineVersion(Context ctx) {
        try { String v = YoutubeDL.getInstance().versionName(ctx.getApplicationContext()); return v == null ? "" : v; }
        catch (Throwable e) { return ""; }
    }

    // ------------------------------------------------------------------ info
    static void fetch(String url, Done<Info> done) {
        setup.execute(() -> {
            try {
                YoutubeDLRequest r = new YoutubeDLRequest(url);
                r.addOption("--no-playlist");
                VideoInfo vi = YoutubeDL.getInstance().getInfo(r);
                Info info = new Info();
                info.url = vi.getWebpageUrl() != null ? vi.getWebpageUrl() : url;
                info.title = vi.getTitle() != null ? vi.getTitle() : url;
                info.uploader = vi.getUploader() != null ? vi.getUploader() : "";
                info.thumbnail = vi.getThumbnail();
                info.duration = vi.getDuration();

                long bestAudio = 0;
                TreeMap<Integer, Long> heights = new TreeMap<>(Collections.reverseOrder());
                if (vi.getFormats() != null) {
                    for (VideoFormat f : vi.getFormats()) {
                        long size = f.getFileSize() > 0 ? f.getFileSize() : f.getFileSizeApproximate();
                        boolean hasVideo = f.getVcodec() != null && !"none".equals(f.getVcodec()) && f.getHeight() > 0;
                        boolean audioOnly = !hasVideo && f.getAcodec() != null && !"none".equals(f.getAcodec());
                        if (audioOnly) bestAudio = Math.max(bestAudio, size);
                        if (hasVideo) {
                            int h = normalize(f.getHeight(), f.getWidth());
                            Long prev = heights.get(h);
                            heights.put(h, prev == null ? size : Math.max(prev, size));
                        }
                    }
                }
                for (java.util.Map.Entry<Integer, Long> e : heights.entrySet()) {
                    int h = e.getKey();
                    long s = e.getValue() > 0 ? e.getValue() + bestAudio : 0;
                    info.video.add(new Choice(h + "p" + badge(h), size(s), false, "video", h));
                }
                if (info.video.isEmpty()) info.video.add(new Choice(Tr.t("dl.best"), "", false, "video", 0));

                long secs = Math.max(0, info.duration);
                info.audio.add(new Choice("MP3 320k", size(secs * 320 * 125), true, "mp3", 320));
                info.audio.add(new Choice("MP3 192k", size(secs * 192 * 125), true, "mp3", 192));
                info.audio.add(new Choice("MP3 128k", size(secs * 128 * 125), true, "mp3", 128));
                info.audio.add(new Choice("M4A " + Tr.t("dl.original"), size(bestAudio), true, "m4a", 0));
                UI.post(() -> done.run(info, null));
            } catch (Throwable e) {
                String msg = String.valueOf(e.getMessage());
                UI.post(() -> done.run(null, msg));
            }
        });
    }

    /** Portrait videos (Shorts) report height > width; use the short side like YouTube does. */
    private static int normalize(int h, int w) {
        int side = (w > 0 && w < h) ? w : h;
        int[] std = {144, 240, 360, 480, 720, 1080, 1440, 2160, 4320};
        for (int s : std) if (side <= s + 8) return s;
        return side;
    }

    private static String badge(int h) {
        if (h >= 4320) return "  8K";
        if (h >= 2160) return "  4K";
        if (h >= 1440) return "  2K";
        if (h >= 1080) return "  FHD";
        if (h >= 720) return "  HD";
        return "";
    }

    static String size(long bytes) {
        if (bytes <= 0) return "";
        if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.US, "≈ %.2f GB", bytes / 1073741824.0);
        return String.format(Locale.US, "≈ %.1f MB", bytes / 1048576.0);
    }

    // ------------------------------------------------------------------ downloads
    static File outDir() {
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Ibra Player Kha");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static Job enqueue(Context ctx, Info info, Choice c) {
        Job j;
        synchronized (jobs) { j = new Job(nextId++, info.url, info.title, c); jobs.add(0, j); }
        version++;
        Context app = ctx.getApplicationContext();
        try {
            Intent svc = new Intent(app, DownloadService.class);
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(svc); else app.startService(svc);
        } catch (Throwable ignored) { }
        queue.execute(() -> run(app, j));
        return j;
    }

    private static void run(Context app, Job j) {
        if ("canceled".equals(j.status)) return;
        j.status = "running";
        version++;
        File dir = outDir();
        long started = System.currentTimeMillis() - 2000;
        try {
            YoutubeDLRequest r = new YoutubeDLRequest(j.url);
            r.addOption("--no-playlist");
            r.addOption("--no-mtime");
            r.addOption("--windows-filenames");
            r.addOption("--embed-metadata");
            r.addOption("-o", dir.getAbsolutePath() + "/%(title).120B" + suffix(j.choice) + ".%(ext)s");
            Choice c = j.choice;
            if ("mp3".equals(c.kind)) {
                r.addOption("-f", "bestaudio/best");
                r.addOption("-x");
                r.addOption("--audio-format", "mp3");
                r.addOption("--audio-quality", c.value + "K");
            } else if ("m4a".equals(c.kind)) {
                r.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best");
                r.addOption("-x");
                r.addOption("--audio-format", "m4a");
            } else {
                r.addOption("-f", "bv*+ba/b");
                r.addOption("-S", (c.value > 0 ? "res:" + c.value + "," : "") + "vcodec:h264,acodec:m4a");
                r.addOption("--merge-output-format", "mp4");
            }
            YoutubeDL.getInstance().execute(r, "job" + j.id, (p, eta, line) -> {
                if (p != null && p >= 0) j.progress = p;
                if (eta != null) j.eta = eta;
                if (line != null && (line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]")
                        || line.startsWith("[Metadata]") || line.startsWith("[VideoConvertor]"))) {
                    if (!"merging".equals(j.status)) { j.status = "merging"; version++; }
                }
                return kotlin.Unit.INSTANCE;
            });
            File f = newest(dir, started);
            j.file = f != null ? f.getAbsolutePath() : "";
            j.progress = 100;
            j.status = "done";
            if (f != null) MediaScannerConnection.scanFile(app, new String[]{f.getAbsolutePath()}, null, null);
        } catch (YoutubeDL.CanceledException e) {
            j.status = "canceled";
        } catch (Throwable e) {
            if (!"canceled".equals(j.status)) {
                j.status = "failed";
                j.error = shortError(String.valueOf(e.getMessage()));
            }
        }
        version++;
    }

    private static String suffix(Choice c) {
        if ("video".equals(c.kind)) return c.value > 0 ? " [" + c.value + "p]" : "";
        return "";
    }

    private static File newest(File dir, long after) {
        File best = null;
        File[] all = dir.listFiles();
        if (all == null) return null;
        for (File f : all) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!f.isFile() || n.endsWith(".part") || n.endsWith(".ytdl") || n.contains(".temp.")) continue;
            if (f.lastModified() < after) continue;
            if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }

    private static String shortError(String m) {
        if (m == null) return "";
        String[] lines = m.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) if (lines[i].contains("ERROR")) return lines[i].replace("ERROR:", "").trim();
        return m.length() > 220 ? m.substring(m.length() - 220) : m;
    }

    static void cancel(Job j) {
        j.status = "canceled";
        version++;
        try { YoutubeDL.getInstance().destroyProcessById("job" + j.id); } catch (Throwable ignored) { }
    }

    static void clearFinished() {
        synchronized (jobs) {
            for (int i = jobs.size() - 1; i >= 0; i--) if (!jobs.get(i).active()) jobs.remove(i);
        }
        version++;
    }

    static boolean busy() {
        synchronized (jobs) { for (Job j : jobs) if (j.active()) return true; }
        return false;
    }

    static Job current() {
        synchronized (jobs) {
            for (Job j : jobs) if ("running".equals(j.status) || "merging".equals(j.status)) return j;
        }
        return null;
    }

    /** Pull the first http(s) link out of shared text ("Check this out https://youtu.be/…"). */
    static String extractUrl(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(text);
        return m.find() ? m.group() : null;
    }

    private YtDl() {}
}

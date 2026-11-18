package com.ibrata2.player;

import java.util.HashMap;
import java.util.Map;

/** Arabic / English strings. */
final class Tr {
    private static final Map<String, String[]> S = new HashMap<>();
    private static boolean arabic = true;

    static void setArabic(boolean ar) { arabic = ar; }
    static boolean isArabic() { return arabic; }

    static String t(String key) {
        String[] v = S.get(key);
        if (v == null) return key;
        return arabic ? v[1] : v[0];
    }

    private static void p(String k, String en, String ar) { S.put(k, new String[]{en, ar}); }

    static {
        p("videos", "Videos", "الفيديو");
        p("music", "Music", "الموسيقى");
        p("folders", "Folders", "المجلدات");
        p("recent", "Recent", "الأخيرة");
        p("favorites", "Favorites", "المفضلة");
        p("settings", "Settings", "الإعدادات");
        p("search", "Search…", "بحث…");
        p("empty", "No media found yet.\nPlug in a USB drive or SD card, then tap Rescan.", "لا توجد وسائط بعد.\nأدخل فلاشة أو كارت ذاكرة ثم اضغط إعادة الفحص.");
        p("rescan", "Rescan", "إعادة الفحص");
        p("permission", "Allow access to videos and music", "اسمح بالوصول إلى الفيديو والموسيقى");
        p("grant", "Allow", "سماح");
        p("nothing", "Nothing playing", "لا يوجد تشغيل");
        p("playAll", "Play all", "تشغيل الكل");
        p("shuffleAll", "Shuffle", "عشوائي");
        p("items", "%d items", "%d عنصر");
        p("back", "Back", "رجوع");
        p("audio", "Audio track", "المسار الصوتي");
        p("subtitles", "Subtitles", "الترجمة");
        p("off", "Off", "إيقاف");
        p("resize", "Screen fit", "ملاءمة الشاشة");
        p("fit", "Fit", "ملاءمة");
        p("fill", "Fill (crop)", "ملء (قص)");
        p("stretch", "Stretch", "تمديد");
        p("zoom", "Zoom", "تكبير");
        p("speed", "Speed", "السرعة");
        p("queue", "Up next", "التالي");
        p("repeatOff", "Repeat off", "بدون تكرار");
        p("repeatOne", "Repeat one", "تكرار الملف");
        p("repeatAll", "Repeat all", "تكرار الكل");
        p("shuffleOn", "Shuffle on", "عشوائي مفعّل");
        p("shuffleOff", "Shuffle off", "عشوائي متوقف");
        p("addFav", "Added to favorites", "أُضيف إلى المفضلة");
        p("removeFav", "Removed from favorites", "أُزيل من المفضلة");
        p("error", "Cannot play this file", "تعذّر تشغيل هذا الملف");
        p("resumed", "Resumed", "تم الاستئناف");
        p("unknownArtist", "Unknown artist", "فنان غير معروف");
        // downloads
        p("downloads", "Downloads", "التحميل");
        p("dl.title", "Download from YouTube", "التحميل من يوتيوب");
        p("dl.open", "Open and download", "افتح وحمّل");
        p("dl.ytSub", "Open YouTube, pick a video, tap Download", "افتح يوتيوب، اختار الفيديو، واضغط تحميل");
        p("dl.openHint", "Open any site, play the video or song you want, then tap the Download button at the top.",
                "افتح أي موقع، روح للفيديو أو الأغنية اللي تريدها، وبعدين اضغط زر التحميل فوق.");
        p("dl.grab", "Download", "تحميل");
        p("dl.openVideoFirst", "Open a video or a song first", "افتح فيديو أو أغنية أولاً");
        p("dl.link", "Or paste a link", "أو الصق الرابط");
        p("dl.paste", "Paste", "لصق");
        p("dl.fetch", "Show qualities", "عرض الجودات");
        p("dl.fetching", "Reading the video…", "جاري قراءة الفيديو…");
        p("dl.clear", "Clear", "مسح");
        p("dl.noLink", "No link found. Copy the video link first.", "ما لكيت رابط. انسخ رابط الفيديو أولاً.");
        p("dl.shareHint", "Tip: in the YouTube app tap Share → Ibra Player Kha and the link comes here by itself. Works with most video sites too.",
                "نصيحة: من تطبيق يوتيوب اضغط مشاركة ← Ibra Player Kha والرابط يجي هنا وحده. ويشتغل ويه أغلب مواقع الفيديو.");
        p("dl.fetchFailed", "Could not read this link:", "تعذّر قراءة هذا الرابط:");
        p("dl.choose", "Choose audio or video and the quality", "اختار صوت أو فيديو والجودة");
        p("dl.audio", "Audio", "صوت");
        p("dl.video", "Video", "فيديو");
        p("dl.best", "Best quality", "أعلى جودة");
        p("dl.original", "original", "الأصلي");
        p("dl.started", "Download started", "بدأ التحميل");
        p("dl.queued", "Waiting", "بالانتظار");
        p("dl.running", "Downloading", "جاري التحميل");
        p("dl.merging", "Processing", "جاري التجهيز");
        p("dl.processing", "Preparing the file…", "جاري تجهيز الملف…");
        p("dl.done", "Done ✓", "اكتمل ✓");
        p("dl.failed", "Failed", "فشل");
        p("dl.canceled", "Canceled", "أُلغي");
        p("dl.cancel", "Cancel", "إلغاء");
        p("dl.none", "No downloads yet.", "ماكو تحميلات بعد.");
        p("dl.clearDone", "Clear finished", "مسح المنتهية");
        p("dl.saveTo", "Saved to:", "مكان الحفظ:");
        p("dl.engine", "Download engine", "أداة التحميل");
        p("dl.engineHint", "If YouTube downloads stop working, update the download engine. It also updates itself once a day.",
                "إذا وقف التحميل من يوتيوب حدّث أداة التحميل. وهي تتحدث وحدها مرة باليوم.");
        p("dl.update", "Update download engine", "تحديث أداة التحميل");
        p("dl.updating", "Updating…", "جاري التحديث…");
        p("dl.updated", "Updated", "تم التحديث");
        p("dl.preparing", "Preparing the downloader (first time takes a few seconds)…", "جاري تجهيز أداة التحميل (أول مرة تاخذ كم ثانية)…");
        p("dl.engineFailed", "The downloader could not start:", "تعذّر تشغيل أداة التحميل:");
        // settings
        p("language", "Language", "اللغة");
        p("theme", "Theme", "المظهر");
        p("t.system", "Automatic (System)", "تلقائي (حسب النظام)");
        p("t.red-white", "Red & White Glass", "زجاجي أبيض وأحمر");
        p("t.white", "White Glass", "زجاجي أبيض");
        p("t.black", "Black Glass", "زجاجي أسود");
        p("t.red-black", "Red & Black Glass", "زجاجي أحمر وأسود");
        p("t.clear", "Clear Glass", "زجاجي شفاف");
        p("steering", "Steering wheel buttons", "أزرار الدركسون");
        p("steeringHint", "Standard media buttons (next, previous, play/pause) work automatically. If a button on your steering wheel does nothing, learn it here.",
                "أزرار الوسائط القياسية (التالي، السابق، تشغيل/إيقاف) تعمل تلقائياً. إذا كان زر في الدركسون لا يعمل، علّمه للبرنامج من هنا.");
        p("learn", "Learn a button", "تعليم زر جديد");
        p("pressNow", "Press the steering wheel button now…", "اضغط زر الدركسون الآن…");
        p("chooseAction", "What should this button do?", "ماذا يفعل هذا الزر؟");
        p("resetKeys", "Reset buttons", "إعادة ضبط الأزرار");
        p("key", "Key", "زر");
        p("a.playPause", "Play / Pause", "تشغيل / إيقاف مؤقت");
        p("a.play", "Play", "تشغيل");
        p("a.pause", "Pause", "إيقاف مؤقت");
        p("a.next", "Next", "التالي");
        p("a.previous", "Previous", "السابق");
        p("a.forward", "Forward 10 s", "تقديم 10 ثوانٍ");
        p("a.rewind", "Back 10 s", "ترجيع 10 ثوانٍ");
        p("a.stop", "Stop", "إيقاف");
        p("a.repeat", "Change repeat", "تغيير التكرار");
        p("a.shuffle", "Shuffle on/off", "تشغيل/إيقاف العشوائي");
        p("a.openPlayer", "Open player", "فتح المشغّل");
        p("a.ignore", "Do nothing", "لا شيء");
        p("globalCapture", "Global button capture", "التقاط الأزرار من أي مكان");
        p("enabled", "On", "مفعّل");
        p("disabled", "Off", "غير مفعّل");
        p("globalCaptureHint", "If the steering wheel buttons do nothing in Ibra Player Kha, turn this on: Settings → Accessibility → Ibra Player Kha → On. Buttons are only taken while Ibra Player Kha is the player you are using; radio and other apps keep their buttons. On Android 13+ you may first need App info → ⋮ → Allow restricted settings.",
                "إذا أزرار الدركسون ما تشتغل ويه Ibra Player Kha فعّل هذا: الإعدادات ← إمكانية الوصول ← Ibra Player Kha ← تشغيل. البرنامج ياخذ الأزرار فقط وقت يكون هو المشغّل المستخدم، والراديو وباقي التطبيقات تبقى أزرارها تشتغل. بأندرويد 13 وأحدث قد تحتاج أولاً: معلومات التطبيق ← ⋮ ← السماح بالإعدادات المقيّدة.");
        p("globalCaptureEnable", "Turn on global capture", "تفعيل الالتقاط من أي مكان");
        p("globalCaptureManage", "Accessibility settings", "إعدادات إمكانية الوصول");
        p("globalCaptureFind", "Find Ibra Player Kha in the list and turn it on", "دوّر على Ibra Player Kha بالقائمة وفعّله");
        p("buttonTest", "Button test (last button received)", "فحص الأزرار (آخر زر وصل)");
        p("pressAny", "Press any steering wheel button…", "اضغط أي زر بالدركسون…");
        p("unitTip", "If nothing appears here when you press a button, the unit sends it only to its own music app. Look in the unit's settings for an option like \"steering wheel for 3rd-party apps\" or \"media app\" and choose Ibra Player Kha, then turn on global capture above.",
                "إذا ما يطلع شي هنا لما تضغط الزر، فالجهاز يرسله لتطبيق الموسيقى مالته بس. دوّر بإعدادات الجهاز على خيار مثل \"الدركسون للتطبيقات الخارجية\" أو \"تطبيق الوسائط\" واختار Ibra Player Kha، وبعدين فعّل الالتقاط من أي مكان فوق.");
        p("scanning", "Scanning drives…", "جاري فحص الأقراص…");
        p("grantAll", "Show every folder on the disk", "إظهار كل المجلدات بالهارد");
        p("grantAllHint", "Tap, then allow \"Access all files\" for Ibra Player Kha", "اضغط هنا ثم فعّل \"السماح بالوصول لكل الملفات\" لـ Ibra Player Kha");
        p("updCheck", "Check for updates", "التحقق من التحديثات");
        p("updAuto", "Check for new versions automatically", "التحقق من الإصدارات الجديدة تلقائياً");
        p("updAvailable", "Version %1$s is available (you have %2$s).\nDownload and install it now?", "الإصدار %1$s متوفر (لديك %2$s).\nتنزيله وتثبيته الآن؟");
        p("updNow", "Update", "تحديث");
        p("updLater", "Later", "لاحقاً");
        p("updLatest", "You have the latest version (%s).", "لديك أحدث إصدار (%s).");
        p("updFailed", "Could not check for updates. Check the internet connection.", "تعذّر التحقق من التحديثات. تأكد من الإنترنت.");
        p("updDownloading", "Downloading the update…", "جاري تنزيل التحديث…");
        p("updInstalling", "Installing…", "جاري التثبيت…");
        p("updVerifyFailed", "The update could not be verified, so it was not installed.", "تعذّر التحقق من التحديث، لذلك لم يتم تثبيته.");
        p("updInstallFailed", "The update was not installed.", "لم يتم تثبيت التحديث.");
        p("updAllowInstall", "Allow Ibra Player Kha to install updates, then tap Update again.", "اسمح لـ Ibra Player Kha بتثبيت التحديثات، ثم اضغط تحديث مرة ثانية.");
        p("playback", "Playback", "التشغيل");
        p("resumeOnOpen", "Continue the last media when the app opens", "متابعة آخر ملف عند فتح البرنامج");
        p("rememberPos", "Remember the position of each file", "تذكّر موضع كل ملف");
        p("autoStart", "Start playing when the car turns on", "بدء التشغيل عند تشغيل السيارة");
        p("keepScreen", "Keep the screen on during video", "إبقاء الشاشة مضاءة أثناء الفيديو");
        p("subSize", "Subtitle size", "حجم الترجمة");
        p("small", "Small", "صغير");
        p("medium", "Medium", "متوسط");
        p("large", "Large", "كبير");
        p("xlarge", "Extra large", "كبير جداً");
        p("subBg", "Subtitle background box", "خلفية خلف الترجمة");
        p("storage", "Storage", "التخزين");
        p("allFiles", "Allow access to all files (subtitles and folders)", "السماح بالوصول لكل الملفات (للترجمة والمجلدات)");
        p("about", "About", "حول البرنامج");
        p("credit", "برمجة و تطوير ابراهيم الخزرجي", "برمجة و تطوير ابراهيم الخزرجي");
        p("version", "Version", "الإصدار");
        p("appsSite", "All my apps", "كل تطبيقاتي");
        p("codecNote", "Plays MP4, MKV, AVI, MOV, TS, WebM, FLV, 3GP, MP3, FLAC, WAV, M4A, OGG, Opus, AC-3, DTS, TrueHD and more. Video codecs use the device's hardware decoder.",
                "يشغّل MP4 و MKV و AVI و MOV و TS و WebM و FLV و 3GP و MP3 و FLAC و WAV و M4A و OGG و Opus و AC-3 و DTS و TrueHD وغيرها. ترميزات الفيديو تعتمد على معالج الجهاز.");
        p("on", "On", "تشغيل");
        p("close", "Close", "إغلاق");
    }

    private Tr() {}
}

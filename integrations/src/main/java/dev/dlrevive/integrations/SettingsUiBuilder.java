package dev.dlrevive.integrations;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Builds the dlrevive YTM settings section entirely by reflection.
 *
 * <p>This class deliberately has NO compile-time dependency on androidx.preference: the
 * Preference / PreferenceCategory / PreferenceGroup types and their obfuscated members are
 * resolved from the running app at runtime. It therefore compiles against plain android.jar +
 * gson and can be dropped into the integrations patch jar without pulling AndroidX onto the
 * integrations compile classpath.</p>
 *
 * <p>Verified obfuscated API (YTM 8.40.54 classes.dex):</p>
 * <ul>
 *   <li>{@code androidx.preference.Preference} / {@code PreferenceCategory} / {@code PreferenceGroup}</li>
 *   <li>{@code Preference(Context)}, {@code PreferenceCategory(Context)} constructors</li>
 *   <li>{@code P(CharSequence)}=setTitle, {@code n(CharSequence)}=setSummary,
 *       {@code L(String)}=setKey, {@code Q(boolean)}=setVisible</li>
 *   <li>{@code PreferenceGroup.ae(Preference)}=addPreference (public; calls private ah + notify)</li>
 *   <li>{@code Preference.o : Lejw} where {@code Lejw.a():V} is the click listener hook</li>
 * </ul>
 *
 * <p>Nothing here ever throws into the host app: every reflective step is defensive and the
 * whole section silently disappears (with a log line) if an API is missing or reshaped.</p>
 */
public final class SettingsUiBuilder {

    private static final String TAG = "dlrevive";

    private static final String PREF_CLASS = "androidx.preference.Preference";
    private static final String CATEGORY_CLASS = "androidx.preference.PreferenceCategory";
    private static final String GROUP_CLASS = "androidx.preference.PreferenceGroup";

    /**
     * Reflection-safe stand-in for {@code WeakHashMap<Preference, Runnable>}: the Preference type
     * does not exist on this compile classpath, so Object is used. Keys are the real Preference
     * instances; Preference does not override equals/hashCode, so this is identity dispatch.
     * A separate Proxy + handler is created per Preference, closing over that exact key.
     */
    private static final WeakHashMap<Object, Runnable> CLICKS = new WeakHashMap<>();

    private static final String OFFLINE_TRADEOFF =
            "Files are saved to public Music storage and open in other players; they do not "
                    + "appear in YTM's own Offline tab (no Premium entitlement).";

    private SettingsUiBuilder() {
    }

    /**
     * Single-register entry point for the injected smali: pass the SettingsHeadersFragment
     * instance and resolve its Context + PreferenceScreen reflectively. Never throws.
     */
    public static void installFromFragment(Object fragment) {
        try {
            if (fragment == null) {
                return;
            }
            Log.d(TAG, "settings hook fired: " + fragment.getClass().getName());
            Context ctx = null;
            Method getContext = findMethod(fragment.getClass(), "getContext", 0);
            if (getContext != null) {
                try {
                    Object value = getContext.invoke(fragment);
                    if (value instanceof Context) {
                        ctx = (Context) value;
                    }
                } catch (Throwable ignored) {
                    // try getActivity next
                }
            }
            if (ctx == null) {
                Method getActivity = findMethod(fragment.getClass(), "getActivity", 0);
                if (getActivity != null) {
                    Object value = getActivity.invoke(fragment);
                    if (value instanceof Context) {
                        ctx = (Context) value;
                    }
                }
            }
            Object screen = resolveScreen(fragment, null);
            install(ctx, screen);
        } catch (Throwable t) {
            log(t);
        }
    }

    /** Build and attach the section. {@code preferenceScreen} is the object returned by the
     *  fragment's getPreferenceScreen(); if a fragment is passed instead, we resolve it. */
    public static void install(Context ctx, Object preferenceScreen) {
        if (ctx == null || preferenceScreen == null) {
            Log.w(TAG, "install: null context or screen; skipping dlrevive settings");
            return;
        }
        Log.d(TAG, "settings install start: ctx=" + (ctx == null ? "null" : ctx.getClass().getName())
                + " screen=" + (preferenceScreen == null ? "null" : preferenceScreen.getClass().getName()));
        try {
            Class<?> prefClass = Class.forName(PREF_CLASS);
            Class<?> categoryClass = Class.forName(CATEGORY_CLASS);
            Class<?> groupClass;
            try {
                groupClass = Class.forName(GROUP_CLASS);
            } catch (ClassNotFoundException ignored) {
                groupClass = null; // only used for a best-effort instanceof check
            }

            Context baseCtx = ctx.getApplicationContext();
            final Context app = baseCtx != null ? baseCtx : ctx;
            Object screen = resolveScreen(preferenceScreen, groupClass);

            // Obtain the screen's PreferenceManager (getPreferenceManager():Lekv) and assign it
            // to every preference we create via Preference.C(Lekv)=setPreferenceManager. Without
            // this, addPreference (ag) dereferences a null manager and throws NPE.
            Object managerValue = null;
            try {
                Method getPm = screen == null ? null : findMethod(screen.getClass(), "getPreferenceManager", 0);
                if (getPm != null) {
                    managerValue = getPm.invoke(screen);
                }
            } catch (Throwable t) {
                log(t);
            }
            final Object manager = managerValue;

            Object category = newPreference(categoryClass, app, manager);
            if (category == null) {
                Log.w(TAG, "install: could not construct PreferenceCategory");
                return;
            }
            callTyped(category, "P", CharSequence.class, (CharSequence) "dlrevive");
            callTyped(category, "L", String.class, "dlrevive_settings_category");

            // Attach the category to the screen FIRST so it inherits the screen's
            // PreferenceManager; only then can child rows be added (addPreference calls
            // PreferenceManager.a() on the receiving group, which is null until attached).
            boolean attached = call(screen, "ag", category);
            Log.d(TAG, "settings category attached=" + attached + " manager=" + (manager != null));

            // --- Enable downloads -------------------------------------------------------
            final Object enableRow = newPreference(prefClass, app, manager);
            if (enableRow != null) {
                callTyped(enableRow, "P", CharSequence.class, (CharSequence) "Enable downloads");
                callTyped(enableRow, "n", CharSequence.class, (CharSequence) enabledSummary(app));
                callTyped(enableRow, "L", String.class, "dlrevive_enabled");
                attachClick(app, enableRow, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DownloadConfig c = loadConfig(app);
                            boolean next = !c.isEnabled();
                            c.setEnabled(next);
                            c.save(configFile(app));
                            callTyped(enableRow, "n", CharSequence.class, (CharSequence) (next ? "Enabled" : "Disabled"));
                        } catch (Throwable t) {
                            log(t);
                        }
                    }
                });
                call(category, "ag", enableRow);
            }

            // --- Audio format -----------------------------------------------------------
            final Object formatRow = newPreference(prefClass, app, manager);
            if (formatRow != null) {
                callTyped(formatRow, "P", CharSequence.class, (CharSequence) "Audio format");
                callTyped(formatRow, "n", CharSequence.class, (CharSequence) formatSummary(app));
                callTyped(formatRow, "L", String.class, "dlrevive_preferred_extension");
                attachClick(app, formatRow, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DownloadConfig c = loadConfig(app);
                            String cur = c.getPreferredExtension();
                            String next = "m4a".equals(cur) ? "opus"
                                    : "opus".equals(cur) ? "webm" : "m4a";
                            c.setPreferredExtension(next);
                            c.save(configFile(app));
                            callTyped(formatRow, "n", CharSequence.class, (CharSequence) ("Current: " + next + " (tap to cycle)"));
                        } catch (Throwable t) {
                            log(t);
                        }
                    }
                });
                call(category, "ag", formatRow);
            }

            // --- Download folder --------------------------------------------------------
            final Object folderRow = newPreference(prefClass, app, manager);
            if (folderRow != null) {
                callTyped(folderRow, "P", CharSequence.class, (CharSequence) "Download folder");
                callTyped(folderRow, "n", CharSequence.class, (CharSequence) folderSummary(app));
                callTyped(folderRow, "L", String.class, "dlrevive_download_subdirectory");
                attachClick(app, folderRow, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DownloadConfig c = loadConfig(app);
                            String cur = c.getDownloadSubdirectory();
                            String next = "dlrevive".equals(cur) ? "Downloads"
                                    : "Downloads".equals(cur) ? "MyMusic" : "dlrevive";
                            c.setDownloadSubdirectory(next);
                            c.save(configFile(app));
                            callTyped(folderRow, "n", CharSequence.class, (CharSequence) ("Current: " + next + " (tap to cycle)"));
                        } catch (Throwable t) {
                            log(t);
                        }
                    }
                });
                call(category, "ag", folderRow);
            }

            // --- Allow mobile data ------------------------------------------------------
            final Object mobileRow = newPreference(prefClass, app, manager);
            if (mobileRow != null) {
                callTyped(mobileRow, "P", CharSequence.class, (CharSequence) "Allow mobile data");
                callTyped(mobileRow, "n", CharSequence.class, (CharSequence) mobileSummary(app));
                callTyped(mobileRow, "L", String.class, "dlrevive_allow_mobile_data");
                attachClick(app, mobileRow, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DownloadConfig c = loadConfig(app);
                            boolean next = !c.isAllowMobileData();
                            c.setAllowMobileData(next);
                            c.save(configFile(app));
                            callTyped(mobileRow, "n", CharSequence.class, (CharSequence) (next ? "Allowed" : "Blocked"));
                        } catch (Throwable t) {
                            log(t);
                        }
                    }
                });
                call(category, "ag", mobileRow);
            }

            // --- Version / status (non-clickable) ---------------------------------------
            Object versionRow = newPreference(prefClass, app, manager);
            if (versionRow != null) {
                callTyped(versionRow, "P", CharSequence.class, (CharSequence) "Version / status");
                callTyped(versionRow, "n", CharSequence.class, (CharSequence)
                        ("dlrevive " + AboutStatus.PATCH_VERSION + " (patch loaded)"));
                callTyped(versionRow, "L", String.class, "dlrevive_version");
                call(category, "ag", versionRow); // no listener -> info row
            }

            // --- About offline library (non-clickable) ----------------------------------
            Object aboutRow = newPreference(prefClass, app, manager);
            if (aboutRow != null) {
                callTyped(aboutRow, "P", CharSequence.class, (CharSequence) "About offline library");
                callTyped(aboutRow, "n", CharSequence.class, (CharSequence) OFFLINE_TRADEOFF);
                callTyped(aboutRow, "L", String.class, "dlrevive_offline_about");
                call(category, "ag", aboutRow); // no listener -> info row
            }

            // --- My downloads (click builds/refreshes child rows) -----------------------
            final Object downloadsRow = newPreference(prefClass, app, manager);
            if (downloadsRow != null) {
                callTyped(downloadsRow, "P", CharSequence.class, (CharSequence) "My downloads");
                callTyped(downloadsRow, "n", CharSequence.class, (CharSequence) "Tap to list downloaded files");
                callTyped(downloadsRow, "L", String.class, "dlrevive_my_downloads");
                final List<Object> fileRows = new ArrayList<>();
                attachClick(app, downloadsRow, new Runnable() {
                    @Override
                    public void run() {
                        refreshDownloads(app, prefClass, category, downloadsRow, fileRows, manager);
                    }
                });
                call(category, "ag", downloadsRow);
            }

            // --- Verify attach (category was attached to screen earlier) -----------------
            Method countMethod = findMethod(category.getClass(), "k", 0);
            Object categoryCount = null;
            if (countMethod != null) {
                try {
                    categoryCount = countMethod.invoke(category);
                } catch (Throwable ignored) {
                }
            }
            Method screenCount = findMethod(screen == null ? Object.class : screen.getClass(), "k", 0);
            Object screenCountValue = null;
            if (screenCount != null) {
                try {
                    screenCountValue = screenCount.invoke(screen);
                } catch (Throwable ignored) {
                }
            }
            Log.d(TAG, "settings install attached=" + attached + " categoryChildren=" + categoryCount
                    + " screenChildren=" + screenCountValue + " screen="
                    + (screen == null ? "null" : screen.getClass().getName()));
            if (!attached) {
                Log.e(TAG, "install: could not attach dlrevive category to preference screen");
            }
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "androidx.preference not available; skipping dlrevive settings", e);
        } catch (NoSuchMethodError | NoSuchFieldError e) {
            Log.w(TAG, "androidx.preference API mismatch; skipping dlrevive settings", e);
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                log(((InvocationTargetException) t).getCause());
            } else {
                log(t);
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Downloaded-file listing
    // ---------------------------------------------------------------------------------

    private static void refreshDownloads(Context app, Class<?> prefClass, Object category,
                                         Object downloadsRow, List<Object> fileRows, Object manager) {
        try {
            // We have no verified obfuscated removePreference name, so reliably hide the old
            // rows via the verified Q(boolean)=setVisible(false), then append fresh ones.
            for (Object old : fileRows) {
                callTyped(old, "Q", boolean.class, false);
            }
            fileRows.clear();

            DownloadConfig c = loadConfig(app);
            String subdir = c.getDownloadSubdirectory();
            // On API 29+ scoped storage, File.listFiles() cannot read /sdcard/Music even though
            // DownloadManager wrote there, so query MediaStore instead (the same entries the
            // system music player sees).
            java.util.List<DownloadedFiles.Entry> entries = mediaStoreDownloads(app, subdir);
            callTyped(downloadsRow, "n", CharSequence.class, (CharSequence)
                    ("Tap to refresh - " + entries.size() + " file(s) in Music/" + subdir));

            for (final DownloadedFiles.Entry entry : entries) {
                Object row = newPreference(prefClass, app, manager);
                if (row == null) {
                    continue;
                }
                callTyped(row, "P", CharSequence.class, (CharSequence) entry.name);
                callTyped(row, "n", CharSequence.class, (CharSequence)
                        ("Music/" + subdir + "/" + entry.name + "  " + DownloadedFiles.humanSize(entry.sizeBytes)));
                final String ext = entry.extension;
                final String path = entry.path;
                attachClick(app, row, new Runnable() {
                    @Override
                    public void run() {
                        openFile(app, new File(path), ext);
                    }
                });
                call(category, "ag", row);
                fileRows.add(row);
            }
        } catch (Throwable t) {
            log(t);
        }
    }

    /**
     * List our downloaded audio files from MediaStore, filtered to the configured subfolder.
     * Works on API 29+ where direct File access to public Music storage is restricted.
     */
    private static java.util.List<DownloadedFiles.Entry> mediaStoreDownloads(Context ctx, String subdir) {
        java.util.List<DownloadedFiles.Entry> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            String[] projection = new String[]{
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATE_MODIFIED
            };
            String selection = MediaStore.Audio.Media.DATA + " LIKE ?";
            String[] args = new String[]{"%/" + subdir + "/%"};
            cursor = ctx.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, args, MediaStore.Audio.Media.DISPLAY_NAME + " ASC");
            if (cursor == null) {
                return result;
            }
            int nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
            int dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                if (nameCol < 0 || dataCol < 0) {
                    continue;
                }
                String name = cursor.getString(nameCol);
                String path = cursor.getString(dataCol);
                if (name == null || path == null) {
                    continue;
                }
                int dot = name.lastIndexOf('.');
                String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!"m4a".equals(ext) && !"opus".equals(ext) && !"webm".equals(ext)) {
                    continue;
                }
                long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0L;
                long date = dateCol >= 0 ? cursor.getLong(dateCol) : 0L;
                result.add(new DownloadedFiles.Entry(name, path, ext, size, date));
            }
        } catch (Throwable t) {
            log(t);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return result;
    }

    /** Resolve a MediaStore content:// Uri for a public-path file and open it with ACTION_VIEW. */
    private static void openFile(Context ctx, File file, String ext) {
        try {
            Uri uri = mediaStoreUri(ctx, file);
            if (uri == null) {
                Log.w(TAG, "no MediaStore entry for " + file.getName());
                return;
            }
            String mime = DownloadedFiles.mimeForExtension(ext);
            // Prefer YTM's own preview player (in-app, takes a content Uri, no Premium gate).
            // Falls back to the generic system player if YTM cannot handle the intent.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(ctx.getPackageName(), LibraryEntry.PREVIEW_PLAYER_ACTIVITY);
            try {
                ctx.startActivity(intent);
                Log.d(TAG, "opened file in YTM preview player: " + file.getName());
                return;
            } catch (Throwable nativeFailure) {
                Log.w(TAG, "YTM preview player unavailable, using system player", nativeFailure);
            }
            Intent fallback = new Intent(Intent.ACTION_VIEW);
            fallback.setDataAndType(uri, mime);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(fallback);
        } catch (Throwable t) {
            log(t);
        }
    }

    private static Uri mediaStoreUri(Context ctx, File file) {
        Cursor cursor = null;
        try {
            String[] projection = new String[]{MediaStore.Audio.Media._ID};
            String selection = MediaStore.Audio.Media.DATA + "=?";
            cursor = ctx.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, new String[]{file.getAbsolutePath()}, null);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Throwable t) {
            log(t);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }
        return null;
    }

    private static String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------------------------
    // Config helpers
    // ---------------------------------------------------------------------------------

    private static File configFile(Context app) {
        return ConfigStore.file(app);
    }

    private static DownloadConfig loadConfig(Context app) {
        return DownloadConfig.load(configFile(app));
    }

    private static String enabledSummary(Context app) {
        return loadConfig(app).isEnabled() ? "Enabled" : "Disabled";
    }

    private static String formatSummary(Context app) {
        return "Current: " + loadConfig(app).getPreferredExtension() + " (tap to cycle)";
    }

    private static String folderSummary(Context app) {
        return "Current: " + loadConfig(app).getDownloadSubdirectory() + " (tap to cycle)";
    }

    private static String mobileSummary(Context app) {
        return loadConfig(app).isAllowMobileData() ? "Allowed" : "Blocked";
    }

    // ---------------------------------------------------------------------------------
    // Reflection helpers
    // ---------------------------------------------------------------------------------

    /** Resolve the real PreferenceScreen when a fragment was passed instead of its screen. */
    private static Object resolveScreen(Object candidate, Class<?> groupClass) {
        if (groupClass != null && groupClass.isInstance(candidate)) {
            return candidate;
        }
        try {
            Method getter = findMethod(candidate.getClass(), "getPreferenceScreen", 0);
            if (getter != null) {
                Object screen = getter.invoke(candidate);
                if (screen != null && (groupClass == null || groupClass.isInstance(screen))) {
                    return screen;
                }
            }
        } catch (Throwable t) {
            log(t);
        }
        return candidate;
    }

    /** Assign the PreferenceManager (Preference.C(Lekv)) to a newly created preference. */
    private static void assignManager(Object pref, Object manager) {
        if (pref == null || manager == null) {
            return;
        }
        callTyped(pref, "C", findManagerType(manager), manager);
    }

    private static Class<?> findManagerType(Object manager) {
        // The declared parameter type is the obfuscated PreferenceManager class itself.
        return manager.getClass();
    }

    private static Object newPreference(Class<?> cls, Context ctx, Object manager) {
        try {
            Constructor<?> ctor = cls.getConstructor(Context.class);
            Object pref = ctor.newInstance(ctx);
            assignManager(pref, manager);
            return pref;
        } catch (NoSuchMethodException e) {
            try {
                for (Constructor<?> c : cls.getDeclaredConstructors()) {
                    if (c.getParameterCount() == 1
                            && c.getParameterTypes()[0].isAssignableFrom(ctx.getClass())) {
                        c.setAccessible(true);
                        Object pref = c.newInstance(ctx);
                        assignManager(pref, manager);
                        return pref;
                    }
                }
            } catch (Throwable t) {
                log(t);
            }
            Log.w(TAG, "no (Context) constructor on " + cls.getName(), e);
            return null;
        } catch (InvocationTargetException e) {
            log(e.getCause());
            return null;
        } catch (Throwable t) {
            log(t);
            return null;
        }
    }

    /** Find a method by obfuscated name and arity, walking up the hierarchy. */
    private static Method findMethod(Class<?> start, String name, int paramCount) {
        return findMethod(start, name, paramCount, null);
    }

    /**
     * Find a method by name/arity and, when {@code expectedParamTypes} is supplied, verify each
     * declared parameter type (primitive or reference) to avoid resolving a wrong overload that
     * happens to share the obfuscated name.
     */
    private static Method findMethod(Class<?> start, String name, int paramCount,
                                     Class<?>[] expectedParamTypes) {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            Method[] methods;
            try {
                methods = c.getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }
            for (Method m : methods) {
                if (!m.getName().equals(name) || m.getParameterCount() != paramCount) {
                    continue;
                }
                if (expectedParamTypes != null) {
                    Class<?>[] actual = m.getParameterTypes();
                    boolean matches = true;
                    for (int i = 0; i < expectedParamTypes.length; i++) {
                        if (actual[i] != expectedParamTypes[i]
                                && !(!actual[i].isPrimitive()
                                    && expectedParamTypes[i] != null
                                    && actual[i].isAssignableFrom(expectedParamTypes[i]))) {
                            matches = false;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                    // call may still work if the method is public
                }
                return m;
            }
        }
        return null;
    }

    /** Typed variant of {@link #call(Object, String, Object...)} for obfuscated setters. */
    private static boolean callTyped(Object target, String name, Class<?> paramType, Object arg) {
        Method m = findMethod(target.getClass(), name, 1, new Class<?>[]{paramType});
        if (m == null) {
            Log.w(TAG, "reflection: no typed method " + name + " on " + target.getClass().getName());
            return false;
        }
        try {
            m.invoke(target, arg);
            return true;
        } catch (Throwable t) {
            log(t instanceof InvocationTargetException
                    ? ((InvocationTargetException) t).getCause() : t);
            return false;
        }
    }

    /** Call an obfuscated method; never propagate a failure into the app. */
    private static boolean call(Object target, String name, Object... args) {
        int arity = args == null ? 0 : args.length;
        Method m = findMethod(target.getClass(), name, arity);
        if (m == null) {
            Log.w(TAG, "reflection: no method " + name + " on " + target.getClass().getName());
            return false;
        }
        try {
            m.invoke(target, args);
            return true;
        } catch (InvocationTargetException e) {
            log(e.getCause());
            return false;
        } catch (Throwable t) {
            log(t);
            return false;
        }
    }

    /**
     * Attach a click listener to one Preference. The field {@code o} holds an app-internal
     * interface ({@code Lejw}) with one abstract method {@code a():V}. We cannot implement that
     * interface at compile time, so a SEPARATE dynamic Proxy is created for each Preference; its
     * InvocationHandler closes over that exact Preference and looks up its Runnable in CLICKS.
     */
    private static void attachClick(Context ctx, Object pref, Runnable action) {
        try {
            Field field = null;
            for (Class<?> c = pref.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field candidate = c.getDeclaredField("o");
                    if (candidate.getType().isInterface()) {
                        field = candidate;
                        break;
                    }
                } catch (NoSuchFieldException ignored) {
                    // keep walking
                }
            }
            if (field == null) {
                Log.w(TAG, "reflection: Preference.o listener field not found");
                return;
            }
            final Class<?> listenerInterface = field.getType();
            final Object key = pref; // captured per-Proxy identity

            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getDeclaringClass() == Object.class) {
                        String n = method.getName();
                        if ("hashCode".equals(n)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(n)) {
                            return proxy == args[0];
                        }
                        if ("toString".equals(n)) {
                            return "dlrevive-click";
                        }
                    }
                    if ("a".equals(method.getName()) && (args == null || args.length == 0)) {
                        Runnable r;
                        synchronized (CLICKS) {
                            r = CLICKS.get(key);
                        }
                        if (r != null) {
                            try {
                                r.run();
                            } catch (Throwable t) {
                                log(t);
                            }
                        }
                    }
                    return null;
                }
            };

            ClassLoader loader = listenerInterface.getClassLoader();
            if (loader == null) {
                loader = ctx.getClassLoader() != null
                        ? ctx.getClassLoader()
                        : SettingsUiBuilder.class.getClassLoader();
            }
            Object proxy = Proxy.newProxyInstance(
                    loader, new Class<?>[]{listenerInterface}, handler);
            field.setAccessible(true);
            field.set(pref, proxy);
            synchronized (CLICKS) {
                CLICKS.put(pref, action);
            }
        } catch (NoSuchFieldError | NoSuchMethodError e) {
            Log.w(TAG, "click hook API mismatch", e);
        } catch (Throwable t) {
            log(t);
        }
    }

    private static void log(Throwable t) {
        try {
            Log.w(TAG, "dlrevive settings: defensive failure", t);
        } catch (Throwable ignored) {
            // logging must never be the thing that crashes the app
        }
    }
}

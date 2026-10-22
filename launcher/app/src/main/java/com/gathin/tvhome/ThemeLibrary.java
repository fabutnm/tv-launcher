package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Themes kept on the TV under themes/<name>/ in {@link Config#dir}, each laid out like a theme folder in the repo
 * (config.json, optional wallpaper.jpg and wallpapers/). Launcher settings lists them; picking one copies its files
 * into place and writes a new theme_revision, which Config treats exactly like a theme push from the PC.
 * The library is filled once over ADB (ThemeProvider accepts themes/<name>/... paths); nothing here uses the network.
 */
final class ThemeLibrary {
    static final String DIR = "themes";
    /** "name:revision" of the library theme picked last; see {@link #current}. */
    static final String CHOICE_KEY = "theme_name";
    private static final String TAG = "ThemeLibrary";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    private ThemeLibrary() {}

    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Folder names of the library themes that have a config.json, sorted. */
    static List<String> names(Context ctx) {
        List<String> names = new ArrayList<>();
        File dir = Config.dir(ctx);
        File[] folders = dir == null ? null : new File(dir, DIR).listFiles(File::isDirectory);
        if (folders == null) return names;
        for (File f : folders) {
            if (validName(f.getName()) && new File(f, Config.FILE_NAME).isFile()) names.add(f.getName());
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** The theme's "name" key from its config.json, or the folder name. Config ignores the key. */
    static String label(Context ctx, String name) {
        File dir = Config.dir(ctx);
        if (dir == null) return name;
        File config = new File(dir, DIR + "/" + name + "/" + Config.FILE_NAME);
        if (config.length() > 64 * 1024) return name;
        try (InputStream in = new FileInputStream(config)) {
            byte[] buf = new byte[(int) config.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            String label = new JSONObject(text).optString("name", "").trim();
            return label.isEmpty() ? name : label;
        } catch (Exception e) {
            return name;
        }
    }

    /** Copies the theme into place. Runs off the main thread: a wallpaper collection is a few MB. */
    static boolean apply(Context ctx, String name) {
        File dir = Config.dir(ctx);
        if (dir == null || !validName(name)) return false;
        File src = new File(dir, DIR + "/" + name);
        if (!new File(src, Config.FILE_NAME).isFile()) return false;
        try {
            copy(new File(src, Config.FILE_NAME), new File(dir, Config.FILE_NAME));
            File wallpaper = new File(src, Config.WALLPAPER_NAME);
            File target = new File(dir, Config.WALLPAPER_NAME);
            if (wallpaper.isFile()) copy(wallpaper, target);
            else if (target.exists() && !target.delete()) throw new IOException("cannot delete " + target);
            File walls = new File(dir, ThemeFiles.WALLPAPERS);
            for (File f : collection(walls)) {
                if (!f.delete()) throw new IOException("cannot delete " + f);
            }
            List<File> fresh = collection(new File(src, ThemeFiles.WALLPAPERS));
            if (!fresh.isEmpty() && !walls.isDirectory() && !walls.mkdirs()) throw new IOException("cannot create " + walls);
            for (File f : fresh) copy(f, new File(walls, f.getName()));
            String revision = UUID.randomUUID().toString().replace("-", "");
            try (OutputStream out = new FileOutputStream(new File(dir, Config.THEME_REVISION_NAME))) {
                out.write(revision.getBytes(StandardCharsets.US_ASCII));
            }
            ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(CHOICE_KEY, name + ":" + revision).commit();
        } catch (IOException e) {
            Log.w(TAG, "could not apply theme " + name + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * The library theme in place, or null: none was picked on the TV, or a push or reset from the PC has replaced it
     * since (theme_revision no longer holds the token written when it was picked).
     */
    static String current(Context ctx) {
        String saved = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(CHOICE_KEY, null);
        File dir = Config.dir(ctx);
        int colon = saved == null ? -1 : saved.indexOf(':');
        if (colon < 0 || dir == null) return null;
        File file = new File(dir, Config.THEME_REVISION_NAME);
        if (!file.isFile() || file.length() > 64) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = in.read(buf);
            String token = n > 0 ? new String(buf, 0, n, StandardCharsets.US_ASCII) : "";
            String name = saved.substring(0, colon);
            return token.equals(saved.substring(colon + 1)) && names(ctx).contains(name) ? name : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** The images and captions file of a wallpapers/ folder, the files a theme push replaces. */
    private static List<File> collection(File folder) {
        File[] files = folder.listFiles((d, n) -> ThemeFiles.collectionImage(n) || WallpaperCaptions.FILE_NAME.equals(n));
        return files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
    }

    private static void copy(File from, File to) throws IOException {
        File tmp = new File(to.getPath() + ".tmp");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (to.exists() && !to.delete()) throw new IOException("cannot replace " + to);
        if (!tmp.renameTo(to)) throw new IOException("cannot move " + tmp + " to " + to);
    }
}

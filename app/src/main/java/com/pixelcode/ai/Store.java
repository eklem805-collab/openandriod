package com.pixelcode.ai;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Файловое хранилище: проекты живут в /sdcard/PixelCode (чтобы Termux их видел). */
public final class Store {

    public static final String ROOT_DIR_NAME = "PixelCode";

    private static File cachedRoot;
    private static boolean usingFallback;

    private Store() {
    }

    /** Корень всех проектов: /sdcard/PixelCode. Если нельзя — каталог приложения (с предупреждением). */
    public static synchronized File root(Context ctx) {
        if (cachedRoot != null) return cachedRoot;
        File sd = Environment.getExternalStorageDirectory();
        File r = new File(sd, ROOT_DIR_NAME);
        try {
            r.mkdirs();
            if (r.isDirectory() && r.canWrite()) {
                cachedRoot = r;
                usingFallback = false;
                return r;
            }
        } catch (Throwable ignored) {
        }
        File fb = ctx.getExternalFilesDir(null);
        if (fb == null) fb = ctx.getFilesDir();
        File fr = new File(fb, ROOT_DIR_NAME);
        fr.mkdirs();
        cachedRoot = fr;
        usingFallback = true;
        return fr;
    }

    public static synchronized boolean isFallback() {
        return usingFallback;
    }

    public static File project(Context ctx, String name) {
        return new File(root(ctx), name);
    }

    /** Каталог проекта, с которым работаем сейчас. */
    public static File currentProject(Context ctx) {
        Prefs p = new Prefs(ctx);
        String n = p.currentProject();
        if (n.length() == 0) return null;
        File f = project(ctx, n);
        return f.isDirectory() ? f : null;
    }

    public static List<File> projects(Context ctx) {
        List<File> out = new ArrayList<File>();
        File r = root(ctx);
        File[] fs = r.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equals("tools")) out.add(f);
        }
        return out;
    }

    public static void write(File f, String content) throws IOException {
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        FileOutputStream os = new FileOutputStream(f);
        try {
            os.write(content.getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }

    public static String read(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                BufferedInputStream buf = new BufferedInputStream(in);
                byte[] data = new byte[(int) f.length()];
                int off = 0;
                while (off < data.length) {
                    int n = buf.read(data, off, data.length - off);
                    if (n < 0) break;
                    off += n;
                }
                return new String(data, 0, off, "UTF-8");
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return "";
        }
    }

    public static String readTail(File f, int maxChars) {
        String s = read(f);
        if (s.length() <= maxChars) return s;
        return s.substring(s.length() - maxChars);
    }

    public static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] cs = f.listFiles();
            if (cs != null) {
                for (File c : cs) deleteRecursive(c);
            }
        }
        f.delete();
    }

    /** Все файлы проекта (рекурсивно), относительные пути. */
    public static List<String> listFilesRecursive(File dir) {
        List<String> out = new ArrayList<String>();
        collect(dir, dir, out, 0);
        return out;
    }

    private static void collect(File rootDir, File cur, List<String> out, int depth) {
        if (depth > 8 || out.size() > 300) return;
        File[] fs = cur.listFiles();
        if (fs == null) return;
        // сортировка: каталоги сначала
        for (int pass = 0; pass < 2; pass++) {
            for (File f : fs) {
                boolean isDir = f.isDirectory();
                if ((pass == 0) != isDir) continue;
                if (f.getName().startsWith(".")) continue;
                if (f.getName().equals("build")) continue;
                String rel = relPath(rootDir, f);
                if (isDir) {
                    out.add(rel + "/");
                    collect(rootDir, f, out, depth + 1);
                } else {
                    out.add(rel);
                }
            }
        }
    }

    public static String relPath(File rootDir, File f) {
        String r = rootDir.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (p.startsWith(r + "/")) return p.substring(r.length() + 1);
        return p;
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    public static OutputStream openOut(File f) throws IOException {
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        return new FileOutputStream(f);
    }
}

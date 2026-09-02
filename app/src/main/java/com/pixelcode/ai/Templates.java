package com.pixelcode.ai;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Создание проектов из шаблонов (assets/templates/…). */
public final class Templates {

    public static final int GAME = 0;
    public static final int APP = 1;
    public static final int EMPTY = 2;

    private Templates() {
    }

    public static String templateName(int type) {
        if (type == GAME) return "Игра";
        if (type == APP) return "Приложение";
        return "Пустой";
    }

    /** Имя пакета из имени проекта: только латиница/цифры. */
    public static String packageNameFor(String projectName) {
        StringBuilder sb = new StringBuilder();
        for (char c : projectName.toCharArray()) {
            char l = Character.toLowerCase(c);
            if ((l >= 'a' && l <= 'z') || (l >= '0' && l <= '9')) sb.append(l);
        }
        if (sb.length() == 0) sb.append('a');
        if (Character.isDigit(sb.charAt(0))) sb.insert(0, 'p');
        if (sb.length() > 24) sb.setLength(24);
        return "com.pixelcode." + sb.toString();
    }

    /** Копирует шаблон в каталог проекта с подстановкой __PKG__, __APP__ и т.п. */
    public static void create(Context ctx, File projectDir, String name, int type) throws IOException {
        String tpl = type == GAME ? "game" : (type == APP ? "app" : "empty");
        String pkg = packageNameFor(name);
        String pkgPath = pkg.replace('.', '/');
        AssetManager am = ctx.getAssets();
        copyDir(am, "templates/" + tpl, projectDir, pkg, pkgPath, name);
        // общий значок приложения для всех шаблонов
        copyAssetBinary(am, "templates/shared/ic_launcher.png",
                new File(projectDir, "app/src/main/res/drawable-xxhdpi/ic_launcher.png"));
    }

    private static void copyDir(AssetManager am, String srcPath, File dstRoot,
                                String pkg, String pkgPath, String appName) throws IOException {
        String[] children = am.list(srcPath);
        if (children == null) return;
        if (children.length == 0) {
            // это файл
            copyAssetText(am, srcPath, dstRoot, pkg, pkgPath, appName);
            return;
        }
        for (String c : children) {
            copyDir(am, srcPath + "/" + c, dstRoot, pkg, pkgPath, appName);
        }
    }

    private static File dstFile(File dstRoot, String srcPath, String pkgPath) {
        String rel = srcPath;
        int i = rel.indexOf('/');
        rel = rel.substring(i + 1); // убрать "templates/<тип>/"
        rel = rel.replace("pkgpath", pkgPath);
        return new File(dstRoot, rel);
    }

    private static void copyAssetText(AssetManager am, String srcPath, File dstRoot,
                                      String pkg, String pkgPath, String appName) throws IOException {
        InputStream in = am.open(srcPath);
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
        in.close();
        String s = sb.toString();
        s = s.replace("__PKG__", pkg).replace("__APP__", appName);
        File f = dstFile(dstRoot, srcPath, pkgPath);
        Store.write(f, s);
    }

    private static void copyAssetBinary(AssetManager am, String srcPath, File dst) throws IOException {
        File p = dst.getParentFile();
        if (p != null) p.mkdirs();
        InputStream in = am.open(srcPath);
        OutputStream out = new FileOutputStream(dst);
        try {
            Store.copyStream(in, out);
        } finally {
            out.close();
            in.close();
        }
    }
}

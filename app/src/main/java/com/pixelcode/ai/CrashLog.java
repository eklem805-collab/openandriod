package com.pixelcode.ai;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Пишет необработанные краши в PixelCode/crash.log (или во внутренний каталог). */
public final class CrashLog {

    private CrashLog() {
    }

    public static void install(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable t) {
                try {
                    write(app, thread, t);
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(thread, t);
            }
        });
    }

    private static void write(Context app, Thread thread, Throwable t) throws Exception {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String txt = "==== " + time + " thread=" + thread.getName() + " ====\n" + sw + "\n";

        File dir = null;
        try {
            File d = new File(Environment.getExternalStorageDirectory(), Store.ROOT_DIR_NAME);
            d.mkdirs();
            if (d.isDirectory() && d.canWrite()) dir = d;
        } catch (Throwable ignored) {
        }
        if (dir == null) {
            File f = app.getExternalFilesDir(null);
            if (f == null) f = app.getFilesDir();
            dir = f;
        }
        FileOutputStream os = new FileOutputStream(new File(dir, "crash.log"), true);
        try {
            os.write(txt.getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }
}

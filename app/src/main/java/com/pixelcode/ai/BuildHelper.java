package com.pixelcode.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Сборка через Termux (RUN_COMMAND) + установка APK. */
public final class BuildHelper {

    public static final String TERMUX_PKG = "com.termux";
    public static final String TERMUX_SERVICE = "com.termux.app.RunCommandService";
    public static final String RUN_CMD_ACTION = "com.termux.RUN_COMMAND";
    public static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";

    private BuildHelper() {
    }

    // ------------------------------------------------ Termux

    public static boolean termuxInstalled(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(TERMUX_PKG, 0);
            return pi != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Копирует скрипты сборки из assets в /sdcard/PixelCode/tools. */
    public static void ensureTools(Context ctx) throws IOException {
        File tools = new File(Store.root(ctx), "tools");
        if (!tools.exists()) tools.mkdirs();
        copyAsset(ctx, "termux/build-apk.sh", new File(tools, "build-apk.sh"));
        copyAsset(ctx, "termux/termux-bootstrap.sh", new File(tools, "termux-bootstrap.sh"));
    }

    private static void copyAsset(Context ctx, String asset, File dst) throws IOException {
        InputStream in = ctx.getAssets().open(asset);
        try {
            OutputStream out = Store.openOut(dst);
            try {
                Store.copyStream(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /** Отправляет скрипт в Termux (нужны permission + allow-external-apps). */
    public static void runInTermux(Context ctx, String scriptPath, String args, String workdir) {
        // Разрешение RUN_COMMAND запрашиваем только здесь, в момент сборки —
        // не на старте приложения (каскад системных диалогов ломал ввод).
        if (android.os.Build.VERSION.SDK_INT >= 23
                && ctx.checkSelfPermission("com.termux.permission.RUN_COMMAND")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Ui.toast(ctx, "Выдай разрешение «отправлять команды в Termux» и нажми кнопку снова");
            if (ctx instanceof Activity) {
                try {
                    ((Activity) ctx).requestPermissions(
                            new String[]{"com.termux.permission.RUN_COMMAND"}, 7);
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        Intent it = new Intent(RUN_CMD_ACTION);
        it.setClassName(TERMUX_PKG, TERMUX_SERVICE);
        it.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH);
        it.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{scriptPath, args});
        it.putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir);
        it.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        try {
            ctx.startService(it);
        } catch (Throwable t) {
            Ui.toast(ctx, "Termux: " + t.getMessage()
                    + "\nПроверь allow-external-apps=true в ~/.termux/termux.properties");
        }
    }

    /** Диалог сборки: запуск + поллинг статуса + установка. */
    public static void showBuildDialog(final Activity activity, final File project) {
        final Prefs prefs = new Prefs(activity);
        prefs.setCurrentProject(project.getName());

        if (!termuxInstalled(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle("Termux не найден")
                    .setMessage("Сборка APK происходит в Termux. Установи Termux (F-Droid/GitHub), "
                            + "запусти его один раз, затем повтори.")
                    .setPositiveButton("Ок", null)
                    .show();
            return;
        }

        try {
            ensureTools(activity);
        } catch (Throwable t) {
            Ui.toast(activity, "Не удалось записать скрипты: " + t.getMessage());
            return;
        }

        File tools = new File(Store.root(activity), "tools");
        String script = new File(tools, "build-apk.sh").getAbsolutePath();
        File buildDir = new File(project, "build");
        final File status = new File(buildDir, "status.txt");
        // сброс статуса
        try {
            buildDir.mkdirs();
            Store.write(status, "reset\n");
        } catch (IOException ignored) {
        }
        final File apk = new File(buildDir, "app.apk");
        final File logFile = new File(buildDir, "build.log");

        runInTermux(activity, script, project.getAbsolutePath(), Store.root(activity).getAbsolutePath());

        View v = LayoutInflater.from(activity).inflate(R.layout.dialog_build, null);
        Ui.applyAll(v);
        final TextView statusView = (TextView) v.findViewById(R.id.status);
        final TextView apkPath = (TextView) v.findViewById(R.id.apkpath);
        final Button btnInstall = (Button) v.findViewById(R.id.btn_install);
        final Button btnLog = (Button) v.findViewById(R.id.btn_log);
        final Button btnFix = (Button) v.findViewById(R.id.btn_fix);
        final Button btnRish = (Button) v.findViewById(R.id.btn_rish);
        apkPath.setText(apk.getAbsolutePath());

        final AlertDialog[] dlg = new AlertDialog[1];
        dlg[0] = new AlertDialog.Builder(activity)
                .setTitle("🔨 Сборка APK — " + project.getName())
                .setView(v)
                .setPositiveButton(R.string.close, null)
                .show();

        final Handler h = new Handler();
        final Runnable poll = new Runnable() {
            int ticks = 0;

            public void run() {
                ticks++;
                String st = Store.read(status).trim();
                boolean ok = st.startsWith("ok") && apk.exists();
                boolean error = st.startsWith("error");
                if (ok) {
                    statusView.setText("✅ ГОТОВО — APK собран");
                    statusView.setTextColor(Ui.C_OK);
                    btnInstall.setEnabled(true);
                    return; // больше не опрашиваем
                }
                if (error) {
                    statusView.setText("❌ Ошибка сборки (см. Лог / Исправить через ИИ)");
                    statusView.setTextColor(Ui.C_DANGER);
                    return;
                }
                statusView.setText("⏳ Сборка идёт в Termux… (" + ticks * 2 + " c)");
                statusView.setTextColor(Ui.C_ACCENT);
                if (ticks < 900) h.postDelayed(this, 2000);
            }
        };
        h.postDelayed(poll, 2000);

        btnInstall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                installApk(activity, apk);
            }
        });
        btnLog.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String tail = Store.readTail(logFile, 8000);
                new AlertDialog.Builder(activity)
                        .setTitle("build.log (конец)")
                        .setMessage(tail.length() == 0 ? "лог пуст" : tail)
                        .setPositiveButton("ок", null)
                        .show();
            }
        });
        btnFix.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String tail = Store.readTail(logFile, 4000);
                dlg[0].dismiss();
                ((MainActivity) activity).startChatWith(Prompts.fixErrors(project, tail));
            }
        });
        btnRish.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String cmd = "pm install -r " + apk.getAbsolutePath();
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        activity.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setText(cmd);
                Ui.toast(activity, "Скопировано: " + cmd
                        + "\nВыполни через rish (Shizuku)");
            }
        });
    }

    /** Быстрая сборка без диалога (для настроек/бутстрапа). */
    public static void runBootstrap(Context ctx) {
        if (!termuxInstalled(ctx)) {
            Ui.toast(ctx, "Termux не найден");
            return;
        }
        try {
            ensureTools(ctx);
        } catch (Throwable t) {
            Ui.toast(ctx, "Ошибка: " + t.getMessage());
            return;
        }
        File tools = new File(Store.root(ctx), "tools");
        String script = new File(tools, "termux-bootstrap.sh").getAbsolutePath();
        runInTermux(ctx, script, "", Store.root(ctx).getAbsolutePath());
        Ui.toast(ctx, "Termux настраивает инструменты — смотри сессию Termux");
    }

    // ------------------------------------------------ Установка

    /** Установка APK через PackageInstaller (системный диалог подтверждения). */
    public static void installApk(final Activity activity, File apk) {
        try {
            PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            OutputStream os = session.openWrite("base", 0, -1);
            InputStream in = new FileInputStream(apk);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            session.fsync(os);
            os.close();

            Intent resIntent = new Intent(activity, InstallReceiver.class);
            resIntent.setAction(InstallReceiver.ACTION);
            resIntent.putExtra("apk", apk.getAbsolutePath());
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    activity, sessionId, resIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            | android.app.PendingIntent.FLAG_IMMUTABLE);
            session.commit(pi.getIntentSender());
            session.close();
            Ui.toast(activity, "Подтверди установку в системном диалоге");
        } catch (Throwable t) {
            new AlertDialog.Builder(activity)
                    .setTitle("Установка не запущена")
                    .setMessage("Не удалось начать установку: " + t.getMessage()
                            + "\n\nРазреши «Установку неизвестных приложений» для PixelCode "
                            + "(Настройки → Приложения → PixelCode) и повтори.")
                    .setPositiveButton("Открыть настройку", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Ui.openInstallSettings(activity);
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }
    }

    public static void handleInstallResult(Context ctx, Intent data) {
        int status = data.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = data.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(ctx, "✅ Установлено! Запусти из меню приложений.", Toast.LENGTH_LONG).show();
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(ctx, "Установка отменена", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(ctx, "Ошибка установки: " + msg, Toast.LENGTH_LONG).show();
        }
    }
}

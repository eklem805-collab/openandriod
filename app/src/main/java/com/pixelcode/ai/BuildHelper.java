package com.pixelcode.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
    public static final String RUN_CMD_PERM = "com.termux.permission.RUN_COMMAND";
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

    public static boolean hasRunCommandPerm(Context ctx) {
        try {
            return ctx.checkPermission(RUN_CMD_PERM, android.os.Process.myPid(),
                    android.os.Process.myUid()) == PackageManager.PERMISSION_GRANTED;
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

    /** Диалог выдачи прав Termux: системный запрос + rish-команда (Shizuku). */
    public static void showGrantTermuxDialog(final Activity activity) {
        String msg = "Чтобы PixelCode мог запускать сборку в Termux, нужно один раз выдать разрешение.\n\n"
                + "Способ 1 — системный диалог:\nнажми «Выдать сейчас» и подтверди.\n\n"
                + "Способ 2 — надёжно, через твой Shizuku (выполни в Termux):\n"
                + "rish -c 'pm grant com.pixelcode.ai com.termux.permission.RUN_COMMAND'\n\n"
                + "И проверь, что в ~/.termux/termux.properties есть строка\n"
                + "allow-external-apps=true\n"
                + "(иначе Termux будет молча игнорировать команды).";
        new AlertDialog.Builder(activity)
                .setTitle("Права для Termux")
                .setMessage(msg)
                .setPositiveButton("Выдать сейчас", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            activity.requestPermissions(new String[]{RUN_CMD_PERM}, 7);
                        } catch (Throwable t) {
                            Ui.toast(activity, "Система не показала диалог — используй rish-команду");
                        }
                    }
                })
                .setNeutralButton("📋 rish-команда", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        copy(activity, "rish -c 'pm grant com.pixelcode.ai com.termux.permission.RUN_COMMAND'");
                        Ui.toast(activity, "Скопировано. Вставь в Termux и выполни (нужен запущенный Shizuku)");
                    }
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    private static void copy(Activity a, String cmd) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData cd = android.content.ClipData.newPlainText("cmd", cmd);
            cm.setPrimaryClip(cd);
        } catch (Throwable ignored) {
        }
    }

    /** Отправляет скрипт в Termux (нужны permission + allow-external-apps). */
    public static void runInTermux(Context ctx, String scriptPath, String args, String workdir) {
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

    /** Диалог сборки: запуск + поллинг статуса + установка + помощь. */
    public static void showBuildDialog(final Activity activity, final File project) {
        final Prefs prefs = new Prefs(activity);
        prefs.setCurrentProject(project.getName());

        // Проекты должны лежать в /sdcard/PixelCode, иначе Termux их не увидит
        if (Store.isFallback()) {
            new AlertDialog.Builder(activity)
                    .setTitle("⚠ Нет доступа к файлам")
                    .setMessage("Проекты сейчас лежат во ВНУТРЕННЕМ каталоге приложения — "
                            + "Termux их не видит, сборка невозможна.\n\n"
                            + "Выдай PixelCode «Доступ ко всем файлам» и вернись.")
                    .setPositiveButton("Выдать сейчас", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Ui.openAllFilesSettings(activity);
                        }
                    })
                    .setNegativeButton("Позже", null)
                    .show();
            return;
        }

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
        final String script = new File(tools, "build-apk.sh").getAbsolutePath();
        final File buildDir = new File(project, "build");
        final File status = new File(buildDir, "status.txt");
        try {
            buildDir.mkdirs();
            Store.write(status, "reset\n");
        } catch (IOException ignored) {
        }
        final File apk = new File(buildDir, "app.apk");
        final File logFile = new File(buildDir, "build.log");

        // --- интерфейс диалога
        View v = LayoutInflater.from(activity).inflate(R.layout.dialog_build, null);
        Ui.applyAll(v);
        final TextView statusView = (TextView) v.findViewById(R.id.status);
        final TextView apkPath = (TextView) v.findViewById(R.id.apkpath);
        final TextView errorView = (TextView) v.findViewById(R.id.errorview);
        final Button btnInstall = (Button) v.findViewById(R.id.btn_install);
        final Button btnLog = (Button) v.findViewById(R.id.btn_log);
        final Button btnFix = (Button) v.findViewById(R.id.btn_fix);
        final Button btnRish = (Button) v.findViewById(R.id.btn_rish);
        final Button btnRun = (Button) v.findViewById(R.id.btn_run);
        final Button btnManual = (Button) v.findViewById(R.id.btn_manual);
        final Button btnHelp = (Button) v.findViewById(R.id.btn_help);
        apkPath.setText(apk.getAbsolutePath());

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
                    errorView.setVisibility(View.GONE);
                    btnInstall.setEnabled(true);
                    return;
                }
                if (error) {
                    statusView.setText("❌ Ошибка сборки");
                    statusView.setTextColor(Ui.C_DANGER);
                    String tail = lastLines(Store.readTail(logFile, 4000), 8);
                    errorView.setVisibility(View.VISIBLE);
                    errorView.setText(tail.length() == 0 ? "(лог пуст — сборка не запускалась)" : tail);
                    return;
                }
                statusView.setText("⏳ Сборка идёт в Termux… (" + ticks * 2 + " c)");
                statusView.setTextColor(Ui.C_ACCENT);
                if (ticks < 900) h.postDelayed(this, 2000);
            }
        };

        final Runnable[] start = new Runnable[1];
        start[0] = new Runnable() {
            public void run() {
                if (!hasRunCommandPerm(activity)) {
                    statusView.setText("⚠ Нет прав на команды Termux");
                    statusView.setTextColor(Ui.C_DANGER);
                    showGrantTermuxDialog(activity);
                    return;
                }
                errorView.setVisibility(View.GONE);
                statusView.setText("⏳ Запуск сборки в Termux…");
                h.removeCallbacks(poll);
                h.postDelayed(poll, 2000);
                runInTermux(activity, script, project.getAbsolutePath(),
                        Store.root(activity).getAbsolutePath());
            }
        };

        final AlertDialog[] dlg = new AlertDialog[1];
        dlg[0] = new AlertDialog.Builder(activity)
                .setTitle("🔨 Сборка APK — " + project.getName())
                .setView(v)
                .setPositiveButton(R.string.close, null)
                .show();

        btnRun.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                start[0].run();
            }
        });
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
                copy(activity, "pm install -r " + apk.getAbsolutePath());
                Ui.toast(activity, "Скопировано: pm install — выполни через rish (Shizuku)");
            }
        });
        btnManual.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copy(activity, "bash " + script + " " + project.getAbsolutePath());
                new AlertDialog.Builder(activity)
                        .setTitle("Ручная сборка (всегда работает)")
                        .setMessage("Команда скопирована в буфер:\n\n"
                                + "bash " + script + " " + project.getAbsolutePath() + "\n\n"
                                + "Вариант 1: открой Termux, вставь и выполни.\n"
                                + "Вариант 2 (Shizuku, без всяких прав):\n"
                                + "rish -c 'bash " + script + " " + project.getAbsolutePath() + "'")
                        .setPositiveButton("ок", null)
                        .show();
            }
        });
        btnHelp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(activity)
                        .setTitle("Чек-лист сборки")
                        .setMessage("1) Termux установлен (F-Droid/GitHub) и открыт хотя бы раз.\n\n"
                                + "2) В Termux есть строка allow-external-apps=true\n"
                                + "в файле ~/.termux/termux.properties, потом выполнено\n"
                                + "termux-reload-settings\n\n"
                                + "3) Выдано разрешение com.termux.permission.RUN_COMMAND:\n"
                                + "rish -c 'pm grant com.pixelcode.ai com.termux.permission.RUN_COMMAND'\n"
                                + "(или системный диалог кнопкой «▶ Запустить сборку»).\n\n"
                                + "4) Инструменты стоят: в Настройках — «Установить инструменты сборки».\n"
                                + "Первая сборка качает android.jar (~26 МБ).\n\n"
                                + "5) Не помогло? «📋 Ручная команда сборки» работает всегда.\n"
                                + "Лог: «Лог» → пришли последние строки разработчику.")
                        .setPositiveButton("ок", null)
                        .show();
            }
        });

        // автозапуск, если права уже есть
        start[0].run();
    }

    /** Последние N непустых строк текста. */
    static String lastLines(String s, int n) {
        if (s == null) return "";
        String[] parts = s.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = parts.length - 1; i >= 0 && count < n; i--) {
            String t = parts[i].trim();
            if (t.length() == 0) continue;
            sb.insert(0, t + '\n');
            count++;
        }
        return sb.toString();
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
            android.widget.Toast.makeText(ctx, "✅ Установлено! Запусти из меню приложений.",
                    android.widget.Toast.LENGTH_LONG).show();
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            android.widget.Toast.makeText(ctx, "Установка отменена", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(ctx, "Ошибка установки: " + msg,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }
}

package com.pixelcode.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

/** Главная активити: нижние вкладки (Проекты / Файлы / ИИ-кодер / Настройки). */
public class MainActivity extends Activity {

    public static final int TAB_PROJECTS = 0;
    public static final int TAB_FILES = 1;
    public static final int TAB_CHAT = 2;
    public static final int TAB_SETTINGS = 3;

    private FrameLayout container;
    private Button[] tabs = new Button[4];
    private int currentTab = -1;
    private long lastBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        setContentView(R.layout.activity_main);
        Ui.applyAll(findViewById(android.R.id.content));

        container = (FrameLayout) findViewById(R.id.container);
        tabs[0] = (Button) findViewById(R.id.tab_projects);
        tabs[1] = (Button) findViewById(R.id.tab_files);
        tabs[2] = (Button) findViewById(R.id.tab_chat);
        tabs[3] = (Button) findViewById(R.id.tab_settings);
        tabs[0].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                show(TAB_PROJECTS);
            }
        });
        tabs[1].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                show(TAB_FILES);
            }
        });
        tabs[2].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                show(TAB_CHAT);
            }
        });
        tabs[3].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                show(TAB_SETTINGS);
            }
        });

        show(TAB_PROJECTS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        Prefs p = new Prefs(this);
        if (!p.onboarded()) {
            p.setOnboarded();
            showOnboarding();
        }
    }

    public void show(int tab) {
        if (tab == currentTab && container.getChildCount() > 0) return;
        currentTab = tab;
        Fragment f;
        if (tab == TAB_FILES) f = new FilesFragment();
        else if (tab == TAB_CHAT) f = new ChatFragment();
        else if (tab == TAB_SETTINGS) f = new SettingsFragment();
        else f = new ProjectsFragment();
        getFragmentManager().beginTransaction().replace(R.id.container, f).commitAllowingStateLoss();
        for (int i = 0; i < 4; i++) {
            boolean on = i == tab;
            tabs[i].setTextColor(on ? Ui.C_ACCENT : Ui.C_DIM);
            tabs[i].setCompoundDrawablesWithIntrinsicBounds(null,
                    tintDrawable(tabs[i], i, on), null, null);
        }
    }

    public void switchTab(int tab) {
        show(tab);
    }

    private android.graphics.drawable.Drawable tintDrawable(Button b, int idx, boolean on) {
        int[] icons = {R.drawable.ic_projects, R.drawable.ic_files, R.drawable.ic_chat, R.drawable.ic_gear};
        android.graphics.drawable.Drawable d = getResources().getDrawable(icons[idx]);
        d.mutate().setTint(on ? Ui.C_ACCENT : Ui.C_DIM);
        return d;
    }

    /** Переключиться на чат и подставить текст в поле ввода. */
    public void startChatWith(String text) {
        show(TAB_CHAT);
        final String t = text;
        final View v = container;
        v.postDelayed(new Runnable() {
            public void run() {
                ChatFragment f = (ChatFragment) getFragmentManager().findFragmentById(R.id.container);
                if (f != null) f.setPrefill(t);
            }
        }, 300);
    }

    private void checkPermissions() {
        // Доступ ко всем файлам (Android 11+) или WRITE (Android <=9)
        if (Build.VERSION.SDK_INT >= 30) {
            if (!isExternalStorageManagerCompat()) {
                warnStorage();
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
        // Разрешение RUN_COMMAND для Termux (dangerous на новых версиях Termux)
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission("com.termux.permission.RUN_COMMAND")
                    != PackageManager.PERMISSION_GRANTED) {
                try {
                    requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 2);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean isExternalStorageManagerCompat() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Object r = Class.forName("android.os.Environment")
                        .getMethod("isExternalStorageManager").invoke(null);
                return Boolean.TRUE.equals(r);
            } catch (Throwable t) {
                return true; // не смогли проверить — считаем что ок
            }
        }
        return true;
    }

    private void warnStorage() {
        // один раз за запуск
        if (warnedStorage) return;
        warnedStorage = true;
        new AlertDialog.Builder(this)
                .setTitle("Доступ к файлам")
                .setMessage("Чтобы проекты лежали в /sdcard/PixelCode (и Termux мог их собирать), "
                        + "разреши PixelCode доступ ко всем файлам. Без этого проекты будут во "
                        + "внутреннем каталоге приложения и Termux их не увидит.")
                .setPositiveButton("Открыть настройки", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            startActivity(new Intent(
                                    "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Throwable t) {
                            try {
                                startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    private boolean warnedStorage;

    private void showOnboarding() {
        String msg = "Добро пожаловать в PixelCode!\n\n"
                + "Это ИИ-кодер: описываешь идею — нейросеть Mistral пишет код, "
                + "а APK собирается прямо на телефоне.\n\n"
                + "Как начать:\n"
                + "1. Вставь API-ключ Mistral (console.mistral.ai) во вкладке «Настройки».\n"
                + "2. В Termux разреши внешние приложения:\n"
                + "   mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings\n"
                + "3. Нажми «Установить инструменты сборки» в Настройках (или в Termux: pkg install openjdk-17 aapt apksigner zipalign zip ecj dx).\n"
                + "4. Создай проект во вкладке «Проекты» и опиши идею в «ИИ-кодер».\n"
                + "5. Кнопка «🔨 APK» отправляет проект на сборку в Termux.\n\n"
                + "Готовый APK появится в <проект>/build/app.apk — его можно сразу установить.\n";
        new AlertDialog.Builder(this)
                .setTitle("⚡ PixelCode")
                .setMessage(msg)
                .setPositiveButton("Понятно", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - lastBack > 2000) {
            lastBack = System.currentTimeMillis();
            Ui.toast(this, "Нажми «Назад» ещё раз для выхода");
            return;
        }
        super.onBackPressed();
    }
}

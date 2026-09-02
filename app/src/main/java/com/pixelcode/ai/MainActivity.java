package com.pixelcode.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
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
        CrashLog.install(this);
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
        // Единственный диалог первого запуска. Никаких системных requestPermissions
        // на старте — они вызывают каскад окон, который на некоторых прошивках
        // ломает ввод (кнопки перестают реагировать).
        Prefs p = new Prefs(this);
        if (!p.onboarded()) {
            p.setOnboarded();
            showWelcome();
        }
        // если фрагменты потерялись (после краша процесса и т.п.) — восстановить
        if (container.getChildCount() == 0) {
            currentTab = -1;
            show(TAB_PROJECTS);
        }
    }

    private void showWelcome() {
        String msg = "Добро пожаловать в PixelCode!\n\n"
                + "1) Выдай доступ ко всем файлам (кнопка ниже) — проекты будут в /sdcard/PixelCode, их увидит Termux.\n"
                + "2) В Termux один раз выполни:\n"
                + "   mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings\n"
                + "3) Вставь API-ключ Mistral в «Настройках» и нажми там «Установить инструменты сборки».\n"
                + "4) Создай проект и опиши идею в «ИИ-кодер» → «⚡ Файлы» → «🔨 APK».\n\n"
                + "Доступ к файлам можно выдать и позже: вкладка «Настройки» → «Разрешить доступ ко всем файлам».";
        new AlertDialog.Builder(this)
                .setTitle("⚡ PixelCode")
                .setMessage(msg)
                .setPositiveButton("Выдать доступ к файлам", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Ui.openAllFilesSettings(MainActivity.this);
                    }
                })
                .setNegativeButton("Позже", null)
                .show();
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

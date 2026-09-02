package com.pixelcode.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

/** Общий стиль: пиксельный шрифт, цвета, тосты. */
public final class Ui {

    public static Typeface pixel;

    // Палитра
    public static final int C_BG      = 0xFF12081F;
    public static final int C_PANEL   = 0xFF1C0E33;
    public static final int C_PANEL2  = 0xFF24123F;
    public static final int C_PURPLE  = 0xFF7C3AED;
    public static final int C_PURPLE_D = 0xFF5B21B6;
    public static final int C_PURPLE_DD = 0xFF3B0F78;
    public static final int C_ACCENT  = 0xFFC084FC;
    public static final int C_ACCENT_B = 0xFFE9D5FF;
    public static final int C_TEXT    = 0xFFEDE9FE;
    public static final int C_DIM     = 0xFF8B7BB8;
    public static final int C_DANGER  = 0xFFF472B6;
    public static final int C_OK      = 0xFF4ADE80;

    private Ui() {
    }

    public static void init(Context ctx) {
        if (pixel == null) {
            try {
                pixel = Typeface.createFromAsset(ctx.getAssets(), "fonts/PixelifySans.ttf");
            } catch (Throwable t) {
                pixel = Typeface.MONOSPACE;
            }
        }
    }

    /** Рекурсивно применяет пиксельный шрифт ко всем TextView. */
    public static void applyAll(View root) {
        if (root instanceof TextView) {
            ((TextView) root).setTypeface(pixel);
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) applyAll(g.getChildAt(i));
        }
    }

    public static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    public static int dp(Context ctx, float v) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    // --------------------------------------------- ссылки на системные настройки

    /** Надёжное открытие «Доступ ко всем файлам» для этого приложения. */
    public static boolean openAllFilesSettings(Activity a) {
        // 1) персональная страница «Все файлы» для приложения
        try {
            a.startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                    Uri.parse("package:" + a.getPackageName())));
            return true;
        } catch (Throwable ignored) {
        }
        // 2) общий список приложений с переключателем «Все файлы»
        try {
            a.startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"));
            return true;
        } catch (Throwable ignored) {
        }
        // 3) всегда работающий вариант — страница приложения в настройках
        return openAppDetails(a);
    }

    /** Надёжное открытие «Установка неизвестных приложений» для этого приложения. */
    public static boolean openInstallSettings(Activity a) {
        try {
            a.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                    Uri.parse("package:" + a.getPackageName())));
            return true;
        } catch (Throwable ignored) {
        }
        return openAppDetails(a);
    }

    /** Страница «О приложении» в системных настройках (работает на любом Android). */
    public static boolean openAppDetails(Activity a) {
        try {
            a.startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                    Uri.parse("package:" + a.getPackageName())));
            return true;
        } catch (Throwable t) {
            toast(a, "Не удалось открыть настройки — открой «О приложении» вручную");
            return false;
        }
    }
}

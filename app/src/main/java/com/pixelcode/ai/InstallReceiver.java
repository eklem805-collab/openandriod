package com.pixelcode.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Результат установки APK. */
public class InstallReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.pixelcode.ai.INSTALL_RESULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        BuildHelper.handleInstallResult(context, intent);
    }
}

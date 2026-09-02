package com.pixelcode.ai;

import android.content.Context;
import android.content.SharedPreferences;

/** Настройки приложения. */
public final class Prefs {

    private static final String FILE = "pixelcode";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String apiKey() {
        return sp.getString("api_key", "");
    }

    public void setApiKey(String v) {
        sp.edit().putString("api_key", v).apply();
    }

    public String baseUrl() {
        String v = sp.getString("base_url", "https://api.mistral.ai/v1");
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    public void setBaseUrl(String v) {
        sp.edit().putString("base_url", v).apply();
    }

    public String model() {
        String custom = sp.getString("model_custom", "");
        if (custom.length() > 0) return custom;
        return sp.getString("model", "codestral-latest");
    }

    public void setModel(String v) {
        sp.edit().putString("model", v).apply();
    }

    public void setModelCustom(String v) {
        sp.edit().putString("model_custom", v.trim()).apply();
    }

    public float temperature() {
        try {
            return Float.parseFloat(sp.getString("temperature", "0.2"));
        } catch (NumberFormatException e) {
            return 0.2f;
        }
    }

    public void setTemperature(String v) {
        sp.edit().putString("temperature", v).apply();
    }

    public int fontSize() {
        try {
            return Integer.parseInt(sp.getString("font_size", "13"));
        } catch (NumberFormatException e) {
            return 13;
        }
    }

    public void setFontSize(String v) {
        sp.edit().putString("font_size", v).apply();
    }

    public String currentProject() {
        return sp.getString("current_project", "");
    }

    public void setCurrentProject(String v) {
        sp.edit().putString("current_project", v).apply();
    }

    public boolean onboarded() {
        return sp.getBoolean("onboarded_v2", false);
    }

    public void setOnboarded() {
        sp.edit().putBoolean("onboarded_v2", true).apply();
    }
}

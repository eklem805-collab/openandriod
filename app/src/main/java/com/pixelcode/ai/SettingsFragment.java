package com.pixelcode.ai;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

/** Настройки: API-ключ, модель, разрешения, инструменты Termux. */
public class SettingsFragment extends Fragment implements TextWatcher {

    private Activity activity;
    private Prefs prefs;
    private EditText apiKey;
    private EditText baseUrl;
    private EditText modelCustom;
    private EditText temperature;
    private EditText fontSize;
    private Spinner model;
    private TextView status;
    private String[] models;
    private boolean loading = true;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        Ui.applyAll(root);
        prefs = new Prefs(activity);

        apiKey = (EditText) root.findViewById(R.id.api_key);
        baseUrl = (EditText) root.findViewById(R.id.base_url);
        modelCustom = (EditText) root.findViewById(R.id.model_custom);
        temperature = (EditText) root.findViewById(R.id.temperature);
        fontSize = (EditText) root.findViewById(R.id.font_size);
        model = (Spinner) root.findViewById(R.id.model);
        status = (TextView) root.findViewById(R.id.status);
        Button btnTest = (Button) root.findViewById(R.id.btn_test);
        Button btnFiles = (Button) root.findViewById(R.id.btn_files_perm);
        Button btnInstall = (Button) root.findViewById(R.id.btn_install_perm);
        Button btnBootstrap = (Button) root.findViewById(R.id.btn_bootstrap);
        TextView help = (TextView) root.findViewById(R.id.help);

        models = new String[]{
                "codestral-latest", "mistral-large-latest", "mistral-medium-latest",
                "mistral-small-latest", "open-mistral-nemo", "magistral-medium-latest",
                "devstral-medium-latest"
        };
        ArrayAdapter<String> ad = new ArrayAdapter<String>(activity,
                android.R.layout.simple_spinner_item, models);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        model.setAdapter(ad);

        // текущие значения
        apiKey.setText(prefs.apiKey());
        baseUrl.setText(prefs.baseUrl());
        modelCustom.setText("");
        temperature.setText(String.valueOf(prefs.temperature()));
        fontSize.setText(String.valueOf(prefs.fontSize()));
        String cur = prefs.model();
        int pos = 0;
        for (int i = 0; i < models.length; i++) {
            if (models[i].equals(cur)) pos = i;
        }
        model.setSelection(pos);

        loading = false;
        apiKey.addTextChangedListener(this);
        baseUrl.addTextChangedListener(this);
        temperature.addTextChangedListener(this);
        fontSize.addTextChangedListener(this);
        modelCustom.addTextChangedListener(this);
        model.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (loading) return;
                prefs.setModel(models[position]);
                refreshStatus();
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnTest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                testApi();
            }
        });
        btnFiles.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                            android.net.Uri.parse("package:" + activity.getPackageName())));
                } catch (Throwable t) {
                    Ui.toast(activity, "Открой настройки приложения вручную (Android 11+)");
                }
            }
        });
        btnInstall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                            android.net.Uri.parse("package:" + activity.getPackageName())));
                } catch (Throwable t) {
                    Ui.toast(activity, "Открой настройки приложения вручную");
                }
            }
        });
        btnBootstrap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                BuildHelper.runBootstrap(activity);
            }
        });

        help.setText("Файлы проектов: " + Store.root(activity).getAbsolutePath()
                + (Store.isFallback() ? "  (⚠ резервный каталог — Termux его не видит!)"
                : "  (Termux видит этот каталог)")
                + "\n\nTermux (один раз):\n"
                + "mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings\n\n"
                + "Ключ Mistral: console.mistral.ai → API Keys.\n"
                + "Shizuku: для тихой установки скопируй rish-команду из диалога сборки "
                + "и выполни в Termux через rish.");

        refreshStatus();
        return root;
    }

    private void refreshStatus() {
        boolean hasKey = prefs.apiKey().length() > 0;
        status.setText(hasKey
                ? "● Модель: " + prefs.model() + " · ключ задан"
                : "○ Вставь API-ключ Mistral (console.mistral.ai)");
        status.setTextColor(hasKey ? Ui.C_OK : Ui.C_DANGER);
    }

    private void testApi() {
        status.setText("… проверяю ключ");
        status.setTextColor(Ui.C_DIM);
        final Mistral m = new Mistral(prefs);
        new Thread(new Runnable() {
            public void run() {
                final String err = m.testKey();
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        if (err == null) {
                            status.setText("✅ Ключ работает! (" + prefs.model() + ")");
                            status.setTextColor(Ui.C_OK);
                        } else {
                            status.setText("❌ " + err);
                            status.setTextColor(Ui.C_DANGER);
                        }
                    }
                });
            }
        }).start();
    }

    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
    }

    public void onTextChanged(CharSequence s, int a, int b, int c) {
    }

    public void afterTextChanged(Editable s) {
        if (loading || prefs == null) return;
        prefs.setApiKey(apiKey.getText().toString().trim());
        prefs.setBaseUrl(baseUrl.getText().toString().trim().length() == 0
                ? "https://api.mistral.ai/v1" : baseUrl.getText().toString().trim());
        prefs.setTemperature(temperature.getText().toString());
        prefs.setFontSize(fontSize.getText().toString());
        prefs.setModelCustom(modelCustom.getText().toString());
        refreshStatus();
    }
}

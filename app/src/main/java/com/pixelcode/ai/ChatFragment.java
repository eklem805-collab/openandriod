package com.pixelcode.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Вкладка чата с ИИ: генерация кода, применение файлов, запуск сборки. */
public class ChatFragment extends Fragment {

    private Activity activity;
    private List<Mistral.Msg> msgs = new ArrayList<Mistral.Msg>();
    private MessageAdapter adapter;
    private ListView list;
    private EditText input;
    private Button btnSend;
    private Button btnStop;
    private TextView projectLabel;
    private Mistral mistral;
    private final Handler handler = new Handler();
    private long lastNotify;
    private String prefill;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chat, container, false);
        Ui.applyAll(root);

        list = (ListView) root.findViewById(R.id.list);
        input = (EditText) root.findViewById(R.id.input);
        btnSend = (Button) root.findViewById(R.id.btn_send);
        btnStop = (Button) root.findViewById(R.id.btn_stop);
        projectLabel = (TextView) root.findViewById(R.id.project);
        Button btnApply = (Button) root.findViewById(R.id.btn_apply);
        Button btnBuild = (Button) root.findViewById(R.id.btn_build);

        adapter = new MessageAdapter();
        list.setAdapter(adapter);

        btnSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                send();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stop();
            }
        });
        btnApply.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyFiles();
            }
        });
        btnBuild.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File p = Store.currentProject(activity);
                if (p == null) {
                    Ui.toast(activity, "Сначала выбери проект во вкладке «Проекты»");
                    return;
                }
                BuildHelper.showBuildDialog(activity, p);
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send();
                    return true;
                }
                return false;
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                btnSend.setEnabled(s.toString().trim().length() > 0);
            }
        });

        reload();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshProjectLabel();
    }

    public void setPrefill(String text) {
        prefill = text;
        if (input != null && text != null) {
            input.setText(text);
        }
    }

    private void refreshProjectLabel() {
        File p = Store.currentProject(activity);
        Prefs prefs = new Prefs(activity);
        projectLabel.setText("Проект: " + (p == null ? "не выбран" : p.getName())
                + "   |   " + prefs.model());
    }

    private File chatFile() {
        File p = Store.currentProject(activity);
        if (p == null) return null;
        return new File(p, ".pixelchat.json");
    }

    private void reload() {
        msgs.clear();
        File f = chatFile();
        if (f != null) {
            msgs.addAll(Mistral.fromJson(Store.read(f)));
        }
        if (msgs.size() == 0) {
            Mistral.Msg hello = new Mistral.Msg("assistant",
                    "Привет! Я — ИИ-кодер PixelCode на базе Mistral.\n\n"
                            + "Создай проект во вкладке «Проекты» и опиши, что сделать: "
                            + "например «сделай арканоид с фиолетовой пиксельной графикой и таблицей рекордов».\n\n"
                            + "Я напишу файлы — жми «⚡ Файлы», чтобы записать их в проект, потом «🔨 APK» для сборки.");
            msgs.add(hello);
        }
        adapter.notifyDataSetChanged();
    }

    private void saveChat() {
        File f = chatFile();
        if (f != null) {
            try {
                Store.write(f, Mistral.toJson(msgs));
            } catch (Throwable ignored) {
            }
        }
    }

    private void send() {
        String text = input.getText().toString().trim();
        if (text.length() == 0) return;
        File project = Store.currentProject(activity);
        if (project == null) {
            Ui.toast(activity, "Сначала создай проект во вкладке «Проекты»");
            return;
        }
        final File fProject = project;
        input.setText("");
        input.setEnabled(false);
        btnSend.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);

        List<Mistral.Msg> request = new ArrayList<Mistral.Msg>();
        request.add(new Mistral.Msg("system", Prompts.SYSTEM));
        request.add(new Mistral.Msg("system", Prompts.context(fProject)));
        // последние 12 сообщений как контекст
        int from = Math.max(1, msgs.size() - 12); // 0 — приветствие
        for (int i = from; i < msgs.size(); i++) request.add(msgs.get(i));
        request.add(new Mistral.Msg("user", text));
        msgs.add(new Mistral.Msg("user", text));
        msgs.add(new Mistral.Msg("assistant", ""));
        adapter.notifyDataSetChanged();
        scrollDown();

        mistral = new Mistral(new Prefs(activity));
        final List<Mistral.Msg> finalRequest = request;
        new Thread(new Runnable() {
            public void run() {
                mistral.chat(finalRequest, new Mistral.Listener() {
                    public void onToken(String token) {
                        msgs.get(msgs.size() - 1).content += token;
                        throttledNotify();
                    }

                    public void onDone(String full, String error) {
                        msgs.get(msgs.size() - 1).content = full;
                        if (error != null && error.length() > 0) {
                            msgs.get(msgs.size() - 1).content = full
                                    + (full.length() > 0 ? "\n\n" : "") + "⚠ " + error;
                        }
                        handler.post(new Runnable() {
                            public void run() {
                                doneUi();
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void throttledNotify() {
        long now = System.currentTimeMillis();
        if (now - lastNotify > 120) {
            lastNotify = now;
            handler.post(new Runnable() {
                public void run() {
                    adapter.notifyDataSetChanged();
                    scrollDown();
                }
            });
        }
    }

    private void doneUi() {
        adapter.notifyDataSetChanged();
        scrollDown();
        saveChat();
        input.setEnabled(true);
        btnStop.setVisibility(View.GONE);
        btnSend.setVisibility(View.VISIBLE);
        refreshProjectLabel();
        if (FilesApplier.hasFiles(lastAssistant())) {
            Ui.toast(activity, "ИИ прислал файлы — жми «⚡ Файлы» чтобы записать");
        }
    }

    private void stop() {
        if (mistral != null) mistral.cancel();
    }

    private String lastAssistant() {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Mistral.Msg m = msgs.get(i);
            if ("assistant".equals(m.role) && m.content.length() > 0) return m.content;
        }
        return "";
    }

    private void applyFiles() {
        File project = Store.currentProject(activity);
        if (project == null) {
            Ui.toast(activity, "Проект не выбран");
            return;
        }
        String text = lastAssistant();
        if (!FilesApplier.hasFiles(text)) {
            Ui.toast(activity, "В последнем ответе ИИ нет блоков ###FILE");
            return;
        }
        FilesApplier.Result res = FilesApplier.apply(text, project);
        StringBuilder sb = new StringBuilder();
        sb.append("Записано файлов: ").append(res.written.size()).append('\n');
        for (String w : res.written) sb.append("✓ ").append(w).append('\n');
        if (res.errors.size() > 0) {
            sb.append('\n').append("Ошибки:\n");
            for (String e : res.errors) sb.append("✗ ").append(e).append('\n');
        }
        new AlertDialog.Builder(activity)
                .setTitle("Файлы применены")
                .setMessage(sb.toString())
                .setPositiveButton("🔨 Собрать APK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        BuildHelper.showBuildDialog(activity, Store.currentProject(activity));
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void scrollDown() {
        list.post(new Runnable() {
            public void run() {
                list.setSelection(msgs.size() - 1);
            }
        });
    }

    /** Адаптер сообщений: пузыри пользователя и ИИ. */
    private class MessageAdapter extends BaseAdapter {

        public int getCount() {
            return msgs.size();
        }

        public Object getItem(int position) {
            return msgs.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(activity).inflate(R.layout.item_message, parent, false);
            }
            LinearLayout bubble = (LinearLayout) v.findViewById(R.id.bubble);
            TextView role = (TextView) v.findViewById(R.id.role);
            TextView text = (TextView) v.findViewById(R.id.text);
            Mistral.Msg m = msgs.get(position);
            boolean user = "user".equals(m.role);
            role.setText(user ? "▼ ТЫ" : "▲ ИИ (Mistral)");
            role.setTextColor(user ? Ui.C_ACCENT : Ui.C_DIM);
            text.setText(m.content.length() == 0 ? "…" : m.content);
            bubble.setBackgroundResource(user ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_ai);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bubble.getLayoutParams();
            lp.width = LinearLayout.LayoutParams.MATCH_PARENT;
            bubble.setLayoutParams(lp);
            Ui.applyAll(v);
            text.setTypeface(Ui.pixel);
            return v;
        }
    }
}

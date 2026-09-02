package com.pixelcode.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Вкладка «Проекты»: список, создание из шаблона, сборка, удаление. */
public class ProjectsFragment extends Fragment {

    private Activity activity;
    private ListView list;
    private ProjectAdapter adapter;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_projects, container, false);
        Ui.applyAll(root);
        list = (ListView) root.findViewById(R.id.list);
        Button create = (Button) root.findViewById(R.id.btn_create);
        adapter = new ProjectAdapter();
        list.setAdapter(adapter);
        create.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showCreateDialog();
            }
        });
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        adapter.notifyDataSetChanged();
    }

    private List<File> data() {
        return Store.projects(activity);
    }

    private void showCreateDialog() {
        LinearLayout wrap = new LinearLayout(activity); // контейнер для диалога
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 16);
        wrap.setPadding(pad, pad, pad, pad);
        final EditText name = new EditText(activity);
        name.setHint(R.string.project_name);
        name.setText("myapp");
        wrap.addView(name);
        final RadioGroup rg = new RadioGroup(activity);
        rg.setOrientation(RadioGroup.VERTICAL);
        android.widget.RadioButton rGame = new android.widget.RadioButton(activity);
        rGame.setText(R.string.tpl_game);
        rGame.setId(1000);
        android.widget.RadioButton rApp = new android.widget.RadioButton(activity);
        rApp.setText(R.string.tpl_app);
        rApp.setId(1001);
        android.widget.RadioButton rEmpty = new android.widget.RadioButton(activity);
        rEmpty.setText(R.string.tpl_empty);
        rEmpty.setId(1002);
        rg.addView(rGame);
        rg.addView(rApp);
        rg.addView(rEmpty);
        rGame.setChecked(true);
        wrap.addView(rg);
        Ui.applyAll(wrap);

        new AlertDialog.Builder(activity)
                .setTitle("Новый проект")
                .setView(wrap)
                .setPositiveButton("Создать", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String n = name.getText().toString().trim();
                        if (n.length() == 0) {
                            Ui.toast(activity, "Имя пустое");
                            return;
                        }
                        int type = Templates.GAME;
                        if (rg.getCheckedRadioButtonId() == 1001) type = Templates.APP;
                        if (rg.getCheckedRadioButtonId() == 1002) type = Templates.EMPTY;
                        createProject(n, type);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void createProject(String name, int type) {
        try {
            File dir = Store.project(activity, name);
            if (dir.exists()) {
                Ui.toast(activity, "Проект «" + name + "» уже существует");
            } else {
                Templates.create(activity, dir, name, type);
                Ui.toast(activity, "Создан проект «" + name + "» (" + Templates.templateName(type) + ")");
            }
            new Prefs(activity).setCurrentProject(name);
            reload();
        } catch (Throwable t) {
            Ui.toast(activity, "Ошибка: " + t.getMessage());
        }
    }

    private void showActions(final File project) {
        final Prefs prefs = new Prefs(activity);
        prefs.setCurrentProject(project.getName());
        final String[] items = {
                "📂 Открыть файлы",
                "💬 Открыть чат ИИ",
                "🔨 Собрать APK",
                "📌 Сделать текущим",
                "🗑 Удалить проект"
        };
        new AlertDialog.Builder(activity)
                .setTitle(project.getName())
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            ((MainActivity) activity).switchTab(MainActivity.TAB_FILES);
                        } else if (which == 1) {
                            ((MainActivity) activity).switchTab(MainActivity.TAB_CHAT);
                        } else if (which == 2) {
                            BuildHelper.showBuildDialog(activity, project);
                        } else if (which == 3) {
                            Ui.toast(activity, "Текущий проект: " + project.getName());
                            reload();
                        } else if (which == 4) {
                            confirmDelete(project);
                        }
                    }
                })
                .show();
    }

    private void confirmDelete(final File project) {
        new AlertDialog.Builder(activity)
                .setTitle("Удалить " + project.getName() + "?")
                .setMessage("Все файлы проекта будут удалены безвозвратно.")
                .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Store.deleteRecursive(project);
                        if (new Prefs(activity).currentProject().equals(project.getName())) {
                            new Prefs(activity).setCurrentProject("");
                        }
                        reload();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private class ProjectAdapter extends BaseAdapter {

        public int getCount() {
            return data().size();
        }

        public Object getItem(int position) {
            return data().get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(activity).inflate(R.layout.item_project, parent, false);
            }
            final File p = data().get(position);
            TextView name = (TextView) v.findViewById(R.id.name);
            TextView sub = (TextView) v.findViewById(R.id.sub);
            Button build = (Button) v.findViewById(R.id.btn_build);
            boolean current = p.getName().equals(new Prefs(activity).currentProject());
            name.setText((current ? "▸ " : "") + p.getName() + (current ? " ◂" : ""));
            name.setTextColor(current ? Ui.C_ACCENT : Ui.C_TEXT);
            java.util.List<String> files = Store.listFilesRecursive(p);
            sub.setText(files.size() + " файлов · " + Templates.packageNameFor(p.getName()));
            build.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    new Prefs(activity).setCurrentProject(p.getName());
                    BuildHelper.showBuildDialog(activity, p);
                }
            });
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showActions(p);
                }
            });
            Ui.applyAll(v);
            return v;
        }
    }
}

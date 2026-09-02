package com.pixelcode.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Вкладка «Файлы»: браузер файлов текущего проекта. */
public class FilesFragment extends Fragment {

    private Activity activity;
    private ListView list;
    private TextView projectLabel;
    private FileAdapter adapter;
    private List<String> paths = new ArrayList<String>(); // относительные пути
    private List<Boolean> dirs = new ArrayList<Boolean>();

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_files, container, false);
        Ui.applyAll(root);
        list = (ListView) root.findViewById(R.id.list);
        projectLabel = (TextView) root.findViewById(R.id.project_label);
        Button newFile = (Button) root.findViewById(R.id.btn_new);
        adapter = new FileAdapter();
        list.setAdapter(adapter);
        newFile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNewFileDialog();
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
        paths.clear();
        dirs.clear();
        File p = Store.currentProject(activity);
        if (p == null) {
            projectLabel.setText("Проект не выбран (создай во вкладке «Проекты»)");
        } else {
            projectLabel.setText("📂 " + p.getName() + "  ·  /sdcard/PixelCode/" + p.getName());
            for (String rel : Store.listFilesRecursive(p)) {
                paths.add(rel);
                dirs.add(Boolean.valueOf(rel.endsWith("/")));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showNewFileDialog() {
        final File p = Store.currentProject(activity);
        if (p == null) {
            Ui.toast(activity, "Сначала создай проект");
            return;
        }
        final EditText input = new EditText(activity);
        input.setHint("app/src/main/java/…/MyView.java");
        input.setText("app/src/main/java/NEW.java");
        new AlertDialog.Builder(activity)
                .setTitle("Новый файл")
                .setView(input)
                .setPositiveButton("Создать", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String rel = input.getText().toString().trim();
                        if (rel.length() == 0 || rel.contains("..") || rel.startsWith("/")) return;
                        try {
                            Store.write(new File(p, rel), "");
                            reload();
                        } catch (Throwable t) {
                            Ui.toast(activity, "Ошибка: " + t.getMessage());
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void open(String rel) {
        File p = Store.currentProject(activity);
        if (p == null) return;
        Intent it = new Intent(activity, EditorActivity.class);
        it.putExtra("path", new File(p, rel).getAbsolutePath());
        startActivity(it);
    }

    private void confirmDelete(final String rel) {
        final File p = Store.currentProject(activity);
        if (p == null) return;
        new AlertDialog.Builder(activity)
                .setTitle("Удалить " + rel + "?")
                .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Store.deleteRecursive(new File(p, rel));
                        reload();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private class FileAdapter extends BaseAdapter {

        public int getCount() {
            return paths.size();
        }

        public Object getItem(int position) {
            return paths.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(activity).inflate(R.layout.item_file, parent, false);
            }
            final String rel = (String) paths.get(position);
            boolean isDir = ((Boolean) dirs.get(position)).booleanValue();
            TextView name = (TextView) v.findViewById(R.id.name);
            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            icon.setImageResource(isDir ? R.drawable.ic_folder : R.drawable.ic_doc);
            icon.setColorFilter(isDir ? Ui.C_ACCENT : Ui.C_DIM);
            name.setText(rel);
            name.setTextColor(isDir ? Ui.C_ACCENT : Ui.C_TEXT);
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!rel.endsWith("/")) open(rel);
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    confirmDelete(rel);
                    return true;
                }
            });
            return v;
        }
    }
}

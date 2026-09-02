package com.pixelcode.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** Полноэкранный редактор кода с нумерацией строк и подсветкой. */
public class EditorActivity extends Activity {

    private File file;
    private EditText editor;
    private TextView gutter;
    private final Handler handler = new Handler();
    private int fontSize = 13;
    private boolean dirty;
    private Runnable pendingHighlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);

        String path = getIntent().getStringExtra("path");
        file = path == null ? null : new File(path);

        fontSize = new Prefs(this).fontSize();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.C_BG);

        // Верхняя панель
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(Ui.C_PANEL);
        top.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));

        TextView title = new TextView(this);
        title.setTypeface(Ui.pixel);
        title.setTextColor(Ui.C_ACCENT);
        title.setTextSize(12);
        title.setText(file == null ? "?" : file.getName());
        title.setPadding(0, Ui.dp(this, 8), 0, 0);

        Button btnFontMinus = makeBtn("A-");
        Button btnFontPlus = makeBtn("A+");
        Button btnSave = makeBtn("💾 " + getString(R.string.save));

        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(btnFontMinus);
        top.addView(btnFontPlus);
        top.addView(btnSave);

        // Редактор: гуттер + скролл
        gutter = new TextView(this);
        gutter.setTypeface(Typeface.MONOSPACE);
        gutter.setTextSize(fontSize);
        gutter.setTextColor(Ui.C_DIM);
        gutter.setText("1\n");
        gutter.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        editor = new EditText(this);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(fontSize);
        editor.setTextColor(Ui.C_TEXT);
        editor.setBackgroundColor(0xFF080311);
        editor.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        editor.setGravity(android.view.Gravity.TOP);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(true);

        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        ScrollView vscroll = new ScrollView(this);
        LinearLayout editorRow = new LinearLayout(this);
        editorRow.setOrientation(LinearLayout.HORIZONTAL);
        editorRow.addView(gutter, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        editorRow.addView(editor, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        hscroll.addView(editorRow);
        vscroll.addView(hscroll);

        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(vscroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        // содержимое
        String content = Store.read(file);
        editor.setText(content);

        // авто-подсветка
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                dirty = true;
                updateGutter();
                scheduleHighlight();
            }
        });

        btnFontMinus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (fontSize > 8) {
                    fontSize -= 2;
                    applyFont();
                }
            }
        });
        btnFontPlus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (fontSize < 28) {
                    fontSize += 2;
                    applyFont();
                }
            }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                save();
            }
        });

        updateGutter();
        Syntax.highlight(editor, isXml());
    }

    private Button makeBtn(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTypeface(Ui.pixel);
        b.setTextColor(Ui.C_TEXT);
        b.setTextSize(11);
        b.setBackgroundResource(R.drawable.bg_btn);
        b.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 6);
        b.setLayoutParams(lp);
        return b;
    }

    private void applyFont() {
        editor.setTextSize(fontSize);
        gutter.setTextSize(fontSize);
        Syntax.highlight(editor, isXml());
        new Prefs(this).setFontSize(String.valueOf(fontSize));
    }

    private boolean isXml() {
        return file != null && file.getName().toLowerCase().endsWith(".xml");
    }

    private void updateGutter() {
        int count = editor.getLineCount();
        if (count > 3000) count = 3000;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            sb.append(i).append('\n');
        }
        gutter.setText(sb.toString());
    }

    private void scheduleHighlight() {
        if (pendingHighlight != null) handler.removeCallbacks(pendingHighlight);
        pendingHighlight = new Runnable() {
            public void run() {
                Syntax.highlight(editor, isXml());
            }
        };
        handler.postDelayed(pendingHighlight, 250);
    }

    private void save() {
        try {
            Store.write(file, editor.getText().toString());
            dirty = false;
            Ui.toast(this, "Сохранено: " + file.getName());
        } catch (Throwable t) {
            Ui.toast(this, "Ошибка: " + t.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        if (dirty) {
            new AlertDialog.Builder(this)
                    .setTitle("Сохранить перед выходом?")
                    .setPositiveButton("Сохранить", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            save();
                            finish();
                        }
                    })
                    .setNegativeButton("Не сохранять", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNeutralButton("Отмена", null)
                    .show();
            return;
        }
        super.onBackPressed();
    }
}

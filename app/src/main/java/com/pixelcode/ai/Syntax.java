package com.pixelcode.ai;

import android.text.Editable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Простая подсветка синтаксиса Java/XML для редактора. */
public final class Syntax {

    private static final String KEYWORDS =
            "abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|"
                    + "extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|"
                    + "package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|"
                    + "throws|transient|try|void|volatile|while|true|false|null";

    // порядок важен: комментарий/строка раньше ключевых слов
    private static final Pattern JAVA = Pattern.compile(
            "(//[^\\n]*|/\\*.*?\\*/)"
                    + "|(\"(?:\\\\.|[^\"\\\\\\n])*\"?)"
                    + "|('(?:\\\\.|[^'\\\\\\n])*'?)"
                    + "|@\\w+"
                    + "|\\b(" + KEYWORDS + ")\\b"
                    + "|\\b(\\d[\\w.]*)\\b",
            Pattern.DOTALL);

    private static final Pattern XMLP = Pattern.compile(
            "(<!--.*?-->)"
                    + "|(<[?/]?[\\w:.-]+)"
                    + "|(\"[^\"]*\")"
                    + "|(\\bandroid:[\\w:.-]+)",
            Pattern.DOTALL);

    private static final int MAX_LEN = 120000;

    private static final List<ForegroundColorSpan> applied = new ArrayList<ForegroundColorSpan>();

    private Syntax() {
    }

    public static void highlight(EditText editor, boolean isXml) {
        Editable e = editor.getEditableText();
        if (e == null || e.length() == 0 || e.length() > MAX_LEN) return;
        for (ForegroundColorSpan s : applied) e.removeSpan(s);
        applied.clear();

        Pattern p = isXml ? XMLP : JAVA;
        Matcher m = p.matcher(e);
        while (m.find()) {
            int g = -1;
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) {
                    g = i;
                    break;
                }
            }
            if (g < 0) continue;
            int color;
            if (isXml) {
                if (g == 1) color = 0xFF6D5A9C;        // комментарий
                else if (g == 2) color = 0xFF7DD3FC;   // тег
                else if (g == 3) color = 0xFF4ADE80;   // строка
                else color = 0xFFC084FC;               // android:attr
            } else {
                if (g == 1) color = 0xFF6D5A9C;        // комментарий
                else if (g == 2 || g == 3) color = 0xFF4ADE80; // строка
                else if (g == 4) color = 0xFFFBBF24;   // аннотация
                else if (g == 5) color = 0xFFC084FC;   // ключевое слово
                else color = 0xFFF472B6;               // число
            }
            ForegroundColorSpan span = new ForegroundColorSpan(color);
            e.setSpan(span, m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            applied.add(span);
        }
    }
}

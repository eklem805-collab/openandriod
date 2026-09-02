package com.pixelcode.ai;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Разбор ответа ИИ вида "###FILE: путь" и запись файлов в проект. */
public final class FilesApplier {

    public static class Result {
        public final List<String> written = new ArrayList<String>();
        public final List<String> errors = new ArrayList<String>();

        public boolean isEmpty() {
            return written.size() == 0 && errors.size() == 0;
        }
    }

    private FilesApplier() {
    }

    /** Ищет блоки ###FILE: <путь> … (до следующего ###FILE / ###END / конца текста). */
    public static Result apply(String text, File projectRoot) {
        Result res = new Result();
        if (text == null) return res;
        String[] lines = text.split("\n", -1);
        String currentPath = null;
        StringBuilder buf = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("###FILE:")) {
                if (currentPath != null) write(res, projectRoot, currentPath, buf.toString());
                currentPath = trimHeader(trimmed);
                buf = new StringBuilder();
            } else if (trimmed.startsWith("###END")) {
                if (currentPath != null) {
                    write(res, projectRoot, currentPath, buf.toString());
                    currentPath = null;
                    buf = new StringBuilder();
                }
            } else if (currentPath != null) {
                buf.append(line).append('\n');
            }
        }
        if (currentPath != null) write(res, projectRoot, currentPath, buf.toString());
        return res;
    }

    private static String trimHeader(String header) {
        String p = header.substring(8).trim();
        // убираем маркеры кода и кавычки, если модель их добавила
        if (p.startsWith("`")) p = p.replace("`", "").trim();
        if (p.startsWith("\"") && p.endsWith("\"") && p.length() > 1) {
            p = p.substring(1, p.length() - 1);
        }
        return p;
    }

    private static void write(Result res, File root, String relPath, String content) {
        relPath = relPath.trim();
        if (relPath.length() == 0) return;
        if (relPath.contains("..") || relPath.startsWith("/")) {
            res.errors.add("путь отклонён: " + relPath);
            return;
        }
        content = stripFences(content);
        try {
            File f = new File(root, relPath);
            File p = f.getParentFile();
            if (p != null) p.mkdirs();
            Store.write(f, content);
            res.written.add(relPath);
        } catch (Throwable t) {
            res.errors.add(relPath + ": " + t.getMessage());
        }
    }

    /** Убирает обёртку ```java … ``` вокруг всего содержимого файла. */
    private static String stripFences(String s) {
        String t = s;
        // ведущий ```строка
        t = t.replaceAll("^\\s*```[a-zA-Z0-9+._-]*\\s*\\n?", "");
        // хвостовой ```
        t = t.replaceAll("\\n?```\\s*$", "");
        return t;
    }

    /** Есть ли вообще файловые блоки в тексте. */
    public static boolean hasFiles(String text) {
        return text != null && text.contains("###FILE:");
    }
}

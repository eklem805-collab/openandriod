package com.pixelcode.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;

/** Клиент Mistral AI: чат со стримингом + проверка ключа. */
public final class Mistral {

    public static class Msg {
        public final String role;
        public String content;

        public Msg(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public interface Listener {
        void onToken(String token);

        void onDone(String full, String error);
    }

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final float temperature;
    private volatile boolean cancelled;
    private HttpURLConnection conn;

    public Mistral(Prefs prefs) {
        this.baseUrl = prefs.baseUrl();
        this.apiKey = prefs.apiKey();
        this.model = prefs.model();
        this.temperature = prefs.temperature();
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = conn;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Проверка ключа: GET /models. Возвращает null если всё ок, иначе текст ошибки. */
    public String testKey() {
        try {
            URL u = new URL(baseUrl + "/models");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            conn = c;
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Authorization", "Bearer " + apiKey);
            int code = c.getResponseCode();
            if (code == 200) return null;
            return "HTTP " + code + ": " + readError(c);
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Чат со стримингом. Блокирующий — вызывать из фонового потока. */
    public void chat(List<Msg> messages, Listener listener) {
        if (apiKey.length() == 0) {
            listener.onDone("", "Нет API-ключа. Вставь ключ Mistral в Настройках (sk-…).");
            return;
        }
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", true);
            body.put("temperature", (double) temperature);
            JSONArray arr = new JSONArray();
            for (Msg m : messages) {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.content);
                arr.put(o);
            }
            body.put("messages", arr);
            byte[] data = body.toString().getBytes("UTF-8");

            URL u = new URL(baseUrl + "/chat/completions");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            conn = c;
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(600000);
            c.setRequestProperty("Authorization", "Bearer " + apiKey);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "text/event-stream");
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();

            int code = c.getResponseCode();
            if (code != 200) {
                listener.onDone(sb.toString(), "HTTP " + code + ": " + readError(c));
                return;
            }

            InputStream in = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while (!cancelled && (line = r.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.length() == 0) continue;
                if (payload.equals("[DONE]")) break;
                try {
                    JSONObject obj = new JSONObject(payload);
                    JSONArray choices = obj.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                    if (delta == null) continue;
                    String tok = delta.optString("content", "");
                    if (tok.length() > 0) {
                        sb.append(tok);
                        listener.onToken(tok);
                    }
                } catch (Throwable ignored) {
                }
            }
            r.close();
            if (cancelled) {
                listener.onDone(sb.toString(), "остановлено");
            } else {
                listener.onDone(sb.toString(), null);
            }
        } catch (IOException e) {
            if (cancelled) {
                listener.onDone(sb.toString(), "остановлено");
            } else if (sb.length() > 0) {
                // ОБРЫВ СОЕДИНЕНИЯ — раньше молча выдавал неполный ответ за «успех»
                listener.onDone(sb.toString(), "соединение оборвалось — ответ НЕПОЛНЫЙ ("
                        + sb.length() + " симв.). Нажми «⏵ Продолжить»");
            } else {
                listener.onDone("", "Сеть: " + e.getMessage()
                        + (apiKey.length() == 0 ? " · и не задан API-ключ!" : ""));
            }
        } catch (Throwable t) {
            listener.onDone(sb.toString(), t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private String readError(HttpURLConnection c) {
        try {
            InputStream es = c.getErrorStream();
            if (es == null) return "";
            BufferedReader r = new BufferedReader(new InputStreamReader(es, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            r.close();
            String raw = sb.toString();
            try {
                JSONObject o = new JSONObject(raw);
                if (o.has("message")) return o.getString("message");
                if (o.has("detail")) return String.valueOf(o.get("detail"));
            } catch (Throwable ignored) {
            }
            return raw.length() > 300 ? raw.substring(0, 300) : raw;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Сериализация истории чата в JSON. */
    public static String toJson(List<Msg> msgs) {
        JSONArray a = new JSONArray();
        for (Msg m : msgs) {
            try {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.content);
                a.put(o);
            } catch (Throwable ignored) {
            }
        }
        return a.toString();
    }

    /** Загрузка истории из JSON. */
    public static List<Msg> fromJson(String s) {
        List<Msg> out = new ArrayList<Msg>();
        if (s == null || s.length() == 0) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Msg(o.getString("role"), o.getString("content")));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}

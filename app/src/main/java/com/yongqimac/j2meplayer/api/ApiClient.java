package com.yongqimac.j2meplayer.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yongqimac.j2meplayer.model.Game;
import com.yongqimac.j2meplayer.model.SaveSlot;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class ApiClient {
    private static final String BASE = "https://yongqimac.vip";
    private static final Gson gson = new Gson();
    private static final int TIMEOUT = 15000;

    public static List<Game> getGames() throws IOException {
        String json = get(BASE + "/api/emulator/roms/j2me");
        ApiResponse<List<Game>> resp = gson.fromJson(json,
                new TypeToken<ApiResponse<List<Game>>>(){}.getType());
        return resp.success && resp.data != null ? resp.data : Collections.emptyList();
    }

    public static byte[] downloadJar(String filename) throws IOException {
        return getBytes(BASE + "/emulator/j2me/jar/" + filename);
    }

    public static List<SaveSlot> getSaves(String gameName) throws IOException {
        String json = get(BASE + "/api/emulator/j2me/saves/" + gameName);
        ApiResponse<List<SaveSlot>> resp = gson.fromJson(json,
                new TypeToken<ApiResponse<List<SaveSlot>>>(){}.getType());
        return resp.success && resp.data != null ? resp.data : Collections.emptyList();
    }

    public static byte[] loadSave(String gameName, int slot) throws IOException {
        return getBytes(BASE + "/api/emulator/j2me/load/" + gameName + "/" + slot);
    }

    public static void saveSave(String gameName, int slot, byte[] data, byte[] screenshot) throws IOException {
        String url = BASE + "/api/emulator/j2me/save/" + gameName + "/" + slot;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestProperty("Content-Type", "application/octet-stream");

        // Format: [4B screenshot size BE][screenshot PNG][RMS base64 text]
        int ssSize = screenshot != null ? screenshot.length : 0;
        int rmsLen = data != null ? data.length : 0;
        byte[] body = new byte[4 + ssSize + rmsLen];
        body[0] = (byte)(ssSize >> 24);
        body[1] = (byte)(ssSize >> 16);
        body[2] = (byte)(ssSize >> 8);
        body[3] = (byte)(ssSize);
        if (screenshot != null) System.arraycopy(screenshot, 0, body, 4, ssSize);
        if (data != null) System.arraycopy(data, 0, body, 4 + ssSize, rmsLen);

        conn.getOutputStream().write(body);
        conn.getOutputStream().close();
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Save failed: HTTP " + code);
        }
    }

    public static void deleteSave(String gameName, int slot) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                BASE + "/api/emulator/j2me/save/" + gameName + "/" + slot).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(TIMEOUT);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Delete failed: HTTP " + code);
        }
    }

    public static boolean toggleFavorite(String name) throws IOException {
        String url = BASE + "/api/emulator/favorites";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT);
        conn.setRequestProperty("Content-Type", "application/json");
        String body = "{\"platform\":\"j2me\",\"name\":\"" + name + "\"}";
        conn.getOutputStream().write(body.getBytes("UTF-8"));
        conn.getOutputStream().close();
        String json = readStream(conn.getInputStream());
        ApiResponse<?> resp = gson.fromJson(json, ApiResponse.class);
        return resp.success;
    }

    // --- Helpers ---

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        return readStream(conn.getInputStream());
    }

    private static byte[] getBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(60000);
        return readBytes(conn.getInputStream());
    }

    private static String readStream(InputStream is) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) > 0) buffer.write(chunk, 0, n);
        is.close();
        return buffer.toByteArray();
    }

    static class ApiResponse<T> {
        boolean success;
        T data;
        String error;
    }
}

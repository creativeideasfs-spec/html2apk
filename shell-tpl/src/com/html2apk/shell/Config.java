package com.html2apk.shell;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 可选配置：assets/ht2a.config（key = value 每行，支持 # 注释）。
 * 缺省使用默认值。构建时由 HTML2APK 生成该文件。
 */
public class Config {
    public String entryFile = "index.html";
    public String statusBarColor = "#111111";
    public String navBarColor = "#111111";
    public String backgroundColor = "#111111";

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("entryFile", "index.html");
        DEFAULTS.put("statusBarColor", "#111111");
        DEFAULTS.put("navBarColor", "#111111");
        DEFAULTS.put("backgroundColor", "#111111");
    }

    public static Config load(Context ctx) {
        Config c = new Config();
        Map<String, String> kv = new HashMap<>();
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(ctx.getAssets().open("ht2a.config"), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx < 0) continue;
                kv.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
            br.close();
        } catch (IOException ignored) {
            // 无配置文件则全部默认
        }
        c.entryFile = kv.getOrDefault("entryFile", DEFAULTS.get("entryFile"));
        c.statusBarColor = kv.getOrDefault("statusBarColor", DEFAULTS.get("statusBarColor"));
        c.navBarColor = kv.getOrDefault("navBarColor", DEFAULTS.get("navBarColor"));
        c.backgroundColor = kv.getOrDefault("backgroundColor", DEFAULTS.get("backgroundColor"));
        return c;
    }
}
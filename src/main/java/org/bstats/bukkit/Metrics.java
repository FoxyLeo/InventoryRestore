package org.bstats.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Metrics {

    private static final String METRICS_VERSION = "3.0";
    private static final String REPORT_URL = "https://bStats.org/submitData/bukkit";
    private static final int INITIAL_DELAY_TICKS = 20 * 60;
    private static final int INTERVAL_TICKS = 20 * 60 * 30;

    private final JavaPlugin plugin;
    private final int pluginId;
    private final Gson gson = new Gson();
    private final String serverUuid;
    private final boolean enabled;
    private final boolean logErrors;
    private final boolean logSentData;
    private final boolean logResponseStatusText;

    public Metrics(JavaPlugin plugin, int pluginId) {
        this.plugin = plugin;
        this.pluginId = pluginId;

        YamlConfiguration config = loadConfig();
        this.enabled = config.getBoolean("enabled", true);
        this.serverUuid = config.getString("serverUuid", UUID.randomUUID().toString());
        this.logErrors = config.getBoolean("logFailedRequests", false);
        this.logSentData = config.getBoolean("logSentData", false);
        this.logResponseStatusText = config.getBoolean("logResponseStatusText", false);

        if (enabled) {
            startSubmitting();
        }
    }

    private YamlConfiguration loadConfig() {
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        File configFile = new File(bStatsFolder, "config.yml");

        if (!bStatsFolder.exists()) {
            bStatsFolder.mkdirs();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!config.isSet("serverUuid")) {
            config.addDefault("enabled", true);
            config.addDefault("serverUuid", UUID.randomUUID().toString());
            config.addDefault("logFailedRequests", false);
            config.addDefault("logSentData", false);
            config.addDefault("logResponseStatusText", false);
            config.options().copyDefaults(true);
            try {
                config.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save bStats config file.", exception);
            }
        }

        return config;
    }

    private void startSubmitting() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::submitData, INITIAL_DELAY_TICKS, INTERVAL_TICKS);
    }

    private void submitData() {
        JsonObject data = getServerData();
        JsonArray plugins = new JsonArray();
        plugins.add(getPluginData());
        data.add("plugins", plugins);

        String payload = gson.toJson(data);
        if (logSentData) {
            plugin.getLogger().info("Sending bStats metrics: " + payload);
        }

        try {
            sendData(payload);
        } catch (IOException exception) {
            if (logErrors) {
                plugin.getLogger().log(Level.WARNING, "Could not submit plugin stats to bStats.", exception);
            }
        }
    }

    private JsonObject getPluginData() {
        JsonObject data = new JsonObject();
        data.addProperty("pluginName", plugin.getDescription().getName());
        data.addProperty("id", pluginId);
        data.addProperty("pluginVersion", plugin.getDescription().getVersion());
        data.add("customCharts", new JsonArray());
        return data;
    }

    private JsonObject getServerData() {
        int playerAmount = Bukkit.getOnlinePlayers().size();
        int onlineMode = Bukkit.getOnlineMode() ? 1 : 0;
        String bukkitVersion = Bukkit.getVersion();
        String bukkitName = Bukkit.getName();

        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");
        int coreCount = Runtime.getRuntime().availableProcessors();

        JsonObject data = new JsonObject();
        data.addProperty("serverUUID", serverUuid);
        data.addProperty("metricsVersion", METRICS_VERSION);
        data.addProperty("playerAmount", playerAmount);
        data.addProperty("onlineMode", onlineMode);
        data.addProperty("bukkitVersion", bukkitVersion);
        data.addProperty("bukkitName", bukkitName);
        data.addProperty("javaVersion", javaVersion);
        data.addProperty("osName", osName);
        data.addProperty("osArch", osArch);
        data.addProperty("osVersion", osVersion);
        data.addProperty("coreCount", coreCount);
        return data;
    }

    private void sendData(String data) throws IOException {
        URL url = new URL(REPORT_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.addRequestProperty("Content-Encoding", "gzip");
        connection.addRequestProperty("User-Agent", "Metrics-Service/" + METRICS_VERSION);
        connection.setDoOutput(true);

        byte[] payload = data.getBytes(StandardCharsets.UTF_8);

        try (OutputStream outputStream = connection.getOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(outputStream) {
                 {
                     this.def.setLevel(9);
                 }
             }) {
            gzip.write(payload);
        }

        int responseCode = connection.getResponseCode();
        if (logResponseStatusText) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode == 200 ? connection.getInputStream() : connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                plugin.getLogger().info("bStats response: " + responseCode + " - " + builder);
            }
        }
    }

}

package mc.euphoria_patches.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class InstallerMeta {
    private final String metaUrl;
    private String betaSnippet;
    private boolean hasBeta;
    private final List<Version> versions = new ArrayList<>();

    public InstallerMeta(String url) {
        this.metaUrl = url;
    }

    public void load() throws IOException {
        JsonObject json = readJsonFromUrl(this.metaUrl);
        betaSnippet = json.get("betaVersionSnippet").getAsString();
        hasBeta = json.get("hasBeta").getAsBoolean();
        
        JsonArray versionsArray = json.getAsJsonArray("versions");
        for (JsonElement element : versionsArray) {
            versions.add(new Version(element.getAsJsonObject()));
        }
    }

    public String getBetaSnippet() {
        return betaSnippet;
    }

    public boolean hasBeta() {
        return hasBeta;
    }

    public List<Version> getVersions() {
        return this.versions;
    }

    public static String readAll(Reader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        int codePoint;
        while ((codePoint = reader.read()) != -1) {
            stringBuilder.append((char) codePoint);
        }
        return stringBuilder.toString();
    }

    public static JsonObject readJsonFromUrl(String url) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8))) {
            String jsonText = readAll(bufferedReader);
            return JsonParser.parseString(jsonText).getAsJsonObject();
        }
    }

    public static class Version {
        boolean outdated;
        boolean snapshot;
        public String name;

        public Version(JsonObject jsonObject) {
            this.name = jsonObject.get("name").getAsString();
            this.snapshot = jsonObject.get("snapshot").getAsBoolean();
            this.outdated = jsonObject.get("outdated").getAsBoolean();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
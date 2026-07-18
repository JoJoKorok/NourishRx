package com.example.medicationmanager.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RxNormClient {
    public interface SearchCallback {
        void onSuccess(List<MedicationOption> options);

        void onError(String message);
    }

    private static final String DRUGS_ENDPOINT = "https://rxnav.nlm.nih.gov/REST/drugs.json?name=";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public void searchDrugs(String query, SearchCallback callback) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.length() < 2) {
            callback.onError("Type at least 2 characters to search.");
            return;
        }

        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String encodedQuery = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name());
                URL url = new URL(DRUGS_ENDPOINT + encodedQuery + "&expand=psn");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(8_000);
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                InputStream stream = responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String response = readFully(stream);
                if (responseCode < 200 || responseCode >= 300) {
                    callback.onError("Medication search failed. Try again or enter it manually.");
                    return;
                }

                callback.onSuccess(parseOptions(response));
            } catch (Exception exception) {
                callback.onError("Could not reach the medication database. You can still enter it manually.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private List<MedicationOption> parseOptions(String response) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONObject drugGroup = root.optJSONObject("drugGroup");
        if (drugGroup == null) {
            return new ArrayList<>();
        }

        JSONArray conceptGroups = drugGroup.optJSONArray("conceptGroup");
        Map<String, MedicationOption> options = new LinkedHashMap<>();
        if (conceptGroups == null) {
            return new ArrayList<>();
        }

        for (int i = 0; i < conceptGroups.length(); i++) {
            JSONObject group = conceptGroups.optJSONObject(i);
            if (group == null) {
                continue;
            }

            String tty = group.optString("tty", "");
            JSONArray concepts = group.optJSONArray("conceptProperties");
            if (concepts == null) {
                continue;
            }

            for (int j = 0; j < concepts.length(); j++) {
                JSONObject concept = concepts.optJSONObject(j);
                if (concept == null) {
                    continue;
                }

                String rxcui = concept.optString("rxcui", "");
                String name = concept.optString("psn", "");
                if (name.isEmpty()) {
                    name = concept.optString("synonym", "");
                }
                if (name.isEmpty()) {
                    name = concept.optString("name", "");
                }
                if (rxcui.isEmpty() || name.isEmpty()) {
                    continue;
                }

                options.put(rxcui, new MedicationOption(rxcui, name, tty));
                if (options.size() >= 25) {
                    return new ArrayList<>(options.values());
                }
            }
        }

        return new ArrayList<>(options.values());
    }

    private String readFully(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    public static class MedicationOption {
        public final String rxcui;
        public final String name;
        public final String type;

        public MedicationOption(String rxcui, String name, String type) {
            this.rxcui = rxcui;
            this.name = name;
            this.type = type;
        }
    }
}

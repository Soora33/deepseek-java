package com.brain.llm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SearchUtils {
    private String baseUrl;
    private String apiKey;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public SearchUtils(String searxngInstance,String apiKey) {
        this.baseUrl = searxngInstance;
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, String>> searXNG(String query, int numResults) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            HttpUrl url = HttpUrl.parse(baseUrl + "/search").newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("format", "json")
                    .addQueryParameter("pageno", "1")
                    .addQueryParameter("language", "zh-CN")
                    .addQueryParameter("categories", "general")
                    .addQueryParameter("engines", "baidu,sogou,bing")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("请求失败: " + response);

                Map<String, Object> responseData = objectMapper.readValue(response.body().string(), Map.class);
                List<Map<String, Object>> searchResults = (List<Map<String, Object>>) responseData.get("results");

                if (searchResults != null) {
                    for (int i = 0; i < Math.min(numResults, searchResults.size()); i++) {
                        Map<String, Object> result = searchResults.get(i);
                        Map<String, String> processedResult = new HashMap<>();
                        processedResult.put("title", (String) result.get("title"));
                        processedResult.put("url", (String) result.get("url"));
                        processedResult.put("snippet", (String) result.get("content"));
                        results.add(processedResult);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("搜索时发生错误: " + e.getMessage());
        }
        return results;
    }

    public List<Map<String, String>> tavilySearch(String query) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            Map<String,String> requestBody = new HashMap<String, String>();
            requestBody.put("query", query);
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .post(RequestBody.create(MediaType.parse("application/json"), objectMapper.writeValueAsString(requestBody)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer" + apiKey)
                    .build();


            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("请求失败: " + response);

                JsonNode jsonNode = objectMapper.readTree(response.body().string()).get("results");

                if (!jsonNode.isEmpty()) {
                    jsonNode.forEach(data -> {
                        Map<String, String> processedResult = new HashMap<>();
                        processedResult.put("title", data.get("title").toString());
                        processedResult.put("url", data.get("url").toString());
                        processedResult.put("content", data.get("content").toString());
                        results.add(processedResult);
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("搜索时发生错误: " + e.getMessage());
        }
        return results;
    }
}
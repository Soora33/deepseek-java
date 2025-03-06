package com.brain.llm.service;

import com.brain.llm.domain.ChatRequest;
import com.brain.llm.util.SearchUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class ChatService {
    @Value("${deepseek.url}")
    private String API_URL;
    @Value("${deepseek.api-key}")
    private String API_KEY;
    @Value("${deepseek.model-name}")
    private String modelName;
    @Value("${deepseek.search-engine}")
    private String searchEngine;
    @Value("${deepseek.search-key}")
    private String searchKey;
    private static final int MAX_HISTORY = 10;
    private final Deque<Map<String, String>> conversationHistory = new ArrayDeque<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private ElasticsearchKnnSearch elasticsearchKnnSearch;

    public ChatService() {
    }

    public SseEmitter handleChatRequest(ChatRequest request) {
        SseEmitter emitter = new SseEmitter();
        try {
            SearchUtils searchUtils = new SearchUtils(searchEngine,searchKey);

            // 获取搜索结果
            StringBuilder context = new StringBuilder();
            if (request.isUseSearch()) {
                List<Map<String, String>> searchResults = new ArrayList<>();
                if (searchEngine.contains("tavily")) {
                    searchResults = searchUtils.tavilySearch(request.getMessage());
                } else {
                    searchResults = searchUtils.searXNG(request.getMessage(), 3);
                }
                if (!searchResults.isEmpty()) {
                    System.out.println("search results size（联网搜索个数）: " + searchResults.size());
                    context.append("\n\n联网搜索结果：\n");
                    for (int i = 0; i < searchResults.size(); i++) {
                        Map<String, String> result = searchResults.get(i);
                        context.append(String.format("\n%d. %s\n", i + 1, result.get("title")));
                        context.append(String.format("   %s\n", result.get("content")));
                        context.append(String.format("   来源: %s\n", result.get("url")));
                    }
                }
            }

            // 是否启用知识库
            if (request.isUseRAG()) {
                List<String> vectorSearch = elasticsearchKnnSearch.vectorSearch(request.isMaxToggle() ? 10 : 5, request.getMessage());
                System.out.println("知识库参考个数: " + vectorSearch.size());
                if (!vectorSearch.isEmpty()) {
                    context.append("\n\n知识库参考：\n");
                }
                vectorSearch.forEach(data -> {
                    context.append(data + "\n");
                });
            }

            // 如果有上下文，添加系统消息
            if (context.length() > 0) {
                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                systemMessage.put("content", "请基于以下参考信息回答用户问题：\n" + context.toString());
                conversationHistory.add(systemMessage);
            }

            // 创建用户消息
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            conversationHistory.add(userMessage);

            // 准备请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", new ArrayList<>(conversationHistory));
            requestBody.put("stream", true);

            // 创建 HTTP 客户端
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(600))
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            // 发送请求并处理响应流
            StringBuilder aiResponseBuilder = new StringBuilder();
            StringBuilder reasoningBuilder = new StringBuilder();
            System.out.println("\n" + "=".repeat(20) + "思考过程" + "=".repeat(20) + "\n");
            client.send(httpRequest, HttpResponse.BodyHandlers.ofLines())
                    .body()
                    .forEach(line -> {
                        try {
                            if (line.startsWith("data: ")) {
                                String jsonData = line.substring(6);
                                if (!"[DONE]".equals(jsonData)) {
                                    Map<String, Object> response = objectMapper.readValue(jsonData, Map.class);
                                    Map<String, Object> delta = extractDeltaContent(response);

                                    // 处理思考过程
                                    if (delta != null && delta.containsKey("reasoning_content") && delta.get("reasoning_content") != null) {
                                        String reasoningContent = (String) delta.get("reasoning_content");
                                        reasoningBuilder.append(reasoningContent);
                                        // 直接打印思考过程
                                        System.out.print(reasoningContent);
                                        System.out.flush(); // 确保立即打印
                                        // 发送思考过程，使用不同的事件类型
                                        emitter.send(SseEmitter.event()
                                                .name("reasoning")
                                                .data(Map.of("reasoning_content", reasoningContent)));
                                    }

                                    // 处理回答内容
                                    if (delta != null && delta.containsKey("content") && delta.get("content") != null) {
                                        // 如果是第一个回答内容，先打印分隔线
                                        if (aiResponseBuilder.isEmpty()) {
                                            System.out.println("\n");
                                        }
                                        String content = (String) delta.get("content");
                                        aiResponseBuilder.append(content);
                                        // 直接打印回答内容
                                        System.out.print(content);
                                        System.out.flush(); // 确保立即打印
                                        emitter.send(SseEmitter.event()
                                                .name("answer")
                                                .data(Map.of("content", content)));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    });

            // 创建AI响应消息并添加到历史记录
            Map<String, String> aiMessage = new HashMap<>();
            aiMessage.put("role", "assistant");
            aiMessage.put("reasoning_content", reasoningBuilder.toString());
            aiMessage.put("content", aiResponseBuilder.toString());
            conversationHistory.add(aiMessage);

            // 如果历史记录超过最大限制，移除最早的消息
            while (conversationHistory.size() > MAX_HISTORY * 2) {
                conversationHistory.pollFirst();
            }

            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }


    private Map<String, Object> extractDeltaContent(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            return (Map<String, Object>) choices.get(0).get("delta");
        }
        return null;
    }

    public void clearHistory() {
        conversationHistory.clear();
    }
}
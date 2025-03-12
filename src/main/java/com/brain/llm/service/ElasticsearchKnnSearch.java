package com.brain.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class ElasticsearchKnnSearch {

    private final RestHighLevelClient esClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // es 索引名
    @Value("${esKnn.index-name:sora_vector_index}")
    private String indexName;
    // es 匹配的字段名
    @Value("${esKnn.es-field:content}")
    private String content;
    // es 匹配的向量字段名
    @Value("${esKnn.es-vector-field:content_vector}")
    private String contentVector;
    // 匹配方式，使用 match 匹配
    @Value("${esKnn.match:match}")
    private String match;
    // 单词匹配比例 一句话中 45% 以上的单词匹配
    @Value("${esKnn.work-check:45}")
    private String workCheck;
    // 匹配逻辑，使用 and
    @Value("${esKnn.rule:and}")
    private String rule;


    public ElasticsearchKnnSearch() {
        // 初始化带认证的ES客户端
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("sora33", "xxx")
        );

        RestClientBuilder builder = RestClient.builder(
                        new HttpHost("localhost", 9200, "http"))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider));

        this.esClient = new RestHighLevelClient(builder);
    }

    // 从接口获取向量数组
    private List<Float> getVectorFromAPI(String message) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5001/msg_to_vector?msg=" + encodedMsg))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to get vector: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode vectorNode = root.get("vector");
        List<Float> vector = new ArrayList<>(vectorNode.size());
        for (JsonNode value : vectorNode) {
            vector.add(value.floatValue());
        }
        return vector;
    }

    // 执行kNN搜索
    public SearchResponse executeKnnSearch(int k, String msg) throws Exception {
        // 1. 获取查询向量
        List<Float> queryVector = getVectorFromAPI(msg);

        // 2. 构建搜索请求
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 3. 构建kNN查询
        // 使用 XContentBuilder 安全构建
        XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
        xContentBuilder.startObject()
                .startObject("knn")
                .field("field", contentVector)
                .array("query_vector", queryVector.toArray())
                .field("k", k)
                .field("num_candidates", 50)
                .startObject("filter")
                .startObject(match)
                .startObject(content)
                .field("query", msg)
                .field("operator", rule)
                .field("minimum_should_match", workCheck + "%")
                .endObject()
                .endObject()
                .endObject()
                .endObject()
                .endObject();

        // 打印生成的JSON
        String queryJson = Strings.toString(xContentBuilder);
        System.out.println("Generated Query:\n" + queryJson);

        sourceBuilder.query(QueryBuilders.wrapperQuery(queryJson));

        searchRequest.source(sourceBuilder);

        // 4. 执行搜索
        return esClient.search(searchRequest, RequestOptions.DEFAULT);
    }

    public void close() throws IOException {
        esClient.close();
    }

    // List<EsVectorResponse>
    public List<String> vectorSearch(int k, String msg) throws Exception {
        ArrayList<String> vectorList = new ArrayList<>();
        try {
            SearchResponse response = executeKnnSearch(k,msg);
            // 处理搜索结果
            System.out.println("Search hits: " + response.getHits().getTotalHits().value);
            response.getHits().forEach(hit -> 
                System.out.println("Hit: " + hit.getSourceAsString()));


            // 遍历搜索结果
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> sourceMap = hit.getSourceAsMap();
                if (sourceMap.containsKey(content)) {
                    // 这里是汇总所有的信息，请灵活修改，对应 es 的字段
                    Object contentText = sourceMap.get(content);
                    String doc_name = sourceMap.get("doc_name") == null ? "" : sourceMap.get("doc_name") + "\n";
                    String chapter = sourceMap.get("chapter") == null ? "" : sourceMap.get("chapter") + "\n";
                    String item_number = sourceMap.get("item_number") == null ? "" : sourceMap.get("item_number") + "\n";
                    if (contentText != null) {
                        String result = doc_name + chapter + item_number + contentText + "\n";
                        vectorList.add(result);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return vectorList;
    }
}

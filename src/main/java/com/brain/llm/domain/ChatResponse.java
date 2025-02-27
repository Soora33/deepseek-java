package com.brain.llm.domain;

public class ChatResponse {
    private String content;

    public ChatResponse() {
    }

    public ChatResponse(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
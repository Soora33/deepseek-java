package com.brain.llm.domain;


public class ChatRequest {
    private String message;
    private boolean useSearch;
    private boolean useRAG;
    private boolean maxToggle;

    public ChatRequest() {
    }

    public ChatRequest(String message, boolean useSearch, boolean useRAG, boolean maxToggle) {
        this.message = message;
        this.useSearch = useSearch;
        this.useRAG = useRAG;
        this.maxToggle = maxToggle;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isUseSearch() {
        return useSearch;
    }

    public void setUseSearch(boolean useSearch) {
        this.useSearch = useSearch;
    }

    public boolean isUseRAG() {
        return useRAG;
    }

    public void setUseRAG(boolean useRAG) {
        this.useRAG = useRAG;
    }

    public boolean isMaxToggle() {
        return maxToggle;
    }

    public void setMaxToggle(boolean maxToggle) {
        this.maxToggle = maxToggle;
    }
}


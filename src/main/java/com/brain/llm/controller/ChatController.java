package com.brain.llm.controller;

import com.brain.llm.domain.ChatRequest;
import com.brain.llm.domain.ChatResponse;
import com.brain.llm.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody ChatRequest request) {
        return chatService.handleChatRequest(request);
    }

    @PostMapping("/clear-history")
    public ChatResponse clearHistory() {
        chatService.clearHistory();
        return new ChatResponse("历史记录已清除");
    }
}
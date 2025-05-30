package com.swx.ragdemo.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * @program: swx-ai-agent
 * @ClassName: McApp
 * @description:
 * @author:
 * @create: 2025/5/26 22:06
 */
@Component
@Slf4j
public class MyApp {
    private static final String SYSTEM_PROMPT = "";

    private final ChatClient chatClient;

    public MyApp(ChatModel ollamaChatModel) {
        chatClient = ChatClient.builder(ollamaChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
    @Resource
    private VectorStore vectorStore;
    /**
     * AI 基础对话
     * @param message
     * @return
     */
    public String doChat (String message){
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(new QuestionAnswerAdvisor(vectorStore)) //使用知识库
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        return content;
    }


}

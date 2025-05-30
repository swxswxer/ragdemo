package com.swx.ragdemo.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @program: swx-ai-agent
 * @ClassName: McAppTest
 * @description:
 * @author:
 * @create: 2025/5/26 22:19
 */
@SpringBootTest
class MyAppTest {
    @Resource
    private MyApp myApp;

    @Test
    void doChat() {

        String message = "你好";
        String answer = myApp.doChat(message);
        System.out.println(answer);

    }



}


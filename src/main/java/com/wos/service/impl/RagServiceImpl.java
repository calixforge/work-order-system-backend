package com.wos.service.impl;

import com.wos.common.Result;
import com.wos.service.IRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagServiceImpl implements IRagService {

    private final ChatClient.Builder chatClient;

    private final VectorStore vectorStore;

    public Result<String> ask(String question){

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question).topK(3).similarityThreshold(0.5)
                .build());

        if (documents.isEmpty()){
            log.info("documents：{}", documents);
            return  Result.success("暂无相关资料");
        }
        StringBuilder answer = new StringBuilder();


        for (int i = 0; i < documents.size(); i++) {
            answer.append(i + 1).append("：").append(documents.get(i).getText()).append('\n');
        }
        String prompt = MessageFormat.format("你是工单助手、只根据资料答,资料里没有就说'暂无相关资料'、不要编。" +
                "资料：{0} 问题：{1}" , answer.toString(), question);
        log.info("prompt: {}", prompt);
        String content = chatClient.build()
                .prompt(prompt)
                .user(question)
                .call()
                .content();

        log.info("content: {}", content);

        return Result.success(content);

    }

}

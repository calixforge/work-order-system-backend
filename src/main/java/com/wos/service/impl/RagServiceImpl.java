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
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagServiceImpl implements IRagService {

    private final ChatClient chatClient;

    private final VectorStore vectorStore;

    public RagServiceImpl(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public Result<String> ask(String question){

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question).topK(3).similarityThreshold(0.5)
                .build());

        if (documents.isEmpty()){
            return  Result.success("暂无相关资料");
        }
        log.info("documents：{}", documents);
        StringBuilder answer = new StringBuilder();


        for (int i = 0; i < documents.size(); i++) {
            answer.append(i + 1).append("：").append(documents.get(i).getText()).append('\n');
        }
        String prompt = MessageFormat.format("你是工单助手、只根据资料答,资料里没有就说'暂无相关资料'、不要编。" +
                "资料：{0} 问题：{1}" , answer.toString(), question);
        log.info("prompt: {}", prompt);
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        String collect = documents.stream()
                .map(d -> {
                    String title = Objects.toString(d.getMetadata().get("title"), "未知标题");
                    String source = Objects.toString(d.getMetadata().get("source"), "未知来源");
                    return "《" + title + "》" + source;
                })
                .distinct()
                .collect(Collectors.joining("、"));

        StringBuilder contents = new StringBuilder();
        contents.append(content).append("\n参考文档:\n").append(collect);
        log.info("contents: {}", contents);

        return Result.success(contents.toString());

    }

}

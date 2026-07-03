package com.wos.service.impl;

import com.wos.common.Result;
import com.wos.service.IRagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagServiceImpl implements IRagService {

    private final ChatClient chatClient;

    private final VectorStore kbVectorStore;

    private final String ragQaPromptTemplate;

    public RagServiceImpl(ChatClient.Builder chatClientBuilder,
                          @Qualifier("kbVectorStore") VectorStore kbVectorStore,
                          @Value("classpath:prompts/rag-qa.st") Resource ragQaPromptResource) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.kbVectorStore = kbVectorStore;
        this.ragQaPromptTemplate = StreamUtils.copyToString(
                ragQaPromptResource.getInputStream(), StandardCharsets.UTF_8);
    }

    public Result<String> ask(String question) {
        List<Document> documents = kbVectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.5)
                .build());

        if (documents.isEmpty()) {
            return Result.success("暂无相关资料");
        }

        log.info("documents: {}", documents);
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            context.append(i + 1).append(": ").append(documents.get(i).getText()).append('\n');
        }

        String prompt = ragQaPromptTemplate
                .replace("{context}", context.toString())
                .replace("{question}", question);
        log.info("prompt: {}", prompt);

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        String references = documents.stream()
                .map(d -> {
                    String title = Objects.toString(d.getMetadata().get("title"), "未知标题");
                    String source = Objects.toString(d.getMetadata().get("source"), "未知来源");
                    return "《" + title + "》" + source;
                })
                .distinct()
                .collect(Collectors.joining("、"));

        String result = content + "\n参考文档:\n" + references;
        log.info("contents: {}", result);

        return Result.success(result);
    }
}

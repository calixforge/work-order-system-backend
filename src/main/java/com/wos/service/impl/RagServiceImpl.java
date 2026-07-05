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

    private static final int TOP_K = 3;

    /** 相似度阈值:拦截无关检索的第一道防线;用真实问题与"你好"这类闲聊各测几发校准 */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    /** 模型的兜底语,用于判断"模型未使用资料";需与 prompts/rag-qa.st 规则中的措辞保持一致 */
    private static final String NO_RESULT = "暂无相关资料";

    /** 检索为空 / 模型判资料无关时,给用户的统一回复 */
    private static final String EMPTY_REPLY = "没有找到相关资料。可以问我 IT 或 OA 流程相关的问题,比如:打印机连不上网怎么办。";

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
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build());

        if (documents.isEmpty()) {
            return Result.success(EMPTY_REPLY);
        }

        log.info("documents: {}", documents);
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            context.append(i + 1).append(": ").append(documents.get(i).getText()).append('\n');
        }

        // 规则+资料放 system,用户问题单独放 user——混在同一条 user 消息里,
        // 模型会把"资料"误当成用户要求回答的内容,被无关资料带偏
        String systemPrompt = ragQaPromptTemplate.replace("{context}", context.toString());
        log.info("systemPrompt: {}", systemPrompt);

        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();

        log.info("模型输出：content: {}", content);
        // 模型判定资料无关而输出兜底语时,不拼引用——否则出现"暂无相关资料"却附参考文档的矛盾
        if (content == null || content.contains(NO_RESULT)) {
            return Result.success(EMPTY_REPLY);
        }

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

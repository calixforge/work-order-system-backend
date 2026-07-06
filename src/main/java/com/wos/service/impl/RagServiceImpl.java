package com.wos.service.impl;

import com.wos.common.Result;
import com.wos.domain.vo.RagAnswerVO;
import com.wos.domain.vo.RagCitationVO;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 行内引用标注,与 rag-qa.st 中约定的 [资料n] 格式保持一致 */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[资料(\\d+)]");

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

    public Result<RagAnswerVO> ask(String question) {
        List<Document> documents = kbVectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build());

        if (documents.isEmpty()) {
            return Result.success(emptyAnswer());
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
        // 模型判定资料无关而输出兜底语时,不带引用——否则出现"暂无相关资料"却附参考文档的矛盾
        if (content == null || content.contains(NO_RESULT)) {
            return Result.success(emptyAnswer());
        }

        RagAnswerVO vo = new RagAnswerVO();
        vo.setAnswer(content);
        vo.setCitations(resolveCitations(content, documents));
        return Result.success(vo);
    }

    /**
     * 解析回答中的 [资料n] 标注,只返回模型实际引用的资料(引用精确化);
     * 一个有效标注都没有(模型未遵守格式)时回退为全部检索结果——提示词是请求不是保证,代码兜底。
     */
    private List<RagCitationVO> resolveCitations(String content, List<Document> documents) {
        Set<Integer> cited = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(content);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            // 只认实际存在的编号,模型编造的编号丢弃
            if (index >= 1 && index <= documents.size()) {
                cited.add(index);
            }
        }
        if (cited.isEmpty()) {
            for (int i = 1; i <= documents.size(); i++) {
                cited.add(i);
            }
        }

        List<RagCitationVO> citations = new ArrayList<>();
        for (Integer index : cited) {
            Document doc = documents.get(index - 1);
            RagCitationVO citation = new RagCitationVO();
            citation.setIndex(index);
            citation.setTitle(Objects.toString(doc.getMetadata().get("title"), "未知标题"));
            citation.setSectionId(Objects.toString(doc.getMetadata().get("sectionId"), null));
            citations.add(citation);
        }
        return citations;
    }

    private RagAnswerVO emptyAnswer() {
        RagAnswerVO vo = new RagAnswerVO();
        vo.setAnswer(EMPTY_REPLY);
        vo.setCitations(List.of());
        return vo;
    }
}

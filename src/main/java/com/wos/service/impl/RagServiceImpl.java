package com.wos.service.impl;

import com.wos.common.Result;
import com.wos.domain.vo.KnowledgeSectionVO;
import com.wos.domain.vo.RagAnswerVO;
import com.wos.domain.vo.RagCitationVO;
import com.wos.service.IKnowledgeBaseService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /** 检索为空时不会请求模型,直接给用户的统一回复 */
    private static final String EMPTY_REPLY = "没有找到相关资料。可以问我 IT 或 OA 流程相关的问题,比如:打印机连不上网怎么办。";

    /** 行内引用标注,与 rag-qa.st 中约定的 [资料n] 格式保持一致 */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[资料(\\d+)]");

    private final ChatClient chatClient;

    private final VectorStore kbVectorStore;

    private final IKnowledgeBaseService knowledgeBaseService;

    private final String ragQaPromptTemplate;

    public RagServiceImpl(ChatClient.Builder chatClientBuilder,
                          @Qualifier("kbVectorStore") VectorStore kbVectorStore,
                          IKnowledgeBaseService knowledgeBaseService,
                          @Value("classpath:prompts/rag-qa.st") Resource ragQaPromptResource) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.kbVectorStore = kbVectorStore;
        this.knowledgeBaseService = knowledgeBaseService;
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

        List<KnowledgeSectionVO> contexts = findFullSections(documents);
        log.info("RAG召回完成, question={}, chunks={}, sections={}", question, documents.size(), contexts.size());
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            KnowledgeSectionVO item = contexts.get(i);
            context.append("资料").append(i + 1).append(": ")
                    .append(item.getTitle()).append('\n')
                    .append(item.getContent()).append('\n');
        }

        // 规则+资料放 system,用户问题单独放 user——混在同一条 user 消息里,
        // 模型会把"资料"误当成用户要求回答的内容,被无关资料带偏
        String systemPrompt = ragQaPromptTemplate.replace("{context}", context.toString());

        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();

        log.info("模型输出摘要: {}", abbreviate(content, 300));
        if (content == null) {
            return Result.success(emptyAnswer());
        }

        RagAnswerVO vo = new RagAnswerVO();
        vo.setAnswer(content);
        vo.setCitations(resolveCitations(content, contexts));
        return Result.success(vo);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /** 检索用 chunk,生成用完整条目:按 sectionId 去重后回填目录缓存里的原文,避免乱序和空洞。 */
    private List<KnowledgeSectionVO> findFullSections(List<Document> documents) {
        Map<String, KnowledgeSectionVO> sections = new LinkedHashMap<>();
        for (Document doc : documents) {
            String sectionId = Objects.toString(doc.getMetadata().get("sectionId"), null);
            KnowledgeSectionVO section = knowledgeBaseService.findSectionById(sectionId)
                    .orElseGet(() -> fallbackSection(doc, sectionId));
            sections.putIfAbsent(section.getId(), section);
        }
        return new ArrayList<>(sections.values());
    }

    private KnowledgeSectionVO fallbackSection(Document doc, String sectionId) {
        KnowledgeSectionVO section = new KnowledgeSectionVO();
        section.setId(sectionId == null ? doc.getId() : sectionId);
        section.setTitle(Objects.toString(doc.getMetadata().get("title"), "未知标题"));
        section.setContent(doc.getText());
        return section;
    }

    /**
     * 解析回答中的 [资料n] 标注,只返回模型实际引用的资料(引用精确化);
     * 没有有效标注时不展示引用,避免"暂无相关资料"或闲聊回答挂上无关资料。
     */
    private List<RagCitationVO> resolveCitations(String content, List<KnowledgeSectionVO> contexts) {
        Set<Integer> cited = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(content);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            // 只认实际存在的编号,模型编造的编号丢弃
            if (index >= 1 && index <= contexts.size()) {
                cited.add(index);
            }
        }

        List<RagCitationVO> citations = new ArrayList<>();
        for (Integer index : cited) {
            KnowledgeSectionVO item = contexts.get(index - 1);
            RagCitationVO citation = new RagCitationVO();
            citation.setIndex(index);
            citation.setTitle(item.getTitle());
            citation.setSectionId(item.getId());
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

package com.wos.service.impl;

import com.wos.domain.vo.KnowledgeSectionVO;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    private static final long SSE_TIMEOUT = 60_000L;

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

    @Override
    public SseEmitter askStream(String question) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        List<Document> documents = kbVectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build());

        if (documents.isEmpty()) {
            sendEmptyAndComplete(emitter);
            return emitter;
        }

        List<KnowledgeSectionVO> contexts = findFullSections(documents);
        log.info("RAG流式召回完成, question={}, chunks={}, sections={}", question, documents.size(), contexts.size());
        sendSources(emitter, contexts);
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            KnowledgeSectionVO item = contexts.get(i);
            context.append("资料").append(i + 1).append(": ")
                    .append(item.getTitle()).append('\n')
                    .append(item.getContent()).append('\n');
        }

        String systemPrompt = ragQaPromptTemplate.replace("{context}", context.toString());
        StringBuilder fullContent = new StringBuilder();
        chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .stream()
                .content()
                .subscribe(delta -> sendAnswerDelta(emitter, fullContent, delta),
                        emitter::completeWithError,
                        () -> completeStream(emitter, fullContent.toString(), contexts));
        return emitter;
    }

    private void sendAnswerDelta(SseEmitter emitter, StringBuilder fullContent, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        fullContent.append(delta);
        try {
            emitter.send(SseEmitter.event().name("answer").data(delta));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendSources(SseEmitter emitter, List<KnowledgeSectionVO> contexts) {
        try {
            emitter.send(SseEmitter.event().name("sources").data(buildCitations(contexts)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void completeStream(SseEmitter emitter, String content, List<KnowledgeSectionVO> contexts) {
        try {
            log.info("模型流式输出摘要: {}", abbreviate(content, 300));
            emitter.send(SseEmitter.event().name("citations").data(resolveCitations(content, contexts)));
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendEmptyAndComplete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("sources").data(List.of()));
            emitter.send(SseEmitter.event().name("answer").data(EMPTY_REPLY));
            emitter.send(SseEmitter.event().name("citations").data(List.of()));
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
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

    private List<RagCitationVO> buildCitations(List<KnowledgeSectionVO> contexts) {
        List<RagCitationVO> citations = new ArrayList<>();
        for (int i = 0; i < contexts.size(); i++) {
            KnowledgeSectionVO item = contexts.get(i);
            RagCitationVO citation = new RagCitationVO();
            citation.setIndex(i + 1);
            citation.setTitle(item.getTitle());
            citation.setSectionId(item.getId());
            citations.add(citation);
        }
        return citations;
    }

}

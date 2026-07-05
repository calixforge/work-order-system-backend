package com.wos.service.impl;

import com.wos.domain.vo.KnowledgeCategoryVO;
import com.wos.domain.vo.KnowledgeSectionVO;
import com.wos.service.IKnowledgeBaseService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务:启动时把 kb 的 Markdown 解析一次(# 分类 / ## 条目),
 * 同一份解析结果同时供两个消费面——
 * ① 目录树(前端浏览与引用跳转);② 向量灌库(RAG 检索)。
 * 单一来源保证目录标题与向量 metadata 完全一致,引用定位不漂移。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    /** 内部契约:metadata 打标与启动清空的过滤条件必须用同一对键值,常量保证一致 */
    private static final String META_TYPE_KEY = "type";
    private static final String META_TYPE_KB = "kb";

    /** 切分参数:经切分实验校准(300 token / 句尾回退最短 100 字符) */
    private static final int CHUNK_SIZE = 300;
    private static final int MIN_CHUNK_SIZE_CHARS = 100;

    @Qualifier("kbVectorStore")
    private final VectorStore kbVectorStore;

    @Value("classpath:kb/工单知识库.md")
    private Resource kbResource;

    private List<KnowledgeCategoryVO> catalog = List.of();

    @PostConstruct
    public void init() {
        try {
            String md = StreamUtils.copyToString(kbResource.getInputStream(), StandardCharsets.UTF_8);
            this.catalog = parse(md);
            int sectionCount = catalog.stream().mapToInt(c -> c.getSections().size()).sum();
            log.info("知识库目录加载完成:{} 个分类,共 {} 条", catalog.size(), sectionCount);
            ingest();
        } catch (Exception e) {
            // 知识库属于附属能力:加载失败只降级智能问答,不阻断工单主业务启动
            log.error("知识库加载失败(目录/向量灌库),智能问答降级", e);
        }
    }

    @Override
    public List<KnowledgeCategoryVO> getCatalog() {
        return catalog;
    }

    /**
     * 逐行扫描:# 起新分类,## 起新条目,其余行为当前条目内容。
     * 条目 id 由标题派生(确定性 UUID):文件增删/调序不影响既有 id,改标题才变——
     * 身份从业务键派生而非位置,与工单点 ID 同一原则。
     */
    private List<KnowledgeCategoryVO> parse(String md) {
        List<KnowledgeCategoryVO> categories = new ArrayList<>();
        // id 冲突守卫:值相同=标题重复(业务键冲突),值不同=哈希碰撞(理论 2^-122,防御性检测)
        Map<String, String> idToTitle = new HashMap<>();
        KnowledgeCategoryVO category = null;
        KnowledgeSectionVO section = null;
        StringBuilder buffer = new StringBuilder();

        for (String line : md.split("\n", -1)) {
            if (line.startsWith("# ")) {
                flush(section, buffer);
                category = new KnowledgeCategoryVO();
                category.setCategory(line.substring(2).trim());
                category.setSections(new ArrayList<>());
                categories.add(category);
                section = null;
            } else if (line.startsWith("## ")) {
                flush(section, buffer);
                String title = line.substring(3).trim();
                String id = sectionId(title);
                String prev = idToTitle.putIfAbsent(id, title);
                if (prev != null) {
                    log.warn("知识库条目 id 冲突:\"{}\" 与 \"{}\" 生成相同 id,引用跳转将错乱,请调整标题", prev, title);
                }
                section = new KnowledgeSectionVO();
                section.setId(id);
                section.setTitle(title);
                if (category != null) {
                    category.getSections().add(section);
                }
            } else if (section != null) {
                buffer.append(line).append('\n');
            }
        }
        flush(section, buffer);
        return categories;
    }

    /** 标题 → 确定性 UUID 字符串(kb: 命名空间,防与其他类型的派生 id 相撞) */
    private String sectionId(String title) {
        return UUID.nameUUIDFromBytes(("kb:" + title).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 把缓冲区内容落到上一个条目,并清空缓冲 */
    private void flush(KnowledgeSectionVO section, StringBuilder buffer) {
        if (section != null) {
            section.setContent(buffer.toString().trim());
        }
        buffer.setLength(0);
    }

    /**
     * 向量灌库:清空旧 kb 数据后,把每个条目(带 sectionId/title 溯源 metadata)
     * 做中文句尾预处理与超长兜底切分,写入向量库。
     */
    private void ingest() {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                .build();

        kbVectorStore.delete(META_TYPE_KEY + " == '" + META_TYPE_KB + "'");

        List<Document> prepared = new ArrayList<>();
        for (KnowledgeCategoryVO c : catalog) {
            for (KnowledgeSectionVO s : c.getSections()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("sectionId", s.getId());
                metadata.put("title", s.getTitle());
                metadata.put("source", kbResource.getFilename());
                metadata.put(META_TYPE_KEY, META_TYPE_KB);
                // 句号补换行:适配 TokenTextSplitter 写死的英文标点收刀
                prepared.add(new Document(s.getContent().replace("。", "。\n"), metadata));
            }
        }
        List<Document> chunks = splitter.apply(prepared);
        kbVectorStore.add(chunks);
        log.info("知识库向量灌库完成:{} 条切出 {} 块", prepared.size(), chunks.size());
    }
}

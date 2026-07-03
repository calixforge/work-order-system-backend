package com.wos.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;

import java.util.List;

/**
 * 知识库启动加载:读 resources/kb/*.md → 按结构切分 → 灌入向量库(只在启动时跑一次)
 */
@Component
@Slf4j
public class KnowledgeBaseLoader {

    /** 内部契约:metadata 打标与启动清空的过滤条件必须用同一对键值,常量保证一致 */
    private static final String META_TYPE_KEY = "type";
    private static final String META_TYPE_KB = "kb";

    private final VectorStore kbVectorStore;

    private final KbProperties kbProperties;

    public KnowledgeBaseLoader(@Qualifier("kbVectorStore") VectorStore kbVectorStore,
                               KbProperties kbProperties) {
        this.kbVectorStore = kbVectorStore;
        this.kbProperties = kbProperties;
    }

    @Value("classpath*:kb/*.md")
    private Resource[] kbResources;

    @PostConstruct
    public void init() {
        try {
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(kbProperties.getChunkSize())
                    .withMinChunkSizeChars(kbProperties.getMinChunkSizeChars())
                    .build();
            kbVectorStore.delete(META_TYPE_KEY + " == '" + META_TYPE_KB + "'");
            for (Resource kbResource : kbResources) {
                List<Document> documents = loadMarkdown(kbResource);

                List<Document> prepared = documents.stream()
                        .map(d -> new Document(d.getText().replace("。", "。\n"), d.getMetadata()))
                        .toList();
                List<Document> chunks = splitter.apply(prepared);
                kbVectorStore.add(chunks);
                log.info("{} 切出 {} 块", kbResource.getFilename(), chunks.size());
            }
        }catch (Exception e) {
            log.error("知识库加载失败", e);
        }
    }

    /**
     * 读单个 md,按结构(## 标题)切成多个 Document
     */
    private List<Document> loadMarkdown(Resource resource) {
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withAdditionalMetadata("source", resource.getFilename())
                .withAdditionalMetadata(META_TYPE_KEY, META_TYPE_KB)
                .build();

        return new MarkdownDocumentReader(resource, config).get();
    }

}

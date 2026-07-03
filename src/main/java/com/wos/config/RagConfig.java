package com.wos.config;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量库集合配置:kb(知识库)与 tickets(相似工单)各一个 collection。
 * 注意:两个都必须手动声明,且返回类型必须写具体的 QdrantVectorStore——
 * starter 的 @ConditionalOnMissingBean 按"声明类型"判断,声明成 VectorStore 接口时
 * 自动配置看不见它们,会按 yml 再造一个第三 bean。
 */
@Configuration
public class RagConfig {

    /**
     * kb 知识库集合
     */
    @Bean
    QdrantVectorStore kbVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName("kb")
                .initializeSchema(true)
                .build();
    }

    /**
     * tickets 工单集合,相似工单检索用。
     */
    @Bean
    QdrantVectorStore ticketVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName("tickets")
                .initializeSchema(true)
                .build();
    }

}

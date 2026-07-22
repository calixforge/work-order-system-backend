package com.wos.service.impl;

import com.wos.common.Result;
import com.wos.common.enums.WorkOrderStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wos.domain.dto.SimilarWorkorderQueryDTO;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.SimilarWorkorderVO;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.ISimilarWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarWorkorderServiceImpl implements ISimilarWorkorderService {

    /** 内部默认值:还没有环境级调参需求,先常量;要热调时再进配置 */
    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.5;

    @Qualifier("ticketVectorStore")
    private final VectorStore ticketVectorStore;

    private final WorkorderMapper workorderMapper;

    @Override
    public void indexWorkorder(String code) {
        String normalizedCode = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode == null || normalizedCode.isBlank()) {
            log.warn("工单编号为空,跳过相似库索引");
            return;
        }
        Workorder wo = workorderMapper.selectOne(new LambdaQueryWrapper<Workorder>()
                .eq(Workorder::getCode, normalizedCode));
        if (wo == null || !WorkOrderStatus.CLOSED.name().equals(wo.getStatus())) {
            log.info("工单 {} 不存在或非已关闭状态,跳过相似库索引", normalizedCode);
            return;
        }

        // 向量体 = 问题侧文本(以问题找问题),解法只进 metadata 供展示
        String text = wo.getDescription() == null || wo.getDescription().isBlank()
                ? wo.getTitle()
                : wo.getTitle() + "\n" + wo.getDescription();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("workorderId", wo.getId());
        metadata.put("workorderCode", Objects.toString(wo.getCode(), ""));
        metadata.put("title", wo.getTitle());
        metadata.put("description", Objects.toString(wo.getDescription(), ""));
        metadata.put("resolutionSummary", Objects.toString(wo.getResolutionSummary(), ""));
        metadata.put("completeTime", Objects.toString(wo.getCompleteTime(), ""));

        // 点 ID = 工单 id 派生的确定性 UUID(Spring AI 的 Qdrant store 要求 Document id 为 UUID 格式),
        // 同一工单重复索引即覆盖;"workorder:" 命名空间防未来同集合混入其他类型时撞 ID
        byte[] bytes = ("workorder:" + wo.getId()).getBytes(StandardCharsets.UTF_8);
        Document doc = new Document(UUID.nameUUIDFromBytes(bytes).toString(), text, metadata);
        ticketVectorStore.add(List.of(doc));
        log.info("工单 {} 已索引至相似工单库", normalizedCode);
    }

    @Override
    public Result<List<SimilarWorkorderVO>> searchSimilar(SimilarWorkorderQueryDTO dto) {
        List<Document> documents = ticketVectorStore.similaritySearch(SearchRequest.builder()
                .query(dto.getQuery())
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build());

        List<SimilarWorkorderVO> vos = documents.stream().map(d -> {
            Map<String, Object> md = d.getMetadata();
            SimilarWorkorderVO vo = new SimilarWorkorderVO();
            String workorderCode = Objects.toString(md.get("workorderCode"), "");
            if (workorderCode.isBlank()) {
                Object workorderId = md.get("workorderId");
                if (workorderId != null) {
                    Workorder legacyWorkorder = workorderMapper.selectById(Long.valueOf(workorderId.toString()));
                    workorderCode = legacyWorkorder == null
                            ? ""
                            : Objects.toString(legacyWorkorder.getCode(), "");
                }
            }
            vo.setWorkorderCode(workorderCode);
            vo.setTitle(Objects.toString(md.get("title"), ""));
            vo.setDescription(Objects.toString(md.get("description"), ""));
            vo.setResolutionSummary(Objects.toString(md.get("resolutionSummary"), ""));
            vo.setCompleteTime(Objects.toString(md.get("completeTime"), ""));
            vo.setScore(d.getScore());
            return vo;
        }).toList();

        return Result.success(vos);
    }
}

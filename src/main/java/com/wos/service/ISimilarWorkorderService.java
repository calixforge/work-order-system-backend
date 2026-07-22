package com.wos.service;

import com.wos.common.Result;
import com.wos.domain.dto.SimilarWorkorderQueryDTO;
import com.wos.domain.vo.SimilarWorkorderVO;

import java.util.List;

/**
 * 工单相似能力:向量索引与相似检索(tickets 集合)
 */
public interface ISimilarWorkorderService {

    /**
     * 将已关闭(验收通过)的工单索引进相似工单向量库。
     * 点 ID = 工单 id,重复调用为 upsert 覆盖,天然幂等;
     * 非 CLOSED 状态直接跳过,调用方无需判断验收结果。
     */
    void indexWorkorder(String code);

    /**
     * 按关键词检索相似历史工单(纯向量检索,不调用大模型)。
     * 返回按相似度降序,附解决方案与相似度分。
     */
    Result<List<SimilarWorkorderVO>> searchSimilar(SimilarWorkorderQueryDTO dto);
}

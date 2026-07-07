package com.wos.service;

import com.wos.domain.vo.KnowledgeCategoryVO;
import com.wos.domain.vo.KnowledgeSectionVO;

import java.util.List;
import java.util.Optional;

public interface IKnowledgeBaseService {

    /** 知识库目录树:一级标题分类 → 二级标题条目(含内容),供前端浏览与引用跳转 */
    List<KnowledgeCategoryVO> getCatalog();

    /** 按条目 id 查询完整知识库条目,供 RAG 命中 chunk 后回填完整资料 */
    Optional<KnowledgeSectionVO> findSectionById(String sectionId);
}

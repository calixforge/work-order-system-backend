package com.wos.service;

import com.wos.domain.vo.KnowledgeCategoryVO;

import java.util.List;

public interface IKnowledgeBaseService {

    /** 知识库目录树:一级标题分类 → 二级标题条目(含内容),供前端浏览与引用跳转 */
    List<KnowledgeCategoryVO> getCatalog();
}

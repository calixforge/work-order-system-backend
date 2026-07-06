package com.wos.service;

import com.wos.common.Result;
import com.wos.domain.vo.RagAnswerVO;

public interface IRagService {

    /** 知识库问答:返回回答(含 [资料n] 行内标注)与被引用资料列表 */
    Result<RagAnswerVO> ask(String question);
}

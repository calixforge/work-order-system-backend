package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "知识库问答结果")
public class RagAnswerVO {

    @Schema(description = "回答内容(Markdown,含 [资料n] 行内标注)")
    private String answer;

    @Schema(description = "被引用的资料(仅含回答中实际标注的;无标注时回退为全部检索结果)")
    private List<RagCitationVO> citations;
}

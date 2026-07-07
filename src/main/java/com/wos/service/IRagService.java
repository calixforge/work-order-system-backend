package com.wos.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IRagService {

    /** 智能问答流式输出:answer 事件返回文本片段,citations 事件返回引用资料 */
    SseEmitter askStream(String question);
}

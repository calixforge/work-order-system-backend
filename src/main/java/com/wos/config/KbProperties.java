package com.wos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库导入的可调参数(默认值即当前实验调优结果,yml 不配也能跑;
 * 需要调整时在 application.yml 用 kb.* 覆盖)
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb")
public class KbProperties {

    /** 切分目标块大小(token),实验结论:300 对中文 FAQ/手册粒度合适 */
    private int chunkSize = 300;

    /** 句尾回退时允许的最小块字符数 */
    private int minChunkSizeChars = 100;
}

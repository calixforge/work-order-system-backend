package com.wos.common.enums;

/**
 * 工单优先级。
 * 数据库存数字 code,接口返回时额外补充 desc 给前端展示。
 */
public enum Priority {

    HIGH(1, "高"),
    MEDIUM(2, "中"),
    LOW(3, "低");

    private final Integer code;
    private final String desc;

    Priority(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据数据库中的优先级 code 获取中文描述。
     */
    public static String descOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (Priority priority : values()) {
            if (priority.code.equals(code)) {
                return priority.desc;
            }
        }
        return null;
    }
}

package com.wos.common.enums;

import lombok.Getter;

/**
 * 系统角色编码。
 * 数据库 role.code 存枚举 name,代码中使用枚举避免魔法字符串。
 */
@Getter
public enum RoleEnum {

    SUBMITTER("提单人"),
    HANDLER("接单人"),
    REVIEWER("审核"),
    DISPATCHER("派单人"),
    ADMIN("管理员");

    private final String desc;

    RoleEnum(String desc) {
        this.desc = desc;
    }
}

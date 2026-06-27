package com.wos.service;

import com.wos.common.Result;
import com.wos.domain.vo.AdminStatsVO;

public interface IAdminService {

    /**
     * 管理员首页统计:工单总数 / 待审 / 待派 / 启用用户 / 停用用户 / 部门数。
     */
    Result<AdminStatsVO> adminStats();
}

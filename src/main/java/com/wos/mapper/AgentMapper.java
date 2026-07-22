package com.wos.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wos.domain.dto.AgentWorkorderQueryDTO;
import com.wos.domain.vo.WorkorderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMapper {

    Page<WorkorderVO> queryWorkorders(
            Page<WorkorderVO> page,
            @Param("query") AgentWorkorderQueryDTO queryDTO,
            @Param("userId") Long userId,
            @Param("departmentId") Long departmentId
    );
}

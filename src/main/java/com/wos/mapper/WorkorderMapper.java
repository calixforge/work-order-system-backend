package com.wos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.WorkorderStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkorderMapper extends BaseMapper<Workorder> {

    WorkorderStatsVO selectStats(@Param("userId") Long userId,
                                 @Param("departmentId") Long departmentId,
                                 @Param("reviewer") boolean reviewer,
                                 @Param("dispatcher") boolean dispatcher,
                                 @Param("handler") boolean handler,
                                 @Param("submitter") boolean submitter);

}

package com.wos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wos.domain.dto.UserQueryDTO;
import com.wos.domain.pojo.User;
import com.wos.domain.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface UserMapper extends BaseMapper<User> {

    Page<UserVO> userHandlersList(Page<UserVO> page, @Param("query") UserQueryDTO queryDTO);
}

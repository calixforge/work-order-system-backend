package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.domain.pojo.WorkorderLog;
import com.wos.mapper.WorkorderLogMapper;
import com.wos.service.IWorkorderLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkorderLogServiceImpl extends ServiceImpl<WorkorderLogMapper, WorkorderLog> implements IWorkorderLogService {
}

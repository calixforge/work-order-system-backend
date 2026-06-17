package com.wos.controller;


import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.WorkorderCreateDTO;
import com.wos.domain.dto.WorkorderQueryDTO;
import com.wos.domain.pojo.Workorder;
import com.wos.service.IWorkorderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;


@Tag(name = "工单管理")
@RequestMapping("/workorder")
@RestController
@RequiredArgsConstructor
public class WorkorderController {

    private final IWorkorderService workorderService;

    @Operation(summary = "创建工单")
    @PostMapping("/create")
    public Result<Long> workorderCreate(@Valid @RequestBody WorkorderCreateDTO createDTO) {

        return  workorderService.workorderCreate(createDTO);
    }


    @Operation(summary = "查看个人创建工单列表")
    @GetMapping("/created")
    public Result<PageResult<Workorder>> workorderQueryCreated(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return  workorderService.workorderQueryCreated(queryDTO);
    }

    @Operation(summary = "查看个人负责工单列表")
    @GetMapping("/assigned")
    public Result<PageResult<Workorder>> workorderQueryAssigned(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return  workorderService.workorderQueryAssigned(queryDTO);
    }

    @Operation(summary = "查看个人待审核工单列表")
    @GetMapping("/review")
    public Result<PageResult<Workorder>> workorderQueryReview(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return  workorderService.workorderQueryReview(queryDTO);
    }

    @Operation(summary = "查看个人待派单工单列表")
    @GetMapping("/dispatch")
    public Result<PageResult<Workorder>> workorderQueryDispatch(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return  workorderService.workorderQueryDispatch(queryDTO);
    }

}

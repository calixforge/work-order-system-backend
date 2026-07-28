package com.wos.controller;


import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.WorkorderCreateDTO;
import com.wos.domain.dto.WorkorderCreatedQueryDTO;
import com.wos.domain.dto.WorkorderQueryDTO;
import com.wos.domain.dto.WorkorderUpdateDTO;
import com.wos.domain.vo.WorkorderCreateVO;
import com.wos.domain.vo.WorkorderDetailVO;
import com.wos.domain.vo.WorkorderStatsVO;
import com.wos.domain.vo.WorkorderVO;
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

    /**
     * 创建工单。
     * submit=true 时直接进入待审核;submit=false 时保存为草稿。
     */
    @Operation(summary = "创建工单")
    @PostMapping("/create")
    public Result<WorkorderCreateVO> workorderCreate(@Valid @RequestBody WorkorderCreateDTO createDTO) {

        return workorderService.workorderCreate(createDTO);
    }

    /**
     * 提单人视角:查看自己创建的工单。
     */
    @Operation(summary = "查看个人创建工单列表")
    @GetMapping("/created")
    public Result<PageResult<WorkorderVO>> workorderQueryCreated(@Valid @ParameterObject WorkorderCreatedQueryDTO queryDTO) {

        return workorderService.workorderQueryCreated(queryDTO);
    }

    /**
     * 接单人视角:查看派给自己处理的工单。
     */
    @Operation(summary = "查看个人负责工单列表")
    @GetMapping("/assigned")
    public Result<PageResult<WorkorderVO>> workorderQueryAssigned(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return workorderService.workorderQueryAssigned(queryDTO);
    }

    /**
     * 审核员视角:只查看本部门待审核工单。
     */
    @Operation(summary = "查看个人待审核工单列表")
    @GetMapping("/review")
    public Result<PageResult<WorkorderVO>> workorderQueryReview(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return workorderService.workorderQueryReview(queryDTO);
    }

    /**
     * 派单人视角:查看全局待派单工单。
     */
    @Operation(summary = "查看个人待派单工单列表")
    @GetMapping("/dispatch")
    public Result<PageResult<WorkorderVO>> workorderQueryDispatch(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return workorderService.workorderQueryDispatch(queryDTO);
    }

    @Operation(summary = "查询首页工单统计")
    @GetMapping("/stats")
    public Result<WorkorderStatsVO> workorderStats() {
        return workorderService.workorderStats();
    }

    @Operation(summary = "管理员查看全部工单列表")
    @GetMapping("/list")
    public Result<PageResult<WorkorderVO>> workorderList(@Valid @ParameterObject WorkorderQueryDTO queryDTO) {

        return workorderService.workorderList(queryDTO);
    }

    @Operation(summary = "按工单编号查询工单详情")
    @GetMapping("/code/{code}")
    public Result<WorkorderDetailVO> getWorkorderDetailByCode(@PathVariable String code) {
        return workorderService.getWorkorderDetailByCode(code);
    }

    @Operation(summary = "编辑草稿工单")
    @PutMapping("/{code}")
    public Result<Void> workorderUpdateDraft(@PathVariable String code,
                                             @Valid @RequestBody WorkorderUpdateDTO dto) {
        return workorderService.workorderUpdateDraft(code, dto);
    }

    @Operation(summary = "删除草稿工单")
    @DeleteMapping("/{code}")
    public Result<Void> workorderDeleteDraft(@PathVariable String code) {
        return workorderService.workorderDeleteDraft(code);
    }

}

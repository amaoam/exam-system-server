package com.zmt.exam.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zmt.exam.common.Result;
import com.zmt.exam.entity.Paper;
import com.zmt.exam.service.PaperService;
import com.zmt.exam.vo.AiPaperVo;
import com.zmt.exam.vo.PaperVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试卷控制器 - 处理试卷管理相关的HTTP请求
 * 包括试卷的CRUD操作、AI智能组卷、状态管理等功能
 */
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/papers")  // 试卷API路径前缀
@Tag(name = "试卷管理", description = "试卷相关操作，包括创建、查询、更新、删除，以及AI智能组卷功能")  // Swagger API分组
@RequiredArgsConstructor
@Slf4j
@CrossOrigin

public class PaperController {
    private  final PaperService paperService;


    /**
     * 获取所有试卷列表（支持模糊搜索和状态筛选）
     */
    @GetMapping("/list")  // 处理GET请求
    @Operation(summary = "获取试卷列表", description = "支持按名称模糊搜索和状态筛选的试卷列表查询")  // API描述
    public Result<java.util.List<Paper>> listPapers(
            @Parameter(description = "试卷名称，支持模糊查询") @RequestParam(required = false) String name,
            @Parameter(description = "试卷状态，可选值：DRAFT/PUBLISHED/STOPPED") @RequestParam(required = false) String status) {

        LambdaQueryWrapper<Paper> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(!ObjectUtils.isEmpty(name),Paper::getName,name);
        lambdaQueryWrapper.eq(!ObjectUtils.isEmpty(status),Paper::getStatus,status);
        List<Paper> paperList = paperService.list(lambdaQueryWrapper);
        log.info("试卷列表接口调用成功！本次条件：name = {} , status = {} , 查询列表为：{}",
                name, status, paperList);
        return Result.success(paperList);
    }

    /**
     * 手动创建试卷
     */
    @PostMapping  // 处理POST请求
    @Operation(summary = "手动创建试卷", description = "通过手动选择题目的方式创建试卷")  // API描述
    public Result<Paper> createPaper(@RequestBody PaperVo paperVo) {
        Paper paper = paperService.customCreatePaper(paperVo);
        log.info("手动组卷成功！试卷信息为：{}",paper);
        return Result.success(paper, "试卷创建成功");
    }

    /**
     * 更新试卷
     * @param id 试卷ID
     * @param paperVo 试卷更新数据
     * @return 操作结果
     */
    @PutMapping("/{id}")  // 处理PUT请求
    @Operation(summary = "更新试卷信息", description = "更新试卷的基本信息和题目配置")  // API描述
    public Result<Paper> updatePaper(
            @Parameter(description = "试卷ID") @PathVariable Integer id, 
            @RequestBody PaperVo paperVo) {
        Paper paper = paperService.customUpdatePaper(id,paperVo);
        log.info("试卷{}信息更新成功！！",paper);
        return Result.success(paper, "试卷更新成功");
    }

    /**
     * AI智能组卷（新版）
     * @param aiPaperVo 包含试卷信息和组卷规则的数据
     * @return 创建好的试卷
     */
    @PostMapping("/ai")  // 处理POST请求
    @Operation(summary = "AI智能组卷", description = "基于设定的规则（题型分布、难度配比等）使用AI自动生成试卷")  // API描述
    public Result<Paper> createPaperWithAI(@RequestBody AiPaperVo aiPaperVo) {
        Paper paper = paperService.customAiCreatePaper(aiPaperVo);
        log.info("智能组卷成功！试卷信息为：{}",paper);
        return Result.success(paper, "AI智能组卷成功");
    }

    /**
     * 获取试卷详情（包含题目）
     */
    @GetMapping("/{id}")  // 处理GET请求
    @Operation(summary = "获取试卷详情", description = "获取试卷的详细信息，包括试卷基本信息和包含的所有题目")  // API描述
    public Result<Paper> getPaperById(@Parameter(description = "试卷ID") @PathVariable Long id) {
        Paper paper =  paperService.customPaperDetailById(id);
        log.info("查询试卷详情接口成功！试卷信息为:{}",paper);
        return Result.success(paper);
    }

    /**
     * 更新试卷状态（发布/停止）
     * @param id 试卷ID
     * @param status 新的状态
     * @return 操作结果
     */
    @PostMapping("/{id}/status")  // 处理POST请求
    @Operation(summary = "更新试卷状态", description = "修改试卷状态：发布试卷供学生考试或停止试卷禁止考试")  // API描述
    public Result<Void> updatePaperStatus(
            @Parameter(description = "试卷ID") @PathVariable Integer id, 
            @Parameter(description = "新的状态，可选值：PUBLISHED/STOPPED") @RequestParam String status) {
        paperService.customUpdatePaperStatus(id,status);
        return Result.success(null, "状态更新成功");
    }

    /**
     * 删除试卷
     * @param id 试卷ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")  // 处理DELETE请求
    @Operation(summary = "删除试卷", description = "删除指定的试卷，注意：已发布的试卷不能删除")  // API描述
    public Result<Void> deletePaper(@Parameter(description = "试卷ID") @PathVariable Integer id) {
        // 检查试卷是否存在  // 验证试卷存在性
        paperService.customRemoveId(id);
        log.info("id:{}的试卷删除成功！",id);
        return Result.success("试卷删除成功！！");
    }
} 
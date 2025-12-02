package com.zmt.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmt.exam.common.Result;
import com.zmt.exam.entity.ExamRecord;
import com.zmt.exam.entity.Paper;
import com.zmt.exam.service.ExamService;
import com.zmt.exam.service.PaperService;
import com.zmt.exam.vo.ExamRankingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考试记录控制器 - 处理考试记录管理相关的HTTP请求
 * 包括考试记录查询、分页展示、成绩排行榜等功能
 */
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/exam-records")  // 考试记录API路径前缀
@Tag(name = "考试记录管理", description = "考试记录相关操作，包括记录查询、成绩管理、排行榜展示等功能")  // Swagger API分组
@CrossOrigin
@Slf4j
@RequiredArgsConstructor
public class ExamRecordController {

    private final ExamService examService;

    private final PaperService paperService;


    /**
     * 分页查询考试记录
     */
    @GetMapping("/list")  // 处理GET请求
    @Operation(summary = "分页查询考试记录", description = "支持多条件筛选的考试记录分页查询，包括按姓名、状态、时间范围等筛选")  // API描述
    public Result<Page<ExamRecord>> getExamRecords(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量", example = "20") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "学生姓名筛选条件") @RequestParam(required = false) String studentName,
            @Parameter(description = "学号筛选条件") @RequestParam(required = false) String studentNumber,
            @Parameter(description = "考试状态，0-进行中，1-已完成，2-已批阅") @RequestParam(required = false) Integer status,
            @Parameter(description = "开始日期，格式：yyyy-MM-dd") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式：yyyy-MM-dd") @RequestParam(required = false) String endDate
    ) {

        Page<ExamRecord> myPage = new Page<>(page,size);
        LambdaQueryWrapper<ExamRecord> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(!ObjectUtils.isEmpty(studentName),ExamRecord::getStudentName,studentName);
        if(status != null){
            String strstatus = switch (status){
                case 0 -> "进行中";
                case 1 -> "已完成";
                case 2 -> "已批阅";
                default -> null;
            };
            lambdaQueryWrapper.eq(!ObjectUtils.isEmpty(strstatus),ExamRecord::getStatus,strstatus);
        }
        lambdaQueryWrapper.ge(!ObjectUtils.isEmpty(startDate),ExamRecord::getStartTime,startDate);
        lambdaQueryWrapper.le(!ObjectUtils.isEmpty(endDate),ExamRecord::getEndTime,endDate);
        examService.page(myPage,lambdaQueryWrapper);
        List<Integer> paperIdList = myPage.getRecords().stream().map(ExamRecord::getExamId).toList();
        LambdaQueryWrapper<Paper> paperLambdaQueryWrapper = new LambdaQueryWrapper<>();
        paperLambdaQueryWrapper.in(!ObjectUtils.isEmpty(paperIdList),Paper::getId,paperIdList);
        List<Paper> paperList = paperService.list(paperLambdaQueryWrapper);
        Map<Long,Paper> paperMap = paperList.stream().collect(Collectors.toMap(Paper::getId,p->p));
        myPage.getRecords().forEach(examRecord -> examRecord.setPaper(paperMap.get(examRecord.getExamId().longValue())));
        return Result.success(myPage);

    }

    /**
     * 根据ID获取考试记录详情
     */
    @GetMapping("/{id}")  // 处理GET请求
    @Operation(summary = "获取考试记录详情", description = "根据记录ID获取考试记录的详细信息，包括试卷内容和答题情况")  // API描述
    public Result<ExamRecord> getExamRecordById(
            @Parameter(description = "考试记录ID") @PathVariable Integer id) {
        ExamRecord examRecord = examService.customGetExamRecordById(id);
        return Result.success(examRecord);
    }

    /**
     * 删除考试记录
     */
    @DeleteMapping("/{id}")  // 处理DELETE请求
    @Operation(summary = "删除考试记录", description = "根据ID删除指定的考试记录")  // API描述
    public Result<Void> deleteExamRecord(
            @Parameter(description = "考试记录ID") @PathVariable Integer id) {

        examService.customRemoveById(id);
        log.info("删除考试记录成功！id:{}",id);
        return Result.success("删除考试记录成功！id:{}");
    }

    /**
     * 获取考试排行榜 - 优化版本
     * 使用SQL关联查询，一次性获取所有需要的数据，性能提升数百倍
     * 
     * @param paperId 试卷ID，可选参数
     * @param limit 显示数量限制，可选参数
     * @return 排行榜列表
     */
    @GetMapping("/ranking")  // 处理GET请求
    @Operation(summary = "获取考试排行榜", description = "获取考试成绩排行榜，支持按试卷筛选和限制显示数量，使用优化的SQL关联查询提升性能")  // API描述
    public Result<List<ExamRankingVO>> getExamRanking(
            @Parameter(description = "试卷ID，可选，不传则显示所有试卷的排行") @RequestParam(required = false) Integer paperId,
            @Parameter(description = "显示数量限制，可选，不传则返回所有记录") @RequestParam(required = false) Integer limit
    ) {
        // 使用优化的查询方法，避免N+1查询问题
        List<ExamRankingVO> examRankingVOS =  examService.customGetRanking(paperId,limit);
        log.info("查询：{}试卷下的{}条数据成功！数据为：{}",paperId,limit,examRankingVOS);
        return Result.success(examRankingVOS);
    }
} 
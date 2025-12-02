package com.zmt.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zmt.exam.entity.ExamRecord;
import com.zmt.exam.vo.ExamRankingVO;
import com.zmt.exam.vo.StartExamVo;
import com.zmt.exam.vo.SubmitAnswerVo;


import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<ExamRecord> {
    /**
     * 开始考试业务
     * @param startExamVo
     * @return
     */
    ExamRecord startExam(StartExamVo startExamVo);
    /**
     * 获取考试记录详情
     * @param id
     * @return
     */
    ExamRecord customGetExamRecordById(Integer id);
    /**
     * 提交考试答案
     * @param examRecordId
     * @param answers
     */
    void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers) throws InterruptedException;

    /**
     * ai判题
     * @param examRecordId
     * @return
     * @throws InterruptedException
     */
    ExamRecord gradeExam(Integer examRecordId) throws InterruptedException;
    /**
     * 删除考试记录
     * @param id
     */
    void customRemoveById(Integer id);
    /**
     * 获取考试排行榜 - 优化版本
     * 使用SQL关联查询，一次性获取所有需要的数据，避免N+1查询问题
     * @param paperId 试卷ID，可选参数，不传则查询所有试卷
     * @param limit 显示数量限制，可选参数，不传则返回所有记录
     * @return 排行榜列表
     */
    List<ExamRankingVO> customGetRanking(Integer paperId, Integer limit);
}
 
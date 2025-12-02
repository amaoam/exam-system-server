package com.zmt.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zmt.exam.entity.ExamRecord;
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
}
 
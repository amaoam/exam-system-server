package com.zmt.exam.service;


import com.zmt.exam.entity.Question;
import com.zmt.exam.vo.AiGenerateRequestVo;
import com.zmt.exam.vo.GradingResult;
import com.zmt.exam.vo.QuestionImportVo;

import java.util.List;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface KimiAiService {

    /**
     * 构建发送给AI的提示词
     */
    String buildPrompt(AiGenerateRequestVo request);

    /**
     * ai题目信息生成
     * @param request
     * @return
     */
    List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request) throws InterruptedException;

    /**
     * 调用ai
     * @param prompt
     * @return
     */
    String callKimiAi(String prompt) throws InterruptedException;
    /**
     * 使用ai,进行简答题判断
     * @param question
     * @param userAnswer
     * @param maxScore
     * @return
     */
    GradingResult gradingTextQuestion(Question question, String userAnswer, Integer maxScore) throws InterruptedException;
    /**
     * 生成ai评语
     * @param totalScore
     * @param maxScore
     * @param questionCount
     * @param correctCount
     * @return
     */
    String buildSummary(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount) throws InterruptedException;
}
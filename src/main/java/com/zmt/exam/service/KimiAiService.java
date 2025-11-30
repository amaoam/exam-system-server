package com.zmt.exam.service;


import com.zmt.exam.vo.AiGenerateRequestVo;
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
}
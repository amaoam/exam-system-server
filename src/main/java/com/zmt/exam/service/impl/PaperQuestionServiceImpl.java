package com.zmt.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmt.exam.entity.PaperQuestion;
import com.zmt.exam.mapper.PaperQuestionMapper;
import com.zmt.exam.service.PaperQuestionService;
import org.springframework.stereotype.Service;

@Service
public class PaperQuestionServiceImpl extends ServiceImpl<PaperQuestionMapper, PaperQuestion> implements PaperQuestionService {
}

package com.zmt.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmt.exam.entity.Question;
import com.zmt.exam.entity.QuestionAnswer;
import com.zmt.exam.entity.QuestionChoice;
import com.zmt.exam.mapper.QuestionAnswerMapper;
import com.zmt.exam.mapper.QuestionChoiceMapper;
import com.zmt.exam.mapper.QuestionMapper;
import com.zmt.exam.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmt.exam.vo.QuestionQueryVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionChoiceMapper questionChoiceMapper;
    @Override
    public void customPageJavaSize(Page<Question> pageBean, QuestionQueryVo questionQueryVo) {
        //1.分页查询题目列表（多条件）
        LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getType()),Question::getType,questionQueryVo.getType());
        lambdaQueryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getDifficulty()),Question::getDifficulty,questionQueryVo.getDifficulty());
        lambdaQueryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getCategoryId()),Question::getCategoryId,questionQueryVo.getCategoryId());
        lambdaQueryWrapper.like(!ObjectUtils.isEmpty(questionQueryVo.getKeyword()),Question::getTitle,questionQueryVo.getKeyword());
        //时间的倒序排序！！
        lambdaQueryWrapper.orderByDesc(Question::getCreateTime);
        page(pageBean,lambdaQueryWrapper);
        //2.提取一个方法， 给题目进行选项和答案装填（热门题目也需要所以提取方法）
        fillQuestionChoiceAndAnswer(pageBean.getRecords());
    }


    private void fillQuestionChoiceAndAnswer(List<Question> questionList) {
        //1. 非空判断
        if(questionList == null || questionList.isEmpty()){
            log.info("没有查询对应的问题集合数据！！");
            return;
        }
        //2. 查询所有答案和选项
        //优化查询本次题目的答案和选项
        //查询本地题目集合对应的id集合！！
        List<Long> ids = questionList.stream().map(Question::getId).toList();
        //查询本次题目的选项集合
        List<QuestionChoice> questionChoiceList = questionChoiceMapper.selectList(new LambdaQueryWrapper<QuestionChoice>().in(QuestionChoice::getQuestionId,ids));
        //查询本次题目的答案
        List<QuestionAnswer> questionAnswersList = questionAnswerMapper.selectList(new LambdaQueryWrapper<QuestionAnswer>().in(QuestionAnswer::getQuestionId,ids));
        //3. 答案和选项进行map转化
        Map<Long,List<QuestionChoice>> questionChoiceMap = questionChoiceList.stream().collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        Map<Long,QuestionAnswer> questionAnswersMap = questionAnswersList.stream().collect(Collectors.toMap(QuestionAnswer::getQuestionId,a->a));
        //4. 循环问题集合，进行选项和答案配置
        questionList.forEach(question -> {
            //每个题目一定答案
            question.setAnswer(questionAnswersMap.get(question.getId()));
            //选择题才有选项
            if("CHOICE".equals(question.getType())){
                List<QuestionChoice> questionChoices = questionChoiceMap.get(question.getId());
                questionChoices.sort(Comparator.comparingInt(QuestionChoice::getSort));
                question.setChoices(questionChoices);
            }
        });

    }
}
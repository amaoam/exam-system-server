package com.zmt.exam.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.zmt.exam.entity.*;
import com.zmt.exam.mapper.ExamRecordMapper;
import com.zmt.exam.mapper.PaperMapper;
import com.zmt.exam.mapper.PaperQuestionMapper;
import com.zmt.exam.mapper.QuestionMapper;
import com.zmt.exam.service.PaperQuestionService;
import com.zmt.exam.service.PaperService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmt.exam.vo.AiPaperVo;
import com.zmt.exam.vo.PaperVo;
import com.zmt.exam.vo.RuleVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 试卷服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {

    private final QuestionMapper questionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperQuestionService paperQuestionService;
    /**
     * 根据试卷id试卷详情
     * 试卷对象
     * 题目集合
     * 注意： 题目的选项sort正序
     * 注意： 所有题目根据类型排序
     * @param id 试卷id
     * @return
     */
    @Override
    public Paper customPaperDetailById(Long id) {
        //1. 单表java代码进行paper查询
        Paper paper = getById(id);
        //2. 校验paper == null -> 抛异常
        if (paper == null){
            throw new RuntimeException("指定id:%s试卷已经被删除，无法查看详情！".formatted(id));
        }
        //3. 根据paperid查询题目集合（中间，题目，答案，选项）
        List<Question> questionList = questionMapper.customQueryQuestionListByPaperId(id);
        //4. 校验题目集合 == null -> 赋空集合！ log->做好记录
        if (ObjectUtils.isEmpty(questionList)){
            paper.setQuestions(new ArrayList<Question>());
            log.warn("试卷中没有题目！可以进行试卷编辑！但是不能用于考试！！,对应试卷id：{}",id);
            return paper;
        }
        //对题目进行排序（选择 -> 判断 -> 简答）
        questionList.sort((o1,o2)->Integer.compare(typeToInt(o1.getType()),typeToInt(o2.getType())));
        //注意：type排序，是字符类型 -》 字符 -》 对应 -》 固定的数字 1 2 3
        log.debug("题目信息排序后：{}",questionList);
        //进行paper题目集合赋值
        paper.setQuestions(questionList);
        return paper;
    }

    @Override
    public Paper customCreatePaper(PaperVo paperVo) {
        //1. 完善试卷内信息 名字 描述 时间  -> 状态 ，总题目数 ， 总分数
        Paper paper = new Paper();
        //名字 描述 时间
        BeanUtils.copyProperties(paperVo,paper);
        //状态 ，总题目数, 总分数
        paper.setStatus("DRAFT");
        if(ObjectUtils.isEmpty(paperVo.getQuestions())){
            //本次没选题目
            paper.setTotalScore(BigDecimal.ZERO);
            paper.setQuestionCount(0);
            save(paper);
            log.warn("本次{}组卷，没有选择题目！注意没有题目的试卷无法进行考试！！",paper);
            return paper;
        }
        /*
            状态默认值： DRAFT
            总题目数： question长度
            总分数： question分数的和
         */
        paper.setQuestionCount(paperVo.getQuestions().size());
        paper.setTotalScore(paperVo.getQuestions().values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        //2. 完成试卷的插入 -》 主键回显 paperId
        save(paper);
        //3. 中间表集合插入 【批量插入】 -》 中间表的service对象
        List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream().
                map(entry->new PaperQuestion(paper.getId().intValue(),
                        Long.valueOf(entry.getKey()),entry.getValue())).toList();
        //4. 中间表的批量插入
        paperQuestionMapper.insertBatchSomeColumn(paperQuestionList);
        return paper;

    }
    @Transactional
    @Override
    public Paper customAiCreatePaper(AiPaperVo aiPaperVo) {
        //1. 试卷的基本属性赋值并保存 （名字 描述 时间 状态）
        Paper paper = new Paper();
        BeanUtils.copyProperties(aiPaperVo,paper);
        paper.setStatus("DRAFT");
        save(paper);
        //2. 组卷规则下的试题选择和中间表的保存
        int questionCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        for(RuleVo rule : aiPaperVo.getRules()){
            //步骤1：校验规则下的题目数量 = 0 跳过
            if (rule.getCount() == 0){
                log.warn("在：{}类型下，不需要出题！",rule.getType().name());
                continue;
            }
            //步骤2：查询当前规则下的所有题目集合 【type categoryIds】
            LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(Question::getType,rule.getType().name());
            lambdaQueryWrapper.in(!ObjectUtils.isEmpty(rule.getCategoryIds()),Question::getCategoryId,rule.getCategoryIds());
            List<Question> allQuestionList = questionMapper.selectList(lambdaQueryWrapper);
            //步骤3：校验查询的题目集合，集合为空！跳过本次！
            if(ObjectUtils.isEmpty(allQuestionList)){
                log.warn("在：{}类型下，我们指定的分类：{},没有查询到题目信息！",rule.getType().name(),rule.getCategoryIds());
                continue;
            }
            //步骤4：判断下是否有规则下count数量！ 没有要全部了
            int realNumbers = Math.min(rule.getCount(), allQuestionList.size());
            //步骤5：本次规则下添加的数量和分数累加
            questionCount +=realNumbers;
            totalScore =  totalScore.add(BigDecimal.valueOf((long) realNumbers * rule.getScore()));
            //步骤6：先打乱数据，再截取需要题目数量
            Collections.shuffle(allQuestionList);
            List<Question> realQuestionList = allQuestionList.subList(0,realNumbers);
            //步骤7：转成中间表并进行保存
            List<PaperQuestion> paperQuestionList = realQuestionList.stream().map(question -> new PaperQuestion(
                    paper.getId().intValue(),question.getId(),BigDecimal.valueOf(rule.getScore())
            )).toList();
            paperQuestionService.saveBatch(paperQuestionList);
        }
        //3. 修改试卷信息（总题数，总分数）
        paper.setQuestionCount(questionCount);
        paper.setTotalScore(totalScore);
        updateById(paper);
        //4. 返回试卷对象
        return paper;
    }
    @Transactional
    @Override
    public Paper customUpdatePaper(Integer id, PaperVo paperVo) {
        //1.校验 （不能发布状态 ， 不能不同id,name相同）
        Paper paper = getById(id);
        if ("PUBLISHED".equals(paper.getStatus())){
            throw new RuntimeException("发布状态下的试卷不允许修改！");
        }
        //校验id name
        LambdaQueryWrapper<Paper> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.ne(Paper::getId,id);
        lambdaQueryWrapper.eq(Paper::getName,paperVo.getName());
        long count = count(lambdaQueryWrapper);
        if(count >0){
            throw new RuntimeException("%s 试卷名字已经存在，请重新修改！".formatted(paperVo.getName()));
        }
        //2.试卷的主体
        BeanUtils.copyProperties(paperVo,paper);
         /*
          状态默认值： DRAFT
          总题目数： question长度
          总分数： question分数的和
         */
        paper.setQuestionCount(paperVo.getQuestions().size());
        paper.setTotalScore(paperVo.getQuestions().values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        updateById(paper);
        //3. 中间表的批量插入
        paperQuestionService.remove(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId,paper.getId()));
        List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream().
                map(entry->new PaperQuestion(paper.getId().intValue(),Long.valueOf(entry.getKey()),entry.getValue())).toList();
        //4. 中间表的批量插入
        paperQuestionService.saveBatch(paperQuestionList);
        return paper;
    }

    @Override
    public void customUpdatePaperStatus(Integer id, String status) {
        Paper paper = new Paper();
        paper.setId(Long.valueOf(id));
        paper.setStatus(status);
        this.updateById(paper);

    }

    @Override
    public void customRemoveId(Integer id) {
        //1.不是发布状态
        Paper paper = getById(id);
        if (paper == null || "PUBLISHED".equals(paper.getStatus())){
            throw new RuntimeException("发布状态的试卷不能删除！");
        }
        //2.不能有关联的考试记录
        LambdaQueryWrapper<ExamRecord> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExamRecord::getExamId,id);
        Long count = examRecordMapper.selectCount(lambdaQueryWrapper);
        if(count >0){
            throw new RuntimeException("当前试卷：%s 下面有关联 %s条考试记录！无法直接删除！".formatted(id,count));
        }
        //3.删除自身表
        removeById(Long.valueOf(id));
        //4.删除中间表
        paperQuestionService.remove(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId,id));
    }

    /**
     * 获取题目类型的排序顺序
     * @param type 题目类型
     * @return 排序序号
     */
    private int typeToInt(String type) {
        if (type == null) {
            log.error("发现题目 type 为 null，自动排最后！");
            return Integer.MAX_VALUE;
        }
        switch (type) {
            case "CHOICE":
                return 1; // 选择题
            case "JUDGE":
                return 2;  // 判断题
            case "TEXT":
                return 3;   // 简答题
            default:
                return 4;       // 其他类型
        }
    }

}
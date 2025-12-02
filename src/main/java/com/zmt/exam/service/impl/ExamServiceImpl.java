package com.zmt.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.zmt.exam.entity.AnswerRecord;
import com.zmt.exam.entity.ExamRecord;
import com.zmt.exam.entity.Paper;
import com.zmt.exam.entity.Question;
import com.zmt.exam.mapper.ExamRecordMapper;
import com.zmt.exam.service.AnswerRecordService;
import com.zmt.exam.service.ExamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmt.exam.service.KimiAiService;
import com.zmt.exam.service.PaperService;
import com.zmt.exam.vo.GradingResult;
import com.zmt.exam.vo.StartExamVo;
import com.zmt.exam.vo.SubmitAnswerVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {
    private final PaperService paperService;
    private final AnswerRecordService answerRecordService;
    private final KimiAiService kimiAiService;
    //开始考试
    @Transactional
    @Override
    public ExamRecord startExam(StartExamVo startExamVo) {
        //宏观： 创建一个考试对象，并存储到数据库即可
        //1. 校验，该学生当前试卷是否存在正在考试的记录！ 存在进行中，返回即可
        LambdaQueryWrapper<ExamRecord> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExamRecord::getStudentName,startExamVo.getStudentName());
        lambdaQueryWrapper.eq(ExamRecord::getExamId,startExamVo.getPaperId());
        lambdaQueryWrapper.eq(ExamRecord::getStatus,"进行中");
        ExamRecord examRecord = getOne(lambdaQueryWrapper);
        if(examRecord != null){
            log.debug("{}在当前试卷：{}有未完成考试记录！直接返回了！", startExamVo.getStudentName(), startExamVo.getPaperId());
            return examRecord;
        }
        //2. 创建新的考试记录！赋予传入的参数（学生姓名，试卷id） 补全（状态，时间，切屏数）
        examRecord = new ExamRecord();
        examRecord.setStudentName(startExamVo.getStudentName());
        examRecord.setExamId(startExamVo.getPaperId());
        examRecord.setStatus("进行中");
        examRecord.setWindowSwitches(0);
        examRecord.setStartTime(LocalDateTime.now());
        //3. 保存即可！
        save(examRecord);
        //4. 返回即可
        return examRecord;
    }

    @Override
    public ExamRecord customGetExamRecordById(Integer id) {
        //宏观：获取考试记录，考试记录对应的试卷对象，获取考试记录对应的答题记录集合
        //注意： 答题记录和顺序和考试记录的顺序相同！
        //1. 获取考试记录详情
        ExamRecord examRecord = getById(id);
        if (examRecord == null) {
            throw new RuntimeException("开始考试的记录已经被删除！");
        }
        //2. 获取考试记录对应试卷对象详情 【试卷 题目 选项 和 答案】
        Paper paper = paperService.customPaperDetailById(examRecord.getExamId().longValue());
        if (paper == null) {
            throw new RuntimeException("当前考试记录的试卷被删除！获取考试记录详情失败！");
        }
        //3. 获取考试记录对应的答题记录集合
        LambdaQueryWrapper<AnswerRecord> lambdaQueryWrapper= new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(AnswerRecord::getExamRecordId,id);
        List<AnswerRecord> answerRecords = answerRecordService.list(lambdaQueryWrapper);
        if(!ObjectUtils.isEmpty(answerRecords)){
            List<Long> questionIdList = paper.getQuestions().stream().map(Question::getId).toList();
            answerRecords.sort((o1,o2)->{
                int x = questionIdList.indexOf(o1.getQuestionId().longValue());
                int y = questionIdList.indexOf(o2.getQuestionId().longValue());
                return  Integer.compare(x,y);
            });
        }
        //4. 数据组装即可
        examRecord.setPaper(paper);
        examRecord.setAnswerRecords(answerRecords);
        return examRecord;


    }

    @Override
    public void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers) throws InterruptedException {
        //宏观： 提交答案中间表保存  修改考试记录数据（已完成 ，结束时间）  触发开始判卷（examRecordId）
        //1.中间表保存问题
        if(!ObjectUtils.isEmpty(answers)){
            List<AnswerRecord> answerRecordList = answers.stream().map(vo->new
                     AnswerRecord(examRecordId,vo.getQuestionId(),vo.getUserAnswer())).toList();
            answerRecordService.saveBatch(answerRecordList);
        }
        //2. 暂时修改下考试记录状态（状态 -》 已完成 || 结束时间 - 设置）
        ExamRecord examRecord = getById(examRecordId);
        examRecord.setEndTime(LocalDateTime.now());
        examRecord.setStatus("已完成");
        updateById(examRecord);
        gradeExam(examRecordId);
    }
    /**
     * AI判卷方法
     * @param examRecordId
     * @return
     */
    @Override
    public ExamRecord gradeExam(Integer examRecordId) throws InterruptedException {
        //宏观：  获取考试记录相关的信息（考试记录对象 考试记录答题记录 考试对应试卷）
        //  进行循环判断（1.答题记录进行修改 2.总体提到总分数 总正确数量）  修改考试记录（状态 -》 已批阅  修改 -》 总分数）   进行ai评语生成（总正确的题目数量）
        //  修改考试记录表  返回考试记录对象
        //1.获取考试记录和相关的信息（试卷和答题记录）
        ExamRecord examRecord = customGetExamRecordById(examRecordId);
        Paper paper = examRecord.getPaper();
        if(paper == null){
            examRecord.setStatus("已批阅");
            examRecord.setAnswers("考试对应的试卷被删除！无法进行成绩判定！");
            updateById(examRecord);
            throw new RuntimeException("考试对应的试卷被删除！无法进行成绩判定！");
        }
        List<AnswerRecord> answerRecords = examRecord.getAnswerRecords();
        if (ObjectUtils.isEmpty(answerRecords)) {
            //没有提交
            examRecord.setStatus("已批阅");
            examRecord.setScore(0);
            examRecord.setAnswers("没有提交记录！成绩为零！继续加油！");
            updateById(examRecord);
            return examRecord;
        }
        //2.进行循环的判卷（1.记录总分数 2.记录正确题目数量 3. 修改每个答题记录的状态（得分，是否正确 0 1 2 ，text-》ai评语））
        int correctNumber = 0 ; //正确题目数量
        int totalScore = 0; //总得分

        //报错继续！ 某个记录错了，后续还需要继续判卷
        //将正确题目转成map,方便每次判断获取正确答案
        Map<Long,Question> questionMap = paper.getQuestions().stream().collect(Collectors.toMap(Question::getId,q->q));
        for(AnswerRecord answerRecord :answerRecords){
            try{
                //1.先获取 答题记录对应的题目对象
                Question question = questionMap.get(answerRecord.getQuestionId().longValue());
                String systemAnswer = question.getAnswer().getAnswer();
                String userAnswer = answerRecord.getUserAnswer();
                if("JUDGE".equalsIgnoreCase(question.getType())){
                    userAnswer = normalizeJudgeAnswer(userAnswer);
                }
                if(!"TEXT".equals(question.getType())){
                    //2.判断题目类型(选择和判断直接判卷)
                    if(systemAnswer.equalsIgnoreCase(userAnswer)){
                        answerRecord.setIsCorrect(1);
                        answerRecord.setScore(question.getScore());
                    }else{
                        answerRecord.setIsCorrect(0);
                        answerRecord.setScore(0);
                    }
                }else{
                    //3.简答题进行ai判断
                    //简答题
                    GradingResult result =
                            kimiAiService.gradingTextQuestion(question,userAnswer,question.getPaperScore().intValue());
                    answerRecord.setScore(result.getScore());
                    //ai评价 正确  feedback  非正确 reason
                    //是否正确 （满分 1 0分 0 其余就是2）
                    if(result.getScore() == 0){
                        answerRecord.setIsCorrect(0);
                        answerRecord.setAiCorrection(result.getReason());
                    }else if(result.getScore() == question.getPaperScore().intValue()){
                        answerRecord.setIsCorrect(1);
                        answerRecord.setAiCorrection(result.getFeedback());
                    }else{
                        answerRecord.setIsCorrect(2);
                        answerRecord.setAiCorrection(result.getReason());
                    }
                }

            }catch (Exception e){
                answerRecord.setScore(0);
                answerRecord.setIsCorrect(0);
                answerRecord.setAiCorrection("判题过程出错！");
            }
            //进行记录修改
            //进行总分数赋值
            totalScore += answerRecord.getScore();
            //正确题目数量累加
            if (answerRecord.getIsCorrect() == 1){
                correctNumber++;
            }
        }
        answerRecordService.updateBatchById(answerRecords);
        //进行ai生成评价，进行考试记录修改和完善
        String summary = kimiAiService.
                buildSummary(totalScore, paper.getTotalScore().intValue(), paper.getQuestionCount(), correctNumber);
        examRecord.setScore(totalScore);
        examRecord.setAnswers(summary);
        examRecord.setStatus("已批阅");
        updateById(examRecord);
        return  examRecord;
    }
    /**
     * 标准化判断题答案，将T/F转换为TRUE/FALSE
     * @param answer 原始答案
     * @return 标准化后的答案
     */
    private String normalizeJudgeAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return "";
        }
        String normalized = answer.trim().toUpperCase();
        return switch (normalized) {
            case "T", "TRUE", "正确" -> "TRUE";
            case "F", "FALSE", "错" -> "FALSE";
            default -> normalized;
        };
    }
}
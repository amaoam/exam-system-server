package com.zmt.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmt.exam.common.CacheConstants;
import com.zmt.exam.entity.*;
import com.zmt.exam.mapper.PaperQuestionMapper;
import com.zmt.exam.mapper.QuestionAnswerMapper;
import com.zmt.exam.mapper.QuestionChoiceMapper;
import com.zmt.exam.mapper.QuestionMapper;
import com.zmt.exam.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmt.exam.utils.RedisUtils;
import com.zmt.exam.vo.QuestionQueryVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.language.bm.Lang;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    private final QuestionMapper questionMapper;
    private final RedisUtils redisUtils;
    private final PaperQuestionMapper paperQuestionMapper;
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

    @Override
    public Question customDetailQuestion(Long id) {
        //1.查询题目详情
        Question question = questionMapper.customGetById(id);
        if (question == null){
            throw  new RuntimeException("题目查询详情失败！原因可能提前被删除！题目id为：" + id);
        }
        //2.进行热点题目缓存
        new Thread(()->{
            incrementQuestion(question.getId());
        }).start();
        return question;
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void customSaveQuestion(Question question) {
        //1.一定插入题目信息 （回显题目id）
        //同一个类型不能题目title相同
        LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Question::getType,question.getType());
        lambdaQueryWrapper.eq(Question::getTitle,question.getTitle());
        boolean exists = baseMapper.exists(lambdaQueryWrapper);
        if (exists) {
            //同一类型，title相同
            throw new RuntimeException("在%s下，存在%s 名称的题目已经存在！保存失败！".formatted(question.getType(),question.getTitle()));
        }
        boolean saved = save(question);
        if (!saved){
            //同一类型，title相同
            throw new RuntimeException("在%s下，存在%s 名称的题目！保存失败！".formatted(question.getType(),question.getTitle()));
        }
        //2.获取答案对象，并先配置题目id
        QuestionAnswer questionAnswer = question.getAnswer();
        questionAnswer.setQuestionId(question.getId());
        //3.判断是不是选择题
        if("CHOICE".equals(question.getType())){
            //是 -》 循环 -》 选项 + 题目id -> 保存 -》 判断是不是正确 进行 AD
            List<QuestionChoice> choices = question.getChoices();
            StringBuilder sb = new StringBuilder();
            for(int i = 0;i<choices.size();i++){
                //给每个选项匹配questionId
                // [0 [1] 2 [3] ]
                QuestionChoice choice = choices.get(i);
                //确保，正确顺序！ 否则默认是0 随机了
                choice.setSort(i);
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);
                if (choice.getIsCorrect()){
                    //true 本次是正确答案
                    if (sb.length() > 0){
                        sb.append(",");
                    }
                    //B,D
                    sb.append((char)('A'+i));
                }
            }
            //进行答案赋值
            questionAnswer.setAnswer(sb.toString());
        }
        // 4.保存答案对象
        questionAnswerMapper.insert(questionAnswer);
        // 5.保证方法的一致性！ 需要添加事务
    }
    /**
     * 更新题目及其完整信息（包含选项和答案）
     * <p>
     * 业务复杂性：
     * - 需要处理选项的增删改：删除旧选项，添加新选项
     * - 答案更新：覆盖原有答案或新增答案
     * - 数据完整性：确保更新过程中数据一致
     * <p>
     * 实现策略：
     * 1. 更新题目主表信息
     * 2. 删除原有选项，重新插入新选项（简化逻辑）
     * 3. 更新或插入答案信息
     *
     * @param question 包含更新信息的题目对象
     */
    @Transactional
    @Override
    public void customUpdateQuestion(Question question) {
        //1. 题目的校验 （同id不允许title重复）
        LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Question::getTitle,question.getTitle());
        lambdaQueryWrapper.ne(Question::getId,question.getId());
        boolean exists = baseMapper.exists(lambdaQueryWrapper);
        if(exists){
            throw new RuntimeException("修改：%s题目的新标题：%s和其他的题目重复了！修改失败！".formatted(question.getId(),question.getTitle()));
        }
        //2.修改题目
        boolean updated = updateById(question);
        if (!updated){
            throw new RuntimeException("修改：%s题目失败！！".formatted(question.getId()));
        }
        //3.获取答案对象
        QuestionAnswer answer = new QuestionAnswer();
        //4.判断是不是选择题
        if ("CHOICE".equals(question.getType())){
            List<QuestionChoice> choices = question.getChoices();
            //删除题目对应的所有选项（原） [根据题目id删除]
            LambdaQueryWrapper<QuestionChoice> lambdaQueryWrapper1 = new LambdaQueryWrapper<>();
            lambdaQueryWrapper1.eq(QuestionChoice::getQuestionId,question.getId());
            questionChoiceMapper.delete(lambdaQueryWrapper1);
            //循环新增选项（选项上id == null）
            // 拼接正确的档案 a,b
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < choices.size(); i++) {
                QuestionChoice choice = choices.get(i);
                choice.setId(null);
                //确保，正确顺序！ 否则默认是0 随机了
                choice.setSort(i);
                choice.setCreateTime(null);
                choice.setUpdateTime(null);
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);
                if(choice.getIsCorrect()){
                    if(sb.length()>0){
                        sb.append(",");
                    }
                    sb.append((char)('A'+i));
                }
            }
            answer.setAnswer(sb.toString());
        }
        questionAnswerMapper.updateById(answer);
        //6. 保证一致性，添加事务

    }
    /**
     * 删除题目
     * 实现策略：
     * 1. 判断试卷是有有引用题目，有，删除失败！提示！
     * 2. 先删除子数据（选项和答案）
     * 3. 删除主数据题目表
     *
     * @param id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void customRemoveQuestionById(Long id) {
        //1. 判断试卷题目表，存在删除失败！
        LambdaQueryWrapper<PaperQuestion> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(PaperQuestion::getQuestionId,id);
        Long count = paperQuestionMapper.selectCount(lambdaQueryWrapper);
        if(count >0){
            throw new RuntimeException("该题目：%s 被试卷表中引用%s次，删除失败！".formatted(id,count));
        }
        //2. 删除主表 题目表
        boolean removed = removeById(id);
        if(!removed){
            throw new RuntimeException("该题目：%s 信息删除失败！！");
        }
        //3. 删除子表 答案和选项表
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId,id));
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId,id));
    }

    @Override
    public List<Question> customFindPopularQuestions(Integer size) {
        //1. 定义热门题目集合（总集合）
        List<Question> popularQuestions = new ArrayList<>();
        //2. 去zset中获取热门题目，并且添加到总集合中
        // 获取题目排行，需要获取id和分数！ 分数用于后续的排序处理！
        Set<ZSetOperations.TypedTuple<Object>> tupleSet = redisUtils.zReverseRangeWithScores(CacheConstants.POPULAR_QUESTIONS_KEY,0,size-1);
        //定义接收id的集合
        List<Long> idsSet = new ArrayList<>();
        if(tupleSet != null && tupleSet.size()>0){
            //根据排行榜的积分，倒序进行Id查询！
            List<Long> idsList = tupleSet.stream().sorted((o1,o2)->Integer.compare(o2.getScore().intValue(),o1.getScore().intValue()))
                    .map(o->Long.valueOf(o.getValue().toString())).collect(Collectors.toList());
            //复制，用于后面补充！！
            idsSet.addAll(idsList);
            log.debug("从redis获取热门题目的id集合，且保证顺序：{}",idsList);
            for(Long id:idsList){
                Question question = getById(id);
                if(question!= null) {
                    //防止redis有缓存，但是数据库中没有！ 后续优化，删除题目，应该删除热题榜单中对应的value
                    popularQuestions.add(question);
                }
            }
            log.debug("去redis查询的热门题目，题目数：{},题目内容为：{}",popularQuestions.size(),popularQuestions);
        }
        //3. 检查是否已经满足size
        int diff = size - popularQuestions.size();
        if(diff>0){
            //4. 不满足，题目表中 非热门题目 时间倒序 limit 差数量
            LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.notIn(Question::getId,idsSet);
            lambdaQueryWrapper.orderByDesc(Question::getCreateTime);
            //limit diff
            lambdaQueryWrapper.last("limit"+diff);
            List<Question> questionDiffList = list(lambdaQueryWrapper);
            log.debug("去question表中补充热门题目，题目数：{},题目内容为：{}",questionDiffList.size(),questionDiffList);
            if (questionDiffList != null && questionDiffList.size() > 0) {
                // 5. 补充也添加到总集合中
                popularQuestions.addAll(questionDiffList);
            }
        }
        //6. 总集合一起进行答案和选项填充
        fillQuestionChoiceAndAnswer(popularQuestions);
        //7. 返回即可
        return popularQuestions;
    }

    //定义进行题目访问次数增长的方法
    //异步方法
    private void incrementQuestion(Long questionId){
        Double score = redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY,questionId,1);
        log.info("完成{}题目分数累计，累计后分数为：{}",questionId,score);
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
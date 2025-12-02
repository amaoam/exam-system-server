package com.zmt.exam.mapper;

import com.zmt.exam.entity.PaperQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @description 针对表【paper_question(试卷-题目关联表)】的数据库操作Mapper
* @createDate 2025-06-20 22:37:43
* @Entity com.exam.entity.PaperQuestion
*/
@Mapper
public interface PaperQuestionMapper extends BaseMapper<PaperQuestion> {
    // 声明批量插入方法，参数为实体列表
    /**
     1. 要求1 方法名称固定 insertBatchSomeColumn
     2. 要求2 参数必须是list实体类集合
     */
    //这里是idea的误报
    int insertBatchSomeColumn(List<PaperQuestion> list);
} 
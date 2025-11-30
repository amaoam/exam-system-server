package com.zmt.exam.mapper;


import com.zmt.exam.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface QuestionMapper extends BaseMapper<Question> {
    /**
     * 获取每个分类的题目数量
     *
     * @return 包含分类ID和题目数量的结果列表
     **/
    @Select("SELECT category_id, COUNT(*) as count FROM questions where is_deleted = 0  GROUP BY category_id ; ")
    List<Map<Long, Object>> getCategoryQuestionCount();

    Question customGetById(Long id);
}

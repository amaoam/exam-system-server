package com.zmt.exam.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmt.exam.entity.Category;
import com.zmt.exam.entity.Question;
import com.zmt.exam.mapper.CategoryMapper;
import com.zmt.exam.mapper.QuestionMapper;
import com.zmt.exam.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@Slf4j
@Service
@RequiredArgsConstructor

public class CategoryServiceImpl extends ServiceImpl<CategoryMapper,Category> implements CategoryService {

    private final CategoryMapper categoryMapper;

    private final QuestionMapper questionMapper;
    @Override
    public List<Category> getAllCategories() {
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
        );
        fillQuestionCount(categories);
        return  categories;
    }
    /**
     * 查询所有类别信息的树状集合，并按照sort字段排序
     * @return
     */
    @Override
    public List<Category> getCategoryTree() {
        // 1. 获取所有分类，并按sort排序
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
        );
        //2.为每个分类填充其自身的题目数量
        fillQuestionCount(categories);
        List<Category> buildTree = buildTree(categories);
        log.info("查询类别树状结构集合：{}",buildTree);
        return buildTree;
    }

    @Override
    public void addCategory(Category category) {
        //1.判断同一个父类分类下不允许重名
        // parent_id = 传入 and name = 传入
        LambdaQueryWrapper<Category> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Category::getParentId, category.getParentId());
        lambdaQueryWrapper.eq(Category::getName,category.getName());
        long count = count(lambdaQueryWrapper);// count 查询存在的数量
        //知识点： 我们可以在自己的service获取自己的mapper -> CategoryMapper baseMapper = getBaseMapper();
        if (count > 0) {
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次添加失败！".formatted(parent.getName(),category.getName()));
        }
        //2.保存
        save(category);
    }

    @Override
    public void updateCategory(Category category) {
        //1.先校验  同一父分类下！ 可以跟自己的name重复，不能跟其他的子分类name重复！
        LambdaQueryWrapper<Category> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Category::getParentId,category.getName());
        lambdaQueryWrapper.ne(Category::getId,category.getId());
        lambdaQueryWrapper.eq(Category::getName,category.getName());
        CategoryMapper categoryMapper = getBaseMapper();
        boolean exists = categoryMapper.exists(lambdaQueryWrapper);
        if(exists){
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次更新失败！".formatted(parent.getName(),category.getName()));
        }
        //2.再更新
        updateById(category);
    }

    @Override
    public void deleteCategory(Long id) {
        //1.检查是否一级标题
        Category category = getById(id);
        if(category.getParentId() == 0){
            throw  new RuntimeException("不能删除一级标题!");
        }
        //2.检查是否存在关联的题目
        LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Question::getCategoryId,id);
        long count = questionMapper.selectCount(lambdaQueryWrapper);
        if(count >0){
            throw new RuntimeException("当前的:%s分类，关联了%s道题目,无法删除！".formatted(category.getName(),count));
        }
        //3.以上不都不满足，删除即可【子关联数据，一并删除】
        removeById(id);
    }

    private  void fillQuestionCount(List<Category> categories){
        //1.一次性查出所有分类的题目数量
        List<Map<Long,Object>> questionCountList = questionMapper.getCategoryQuestionCount();
        // 2. 将查询结果从 List<Map> 转换为 Map<categoryId, count>，便于快速查找
        Map<Long,Long> questionCountMap = questionCountList.stream().collect(Collectors.toMap(
                map->Long.valueOf(map.get("category_id").toString()),
                map->Long.valueOf(map.get("count").toString())
        ));
        // 3. 遍历分类列表，为每个分类设置其对应的题目数量
        categories.forEach(category -> {
            category.setCount(questionCountMap.getOrDefault(category.getId(),0L));
        });
    }
    // 构建树形结构的私有辅助方法
    private List<Category> buildTree(List<Category> categories) {
        // 1. 使用Stream API按parentId进行分组，得到 Map<parentId, List<children>>
        Map<Long, List<Category>> childrenMap = categories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

            /*
                stream()：把 List<Category> 转成 Stream 流，开启流式操作。
                Collectors.groupingBy：按指定规则分组，这里用方法引用 Category::getParentId ，提取分类的 parentId 作为分组 key，value 是对应 parentId 的分类列表，快速构建 父 ID - 子分类列表 映射。
             */

        // 2. 遍历所有分类，为它们设置children属性，并递归地累加题目数量
        categories.forEach(category -> {
            // 从Map中找到当前分类的所有子分类
            List<Category> children = childrenMap.getOrDefault(category.getId(), new ArrayList<>());
            category.setChildren(children);

            // 汇总子分类的题目数量到父分类
            long childrenQuestionCount = children.stream()
                    .mapToLong(c -> c.getCount() != null ? c.getCount() : 0L)
                    .sum();
                /*
                    forEach：遍历每个分类，对单个分类做处理，类似增强 for 循环，但结合 Stream 更灵活。
                    getOrDefault：从分组好的 childrenMap 取当前分类的子分类，无对应值时给默认空列表，避免空指针。
                    嵌套 stream().mapToLong().sum()：先转成 LongStream ，通过 mapToLong 处理 count （空值转 0 ），再用 sum 汇总子分类题目数，结合自身题目数，设置到当前分类，完成递归汇总逻辑。
                 */

            long selfQuestionCount = category.getCount() != null ? category.getCount() : 0L;
            // 父分类的总数 = 自身的题目数 + 所有子分类的题目数总和
            category.setCount(selfQuestionCount + childrenQuestionCount);
        });

        // 3. 最后，筛选出所有顶级分类（parentId为0），它们是树的根节点
            /*
                filter：按条件（parentId == 0 ）过滤分类，只保留顶级分类。
                collect(Collectors.toList())：把过滤后的 Stream 流转为 List ，作为分类树的根节点集合返回。
             */
        return categories.stream()
                .filter(c -> c.getParentId() == 0)
                .collect(Collectors.toList());
    }
}


























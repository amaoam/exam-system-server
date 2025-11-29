package com.zmt.exam.service;

import com.zmt.exam.entity.Category;

import java.util.List;

public interface CategoryService {


    List<Category> getAllCategories();

    List<Category> getCategoryTree();

    void addCategory(Category category);

    void updateCategory(Category category);

    void deleteCategory(Long id);
} 
package com.zmt.exam.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.zmt.exam.mapper")
public class MybatisPlusConfiguration {

}
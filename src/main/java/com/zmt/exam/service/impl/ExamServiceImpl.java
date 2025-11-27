package com.zmt.exam.service.impl;

import com.zmt.exam.entity.ExamRecord;
import com.zmt.exam.mapper.ExamRecordMapper;
import com.zmt.exam.service.ExamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {

} 
package com.zmt.exam.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Slf4j
      public class MybatisMetaObjectHandler implements MetaObjectHandler {
          @Override
          public void insertFill(MetaObject metaObject) {
                log.info("开始插入填充...");
                this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
                this.strictInsertFill(metaObject, "isDeleted", Byte.class, (byte)0);
          }

          @Override
          public void updateFill(MetaObject metaObject) {
                log.info("开始更新填充...");
                //        this.strictInsertFill(metaObject, "updateUserId", Long.class, 123456L)
                this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
          }

      }
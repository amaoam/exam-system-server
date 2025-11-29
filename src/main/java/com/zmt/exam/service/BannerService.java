package com.zmt.exam.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.zmt.exam.entity.Banner;
import io.minio.errors.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 轮播图服务接口
 */
public interface BannerService extends IService<Banner> {
    /**
     * 上传图片页面
     * @param file
     * @return 返回图片
     */
    String uploadImage(MultipartFile file) throws Exception;
    /**
     * 完成轮播图添加
     * 插入失败抛出异常
     * @param banner 【要插入的轮播图】
     */
    void addBanner(Banner banner);
    /**
     * 轮播图更新业务！
     * 更新错误，抛出异常
     * @param banner
     */
    void updateBanner(Banner banner);
} 
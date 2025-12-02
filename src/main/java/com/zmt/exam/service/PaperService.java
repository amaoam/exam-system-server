package com.zmt.exam.service;

import com.zmt.exam.entity.Paper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zmt.exam.vo.AiPaperVo;
import com.zmt.exam.vo.PaperVo;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {
    /**
     * 根据试卷id试卷详情
     *    试卷对象
     *    题目集合
     *    注意： 题目的选项sort正序
     *    注意： 所有题目根据类型排序
     * @param id 试卷id
     * @return
     */
    Paper customPaperDetailById(Long id);
    /**
     * 手动组卷
     * @param paperVo
     * @return
     */
    Paper customCreatePaper(PaperVo paperVo);
    /**
     * 智能组卷
     * @param aiPaperVo
     * @return
     */
    Paper customAiCreatePaper(AiPaperVo aiPaperVo);
    /**
     * 更新试卷内信息
     * @param id
     * @param paperVo
     * @return
     */
    Paper customUpdatePaper(Integer id, PaperVo paperVo);

    void customUpdatePaperStatus(Integer id, String status);

    void customRemoveId(Integer id);
}
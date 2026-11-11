package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 敏感词实体（BR-11，最小词表约 20 词 [假设 A-05]，data.sql 种子）。
 * Service 层启动时加载到内存 Set 做过滤（词表小，02§2.7）。
 */
@Data
@TableName("sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;
}
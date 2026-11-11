package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.User;

/**
 * 用户 Mapper（02§2.3.1）：通用 CRUD 走 BaseMapper，全部参数化 SQL（NFR-SEC-03）。
 */
public interface UserMapper extends BaseMapper<User> {
}
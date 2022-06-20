package com.cjw.pet.service;

import com.cjw.pet.pojo.BackgroundUser;
import com.cjw.pet.pojo.PageDomain;
import com.cjw.pet.pojo.PageList;

/**
 * @author cjw
 */
public interface BackgroundUserService {
    /**
     * 添加用户
     * @param user 参数
     * @return 结果
     */
    Boolean register(BackgroundUser user);

    /**
     * 登陆
     * @param user 参数
     * @return 结果
     */
    BackgroundUser login(BackgroundUser user);

    /**
     * 修改用户信息，密码，手机号码，头像
     * @param user 用户
     * @return 结果
     */
    Boolean updateUser(BackgroundUser user);

    /**
     * 修改用户信息，密码，手机号码，头像
     * @param user 用户
     * @return 结果
     */
    Boolean adminUpdateUser(BackgroundUser user);

    /**
     * 加载后台用户
     * @param pageDomain 参数
     * @return 结果
     */
    PageList<BackgroundUser> ListUser(PageDomain pageDomain);

    /**
     * 获取当前用户登陆的信息
     * @return 结果
     */
    BackgroundUser getUserInfo();

    /**
     * 删除用户
     * @param id 用户id
     * @return 结果
     */
    Boolean deletedUser(Long id);

    /**
     * 根据用户id获取用户信息
     * @param id 用户id
     * @return 用户信息
     */
    BackgroundUser getUserById(Long id);

}

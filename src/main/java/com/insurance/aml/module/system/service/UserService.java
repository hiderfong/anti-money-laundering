package com.insurance.aml.module.system.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.system.model.dto.*;

import java.util.List;

/**
 * 用户管理服务接口
 */
public interface UserService {

    /**
     * 创建用户
     *
     * @param req 创建请求
     * @return 用户视图
     */
    UserVO createUser(UserCreateRequest req);

    /**
     * 更新用户
     *
     * @param req 更新请求
     * @return 用户视图
     */
    UserVO updateUser(UserUpdateRequest req);

    /**
     * 删除用户（软删除，状态置为DISABLED）
     *
     * @param id 用户ID
     */
    void deleteUser(Long id);

    /**
     * 重置用户密码
     *
     * @param id          用户ID
     * @param newPassword 新密码
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 分页查询用户
     *
     * @param req 查询请求
     * @return 分页结果
     */
    PageResult<UserVO> pageQueryUsers(UserQueryRequest req);

    /**
     * 获取用户详情（含角色信息）
     *
     * @param id 用户ID
     * @return 用户视图
     */
    UserVO getUserDetail(Long id);

    /**
     * 为用户分配角色
     *
     * @param userId  用户ID
     * @param roleIds 角色ID列表
     */
    void assignRoles(Long userId, List<Long> roleIds);
}

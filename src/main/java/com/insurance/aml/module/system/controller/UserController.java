package com.insurance.aml.module.system.controller;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.system.model.dto.*;
import com.insurance.aml.module.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * 提供用户增删改查、密码重置、角色分配等接口
 */
@Slf4j
@RestController
@RequestMapping("/system/users")
@Tag(name = "用户管理", description = "用户管理相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 创建用户
     */
    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户，用户名不可重复")
    public Result<UserVO> createUser(@Valid @RequestBody UserCreateRequest req) {
        log.info("创建用户请求，username={}", req.getUsername());
        UserVO user = userService.createUser(req);
        return Result.success(user);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "更新用户基本信息")
    public Result<UserVO> updateUser(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest req) {
        req.setId(id);
        log.info("更新用户请求，userId={}", id);
        UserVO user = userService.updateUser(req);
        return Result.success(user);
    }

    /**
     * 删除用户（软删除）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "软删除用户，将状态置为DISABLED")
    public Result<Void> deleteUser(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        log.info("删除用户请求，userId={}", id);
        userService.deleteUser(id);
        return Result.success();
    }

    /**
     * 重置用户密码
     */
    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置密码", description = "管理员重置指定用户密码")
    public Result<Void> resetPassword(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @RequestBody String newPassword) {
        log.info("重置用户密码请求，userId={}", id);
        userService.resetPassword(id, newPassword);
        return Result.success();
    }

    /**
     * 分页查询用户
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询用户", description = "支持按用户名、姓名、部门、状态筛选")
    public Result<PageResult<UserVO>> pageQueryUsers(UserQueryRequest req) {
        log.debug("分页查询用户请求");
        PageResult<UserVO> result = userService.pageQueryUsers(req);
        return Result.success(result);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "用户详情", description = "获取用户详细信息（含角色）")
    public Result<UserVO> getUserDetail(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        UserVO user = userService.getUserDetail(id);
        return Result.success(user);
    }

    /**
     * 为用户分配角色
     */
    @PostMapping("/{id}/roles")
    @Operation(summary = "分配角色", description = "为指定用户分配角色，替换原有角色")
    public Result<Void> assignRoles(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @RequestBody List<Long> roleIds) {
        log.info("分配用户角色请求，userId={}, roleIds={}", id, roleIds);
        userService.assignRoles(id, roleIds);
        return Result.success();
    }
}

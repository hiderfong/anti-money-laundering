package com.insurance.aml.module.system.service;

import com.insurance.aml.module.system.mapper.SysRoleMapper;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import com.insurance.aml.module.system.mapper.SysUserRoleMapper;
import com.insurance.aml.module.system.model.dto.UserCreateRequest;
import com.insurance.aml.module.system.model.dto.UserVO;
import com.insurance.aml.module.system.model.entity.SysUser;
import com.insurance.aml.module.system.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 用户管理服务测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户管理服务测试")
class UserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("创建用户 -> 默认状态为 ENABLED，可被认证服务登录")
    void createUser_defaultStatusIsEnabled() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysUserRoleMapper.findRoleIdsByUserId(100L)).thenReturn(List.of());
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        }).when(sysUserMapper).insert(any(SysUser.class));

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("e2e_created_user");
        request.setPassword("admin123");
        request.setRealName("E2E创建用户");
        request.setEmail("e2e_created_user@test.local");

        UserVO result = userService.createUser(request);

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(userCaptor.capture());

        SysUser inserted = userCaptor.getValue();
        assertEquals("ENABLED", inserted.getStatus());
        assertEquals("ENABLED", result.getStatus());
        assertEquals("e2e_created_user", result.getUsername());
        assertNotEquals("admin123", inserted.getPasswordHash(), "密码必须以 BCrypt 哈希存储");
    }
}

package com.insurance.aml.module.auth.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * JWT用户详情信息
 * 实现UserDetails接口，用于Spring Security认证
 */
@Data
@Builder
public class JwtUserDetails implements UserDetails {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（BCrypt加密）
     */
    private String password;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 用户权限集合
     */
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        // 账户未过期
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 账户未锁定
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // 凭证未过期
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 账户已启用
        return true;
    }
}

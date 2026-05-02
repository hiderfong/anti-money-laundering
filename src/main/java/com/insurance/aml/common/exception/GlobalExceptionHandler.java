package com.insurance.aml.common.exception;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.TypeMismatchException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 配置数据绑定：对数字类型字段的非法值进行容错处理
     * 当浏览器扩展注入非法参数（如 priority=u=3,i）时，不会直接抛出 400
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(java.lang.Number.class, new CustomNumberEditor(java.lang.Number.class, true) {
            @Override
            public void setAsText(String text) throws IllegalArgumentException {
                try {
                    super.setAsText(text);
                } catch (IllegalArgumentException e) {
                    // 非法数字值设为 null，不抛异常
                    setValue(null);
                }
            }
        });
    }

    /**
     * 处理类型不匹配异常（如 query 参数类型错误）
     */
    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatchException(TypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型不匹配: uri={}, query={}, property={}, value={}, message={}",
                request.getRequestURI(),
                request.getQueryString(),
                e.getPropertyName(),
                e.getValue(),
                e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "参数格式不正确: " + e.getPropertyName());
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("业务异常: uri={}, code={}, message={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常 - @RequestBody参数校验
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                              HttpServletRequest request) {
        String message = extractFirstFieldError(e);
        log.error("参数校验异常: uri={}, message={}", request.getRequestURI(), message);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理绑定异常 - 表单参数绑定
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = extractFirstBindingError(e);
        log.error("参数绑定异常: uri={}, query={}, message={}", request.getRequestURI(), request.getQueryString(), message);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.error("请求方法不支持: uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "request method not supported: " + e.getMethod());
    }

    /**
     * 处理请求体读取异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        log.error("请求体读取异常: uri={}, message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "request body is not readable");
    }

    /**
     * 处理访问拒绝异常（权限不足）
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.error("访问拒绝: uri={}, message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 处理认证异常（未登录/认证失败）
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException e, HttpServletRequest request) {
        log.error("认证异常: uri={}, message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.UNAUTHORIZED);
    }

    /**
     * 处理未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常: uri={}, message={}", request.getRequestURI(), e.getMessage(), e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }

    /**
     * 提取第一个字段校验错误消息
     */
    private String extractFirstFieldError(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            return fieldError.getDefaultMessage();
        }
        return "validation failed";
    }

    /**
     * 提取第一个绑定错误消息
     */
    private String extractFirstBindingError(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            return fieldError.getDefaultMessage();
        }
        return "bind error";
    }
}

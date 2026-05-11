package com.insurance.aml.module.system.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.system.model.entity.SysDict;
import com.insurance.aml.module.system.model.entity.SysDictItem;
import com.insurance.aml.module.system.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典控制器
 * 提供字典查询和缓存刷新接口
 */
@Slf4j
@RestController
@RequestMapping("/system/dicts")
@RequiredArgsConstructor
@Tag(name = "数据字典", description = "数据字典管理相关接口")
public class DictController {
    private final DictService dictService;

    /**
     * 查询所有字典列表
     */
    @GetMapping
    @Operation(summary = "查询字典列表", description = "查询所有启用状态的数据字典")
    public Result<List<SysDict>> listDicts() {
        log.debug("接收到查询字典列表请求");
        List<SysDict> dicts = dictService.listDicts();
        return Result.success(dicts);
    }

    /**
     * 根据字典编码查询字典项列表
     */
    @GetMapping("/{dictCode}/items")
    @Operation(summary = "查询字典项", description = "根据字典编码查询字典项列表，优先从缓存获取")
    public Result<List<SysDictItem>> getDictItems(
            @Parameter(description = "字典编码", required = true) @PathVariable String dictCode) {
        log.debug("接收到查询字典项请求，dictCode={}", dictCode);
        List<SysDictItem> items = dictService.getDictItems(dictCode);
        return Result.success(items);
    }

    /**
     * 刷新字典缓存
     */
    @PostMapping("/refresh-cache")
    @Operation(summary = "刷新字典缓存", description = "将所有字典数据重新加载到Redis缓存")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role')")
    public Result<Void> refreshDictCache() {
        log.info("接收到刷新字典缓存请求");
        dictService.refreshDictCache();
        return Result.success();
    }
}

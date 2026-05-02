package com.insurance.aml.module.screening.service.impl;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.module.screening.mapper.WatchlistAliasMapper;
import com.insurance.aml.module.screening.mapper.WatchlistIdentityMapper;
import com.insurance.aml.module.screening.mapper.WatchlistMapper;
import com.insurance.aml.module.screening.model.entity.Watchlist;
import com.insurance.aml.module.screening.model.entity.WatchlistAlias;
import com.insurance.aml.module.screening.model.entity.WatchlistIdentity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 制裁名单导入服务
 * 支持从CSV和JSON格式的文件中导入制裁名单数据
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WatchlistImportService {

    private final WatchlistMapper watchlistMapper;
    private final WatchlistAliasMapper watchlistAliasMapper;
    private final WatchlistIdentityMapper watchlistIdentityMapper;
    private final ObjectMapper objectMapper;

    /**
     * 从CSV文件导入制裁名单
     * CSV格式（无表头）：external_id, entity_type, name, name_en, gender, nationality, date_of_birth, place_of_birth, remarks
     *
     * @param inputStream CSV文件输入流
     * @param sourceId    来源ID（关联t_watchlist_source）
     */
    @Transactional(rollbackFor = Exception.class)
    public void importFromCsv(InputStream inputStream, Long sourceId) {
        log.info("开始从CSV导入制裁名单，sourceId={}", sourceId);

        List<Watchlist> entries = new ArrayList<>();
        List<WatchlistAlias> allAliases = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                // 跳过表头行
                if (isHeader) {
                    isHeader = false;
                    if (line.toLowerCase().contains("external_id") || line.toLowerCase().contains("name")) {
                        continue;
                    }
                }

                if (line.isBlank()) {
                    continue;
                }

                try {
                    String[] fields = parseCsvLine(line);
                    if (fields.length < 4) {
                        log.warn("CSV第{}行字段不足，跳过: {}", lineNum, line);
                        continue;
                    }

                    // 构建制裁名单条目
                    Watchlist entry = new Watchlist();
                    entry.setSourceId(sourceId);
                    entry.setExternalId(getField(fields, 0));
                    entry.setEntityType(getField(fields, 1));
                    entry.setName(getField(fields, 2));
                    entry.setNameEn(getField(fields, 3));
                    entry.setGender(getField(fields, 4));
                    entry.setNationality(getField(fields, 5));
                    entry.setDateOfBirth(getField(fields, 6));
                    entry.setPlaceOfBirth(getField(fields, 7));
                    entry.setRemarks(getField(fields, 8));
                    entry.setStatus("ACTIVE");
                    entries.add(entry);

                    // 将主名称也作为别名（ORIGINAL类型）
                    if (entry.getName() != null && !entry.getName().isBlank()) {
                        WatchlistAlias alias = new WatchlistAlias();
                        alias.setAliasName(entry.getName());
                        alias.setAliasType("ORIGINAL");
                        alias.setLanguage("zh");
                        alias.setCreatedTime(LocalDateTime.now());
                        allAliases.add(alias);
                    }

                } catch (Exception e) {
                    log.warn("CSV第{}行解析失败，跳过: {}, error={}", lineNum, line, e.getMessage());
                }
            }

            // 批量插入制裁名单条目
            if (!entries.isEmpty()) {
                for (Watchlist entry : entries) {
                    watchlistMapper.insert(entry);
                }
                log.info("CSV导入完成，共导入 {} 条制裁名单记录", entries.size());
            }

            // 关联别名到对应条目（通过遍历顺序对应）
            int aliasIdx = 0;
            for (int i = 0; i < entries.size() && aliasIdx < allAliases.size(); i++) {
                Watchlist entry = entries.get(i);
                if (entry.getName() != null && !entry.getName().isBlank()) {
                    WatchlistAlias alias = allAliases.get(aliasIdx++);
                    alias.setWatchlistId(entry.getId());
                    watchlistAliasMapper.insert(alias);
                }
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CSV文件读取失败", e);
            throw new BusinessException("CSV文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 从JSON文件导入制裁名单
     * JSON格式：数组，每个元素包含 watchlist 基本信息及 aliases、identities 子数组
     * 示例：[{"externalId":"...","name":"...","aliases":["别名1","别名2"],"identities":[{"idType":"PASSPORT","idNumber":"..."}]}]
     *
     * @param inputStream JSON文件输入流
     * @param sourceId    来源ID（关联t_watchlist_source）
     */
    @Transactional(rollbackFor = Exception.class)
    public void importFromJson(InputStream inputStream, Long sourceId) {
        log.info("开始从JSON导入制裁名单，sourceId={}", sourceId);

        try {
            // 读取JSON为通用Map列表
            List<Map<String, Object>> dataList = objectMapper.readValue(
                    inputStream, new TypeReference<>() {}
            );

            int importedCount = 0;

            for (Map<String, Object> data : dataList) {
                try {
                    // 构建制裁名单条目
                    Watchlist entry = new Watchlist();
                    entry.setSourceId(sourceId);
                    entry.setExternalId(getString(data, "externalId"));
                    entry.setEntityType(getString(data, "entityType"));
                    entry.setName(getString(data, "name"));
                    entry.setNameEn(getString(data, "nameEn"));
                    entry.setGender(getString(data, "gender"));
                    entry.setNationality(getString(data, "nationality"));
                    entry.setDateOfBirth(getString(data, "dateOfBirth"));
                    entry.setPlaceOfBirth(getString(data, "placeOfBirth"));
                    entry.setRemarks(getString(data, "remarks"));
                    entry.setStatus("ACTIVE");
                    watchlistMapper.insert(entry);

                    // 解析并保存别名
                    Object aliasesObj = data.get("aliases");
                    if (aliasesObj instanceof List<?> aliasesList) {
                        for (Object aliasName : aliasesList) {
                            if (aliasName instanceof String name && !name.isBlank()) {
                                WatchlistAlias alias = new WatchlistAlias();
                                alias.setWatchlistId(entry.getId());
                                alias.setAliasName(name);
                                alias.setAliasType("ALIAS");
                                alias.setLanguage(getString(data, "language"));
                                alias.setCreatedTime(LocalDateTime.now());
                                watchlistAliasMapper.insert(alias);
                            }
                        }
                    }

                    // 解析并保存证件信息
                    Object identitiesObj = data.get("identities");
                    if (identitiesObj instanceof List<?> identitiesList) {
                        for (Object identityObj : identitiesList) {
                            if (identityObj instanceof Map<?, ?> identityMap) {
                                WatchlistIdentity identity = new WatchlistIdentity();
                                identity.setWatchlistId(entry.getId());
                                identity.setIdType(getString(identityMap, "idType"));
                                identity.setIdNumber(getString(identityMap, "idNumber"));
                                identity.setIssuingCountry(getString(identityMap, "issuingCountry"));
                                identity.setExpiryDate(getString(identityMap, "expiryDate"));
                                identity.setCreatedTime(LocalDateTime.now());
                                watchlistIdentityMapper.insert(identity);
                            }
                        }
                    }

                    importedCount++;
                } catch (Exception e) {
                    log.warn("JSON条目解析失败，跳过: externalId={}, error={}", data.get("externalId"), e.getMessage());
                }
            }

            log.info("JSON导入完成，共导入 {} 条制裁名单记录", importedCount);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("JSON文件解析失败", e);
            throw new BusinessException("JSON文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 简单CSV行解析（支持带引号的字段）
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());

        return fields.toArray(new String[0]);
    }

    /**
     * 安全获取字段值
     */
    private String getField(String[] fields, int index) {
        if (index < fields.length) {
            String val = fields[index];
            return (val == null || val.isBlank() || "null".equalsIgnoreCase(val)) ? null : val.trim();
        }
        return null;
    }

    /**
     * 从Map中安全获取字符串值
     */
    private String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        String str = val.toString().trim();
        return str.isBlank() || "null".equalsIgnoreCase(str) ? null : str;
    }
}

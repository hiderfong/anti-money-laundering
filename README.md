# Insurance AML System (保险公司反洗钱系统)

保险公司内部反洗钱管理系统的项目仓库。

## 项目目录结构

```
Anti-money-Laundering/
├── README.md                        # 项目说明
├── pom.xml                          # Maven 项目配置（待创建）
├── docs/                            # 文档目录
│   ├── regulations/                 # 法规参考文件
│   │   └── 反洗钱法规参考-产品设计版.md
│   ├── use-cases/                   # 用例文档
│   ├── meeting-notes/               # 会议记录
│   └── api/                         # API 文档
├── src/
│   ├── main/
│   │   ├── java/com/insurance/aml/
│   │   │   ├── config/              # 配置类（Spring、安全、数据库）
│   │   │   ├── controller/          # REST 控制器
│   │   │   ├── service/
│   │   │   │   ├── core/            # 核心业务逻辑
│   │   │   │   ├── screening/       # 制裁名单筛查
│   │   │   │   ├── reporting/       # 大额/可疑交易报告
│   │   │   │   ├── kyc/             # 客户身份识别
│   │   │   │   └── risk/            # 风险评估与分类
│   │   │   ├── model/
│   │   │   │   ├── entity/          # JPA 实体
│   │   │   │   ├── dto/             # 数据传输对象
│   │   │   │   └── enum/            # 枚举定义
│   │   │   ├── repository/          # 数据访问层
│   │   │   ├── util/                # 工具类
│   │   │   └── exception/           # 自定义异常
│   │   └── resources/
│   │       ├── mapper/              # MyBatis 映射文件（如使用）
│   │       ├── i18n/                # 国际化资源
│   │       └── templates/           # 模板文件
│   └── test/java/com/insurance/aml/
│       ├── service/                 # 服务层单元测试
│       ├── controller/              # 控制器测试
│       └── integration/             # 集成测试
├── sql/
│   ├── migration/                   # 数据库版本迁移脚本
│   └── scripts/                     # 数据库维护脚本
├── scripts/                         # 部署和运维脚本
├── config/                          # 环境配置文件
├── docker/                          # Docker 相关文件
└── .github/workflows/               # CI/CD 配置
```

## 核心功能模块

| 模块 | 说明 | 目录 |
|------|------|------|
| KYC/CDD | 客户身份识别与尽职调查 | service/kyc/ |
| 制裁名单筛查 | OFAC/UN/公安部等名单匹配 | service/screening/ |
| 大额交易监测 | 自动识别达到报告门槛的交易 | service/reporting/ |
| 可疑交易监测 | 基于规则和模型识别可疑行为 | service/reporting/ |
| 风险评估 | 客户风险等级划分与动态调整 | service/risk/ |
| 监管报送 | 对接人民银行反洗钱监测分析系统 | service/reporting/ |

## 技术栈（规划）

- Java 17+
- Spring Boot 3.x
- Spring Security + JWT
- MyBatis-Plus / JPA
- MySQL 8.0
- Redis（缓存 & 名单匹配加速）
- Elasticsearch（交易检索）
- RabbitMQ / Kafka（异步报告处理）
- Docker + K8s

## 法规参考

详见 [docs/regulations/反洗钱法规参考-产品设计版.md](docs/regulations/反洗钱法规参考-产品设计版.md)

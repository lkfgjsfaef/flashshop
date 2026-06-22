# FlashShop — 高并发优惠券秒杀平台

本地生活与商户运营综合平台，覆盖商铺浏览、达人探店、优惠券秒杀、好友关注、签到统计等核心业务场景。针对秒杀峰值流量、缓存异常、数据一致性等痛点进行全链路架构优化与闭环治理。

## 技术栈

| 领域 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.4, Java 17 |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8.0 + ShardingSphere 5.3 (2库×2表) |
| 缓存 | Redis 7 + Caffeine 本地缓存 + Redisson 3.52 |
| 消息队列 | Kafka 3.4 |
| 认证 | Sa-Token 1.43 |
| 监控 | Prometheus + Micrometer |
| 前端 | Vue 3 |

## 核心特性

- **全链路流控** — 令牌前置授权 + 令牌桶/滑动窗口限流，将流量控制前置到接口层
- **多层缓存体系** — 本地缓存 → Redis → 布隆过滤器 → 空值缓存 → DB，四级防线应对穿透/击穿/雪崩
- **秒杀一致性闭环** — Lua 原子扣减 → Kafka 异步下单 → 对账补偿，保证零超卖
- **MQ 可靠性保障** — 发布确认、重试退避、死信队列、延迟丢弃、消费幂等与去重
- **分布式锁框架** — 注解驱动的 Redisson 分布式锁（读锁/写锁/公平锁/可重入锁）
- **分库分表** — ShardingSphere 2库2表，支持千万级数据水平扩展
- **可观测性** — Prometheus 指标采集 + 核心链路埋点

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- Docker & Docker Compose（用于启动基础设施）

### 1. 启动基础设施

```bash
cd docker
docker-compose up -d
```

这将启动 MySQL 8.0 (端口 3306)、Redis 7 (端口 6379)、Kafka 3.4 (端口 9092)。

### 2. 初始化数据库

```bash
# 先创建数据库
mysql -h 127.0.0.1 -u root -p < sql/1_create_database.sql

# 导入表结构
mysql -h 127.0.0.1 -u root -p < sql/hmdp_0.sql
mysql -h 127.0.0.1 -u root -p < sql/hmdp_1.sql
```

### 3. 启动后端

```bash
mvn clean compile
mvn -pl hmdp-core-service spring-boot:run
```

应用运行在 `http://localhost:8085`。

### 4. 启动前端

```bash
cd hmdp-vue3
npm install
npm run dev
```

## 模块结构

```
hmdp-plus
├── hmdp-common              # 公共模块：枚举、异常、常量、工具类
├── hmdp-core-service        # 核心服务：Controller、Service、Mapper、Kafka、Lua 脚本
├── hmdp-parameter           # 数据传输对象（DTO/VO）
├── hmdp-redisson-framework  # Redisson 封装：分布式锁、布隆过滤器、延迟队列、防重执行
├── hmdp-redis-tool-framework # Redis 工具：缓存抽象、限流框架
├── hmdp-mq-framework        # MQ 框架：Kafka 生产者/消费者抽象
├── hmdp-id-generator-framework # 雪花算法全局 ID 生成器
├── hmdp-sharding            # ShardingSphere 分库分表配置
└── hmdp-vue3                # 前端 (Vue 3)
```

## 架构亮点

### 秒杀流程

```
用户请求 → 令牌桶限流 → 资格令牌校验 → Lua 原子扣减(Redis)
                                              ↓
                               Kafka 异步消息 → 消费者创建订单(DB)
                                              ↓
                              对账任务定时扫描 → 库存补偿/告警
```

### 缓存策略

```
查询请求 → 本地缓存(Caffeine) → Redis(含空值缓存) → 布隆过滤器 → DB
              ↓ 未命中              ↓ 未命中          ↓ 不存在
         异步刷新                 双检加锁回源      直接返回空
```

## License

Apache License 2.0 — 详见 [LICENSE](LICENSE)

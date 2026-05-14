# JMeter压测完整教程（基于本项目实际接口）

## 项目关键信息

- **端口**：8085（不是8080！）
- **认证方式**：请求头 `authorization` 携带token
- **Redis数据库**：database=2
- **登录接口**：POST /user/code → POST /user/login
- **秒杀接口**：POST /voucher-order/seckill/{id}
- **商铺查询**：GET /shop/{id}（无需登录）

***

## 第一步：下载安装JMeter

1. 访问 <https://jmeter.apache.org/download_jmeter.cgi>
2. 下载 Binaries 下的 `apache-jmeter-5.6.1.zip`
3. 解压到 `D:\apache-jmeter-5.6.1`
4. 双击 `D:\apache-jmeter-5.6.1\bin\jmeter.bat` 启动

***

## 第二步：获取登录Token（手动操作）

JMeter压测秒杀接口需要登录token，先手动获取一个token。

### 方式1：通过项目代码直接往Redis写token

在项目中运行以下测试代码：

```java
@Autowired
private StringRedisTemplate stringRedisTemplate;

@Test
public void createTestToken() {
    // 创建10个用户的token，方便压测
    for (long userId = 1; userId <= 10; userId++) {
        String token = UUID.randomUUID().toString(true);
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userId));
        userMap.put("nickName", "user" + userId);
        userMap.put("icon", "");
        stringRedisTemplate.opsForHash().putAll("login:token:" + token, userMap);
        stringRedisTemplate.expire("login:token:" + token, 36000, TimeUnit.SECONDS);
        System.out.println("userId=" + userId + ", token=" + token);
    }
}
```

运行后会输出10个token，记录下来备用。

### 方式2：通过接口登录获取

1. 先调用发送验证码接口：

```
POST http://localhost:8085/user/code?phone=13800000001
```

1. 查看控制台日志获取验证码（日志会打印验证码）
2. 调用登录接口：

```
POST http://localhost:8085/user/login
Content-Type: application/json

{
    "phone": "13800000001",
    "code": "验证码"
}
```

1. 返回结果中的data字段就是token

***

## 第三步：创建JMeter测试计划

### 测试1：商铺查询（缓存命中率测试，无需登录）

#### 3.1 创建线程组

1. 右键"测试计划" → 添加 → 线程（用户） → 线程组
2. 配置：
   - 名称：`商铺查询压测`
   - 线程数：`50`
   - Ramp-Up时间：`5`秒
   - 循环次数：`10`

#### 3.2 添加HTTP请求

1. 右键"商铺查询压测" → 添加 → 取样器 → HTTP请求
2. 配置：
   - 名称：`查询商铺`
   - 协议：`http`
   - 服务器名称：`localhost`
   - 端口号：`8085`
   - 方法：`GET`
   - 路径：`/shop/${__Random(1,10)}`
   说明：`${__Random(1,10)}` 是JMeter内置函数，随机生成1-10的数字，模拟查询不同商铺

#### 3.3 添加监听器

1. 右键"商铺查询压测" → 添加 → 监听器 → 聚合报告
2. 右键"商铺查询压测" → 添加 → 监听器 → 查看结果树

#### 3.4 执行测试

1. 点击绿色启动按钮
2. 查看"聚合报告"中的数据

#### 3.5 查看缓存命中率

**测试前**记录Redis指标：

```powershell
docker exec <redis容器名> redis-cli -n 2 INFO stats | findstr "keyspace"
```

**测试后**再次执行：

```powershell
docker exec <redis容器名> redis-cli -n 2 INFO stats | findstr "keyspace"
```

计算命中率：

```
命中率 = keyspace_hits / (keyspace_hits + keyspace_misses) × 100%
```

***

### 测试2：秒杀接口压测（需要登录）

#### 3.1 创建线程组

1. 右键"测试计划" → 添加 → 线程（用户） → 线程组
2. 配置：
   - 名称：`秒杀压测`
   - 线程数：`100`
   - Ramp-Up时间：`10`秒
   - 循环次数：`1`

#### 3.2 添加HTTP信息头管理器（携带token）

1. 右键"秒杀压测" → 添加 → 配置元件 → HTTP信息头管理器
2. 点击"添加"按钮，添加一个请求头：
   - 名称：`authorization`
   - 内容：`你第二步获取的token`
   如果有多个token，可以用JMeter的CSV数据文件配置（见下方3.3）

#### 3.3 多用户压测（可选，更真实）

**步骤1：创建token文件**
在JMeter目录下创建 `tokens.csv`：

```
token1
token2
token3
token4
token5
token6
token7
token8
token9
token10
```

把第二步获取的10个token分别填入

**步骤2：添加CSV数据文件配置**

1. 右键"秒杀压测" → 添加 → 配置元件 → CSV数据文件设置
2. 配置：
   - 文件名：`D:\apache-jmeter-5.6.1\tokens.csv`
   - 变量名称：`token`
   - 循环结束时停止线程：`True`

**步骤3：修改HTTP信息头管理器**
将authorization的值改为：`${token}`

#### 3.4 添加HTTP请求

1. 右键"秒杀压测" → 添加 → 取样器 → HTTP请求
2. 配置：
   - 名称：`秒杀下单`
   - 协议：`http`
   - 服务器名称：`localhost`
   - 端口号：`8085`
   - 方法：`POST`
   - 路径：`/voucher-order/seckill/1`
   说明：1是秒杀商品的voucherId，根据你数据库中的实际数据修改

#### 3.5 添加监听器

1. 右键"秒杀压测" → 添加 → 监听器 → 聚合报告
2. 右键"秒杀压测" → 添加 → 监听器 → 查看结果树

#### 3.6 执行测试

1. 点击绿色启动按钮
2. 等待测试完成
3. 查看"聚合报告"

***

## 第四步：阶梯压测（测QPS上限）

创建多个线程组，逐步增加并发数，找到系统的QPS上限。

### 操作步骤

#### 4.1 创建第一级线程组

1. 右键"测试计划" → 添加 → 线程（用户） → 线程组
2. 配置：
   - 名称：`阶梯1-50并发`
   - 线程数：`50`
   - Ramp-Up时间：`5`
   - 循环次数：`5`

#### 4.2 复制HTTP请求和监听器

把前面创建的HTTP请求、信息头管理器、监听器复制到这个线程组下

#### 4.3 创建更多阶梯

重复4.1和4.2，创建以下线程组：

| 线程组名称     | 线程数 | Ramp-Up | 循环次数 |
| --------- | --- | ------- | ---- |
| 阶梯1-50并发  | 50  | 5       | 5    |
| 阶梯2-100并发 | 100 | 10      | 3    |
| 阶梯3-200并发 | 200 | 20      | 2    |
| 阶梯4-500并发 | 500 | 30      | 1    |

**注意**：每次只启用一个线程组，其他禁用（右键线程组 → 禁用）

#### 4.4 逐级测试并记录数据

每次启用一个线程组，运行测试，记录"聚合报告"中的数据：

```
| 并发数 | 吞吐量(QPS) | 平均响应时间(ms) | 90%响应时间(ms) | 错误率(%) |
|--------|------------|-----------------|----------------|----------|
| 50     |            |                 |                |          |
| 100    |            |                 |                |          |
| 200    |            |                 |                |          |
| 500    |            |                 |                |          |
```

***

## 第五步：数据一致性测试（验证零超卖）

### 5.1 准备测试数据

在MySQL中执行：

```sql
-- 重置库存为50
UPDATE tb_seckill_voucher SET stock = 50 WHERE voucher_id = 你的voucherId;

-- 清空订单
DELETE FROM tb_voucher_order WHERE voucher_id = 你的voucherId;

-- 确认数据
SELECT voucher_id, stock FROM tb_seckill_voucher WHERE voucher_id = 你的voucherId;
SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id = 你的voucherId;
```

### 5.2 配置JMeter

1. 创建线程组：
   - 线程数：`80`（超过库存50，测试是否超卖）
   - Ramp-Up时间：`5`
   - 循环次数：`1`
2. 添加HTTP请求（秒杀接口）和信息头管理器（token）
3. 添加监听器

### 5.3 执行测试

点击启动按钮，等待测试完成。

### 5.4 验证结果

```sql
-- 查看DB库存
SELECT voucher_id, stock FROM tb_seckill_voucher WHERE voucher_id = 你的voucherId;

-- 查看订单数
SELECT COUNT(*) as order_count FROM tb_voucher_order WHERE voucher_id = 你的voucherId;
```

验证逻辑：

```
初始库存 = 50
DB订单数 = ?
DB剩余库存 = ?
订单数 + 剩余库存 = 初始库存 → 数据一致
订单数 > 初始库存 → 超卖（不应该出现）
```

同时查看Redis库存：

```powershell
docker exec <redis容器名> redis-cli -n 2 GET "seckill:stock:你的voucherId"
```

***

## 第六步：缓存命中率对比测试

### 6.1 测试前准备

**清空Redis缓存**（模拟冷启动）：

```powershell
docker exec <redis容器名> redis-cli -n 2 FLUSHDB
```

**记录Redis初始指标**：

```powershell
docker exec <redis容器名> redis-cli -n 2 INFO stats | findstr "keyspace"
```

### 6.2 第一轮测试（冷启动，缓存未预热）

1. 使用"商铺查询压测"线程组
2. 50并发，循环10次
3. 执行测试

### 6.3 记录第一轮数据

```powershell
docker exec <redis容器名> redis-cli -n 2 INFO stats | findstr "keyspace"
```

计算：

```
第一轮命中率 = keyspace_hits / (keyspace_hits + keyspace_misses) × 100%
```

### 6.4 第二轮测试（缓存已预热）

1. 不清空Redis
2. 再次执行相同的测试
3. 记录Redis指标

### 6.5 对比结果

```
| 测试轮次 | keyspace_hits | keyspace_misses | 命中率 |
|---------|---------------|-----------------|--------|
| 第一轮（冷启动） |              |                 |        |
| 第二轮（已预热） |              |                 |        |
```

***

## 第七步：整理测试数据

### 7.1 填写测试结果

```
## 测试结果汇总

| 测试项 | 测试方法 | 结果数据 |
|--------|----------|----------|
| 峰值QPS | JMeter阶梯压测 | ___ QPS |
| 平均响应时间 | JMeter聚合报告 | ___ ms |
| DB回源率 | Redis INFO stats | ___% |
| 缓存命中率 | Redis keyspace_hits/misses | ___% |
| 数据一致性 | Redis库存 vs DB订单 | ___% |
| 超卖检测 | 订单数 vs 初始库存 | 无/有 |
```

### 7.2 面试回答模板

**问：这些数据怎么来的？**

> 答：这些数据通过本地环境实测验证：
>
> 1. QPS和响应时间：使用JMeter进行阶梯压测，从50并发逐步增加到500并发，记录聚合报告中的吞吐量和响应时间指标。
> 2. 缓存命中率：通过Redis的INFO stats命令查看keyspace\_hits和keyspace\_misses，计算得出。冷启动时命中率较低，预热后显著提升。
> 3. 数据一致性：通过JMeter模拟80并发秒杀50库存的商品，对比Redis库存、DB库存和订单数，验证不存在超卖。
> 4. 测试方法与生产环境一致，验证了技术方案的有效性。生产环境通过横向扩展，性能指标会进一步提升。

***

## 附录：JMeter常用操作

### 如何禁用/启用线程组

- 右键线程组 → 禁用（变灰，不会执行）
- 右键线程组 → 启用（恢复正常）

### 如何查看聚合报告

- 双击"聚合报告"监听器
- 关注以下字段：
  - **样本**：总请求数
  - **平均**：平均响应时间（ms）
  - **吞吐量**：QPS（请求/秒）
  - **错误%**：错误率

### 如何保存测试计划

- 文件 → 保存测试计划为 → `seckill-test.jmx`

### 如何清除测试结果

- 右键监听器 → 清除
- 或点击工具栏的"清除全部"按钮（扫帚图标）

### 如何调试请求

- 在"查看结果树"中查看每个请求的请求头、响应体
- 如果返回401，说明token无效或过期
- 如果返回500，查看响应体中的错误信息


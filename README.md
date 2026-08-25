# 潮人探店后端

一个基于 Spring Boot 的本地生活点评服务后端，围绕用户登录、商铺查询、达人探店、关注关系和优惠券秒杀等场景实现。项目使用 Redis 处理缓存、登录态和秒杀资格校验，并通过 Kafka 异步完成秒杀订单落库。

## 主要功能

- 手机号验证码登录与 Redis Token 会话
- 商铺类型、商铺详情查询与缓存
- 探店笔记发布、点赞、评论和滚动分页
- 用户关注、共同关注与关注动态
- 优惠券查询和限时秒杀
- Lua 原子校验库存与一人一单
- Kafka 异步订单处理及失败补偿
- 阿里云 OSS 图片上传

## 技术栈

- Java 8、Spring Boot 2.3.12
- MyBatis-Plus、MySQL
- Redis、Redisson、Lua
- Apache Kafka
- Maven、Lombok、Hutool
- 阿里云 OSS

## 运行环境

请先安装并启动以下服务：

- JDK 8+
- Maven 3.6+
- MySQL 5.7+
- Redis 6+
- Kafka

## 本地启动

1. 创建数据库并导入初始化脚本：

   ```bash
   mysql -u root -p < src/main/resources/db/trendspot.sql
   ```

2. 设置运行所需的环境变量：

   ```bash
   export MYSQL_USERNAME=root
   export MYSQL_PASSWORD='你的数据库密码'
   export REDIS_PASSWORD='你的 Redis 密码（无密码时留空）'

   # 使用图片上传功能时再配置
   export ALIYUN_OSS_ENDPOINT='oss-cn-beijing.aliyuncs.com'
   export ALIYUN_OSS_ACCESS_KEY_ID='你的 AccessKey ID'
   export ALIYUN_OSS_ACCESS_KEY_SECRET='你的 AccessKey Secret'
   export ALIYUN_OSS_BUCKET_NAME='你的 Bucket 名称'
   ```

3. 确认本地服务地址与 `src/main/resources/application.yaml` 一致：

   - MySQL：`127.0.0.1:3306`
   - Redis：`localhost:6379`
   - Kafka：`localhost:9092`

4. 构建并启动：

   ```bash
   mvn clean package
   mvn spring-boot:run
   ```

服务默认监听 `http://localhost:8082`。

## 项目结构

```text
src/main/java/com/trendspot
├── config       # Web、MyBatis、Redis、Kafka、OSS 配置
├── consumer     # Kafka 秒杀订单消费者
├── controller   # HTTP 接口
├── dto          # 请求与响应对象
├── entity       # 数据库实体
├── mapper       # MyBatis-Plus Mapper
├── service      # 业务接口及实现
└── utils        # 缓存、锁、Token、ID 生成等工具

src/main/resources
├── db/trendspot.sql  # 数据库初始化脚本
├── mapper       # MyBatis XML
└── *.lua        # 秒杀、解锁与回滚脚本
```

## 配置安全

数据库密码和阿里云 OSS 凭据均通过环境变量注入，请勿将真实密钥提交到 Git 仓库。生产环境建议使用专用的密钥管理服务，并为凭据设置最小权限。

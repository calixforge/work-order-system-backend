# 智能工单系统

基于 Spring Boot 3 + MyBatis-Plus + Redis 的企业内部工单流转系统，覆盖提单、审核、派单、处理、验收、用户管理、角色分配和部门管理等后台业务场景。

项目围绕工单状态流转、角色权限控制、登录态管理和业务留痕，模拟企业内部协作流程中的真实后端设计。

## 项目特点

- 工单全流程流转：草稿、待审核、待派单、已接单、已完成、已关闭、已取消
- 多角色权限模型：提单人、审核员、派单人、接单人、管理员
- 用户多角色并集权限，支持一人多职
- JWT + Redis 登录态控制，支持单用户单 token 和滑动过期
- Redis 缓存角色权限和部门名称，减少高频查询
- 工单流转日志完整留痕，详情页可查看操作轨迹
- 用户停用不删除，保证历史工单仍能展示人员信息
- 部门删除前校验历史引用，避免数据断链
- Knife4j 自动生成接口文档

## 技术栈

| 技术 | 说明 |
| --- | --- |
| Spring Boot 3.3.5 | 后端应用框架 |
| Spring MVC | REST API |
| MyBatis-Plus | ORM 与分页 |
| MySQL | 业务数据存储 |
| Redis | 登录态与缓存 |
| JWT | token 生成与解析 |
| BCrypt | 密码加密 |
| Knife4j / OpenAPI 3 | 接口文档 |
| Lombok | 简化 Java 代码 |

## 业务流程

工单状态机流转如下（状态机定义见 `WorkorderServiceImpl` 的 `TRANSITIONS`）：

![工单状态机流转图](docs/state-machine.svg)

## 角色说明

| 角色 | 权限 |
| --- | --- |
| `SUBMITTER` | 创建工单、提交、撤回、取消、验收 |
| `REVIEWER` | 审核本部门工单 |
| `DISPATCHER` | 查看待派单工单并分配接单人 |
| `HANDLER` | 查看自己负责的工单、转派、完成 |
| `ADMIN` | 用户、角色、部门管理，查看全部工单 |

## 功能模块

### 登录认证

- 用户登录、退出登录
- JWT 解析当前用户
- Redis 保存当前有效 token
- 重复登录覆盖旧 token
- 请求拦截器统一认证

### 用户与角色

- 管理员创建、编辑、停用、启用用户
- 管理员重置用户密码
- 用户修改自己的密码
- 查询用户列表与用户详情
- 分配和剥夺角色
- 剥夺角色前检查未结束工单责任

### 部门管理

- 创建、编辑、删除、查询部门
- 部门名称唯一校验
- 删除前检查用户和工单引用
- 工单列表中的部门名称使用 Redis 缓存

### 工单管理

- 创建草稿或直接提交
- 查询我创建的工单
- 查询我负责的工单
- 查询本部门待审核工单
- 查询待派单工单
- 管理员查询全部工单
- 工单详情包含基础信息和流转日志

## 项目结构

```text
src/main/java/com/wos
├── common          # 统一响应、分页、权限检查、用户上下文
├── config          # 拦截器、Knife4j、MyBatis-Plus 配置
├── controller      # 接口层
├── domain
│   ├── dto         # 请求参数
│   ├── pojo        # 数据库实体
│   └── vo          # 响应对象
├── exception       # 业务异常与全局异常处理
├── mapper          # MyBatis-Plus Mapper
├── service         # 业务接口
├── service/impl    # 业务实现
└── util            # JWT、密码工具
```

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 5.7+
- Redis 6+

### 初始化数据库

创建数据库：

```sql
CREATE DATABASE work_order_system DEFAULT CHARACTER SET utf8mb4;
```

依次执行：

```text
sql/work_order_system.sql
sql/init_data.sql
```

初始化账号：

```text
用户名：admin
密码：admin123
```

### 本地配置

项目默认启用 `local` profile，请创建：

```text
src/main/resources/application-local.yml
```

示例配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/work_order_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
      database: 0

mybatis-plus:
  mapper-locations: classpath*:com/wos/mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

jwt:
  secret: replace-with-a-long-random-secret
  ttl: 604800000
```

### 启动后端

```bash
./mvnw spring-boot:run
```

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

## Docker 部署

使用 Docker Compose 一键编排 nginx + app + MySQL + Redis:Nginx 发前端静态页面并把 `/api` 反代到后端,后端连 MySQL / Redis,整套从浏览器一个入口访问。

> 前端为独立仓库:<https://github.com/xc605/work-order-system-web>

### 环境要求

- Docker 20+ 与 Docker Compose v2(`docker compose version` 可查)
- 国内服务器建议先配置镜像加速器(`/etc/docker/daemon.json` 的 `registry-mirrors`),否则拉取基础镜像较慢

### 1. 打包后端 jar

```bash
./mvnw clean package -DskipTests
```

产物:`target/work-order-system-0.0.1-SNAPSHOT.jar`。

### 2. 打包前端 dist

在前端仓库构建静态资源:

```bash
git clone https://github.com/xc605/work-order-system-web.git
cd work-order-system-web
pnpm install
pnpm build
```

产物:`dist/`(纯静态文件)。

### 3. 准备部署目录

新建一个目录,放入以下文件:

```text
deploy/
├── Dockerfile                 # 从后端项目根目录拷贝
├── docker-compose.yml         # 从后端项目根目录拷贝
├── app.jar                    # 后端 jar,重命名为 app.jar
├── .env                       # 由 .env.example 复制并填写真实值
├── sql/
│   ├── work_order_system.sql  # 从后端 sql/ 拷贝
│   └── init_data.sql
└── nginx/
    ├── nginx.conf             # 从后端 nginx/ 拷贝
    └── dist/                  # 前端 pnpm build 出的 dist 内容
```

> `application-docker.yml` 已随 jar 打包,无需单独拷贝。

### 4. 配置 .env

复制 `.env.example` 为 `.env` 并填写真实值(密码避免使用 `$ # " ' \` 反引号、空格等字符):

```text
MYSQL_PASSWORD=你的MySQL密码
REDIS_PASSWORD=你的Redis密码
JWT_SECRET=一段长随机串(至少 32 位)
```

### 5. 启动

```bash
docker compose up -d --build
```

首次启动会:构建 app 镜像 → 启动 MySQL(自动建库并按 `01-schema → 02-data` 顺序执行 `sql/` 初始化脚本)→ 启动 Redis → 启动 app → 启动 nginx。

### 6. 验证

```bash
docker compose ps            # 四个容器:nginx / app / mysql / redis(mysql 显示 healthy)
docker compose logs -f app   # 查看 app 启动日志
```

- 完整系统:浏览器访问 `http://<服务器IP>`(80 端口),用 `admin / admin123` 登录。
- 后端接口文档(调试):`http://<服务器IP>:8080/doc.html`。

### 说明

- Nginx 发前端 `dist`(SPA 路由回退),并把 `/api` 反代到 `app:8080`(去掉 `/api` 前缀,等价于开发期 vite proxy)。
- 容器内 app 通过服务名连接:`mysql:3306`、`redis:6379`(见 `application-docker.yml`,由环境变量 `SPRING_PROFILES_ACTIVE=docker` 激活,覆盖默认的 local profile)。
- MySQL 数据用命名卷 `mysql-data` 持久化;初始化脚本仅在数据卷为空(首次启动)时执行,重启不会重复初始化。

## 接口文档

项目集成 Knife4j，启动后可在浏览器访问：

```text
http://localhost:8080/doc.html
```

除登录接口、接口文档、静态头像资源外，其他接口都需要携带：

```http
Authorization: Bearer <token>
```

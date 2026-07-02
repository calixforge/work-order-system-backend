# 智能工单系统

基于 Spring Boot 3 + MyBatis-Plus + Redis + Spring AI 的企业内部智能工单系统，覆盖提单、审核、派单、处理、验收、用户管理、角色分配、部门管理和 RAG 知识库问答等后台业务场景。

项目围绕工单状态流转、角色权限控制、登录态管理、业务留痕和 AI 辅助问答，模拟企业内部协作流程中的真实后端设计。

## 项目特点

- 工单全流程流转：草稿、待审核、待派单、已接单、已完成、已关闭、已取消
- 多角色权限模型：提单人、审核员、派单人、接单人、管理员
- 用户多角色并集权限，支持一人多职
- JWT + Redis 登录态控制，支持单用户单 token 和滑动过期
- Redis 缓存角色权限和部门名称，减少高频查询
- 工单流转日志完整留痕，详情页可查看操作轨迹
- 用户停用不删除，保证历史工单仍能展示人员信息
- 部门删除前校验历史引用，避免数据断链
- Spring AI + Qdrant 实现 RAG 知识库问答，支持 Markdown 文档加载、结构化切分、向量检索、相似度阈值拦截和引用溯源
- Docker Compose 编排 app + MySQL + Redis + Qdrant + Nginx，向量数据使用 Qdrant 卷持久化
- Knife4j 自动生成接口文档

## 技术栈

| 技术 | 说明 |
| --- | --- |
| Spring Boot 3.3.5 | 后端应用框架 |
| Spring MVC | REST API |
| MyBatis-Plus | ORM 与分页 |
| MySQL | 业务数据存储 |
| Redis | 登录态与缓存 |
| Spring AI | 大模型调用、Embedding 与 RAG 管线 |
| Qdrant | 向量数据持久化与相似度检索 |
| OpenAI-compatible API | 对接云端 Chat / Embedding 模型 |
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

### RAG 智能问答

- 启动时加载 `resources/kb/*.md` 知识库文档
- Markdown 按标题结构切分，超长段落再做 token 兜底切分
- 使用 Embedding 模型向量化文档并写入 Qdrant
- 用户提问时按语义相似度检索 TopK 资料
- 相似度低于阈值时直接返回暂无资料，减少幻觉和无效模型调用
- 回答尾部由代码拼接引用来源，便于核对出处

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
- Qdrant 1.x+
- 可用的 OpenAI-compatible Chat API 与 Embedding API

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

可复制 [application-local.example.yml](src/main/resources/application-local.example.yml) 为 `application-local.yml`，并按本机 MySQL / Redis / Qdrant / AI API 情况填写真实值。


### 启动后端

```bash
./mvnw spring-boot:run
```

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

## Docker 部署

使用 Docker Compose 一键编排 nginx + app + MySQL + Redis + Qdrant:Nginx 发前端静态页面并把 `/api` 反代到后端,后端连 MySQL / Redis / Qdrant,整套从浏览器一个入口访问。

> 前端为独立仓库:<https://github.com/xc605/work-order-system-web>

详细部署步骤、目录结构、`.env` 配置、Qdrant 控制台和验证方式见 [Docker 部署指南](docs/docker-deploy.md)。

Docker 部署完成后，可访问 Qdrant 控制台:`http://<服务器IP>:6333/dashboard`。

## 接口文档

项目集成 Knife4j，启动后可在浏览器访问：

```text
http://localhost:8080/doc.html
```

除登录接口、接口文档、静态头像资源外，其他接口都需要携带：

```http
Authorization: Bearer <token>
```

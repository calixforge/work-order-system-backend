# Docker 部署指南

使用 Docker Compose 一键编排 nginx + app + MySQL + Redis + Qdrant:Nginx 发前端静态页面并把 `/api` 反代到后端,后端连 MySQL / Redis / Qdrant(向量库),整套从浏览器一个入口访问。

> 前端为独立仓库:<https://github.com/calixforge/work-order-system-web>

## 环境要求

- Docker 20+ 与 Docker Compose v2(`docker compose version` 可查)
- 国内服务器建议先配置镜像加速器(`/etc/docker/daemon.json` 的 `registry-mirrors`),否则拉取基础镜像较慢

## 1. 打包后端 jar

```bash
./mvnw clean package -DskipTests
```

产物:`target/work-order-system-0.0.1-SNAPSHOT.jar`。

## 2. 打包前端 dist

在前端仓库构建静态资源:

```bash
git clone https://github.com/calixforge/work-order-system-web.git
cd work-order-system-web
pnpm install
pnpm build
```

产物:`dist/`(纯静态文件)。

## 3. 准备部署目录

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

## 4. 配置 .env

复制 `.env.example` 为 `.env` 并填写真实值(密码避免使用 `$ # " ' \` 反引号、空格等字符):

```text
MYSQL_PASSWORD=你的MySQL密码
REDIS_PASSWORD=你的Redis密码
JWT_SECRET=一段长随机串(至少 32 位)

# AI / RAG
AI_BASE_URL=chat 模型的 OpenAI 兼容端点
AI_API_KEY=chat 模型的 API Key
AI_CHAT_MODEL=chat 模型名
AI_EMBEDDING_API_KEY=SiliconFlow 的 API Key(embedding 用 BAAI/bge-m3)
```

## 5. 启动

```bash
docker compose up -d --build
```

首次启动会:构建 app 镜像 → 启动 MySQL(自动建库并按 `01-schema → 02-data` 顺序执行 `sql/` 初始化脚本)→ 启动 Redis → 启动 Qdrant → 等 MySQL / Qdrant 健康检查通过 → 启动 app(自动导入 `kb/` 知识库文档)→ 启动 nginx。

## 6. 验证

```bash
docker compose ps            # 五个容器:nginx / app / mysql / redis / qdrant(mysql/qdrant 显示 healthy)
docker compose logs -f app   # 查看 app 启动日志(可看到知识库"切出 N 块"的导入日志)
```

- 完整系统:浏览器访问 `http://<服务器IP>`(80 端口),用 `admin / admin123` 登录。
- 后端接口文档(调试):`http://<服务器IP>:8080/doc.html`。
- Qdrant 控制台(调试):`http://<服务器IP>:6333/dashboard`,可查看 `kb` collection 与向量点。

## 说明

- Nginx 发前端 `dist`(SPA 路由回退),并把 `/api` 反代到 `app:8080`(去掉 `/api` 前缀,等价于开发期 vite proxy)。
- 容器内 app 通过服务名连接:`mysql:3306`、`redis:6379`、`qdrant:6334`(gRPC 口;见 `application-docker.yml`,由环境变量 `SPRING_PROFILES_ACTIVE=docker` 激活,覆盖默认的 local profile)。
- MySQL 数据用命名卷 `mysql-data` 持久化;初始化脚本仅在数据卷为空(首次启动)时执行,重启不会重复初始化。
- Qdrant 向量数据用命名卷 `qdrant-data` 持久化;app 会等待 Qdrant 健康检查通过后再启动,避免向量库 Bean 初始化和 collection 创建时撞上 Qdrant 未就绪。
- 知识库文档在 app 启动时自动导入;导入失败仅记录日志、不阻断工单主业务启动(智能问答降级)。

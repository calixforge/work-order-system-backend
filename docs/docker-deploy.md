# Docker 部署指南

使用 Docker Compose 一键编排 nginx + app + MySQL + Redis。Nginx 提供前端静态页面，将 `/api` 转发到 Java 后端，并将 `/agent-api` 转发到独立部署的 Agent。

> 前端为独立仓库:<https://github.com/calixforge/work-order-system-web>
>
> Agent 为独立仓库:<https://github.com/calixforge/work-order-system-agent>

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
```

## 5. 启动

```bash
docker network inspect work-order-system-network >/dev/null 2>&1 || docker network create work-order-system-network
docker compose up -d --build
```

首次启动会:构建 app 镜像 → 启动 MySQL(自动建库并按 `01-schema → 02-data` 顺序执行 `sql/` 初始化脚本)→ 启动 Redis → 等 MySQL 健康检查通过 → 启动 app → 启动 nginx。

## 6. 验证

```bash
docker compose ps            # 四个容器:nginx / app / mysql / redis(mysql 显示 healthy)
docker compose logs -f app   # 查看 app 启动日志
```

- 完整系统:浏览器访问 `http://<服务器IP>`(80 端口),用 `admin / admin123` 登录。
- 后端接口文档(调试):`http://<服务器IP>:8080/doc.html`。

## 说明

- Nginx 发前端 `dist`(SPA 路由回退),把 `/api` 反代到 `app:8080`,把 `/agent-api` 反代到共享网络中的 `agent:8000`。
- 容器内 app 通过服务名连接:`mysql:3306`、`redis:6379`(见 `application-docker.yml`,由环境变量 `SPRING_PROFILES_ACTIVE=docker` 激活,覆盖默认的 local profile)。
- MySQL 数据用命名卷 `mysql-data` 持久化;初始化脚本仅在数据卷为空(首次启动)时执行,重启不会重复初始化。
- Agent 需要单独部署，并以 `agent` 网络别名加入外部网络 `work-order-system-network`。

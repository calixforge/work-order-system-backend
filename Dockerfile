# 用 JRE 17 基础镜像跑 jar(只含运行时,不含编译工具,镜像小)
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 容器时区设为东八区,避免 Java 取的时间差 8 小时
ENV TZ=Asia/Shanghai

# 把构建好的 jar 拷进镜像(部署时把 jar 改名成 app.jar 放在同目录)
COPY app.jar app.jar

# 应用端口(仅文档作用,真正映射在 compose 里)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

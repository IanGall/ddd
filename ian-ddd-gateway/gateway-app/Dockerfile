# 基础镜像：eclipse-temurin 官方 manifest list 同时提供 linux/amd64 与 linux/arm64（alpine 3.24 + JDK 21），
# 本文件不含任何架构相关指令，因此同一份 Dockerfile 可直接产出双架构镜像（构建方式见 README 的「容器化部署」）。
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY target/ian-ddd-gateway-1.0-SNAPSHOT.jar app.jar

# 时区与健康检查依赖
# alpine 默认既没有 tzdata（缺了它软链会指向不存在的文件、时间静默退回 UTC），也没有 curl；
# 两者都用 apk 装，别再写 apt-get（alpine 没有 apt）。
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata curl \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

# 容器内存感知的 JVM 参数，部署时可用 JAVA_OPTS 覆盖
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8092
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8092/actuator/health || exit 1
# exec 使 java（而非 busybox sh）成为 PID 1，正确接收 SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

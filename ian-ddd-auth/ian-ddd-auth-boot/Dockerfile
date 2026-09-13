# 基础镜像
FROM eclipse-temurin:21-jre

# 配置
ENV PARAMS=""

# 生产部署固定 prod Profile：中间件地址与凭证一律由部署环境注入（见 application-prod.yml）
ENV SPRING_PROFILES_ACTIVE=prod

# 时区（与网关保持一致，避免跨服务日志/时间口径不同）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加应用
COPY target/ian-ddd-auth-boot.jar /ian-ddd-auth-boot.jar

EXPOSE 8091

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /ian-ddd-auth-boot.jar $PARAMS"]

# 基础镜像
FROM eclipse-temurin:21-jre

# 配置
ENV PARAMS=""

# 时区
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加应用
ADD target/ian-frame-archetype-std-boot.jar /ian-frame-archetype-std-boot.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /ian-frame-archetype-std-boot.jar $PARAMS"]

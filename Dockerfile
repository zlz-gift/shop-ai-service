# ---------- 构建阶段 ----------
# 用带 Maven 的 JDK 21 镜像，避免"构建机必须装 Maven"的前提
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先把国内镜像配置塞进去：依赖下载快十倍，且不会因为 central 抽风构建失败
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 先只拷 pom 拉依赖，源码变了也能复用这一层缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 运行阶段 ----------
# 只要 JRE，镜像小一半；alpine 再小一圈
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 不用 root 跑应用
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/data && chown -R app:app /app

COPY --from=build /build/target/*.jar app.jar

USER app

EXPOSE 8081

# 容器里用百分比而不是固定 -Xmx，让 JVM 感知 cgroup 限制
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

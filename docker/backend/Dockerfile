# ========== 后端: Spring Boot 3.2 + JDK 17 ==========
# 可选: 多阶段用 maven 现场打包; 默认直接用本地 mvn package 产物以加速
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="pcba-erp"
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai" \
    APP_HOME=/opt/pcba-erp

WORKDIR $APP_HOME

# 启动脚本 + fatjar (build.context=.. 即 pcba-erp 根)
COPY ./backend/pcba-erp-admin/target/pcba-erp.jar $APP_HOME/app.jar

RUN set -eux \
 && apk add --no-cache curl tzdata \
 && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
 && echo $TZ > /etc/timezone \
 && addgroup -S spring && adduser -S spring -G spring \
 && chown -R spring:spring $APP_HOME

USER spring:spring
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --noproxy '*' -fsS http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT [ "sh", "-c", "exec java $JAVA_OPTS -jar $APP_HOME/app.jar" ]

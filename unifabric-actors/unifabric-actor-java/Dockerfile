FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 将已经构建好的 actor-java 可执行 JAR 拷贝进容器
# 需要先在宿主机执行：
#   mvn -pl iarnet-actor-java package
# 默认匹配 target 目录下的 iarnet-actor-java-*.jar
COPY target/iarnet-actor-java-*.jar app.jar

# 启动 Java Actor runtime
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


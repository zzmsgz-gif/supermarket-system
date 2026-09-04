# 多阶段构建：先用 Maven 编译，再跑精简 JRE 镜像。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
# 容器内存受限（如 Railway/Hobby 档）时可用 JAVA_OPTS 调堆大小，例如 -Xmx512m
ENV JAVA_OPTS="-Xmx512m -XX:+UseG1GC -XX:MaxMetaspaceSize=128m"
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

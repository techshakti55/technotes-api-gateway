# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21-jammy AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package && \
    cp target/technotes-api-gateway-*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --gid 10001 technotes && \
    useradd --uid 10001 --gid technotes --no-create-home --shell /usr/sbin/nologin technotes
COPY --from=build --chown=10001:10001 /tmp/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

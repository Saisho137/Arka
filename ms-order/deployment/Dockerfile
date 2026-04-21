# ── Stage 1: Build ──
FROM gradle:9.4-jdk21 AS builder
WORKDIR /myapp

# Copiar wrapper y configuración primero (mejor cache de capas)
COPY gradle/ gradle/
COPY gradlew gradlew
COPY *.gradle ./
COPY gradle.properties gradle.properties
COPY lombok.config lombok.config

# Copiar código fuente
COPY applications applications
COPY domain domain
COPY infrastructure infrastructure

RUN gradle build -x test --no-daemon \
    --no-configuration-cache \
    --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC"

# ── Stage 2: Run ──
FROM amazoncorretto:21-alpine
WORKDIR /myapprun
COPY --from=builder /myapp/applications/app-service/build/libs/*.jar ms-order.jar

RUN apk update && apk add --no-cache curl \
    && addgroup -S appgroup && adduser -S appuser -G appgroup

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"

USER appuser
ENTRYPOINT ["/bin/sh", "-c", "java $JAVA_OPTS -jar ms-order.jar"]

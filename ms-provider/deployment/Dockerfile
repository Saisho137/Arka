# ── Stage 1: Build ──
FROM gradle:9.4-jdk21 AS builder
WORKDIR /myapp

COPY applications applications
COPY domain domain
COPY infrastructure infrastructure
COPY *.gradle .
COPY lombok.* .
COPY gradlew.* .
COPY gradle.* .

RUN gradle build -x test --no-daemon

# ── Stage 2: Run ──
FROM amazoncorretto:21-alpine
WORKDIR /myapprun
COPY --from=builder /myapp/applications/app-service/build/libs/*.jar ms-provider.jar

RUN apk update && apk add --no-cache curl \
    && addgroup -S appgroup && adduser -S appuser -G appgroup

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"

USER appuser
ENTRYPOINT ["/bin/sh", "-c", "java $JAVA_OPTS -jar ms-provider.jar"]

# Sunbird Notification Service Dockerfile
# Stage 1: Extraction
FROM alpine:3.14 AS builder
RUN apk update && apk add unzip
WORKDIR /app
COPY modules/notification/service/target/notification-service-1.0-SNAPSHOT-dist.zip .
RUN unzip notification-service-1.0-SNAPSHOT-dist.zip

# Stage 2: Runtime
FROM adoptopenjdk/openjdk11:alpine-slim

RUN adduser -u 1001 -h /home/sunbird/ -D sunbird \
    && mkdir -p /home/sunbird/ \
    && apk add --no-cache curl

WORKDIR /home/sunbird/
COPY --from=builder --chown=sunbird:sunbird /app/notification-service-1.0-SNAPSHOT /home/sunbird/notification-service-1.0-SNAPSHOT

USER sunbird
EXPOSE 9000

CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS \
  -Dlog4j2.formatMsgNoLookups=true \
  -Dplay.server.http.idleTimeout=180s \
  -cp '/home/sunbird/notification-service-1.0-SNAPSHOT/lib/*' \
  play.core.server.ProdServerStart /home/sunbird/notification-service-1.0-SNAPSHOT

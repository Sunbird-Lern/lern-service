# Sunbird UserOrg Service Dockerfile
# Stage 1: Extraction
FROM alpine:3.14 AS builder
RUN apk update && apk add unzip
WORKDIR /app
COPY modules/userorg/controller/target/userorg-service-1.0-SNAPSHOT-dist.zip .
RUN unzip userorg-service-1.0-SNAPSHOT-dist.zip

# Stage 2: Runtime
FROM adoptopenjdk/openjdk11:alpine-slim

RUN adduser -u 1001 -h /home/sunbird/ -D sunbird \
    && mkdir -p /home/sunbird \
    && apk add --no-cache curl

WORKDIR /home/sunbird/
COPY --from=builder --chown=sunbird:sunbird /app/userorg-service-1.0-SNAPSHOT /home/sunbird/userorg-service-1.0-SNAPSHOT

USER sunbird

CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS \
  -Dplay.server.http.idleTimeout=180s \
  -Dlog4j2.formatMsgNoLookups=true \
  -cp '/home/sunbird/userorg-service-1.0-SNAPSHOT/lib/*' \
  play.core.server.ProdServerStart /home/sunbird/userorg-service-1.0-SNAPSHOT


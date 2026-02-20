# Sunbird Lern Service Dockerfile
# Stage 1: Extraction
FROM alpine:3.14 AS builder
RUN apk update && apk add unzip
WORKDIR /app
COPY modules/lern/service/target/lern-service-impl-1.0-SNAPSHOT-dist.zip .
RUN unzip lern-service-impl-1.0-SNAPSHOT-dist.zip

# Stage 2: Runtime
FROM sunbird/openjdk-java11-alpine:latest

RUN apk update \
    && apk add --no-cache curl \
    && adduser -u 1001 -h /home/sunbird/ -D sunbird \
    && mkdir -p /home/sunbird/

WORKDIR /home/sunbird/
COPY --from=builder --chown=sunbird:sunbird /app/lern-service-impl-1.0-SNAPSHOT /home/sunbird/lern-service-impl-1.0-SNAPSHOT
COPY modules/lern/service/conf/logback.xml /home/sunbird/lern-service-impl-1.0-SNAPSHOT/conf/logback.xml

USER sunbird
EXPOSE 9000

CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS \
  -Dlog4j2.formatMsgNoLookups=true \
  -Dplay.server.http.idleTimeout=180s \
  -Dlogback.configurationFile=/home/sunbird/lern-service-impl-1.0-SNAPSHOT/conf/logback.xml \
  -cp '/home/sunbird/lern-service-impl-1.0-SNAPSHOT/lib/lern-service-impl-1.0-SNAPSHOT.jar:/home/sunbird/lern-service-impl-1.0-SNAPSHOT/lib/*' \
  play.core.server.ProdServerStart /home/sunbird/lern-service-impl-1.0-SNAPSHOT

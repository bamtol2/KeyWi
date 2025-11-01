FROM azul/zulu-openjdk:17-latest
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

ENV JAVA_OPTS="-Xmx768m -Xms512m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"

EXPOSE 8300
USER 1001

ARG JAR_FILE
COPY ${JAR_FILE} app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=51s --retries=3 \
  CMD curl -f http://localhost:8300/actuator/health || exit 1

CMD java $JAVA_OPTS -jar app.jar

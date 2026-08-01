FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/change-assurance-agent.war app.war
RUN mkdir -p /app/uploads
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.war"]

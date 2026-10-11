FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
# CI runs the full isolated MySQL suite before this packaging stage.
# Docker image builds do not get gateway, database, mail or Cloudinary secrets.
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre-noble
RUN groupadd --system waylo && useradd --system --gid waylo --home-dir /app waylo \
    && mkdir -p /app /var/data/waylo-uploads && chown -R waylo:waylo /app /var/data
WORKDIR /app
COPY --from=build --chown=waylo:waylo /build/target/Waylo-0.0.1-SNAPSHOT.jar /app/waylo.jar
ENV TZ=Asia/Dhaka \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Dhaka -XX:MaxRAMPercentage=65" \
    WAYLO_SSLCOMMERZ_ENABLED=false \
    WAYLO_UPLOAD_DIRECTORY=/var/data/waylo-uploads
USER waylo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/waylo.jar"]

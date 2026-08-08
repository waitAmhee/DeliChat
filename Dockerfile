FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --create-home --shell /usr/sbin/nologin delichat
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER delichat

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

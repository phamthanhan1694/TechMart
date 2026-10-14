# --- Giai doan build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Tach buoc tai dependency ra rieng de Docker cache lai giua cac lan build
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Giai doan chay ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Khong chay bang root
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

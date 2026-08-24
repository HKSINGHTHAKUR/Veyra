# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the Maven wrapper and pom first to benefit from Docker layer caching.
COPY .mvn .mvn
COPY mvnw pom.xml ./

# mvnw is committed with CRLF on Windows — normalize for Linux.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Resolve dependencies (this layer is cached unless pom.xml/.mvn change).
RUN ./mvnw dependency:go-offline -B

# Copy sources and build the executable Spring Boot jar (skips tests).
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the executable jar produced by spring-boot-maven-plugin.
COPY --from=build /app/target/appointment-manager-0.0.1-SNAPSHOT.jar app.jar

# Spring Boot reads ${PORT:8080} from application.yml; Render injects PORT.
# JAVA_OPTS allows JVM tuning via Render environment variables.
ENV JAVA_OPTS=""
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

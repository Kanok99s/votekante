# ------------------------------------------------------------------
# VoteKante Docker image (used by Render, but works on any Docker host)
# Build stage:  compile the Spring Boot fat jar with Maven
# Runtime stage: plain JRE, non-root user
# ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Resolve dependencies before copying sources so changes cache well
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 votekante
COPY --from=build /app/target/votekante-*.jar app.jar
USER votekante
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

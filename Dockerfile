FROM gradle:8-jdk17 AS build

WORKDIR /app

# Copy project files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY src ./src

# Build the project with Gradle
RUN ./gradlew bootJar --no-daemon

# Use OpenJDK for the runtime image
FROM openjdk:17-slim

WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Configure the application to use the Docker environment
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/code_sharing_platform
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=Deepak@99
ENV SPRING_JPA_HIBERNATE_DDL_AUTO=update
ENV SPRING_DATASOURCE_DRIVERCLASSNAME=org.postgresql.Driver
ENV SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
ENV APP_JWT_SECRET=verylongandsecurekeyusedforhashingthesignature0123456789
ENV APP_JWT_EXPIRATION=86400000
ENV GIT_REPOSITORIES_BASE_PATH=/app/git-repositories

# Create directory for git repositories
RUN mkdir -p /app/git-repositories

# Expose the port the app runs on
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "app.jar"]
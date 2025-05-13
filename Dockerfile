FROM eclipse-temurin:17-jdk-jammy AS builder

# Install git and other necessary tools
RUN apt-get update && apt-get install -y \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Clone the repository
ARG REPO_URL=https://github.com/mdeepak08/Code_sharing_platform.git
ARG BRANCH=master
RUN git clone --branch ${BRANCH} ${REPO_URL} .

# Replace application plugin mainClass (CLI) with backend Spring Boot main class
RUN sed -i 's/com\.codeshare\.platform\.cli\.CodeShareCLI/com.codeshare.platform.CodeSharingPlatformApplication/' build.gradle

# Make Gradle wrapper executable and build the application
RUN chmod +x ./gradlew
RUN ./gradlew bootJar -Papplication.mainClass=com.codeshare.platform.CodeSharingPlatformApplication --no-daemon --stacktrace

# Ensure the generated Spring Boot jar uses the server main class (not the CLI)
RUN JAR_FILE=$(ls build/libs/code-sharing-platform-*.jar | head -n 1) && \
    printf 'Manifest-Version: 1.0\nMain-Class: org.springframework.boot.loader.launch.JarLauncher\nStart-Class: com.codeshare.platform.CodeSharingPlatformApplication\n' > /tmp/manifest && \
    jar ufm "$JAR_FILE" /tmp/manifest

# Also build the CLI tool as mentioned in the readme
RUN ./gradlew cliJar createBat createSh --no-daemon

# Second stage: Create the runtime environment
FROM eclipse-temurin:17-jre-jammy

# Install necessary runtime dependencies
RUN apt-get update && apt-get install -y \
    postgresql-client \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/code-sharing-platform-*.jar app.jar

# Create directory for git repositories
RUN mkdir -p /app/git-repositories

# Copy CLI tools and JAR for the CLI
COPY --from=builder /app/build/codeshare /app/codeshare
COPY --from=builder /app/build/codeshare.bat /app/codeshare.bat
COPY --from=builder /app/build/libs/codeshare-cli-*.jar /app/build/libs/

# Set permissions for CLI tools after they have been copied
RUN chmod +x /app/codeshare

# Patch the wrapper script to reference the correct CLI JAR name
RUN set -e; CLI_JAR=$(ls /app/build/libs/codeshare-cli-*-cli.jar | head -n 1 | xargs -n1 basename); \
    sed -i "s|codeshare-cli-0.0.1-SNAPSHOT.jar|$CLI_JAR|" /app/codeshare

# Set environment variables that can be overridden at container startup
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/code_sharing_platform
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=Deepak@99
ENV SPRING_JPA_HIBERNATE_DDL_AUTO=update
ENV SPRING_JPA_SHOW_SQL=true
ENV LOGGING_LEVEL_COM_CODESHARE_PLATFORM=INFO
ENV GIT_REPOSITORIES_BASE_PATH=/app/git-repositories
ENV SERVER_PORT=8080
ENV START_CLASS=com.codeshare.platform.CodeSharingPlatformApplication

# Create a database connection check script
RUN echo '#!/bin/sh \n\
echo "Waiting for PostgreSQL to start..." \n\
until pg_isready -h postgres -p 5432 -U ${SPRING_DATASOURCE_USERNAME}; do \n\
  echo "PostgreSQL not available yet, waiting..." \n\
  sleep 2 \n\
done \n\
echo "PostgreSQL is up - starting application" \n\
java -Dloader.main=${START_CLASS} -jar /app/app.jar \n\
' > /app/start.sh && chmod +x /app/start.sh

# Create an entrypoint script with health check
COPY --from=builder /app/help.txt /app/help.txt

# Expose the application port
EXPOSE 8080

# Create a volume for git repositories
VOLUME /app/git-repositories

# Command to run the application with health check
ENTRYPOINT ["/app/start.sh"]
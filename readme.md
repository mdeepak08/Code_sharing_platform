# CodeShare Platform - Getting Started Guide

CodeShare is a collaborative code sharing platform similar to GitHub, featuring both a web interface and a command-line interface (CLI).

## Prerequisites

- Java 17 or higher
- PostgreSQL 12 or higher
- Gradle 8.13 or higher (or use the included Gradle wrapper)
- A modern web browser

## Quick Start

### 1. Database Setup

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database and user
CREATE DATABASE code_sharing_platform;
CREATE USER codeshare WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE code_sharing_platform TO codeshare;
\q
```

Update `src/main/resources/application.properties` with your database credentials:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/code_sharing_platform
spring.datasource.username=codeshare
spring.datasource.password=yourpassword
```

### 2. Build the Project

```bash
# Clone the repository
git clone <repository-url>
cd code-sharing-platform

# Build the entire project
./gradlew build

# Build the CLI tool
./gradlew cliJar createBat createSh
```

### 3. Start the Server

```bash
./gradlew bootRun
```

The server will start at http://localhost:8080

### 4. Install the CLI

**Linux/macOS:**
```bash
# Copy the executable to your PATH
sudo cp build/codeshare /usr/local/bin/
```

**Windows:**
```bash
# Copy the batch file to your PATH
copy build\codeshare.bat C:\Windows\System32\
```

### 5. Use the Platform

#### Web Interface
Navigate to http://localhost:8080 and create an account.

#### CLI Usage
```bash
# Login to your account
codeshare login

# Initialize a new repository
codeshare init "My Project" "Project description"

# Check repository status
codeshare status

# Commit changes
codeshare commit -m "Initial commit"

# Push to remote
codeshare push

# Pull latest changes
codeshare pull

# Create a new branch
codeshare branch create feature-branch

# Switch branches
codeshare branch switch feature-branch

# List all branches
codeshare branch
```

## Common CLI Workflows

### Starting a New Project
```bash
mkdir my-project
cd my-project
codeshare init "My Project" "A cool project"
echo "# My Project" > README.md
codeshare commit -m "Initial commit"
codeshare push
```

### Cloning an Existing Project
```bash
codeshare clone <project-id> my-project-copy
cd my-project-copy
codeshare status
```

### Working with Branches
```bash
# Create and switch to a new branch
codeshare branch create feature-x
codeshare branch switch feature-x

# Make changes and commit
echo "New feature" > feature.txt
codeshare commit -m "Add new feature"
codeshare push
```

## Features

- **Version Control**: Git-like branching and committing
- **Web Interface**: Project management, file editing, and pull requests
- **CLI Tool**: Command-line operations for efficient workflow
- **Collaboration**: Pull requests, code reviews, and activity tracking
- **Security**: JWT authentication and private repositories

## Project Structure

```
code-sharing-platform/
├── src/
│   └── main/
│       ├── java/com/codeshare/platform/
│       │   ├── cli/          # CLI implementation
│       │   ├── controller/   # REST endpoints
│       │   ├── model/        # Domain models
│       │   ├── service/      # Business logic
│       │   └── security/     # Authentication
│       └── resources/
│           ├── static/       # Web interface
│           └── application.properties
├── build.gradle
└── docker-compose.yml
```

## 🚀 Docker One-Command Setup (Recommended)

The entire stack can be built and launched with a single script – no manual tweaks needed.

```bash
# 1) Ensure Docker Desktop (or Docker Engine + Docker Compose v2) is installed & running.

# 2) Clone or download this project then run:
cd java_proj
bash run-codeshare.sh start   # or simply: bash run-codeshare.sh (non-interactive CI)

# The first run can take a few minutes (Gradle builds the JAR, images are pulled).

# 3) Open the web UI
open http://localhost:8080            # macOS (use xdg-open on Linux) or
curl -I http://localhost:8080         # verify HTTP 200
```

Script commands:
• `start`  – build & start containers in the background (default when run non-interactively)
• `stop`   – stop containers
• `logs`   – tail logs (you'll be prompted for service)
• `clean`  – remove containers *and* volumes (DB data is wiped)

> TIP: In a CI pipeline you can just add `bash java_proj/run-codeshare.sh start` and the stack will be ready once the script exits.

---

## 📚 Manual Docker Compose alternative
If you prefer vanilla Compose commands:
```bash
cd java_proj
# Build the application image & start services
docker compose up --build -d

# View logs
docker compose logs -f app

# Tear down
docker compose down
```

---

## Traditional (non-Docker) Setup
*For local development without containers.*  You'll need Java 17, Gradle 8.x and a Postgres instance running.

```bash
# Clone the source
REPO_URL=https://github.com/mdeepak08/Code_sharing_platform.git
 git clone $REPO_URL
 cd Code_sharing_platform

# Build and run
./gradlew bootJar
java -jar build/libs/code-sharing-platform-*.jar
```

Update `src/main/resources/application.properties` with your DB credentials if you go this route.

---

#!/bin/bash
# =========================================================
# Development Command Reference Script
# A collection of commonly used commands for development
# =========================================================

# Text colors for better readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to display section headers
header() {
  echo -e "\n${GREEN}========== $1 ==========${NC}\n"
}

# Function to display commands
show_cmd() {
  echo -e "${YELLOW}$1${NC}: ${CYAN}$2${NC}"
}

# =========================================================
# PYTHON COMMANDS
# =========================================================
python_commands() {
  header "PYTHON COMMANDS"
  
  show_cmd "Run Python script" "python script.py"
  show_cmd "Run Python module" "python -m module_name"
  show_cmd "Run Python interactive shell" "python"
  show_cmd "Create virtual environment" "python -m venv venv"
  show_cmd "Activate virtual environment (Linux/Mac)" "source venv/bin/activate"
  show_cmd "Activate virtual environment (Windows)" "venv\\Scripts\\activate"
  show_cmd "Install package" "pip install package_name"
  show_cmd "Install from requirements" "pip install -r requirements.txt"
  show_cmd "Freeze dependencies" "pip freeze > requirements.txt"
  show_cmd "Run tests" "pytest" 
  show_cmd "Run tests with coverage" "pytest --cov=my_package"
  show_cmd "Lint code" "flake8 ."
  show_cmd "Format code" "black ."
  show_cmd "Type checking" "mypy ."
}

# =========================================================
# JAVA COMMANDS
# =========================================================
java_commands() {
  header "JAVA COMMANDS"
  
  show_cmd "Compile Java file" "javac MyClass.java"
  show_cmd "Run Java program" "java MyClass"
  show_cmd "Run Java with classpath" "java -cp path/to/classes MyClass"
  show_cmd "Create JAR file" "jar cf myjar.jar *.class"
  show_cmd "Run JAR file" "java -jar myjar.jar"
  show_cmd "Show Java version" "java -version"
  show_cmd "Show Java compiler version" "javac -version"
  show_cmd "Run with JVM options" "java -Xmx1024m MyClass"
  show_cmd "Run Maven clean install" "mvn clean install"
  show_cmd "Run Maven with skipping tests" "mvn clean install -DskipTests"
  show_cmd "Compile with Maven" "mvn compile"
  show_cmd "Run tests with Maven" "mvn test"
}

# =========================================================
# GRADLE COMMANDS
# =========================================================
gradle_commands() {
  header "GRADLE COMMANDS"
  
  show_cmd "Run Gradle build" "./gradlew build"
  show_cmd "Run Gradle clean" "./gradlew clean"
  show_cmd "Run specific task" "./gradlew taskName"
  show_cmd "Run with debug" "./gradlew --debug taskName"
  show_cmd "Show dependencies" "./gradlew dependencies"
  show_cmd "Run tests" "./gradlew test"
  show_cmd "Run specific test" "./gradlew test --tests TestName"
  show_cmd "Refresh dependencies" "./gradlew --refresh-dependencies"
  show_cmd "List tasks" "./gradlew tasks"
  show_cmd "Run Spring Boot app" "./gradlew bootRun"
  show_cmd "Create distribution" "./gradlew distZip"
}

# =========================================================
# POSTGRESQL COMMANDS
# =========================================================
postgres_commands() {
  header "POSTGRESQL COMMANDS"
  
  show_cmd "Connect to database" "psql -d database_name -U username"
  show_cmd "Connect with host and port" "psql -h hostname -p port -d database -U username"
  show_cmd "Create database" "createdb database_name"
  show_cmd "Drop database" "dropdb database_name"
  show_cmd "Backup database" "pg_dump -U username database_name > backup.sql"
  show_cmd "Restore database" "psql -U username database_name < backup.sql"
  show_cmd "Export query to CSV" "psql -d database -c \"COPY (SELECT * FROM table) TO STDOUT WITH CSV HEADER\" > output.csv"
  show_cmd "List databases" "\\l (inside psql)"
  show_cmd "List tables" "\\dt (inside psql)"
  show_cmd "Describe table" "\\d table_name (inside psql)"
  show_cmd "Execute SQL file" "psql -d database_name -f script.sql"
  show_cmd "Start PostgreSQL service" "sudo service postgresql start"
  show_cmd "Stop PostgreSQL service" "sudo service postgresql stop"
}

# =========================================================
# GIT COMMANDS
# =========================================================
git_commands() {
  header "GIT COMMANDS"
  
  show_cmd "Initialize repository" "git init"
  show_cmd "Clone repository" "git clone url"
  show_cmd "Check status" "git status"
  show_cmd "Add file" "git add filename"
  show_cmd "Add all changes" "git add ."
  show_cmd "Commit changes" "git commit -m \"commit message\""
  show_cmd "Push to remote" "git push origin branch_name"
  show_cmd "Pull from remote" "git pull origin branch_name"
  show_cmd "Create branch" "git branch branch_name"
  show_cmd "Switch branch" "git checkout branch_name"
  show_cmd "Create and switch branch" "git checkout -b branch_name"
  show_cmd "Merge branch" "git merge branch_name"
  show_cmd "View commit history" "git log"
  show_cmd "View commit history (compact)" "git log --oneline"
  show_cmd "Discard changes" "git checkout -- filename"
  show_cmd "Stash changes" "git stash"
  show_cmd "Apply stashed changes" "git stash apply"
}

# =========================================================
# DOCKER COMMANDS
# =========================================================
docker_commands() {
  header "DOCKER COMMANDS"
  
  show_cmd "List running containers" "docker ps"
  show_cmd "List all containers" "docker ps -a"
  show_cmd "List images" "docker images"
  show_cmd "Build image" "docker build -t image_name:tag ."
  show_cmd "Run container" "docker run -d --name container_name image_name"
  show_cmd "Run with port mapping" "docker run -p host_port:container_port image_name"
  show_cmd "Run with volume" "docker run -v host_path:container_path image_name"
  show_cmd "Stop container" "docker stop container_name"
  show_cmd "Remove container" "docker rm container_name"
  show_cmd "Remove image" "docker rmi image_name"
  show_cmd "View container logs" "docker logs container_name"
  show_cmd "Execute command in container" "docker exec -it container_name command"
  show_cmd "Run docker-compose" "docker-compose up -d"
  show_cmd "Stop docker-compose" "docker-compose down"
}

# =========================================================
# SYSTEM COMMANDS
# =========================================================
system_commands() {
  header "SYSTEM COMMANDS"
  
  show_cmd "Check disk space" "df -h"
  show_cmd "Check directory size" "du -sh directory_name"
  show_cmd "Check memory usage" "free -h"
  show_cmd "Check CPU info" "lscpu"
  show_cmd "Process status" "ps aux"
  show_cmd "Process status (filtered)" "ps aux | grep process_name"
  show_cmd "Kill process" "kill -9 process_id"
  show_cmd "View file content" "cat filename"
  show_cmd "View file with paging" "less filename"
  show_cmd "Find file" "find /path -name filename"
  show_cmd "Search in files" "grep 'pattern' filename"
  show_cmd "Recursive search" "grep -r 'pattern' directory"
  show_cmd "Change file permissions" "chmod 755 filename"
  show_cmd "Change ownership" "chown user:group filename"
}

# =========================================================
# NPM/NODE COMMANDS
# =========================================================
node_commands() {
  header "NPM/NODE COMMANDS"
  
  show_cmd "Initialize new project" "npm init"
  show_cmd "Install package" "npm install package_name"
  show_cmd "Install dev dependency" "npm install package_name --save-dev"
  show_cmd "Install all dependencies" "npm install"
  show_cmd "Run script" "npm run script_name"
  show_cmd "Start application" "npm start"
  show_cmd "Run tests" "npm test"
  show_cmd "Update packages" "npm update"
  show_cmd "List installed packages" "npm list"
  show_cmd "List global packages" "npm list -g"
  show_cmd "Create React app" "npx create-react-app app_name"
  show_cmd "Run Node.js script" "node script.js"
}

# Main menu function
main_menu() {
  clear
  echo -e "${BLUE}========================================${NC}"
  echo -e "${BLUE}     DEVELOPMENT COMMAND REFERENCE     ${NC}"
  echo -e "${BLUE}========================================${NC}"
  echo -e ""
  echo -e "${CYAN}Select a category:${NC}"
  echo -e "${YELLOW}1.${NC} Python Commands"
  echo -e "${YELLOW}2.${NC} Java Commands"
  echo -e "${YELLOW}3.${NC} Gradle Commands"
  echo -e "${YELLOW}4.${NC} PostgreSQL Commands"
  echo -e "${YELLOW}5.${NC} Git Commands"
  echo -e "${YELLOW}6.${NC} Docker Commands"
  echo -e "${YELLOW}7.${NC} System Commands"
  echo -e "${YELLOW}8.${NC} NPM/Node Commands"
  echo -e "${YELLOW}0.${NC} Exit"
  echo -e ""
  echo -e "${CYAN}Enter your choice:${NC} "
  read -r choice
  
  case $choice in
    1) python_commands; press_enter; main_menu ;;
    2) java_commands; press_enter; main_menu ;;
    3) gradle_commands; press_enter; main_menu ;;
    4) postgres_commands; press_enter; main_menu ;;
    5) git_commands; press_enter; main_menu ;;
    6) docker_commands; press_enter; main_menu ;;
    7) system_commands; press_enter; main_menu ;;
    8) node_commands; press_enter; main_menu ;;
    0) echo "Exiting..."; exit 0 ;;
    *) echo -e "${RED}Invalid option. Press Enter to continue...${NC}"; read -r; main_menu ;;
  esac
}

# Function to wait for user to press Enter
press_enter() {
  echo ""
  echo -e "${PURPLE}Press Enter to return to menu...${NC}"
  read -r
}

# Check for direct command argument
if [ $# -eq 1 ]; then
  case $1 in
    python) python_commands ;;
    java) java_commands ;;
    gradle) gradle_commands ;;
    postgres) postgres_commands ;;
    git) git_commands ;;
    docker) docker_commands ;;
    system) system_commands ;;
    node) node_commands ;;
    *) echo -e "${RED}Unknown command category: $1${NC}" ;;
  esac
else
  # Start with main menu
  main_menu
fi
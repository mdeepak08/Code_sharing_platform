#!/bin/bash

# Script to automatically set up and run the Code Sharing Platform using Docker

# Color codes for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${GREEN}=======================================${NC}"
echo -e "${GREEN}Code Sharing Platform Docker Setup${NC}"
echo -e "${GREEN}=======================================${NC}"

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed. Please install Docker first.${NC}"
    echo "Visit https://docs.docker.com/get-docker/ for installation instructions."
    echo -e "${YELLOW}For macOS, you can install Docker Desktop which includes Docker Compose:${NC}"
    echo -e "1. Go to https://docs.docker.com/desktop/install/mac-install/"
    echo -e "2. Download Docker Desktop for your Mac architecture (Intel or Apple Silicon)"
    echo -e "3. Open the .dmg file and drag Docker to your Applications folder"
    echo -e "4. Open Docker Desktop from Applications"
    
    # Check if Homebrew is installed
    if command -v brew &> /dev/null; then
        echo -e "\n${GREEN}You can also install Docker using Homebrew:${NC}"
        echo -e "${CYAN}brew install --cask docker${NC}"
    fi
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo -e "${RED}Docker is installed but not running.${NC}"
    echo -e "${YELLOW}Please start Docker Desktop from your Applications folder.${NC}"
    echo -e "If Docker Desktop is already running, wait a moment for it to fully initialize."
    exit 1
fi

# Check if Docker Compose is installed
if ! docker compose version &> /dev/null; then
    if ! docker-compose version &> /dev/null; then
        echo -e "${RED}Docker Compose is not available.${NC}"
        echo -e "${YELLOW}Possible solutions:${NC}"
        echo -e "1. Make sure Docker Desktop is running completely (Docker Compose comes bundled with it)"
        echo -e "2. Try restarting Docker Desktop"
        echo -e "3. Check if Docker Desktop needs updates"
        echo -e "\n${YELLOW}For manual Docker Compose installation:${NC}"
        echo -e "Visit https://docs.docker.com/compose/install/ for instructions"
        
        # Check if Homebrew is installed
        if command -v brew &> /dev/null; then
            echo -e "\n${GREEN}You can also install Docker Compose using Homebrew:${NC}"
            echo -e "${CYAN}brew install docker-compose${NC}"
        fi
        exit 1
    else
        # If only docker-compose (v1) is available
        COMPOSE_CMD="docker-compose"
    fi
else
    # If docker compose (v2) is available
    COMPOSE_CMD="docker compose"
fi

# Ensure Dockerfile and docker-compose.yml exist
if [ ! -f Dockerfile ]; then
    echo -e "${RED}Dockerfile not found in the current directory. Please create it.${NC}"
    exit 1
fi

if [ ! -f docker-compose.yml ]; then
    echo -e "${RED}docker-compose.yml not found in the current directory. Please create it.${NC}"
    exit 1
fi

# Detect non-interactive execution (stdin not a TTY) and default to "start"
if [[ ! -t 0 && -z "$1" ]]; then
  set -- start
fi

case "$1" in
  start|up)
    echo -e "${YELLOW}Building and starting the application with Docker Compose...${NC}"
    echo -e "${YELLOW}This may take several minutes for the first run as it builds the application.${NC}"
    $COMPOSE_CMD up --build -d
    exit $?
    ;;
  stop)
    echo -e "${YELLOW}Stopping services...${NC}"
    $COMPOSE_CMD down
    exit $?
    ;;
  logs)
    echo -e "${YELLOW}Which service logs do you want to view?${NC}"
    echo -e "1. Application"
    echo -e "2. PostgreSQL"
    echo -e "3. All services"
    echo -e "\nEnter your choice [1-3]: "
    read -r log_choice
    
    case $log_choice in
        1) $COMPOSE_CMD logs -f app ;;
        2) $COMPOSE_CMD logs -f postgres ;;
        3) $COMPOSE_CMD logs -f ;;
        *) echo -e "${RED}Invalid choice.${NC}" ;;
    esac
    exit $?
    ;;
  clean)
    echo -e "${RED}WARNING: This will remove all containers and volumes.${NC}"
    echo -e "${RED}All data will be lost. Are you sure? [y/N]: ${NC}"
    read -r confirm
    
    if [[ $confirm =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}Removing containers and volumes...${NC}"
        $COMPOSE_CMD down -v
        echo -e "${GREEN}Cleanup complete.${NC}"
    fi
    exit $?
    ;;
  "" ) # no args & running in interactive terminal – fall through to menu
    ;;
  *)
    echo -e "${RED}Unknown command '$1'.${NC}"
    echo "Valid commands: start | stop | logs | clean"
    exit 1
    ;;
esac

# Menu function
show_menu() {
    echo -e "\n${GREEN}CodeShare Platform Management${NC}"
    echo -e "1. Start services"
    echo -e "2. Stop services"
    echo -e "3. View logs"
    echo -e "4. Restart services"
    echo -e "5. Clean up (remove containers and volumes)"
    echo -e "6. Run CLI command"
    echo -e "7. Show database info"
    echo -e "8. Exit"
    echo -e "\nEnter your choice [1-8]: "
}

# Menu loop
while true; do
    show_menu
    read -r choice

    case $choice in
        1)  # Start services
            echo -e "${YELLOW}Building and starting the application with Docker Compose...${NC}"
            echo -e "${YELLOW}This may take several minutes for the first run as it builds the application.${NC}"
            $COMPOSE_CMD up --build -d
            
            if [ $? -eq 0 ]; then
                echo -e "\n${GREEN}Application started successfully!${NC}"
                echo -e "Website available at: ${YELLOW}http://localhost:8080${NC}"
                echo -e "PostgreSQL database running on port ${YELLOW}5432${NC}"
            else
                echo -e "\n${RED}There was an error starting the application.${NC}"
                echo -e "${RED}Check the output above for details.${NC}"
            fi
            ;;
            
        2)  # Stop services
            echo -e "${YELLOW}Stopping services...${NC}"
            $COMPOSE_CMD down
            ;;
            
        3)  # View logs
            echo -e "${YELLOW}Which service logs do you want to view?${NC}"
            echo -e "1. Application"
            echo -e "2. PostgreSQL"
            echo -e "3. All services"
            echo -e "\nEnter your choice [1-3]: "
            read -r log_choice
            
            case $log_choice in
                1) $COMPOSE_CMD logs -f app ;;
                2) $COMPOSE_CMD logs -f postgres ;;
                3) $COMPOSE_CMD logs -f ;;
                *) echo -e "${RED}Invalid choice.${NC}" ;;
            esac
            ;;
            
        4)  # Restart services
            echo -e "${YELLOW}Restarting services...${NC}"
            $COMPOSE_CMD restart
            ;;
            
        5)  # Clean up
            echo -e "${RED}WARNING: This will remove all containers and volumes.${NC}"
            echo -e "${RED}All data will be lost. Are you sure? [y/N]: ${NC}"
            read -r confirm
            
            if [[ $confirm =~ ^[Yy]$ ]]; then
                echo -e "${YELLOW}Removing containers and volumes...${NC}"
                $COMPOSE_CMD down -v
                echo -e "${GREEN}Cleanup complete.${NC}"
            fi
            ;;
            
        6)  # Run CLI command
            echo -e "${YELLOW}Enter the CLI command to run (without 'codeshare' prefix):${NC}"
            read -r cmd
            
            if [ -z "$cmd" ]; then
                echo -e "${RED}No command entered.${NC}"
            else
                echo -e "${YELLOW}Running: codeshare $cmd${NC}"
                $COMPOSE_CMD exec app /app/codeshare $cmd
            fi
            ;;
            
        7)  # Show database info
            echo -e "${YELLOW}Database Information:${NC}"
            echo -e "Host: localhost"
            echo -e "Port: 5432"
            echo -e "Database: code_sharing_platform"
            echo -e "Username: postgres"
            echo -e "Password: Deepak@99"
            ;;
            
        8)  # Exit
            echo -e "${GREEN}Exiting...${NC}"
            exit 0
            ;;
            
        *)  echo -e "${RED}Invalid choice. Please try again.${NC}"
            ;;
    esac
done
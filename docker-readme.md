1. Build and run the application using Docker Compose:
   ```bash
   docker-compose up -d
   ```
   
   This command:
   - Builds the Docker images defined in the Dockerfile
   - Starts PostgreSQL and the CodeShare application
   - Sets up the necessary network and volumes

2. The first build may take several minutes as it:
   - Downloads base images
   - Clones the CodeShare Platform repository
   - Builds the application with Gradle

3. Once complete, access the web interface at:
   ```
   http://localhost:8080
   ```

## Using the CLI in Docker

The CodeShare CLI is included in the Docker container. You can use it by executing commands in the running container:

```bash
# Example: Show help
docker-compose exec app /app/codeshare --help

# Example: Login to the platform
docker-compose exec app /app/codeshare login

# Example: Initialize a repository
docker-compose exec app /app/codeshare init "My Project" "Description"
```

## Configuration

### Environment Variables

You can modify any environment variable by editing the `docker-compose.yml` file:

- `SPRING_DATASOURCE_URL`: Database connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `SPRING_JPA_HIBERNATE_DDL_AUTO`: Schema generation strategy
- `LOGGING_LEVEL_COM_CODESHARE_PLATFORM`: Application logging level

### Persistent Data

The setup uses Docker volumes to persist:
- PostgreSQL database data
- Git repositories stored by the application

These volumes remain even if you stop or restart the containers.

## Troubleshooting

### Database Connection Issues

If the application cannot connect to the database:

1. Check if PostgreSQL container is running:
   ```bash
   docker-compose ps
   ```

2. Check PostgreSQL logs:
   ```bash
   docker-compose logs postgres
   ```

### Application Issues

To view application logs:
```bash
docker-compose logs app
```

To restart the application:
```bash
docker-compose restart app
```

## Stopping the Application

To stop all containers:
```bash
docker-compose down
```

To stop and remove all data (volumes):
```bash
docker-compose down -v
```

## Advanced Configuration

### Using a Different Branch or Repository

To use a different branch or repository, modify the build arguments in `docker-compose.yml`:

```yaml
build:
  args:
    - REPO_URL=https://github.com/yourusername/your-fork.git
    - BRANCH=your-branch
```

Then rebuild the application:
```bash
docker-compose up -d --build
``` 
noteId: "54c7fe502d4d11f0ad2d25d4b813a5c3"
tags: []

---

 
// Main JavaScript file for Code Sharing Platform

document.addEventListener('DOMContentLoaded', function() {
    // Check authentication status
    checkAuth();
    // Load projects
    loadProjects();

    // Setup form handlers
    setupCreateProjectForm();
});

// Authentication check
function checkAuth() {
    const token = localStorage.getItem('jwt_token');
    if (token) {
        // User is logged in
        updateAuthUI(true);
        // Set token for all future requests
        setAuthHeader(token);
    } else {
        updateAuthUI(false);
    }
}

// Update UI based on auth status
function updateAuthUI(isLoggedIn) {
    const authButtons = document.getElementById('authButtons');
    if (isLoggedIn) {
        authButtons.innerHTML = `
            <span class="navbar-text me-3" id="userEmail"></span>
            <button class="btn btn-outline-light" onclick="logout()">Logout</button>
        `;
        // Get and display user info
        fetchUserInfo();
    }
}

// Set auth header for API calls
function setAuthHeader(token) {
    window.authHeader = {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    };
}

// Load projects
async function loadProjects() {
    try {
        const response = await fetch('/api/projects', {
            headers: window.authHeader || {}
        });
        const projects = await response.json();
        displayProjects(projects);
    } catch (error) {
        console.error('Error loading projects:', error);
        showError('Failed to load projects');
    }
}

// Display projects in the UI
function displayProjects(projects) {
    const projectsList = document.getElementById('projectsList');
    projectsList.innerHTML = projects.map(project => `
        <div class="list-group-item">
            <div class="d-flex justify-content-between align-items-center">
                <h5 class="project-title mb-1">${project.name}</h5>
                <span class="badge bg-${project.isPublic ? 'success' : 'secondary'}">
                    ${project.isPublic ? 'Public' : 'Private'}
                </span>
            </div>
            <p class="project-description mb-1">${project.description || 'No description'}</p>
            <small class="project-meta">
                Created by ${project.owner.username} on ${new Date(project.createdAt).toLocaleDateString()}
            </small>
            <div class="mt-2">
                <button class="btn btn-sm btn-outline-primary" onclick="viewProject(${project.id})">
                    View Project
                </button>
            </div>
        </div>
    `).join('');
}

// Setup create project form
function setupCreateProjectForm() {
    const form = document.getElementById('createProjectForm');
    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const projectData = {
                name: document.getElementById('projectName').value,
                description: document.getElementById('projectDescription').value,
                isPublic: document.getElementById('isPublic').checked
            };
            
            try {
                const response = await fetch('/api/projects', {
                    method: 'POST',
                    headers: {
                        ...window.authHeader,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(projectData)
                });

                if (response.ok) {
                    // Reload projects after creation
                    loadProjects();
                    // Reset form
                    form.reset();
                    showSuccess('Project created successfully');
                } else {
                    throw new Error('Failed to create project');
                }
            } catch (error) {
                console.error('Error creating project:', error);
                showError('Failed to create project');
            }
        });
    }
}

// View project details
function viewProject(projectId) {
    window.location.href = `/projects/${projectId}`;
}

// Utility functions for notifications
function showError(message) {
    // Implement error notification
    alert(message); // Replace with better UI notification
}

function showSuccess(message) {
    // Implement success notification
    alert(message); // Replace with better UI notification
}

// Logout function
function logout() {
    localStorage.removeItem('jwt_token');
    window.location.href = '/login';
} 

// Function to build a file tree structure from flat list
function buildFileTree(files) {
    // Create a root directory
    const root = { type: 'directory', name: 'root', children: {}, files: [] };
    
    // For each file, add it to the directory structure
    files.forEach(file => {
        let path = file.path;
        let parts = path.split('/');
        let fileName = parts.pop(); // Last part is the file name
        
        // Navigate to the right directory node
        let currentDir = root;
        for (let part of parts) {
            if (part === '') continue; // Skip empty parts
            
            // Create directory if it doesn't exist
            if (!currentDir.children[part]) {
                currentDir.children[part] = { 
                    type: 'directory', 
                    name: part, 
                    children: {}, 
                    files: [] 
                };
            }
            
            // Move to this directory
            currentDir = currentDir.children[part];
        }
        
        // Add the file to the current directory
        currentDir.files.push({
            type: 'file',
            name: fileName,
            id: file.id,
            path: file.path
        });
    });
    
    return root;
}

// Function to render a file tree recursively
function renderFileTree(node, container, level = 0) {
    const indent = '  '.repeat(level);
    
    // First render files in this directory
    node.files.forEach(file => {
        const fileElement = document.createElement('div');
        fileElement.className = 'file-item';
        fileElement.innerHTML = `${indent}<a href="#" class="file-link" data-file-id="${file.id}">${file.name}</a>`;
        container.appendChild(fileElement);
    });
    
    // Then render subdirectories
    Object.keys(node.children).sort().forEach(dirName => {
        const dirNode = node.children[dirName];
        
        // Create directory element
        const dirElement = document.createElement('div');
        dirElement.className = 'directory-item';
        dirElement.innerHTML = `${indent}<span class="directory-name">${dirNode.name}/</span>`;
        container.appendChild(dirElement);
        
        // Recursively render its content
        renderFileTree(dirNode, container, level + 1);
    });
}

// Usage
const fileTree = buildFileTree(files);
const container = document.getElementById('filesList');
renderFileTree(fileTree, container);
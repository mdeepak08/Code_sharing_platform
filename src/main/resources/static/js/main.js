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
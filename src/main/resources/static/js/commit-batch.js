// Constants and variables
const token = localStorage.getItem('jwt_token');
let projectId;
let commitIds = [];

// Initialize the page
document.addEventListener('DOMContentLoaded', function() {
    // Parse URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    projectId = urlParams.get('projectId');
    
    // Get commit IDs from URL
    const commitParam = urlParams.get('commits');
    if (commitParam) {
        commitIds = commitParam.split(',');
    } else {
        // Single commit mode
        const commitId = urlParams.get('id');
        if (commitId) {
            commitIds = [commitId];
        }
    }
    
    if (!projectId || commitIds.length === 0) {
        showError('Missing project ID or commit IDs');
        return;
    }
    
    loadProjectDetails();
    loadBatchCommitDetails();
    setupEventListeners();
});

// Load project details
async function loadProjectDetails() {
    try {
        const response = await fetch(`/api/projects/${projectId}`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        
        if (response.ok) {
            const result = await response.json();
            if (result.success && result.data) {
                const project = result.data;
                document.title = `Commits - ${project.name}`;
                
                // Set breadcrumb links
                document.getElementById('projectLink').textContent = project.name;
                document.getElementById('projectLink').href = `/project.html?id=${projectId}`;
            }
        }
    } catch (error) {
        console.error('Error loading project details:', error);
    }
}

// Load batch commit details
async function loadBatchCommitDetails() {
    const filesList = document.getElementById('filesList'); // Get these references earlier
        const diffContainer = document.getElementById('diffContainer');
    
         if (!filesList || !diffContainer) {
            console.error("Core DOM elements for commit details not found.");
            showError('UI Error: Necessary elements not found.'); // Use showError
            return;
        }
    
    try {
    // Show loading spinner directly in the diffContainer
    diffContainer.innerHTML = `
   <div class="text-center py-5">
     <div class="spinner-border text-primary" role="status">
    <span class="visually-hidden">Loading...</span>
     </div>
   <p class="mt-3">Loading commit details...</p>
    </div>
    `;
        // Optional: Clear files list spinner/content too
        filesList.innerHTML = `
                 <div class="text-center py-4">
      <div class="spinner-border text-primary" role="status">
     <span class="visually-hidden">Loading...</span>
     </div>
     </div>
            `;
    
    
     // Construct the API query string (rest of this is the same)
    const queryString = commitIds.map(id => `commitIds=${id}`).join('&');
   const response = await fetch(`/api/version-control/commit-batch?${queryString}`, {
    headers: {
    'Authorization': `Bearer ${token}`
     }
   });
    
     if (!response.ok) {
    throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
    }
    
     const result = await response.json();
    
if (result.success && result.data) {
    const batchDetails = result.data;
    const commits = batchDetails.commits || [];
     const fileChanges = batchDetails.fileChanges || {};
    
    // Update commit header (uses elements outside diffContainer)
     updateCommitHeader(commits);
    
    // Display file changes (will now correctly find diffContainer and filesList)
     displayFileChanges(fileChanges); // <-- This function is called AFTER fetch success
     // Update commit stats
     updateCommitStats(commits, fileChanges);
    
            // Remove spinner from diffContainer if displayFileChanges succeeded
            // (displayFileChanges clears it anyway, but good practice)
     } else {
    throw new Error(result.message || 'Failed to load commit details');
    }
    } catch (error) {
     console.error('Error loading batch commit details:', error);
    // Use showError to display the error message in commitContent
     showError(`Failed to load commit details: ${error.message}`);
    }
    }

// Update commit header information
function updateCommitHeader(commits) {
    if (commits.length === 0) return;
    
    const latestCommit = commits[0];
    const commitHeader = document.getElementById('commitHeader');
    
    // Format dates
    const commitDate = new Date(latestCommit.createdAt);
    const formattedDate = commitDate.toLocaleDateString() + ' ' + 
        commitDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    
    let authorUsername = "Unknown";
    if (latestCommit.author && latestCommit.author.username) {
        authorUsername = latestCommit.author.username;
    }
    
    // Update commit title
    document.getElementById('commitTitle').textContent = 
        commits.length > 1 ? `${commits.length} commits` : latestCommit.message;
    
    // Update commit author and date
    document.getElementById('commitAuthor').textContent = authorUsername;
    document.getElementById('commitDate').textContent = formattedDate;
    document.getElementById('commitAuthorAvatar').src = 
        `https://www.gravatar.com/avatar/${hashString(authorUsername)}?d=mp&f=y`;
    
    // If multiple commits, show commit list
    if (commits.length > 1) {
        const commitsList = document.createElement('div');
        commitsList.className = 'commit-list mt-3';
        
        commits.forEach(commit => {
            const commitDate = new Date(commit.createdAt);
            const shortDate = commitDate.toLocaleDateString();
            
            const commitItem = document.createElement('div');
            commitItem.className = 'commit-list-item d-flex align-items-center py-2';
            commitItem.innerHTML = `
                <div class="commit-hash me-3">
                    <span class="badge bg-light text-dark">
                        ${commit.id.toString().substring(0, 7)}
                    </span>
                </div>
                <div class="commit-message flex-grow-1">${escapeHtml(commit.message)}</div>
                <div class="commit-date text-muted small">${shortDate}</div>
            `;
            
            commitsList.appendChild(commitItem);
        });
        
        document.getElementById('commitMetaInfo').appendChild(commitsList);
    }
}

// Display file changes
function displayFileChanges(fileChanges) {
    const filesList = document.getElementById('filesList');
    const diffContainer = document.getElementById('diffContainer');
        // --- Add null checks here based on the error ---
    // The error is on filesList, so check it specifically
    if (!filesList) {
        console.error("DOM element with ID 'filesList' not found. Aborting file changes display.");
        // Optional: Show a user-friendly message on the page indicating a UI issue
        // Example: document.getElementById('commitContent').innerHTML = "<div class='alert alert-danger'>UI Error: Could not find the file list area.</div>";
        return; // Stop execution if the element is missing
    }
     // You might as well check diffContainer too for robustness
     if (!diffContainer) {
        console.error("DOM element with ID 'diffContainer' not found. Aborting file changes display.");
        // Optional: Show a user-friendly message
        // Example: document.getElementById('commitContent').innerHTML = "<div class='alert alert-danger'>UI Error: Could not find the difference viewer area.</div>";
        return; // Stop execution
    }
    // --- End null checks ---

    // Clear existing content
    filesList.innerHTML = '';
    diffContainer.innerHTML = '';
    
    if (Object.keys(fileChanges).length === 0) {
        diffContainer.innerHTML = `
            <div class="alert alert-info">
                <i class="fa fa-info-circle me-2"></i>
                No file changes found in this commit.
            </div>
        `;
        return;
    }
    
    // Sort files alphabetically
    const sortedFiles = Object.keys(fileChanges).sort();
    
    sortedFiles.forEach(filePath => {
        // Create file entry in the sidebar
        const fileItem = document.createElement('div');
        fileItem.className = 'file-item';
        fileItem.dataset.path = filePath;
        
        // Determine file icon based on extension
        const extension = filePath.split('.').pop().toLowerCase();
        let fileIcon = 'fa-file';
        
        if (['js', 'jsx', 'ts', 'tsx'].includes(extension)) {
            fileIcon = 'fa-file-code text-warning';
        } else if (['html', 'htm', 'xml'].includes(extension)) {
            fileIcon = 'fa-file-code text-danger';
        } else if (['css', 'scss', 'sass'].includes(extension)) {
            fileIcon = 'fa-file-code text-primary';
        } else if (['java', 'py', 'rb', 'php', 'c', 'cpp', 'cs'].includes(extension)) {
            fileIcon = 'fa-file-code text-success';
        } else if (['md', 'txt'].includes(extension)) {
            fileIcon = 'fa-file-alt';
        } else if (['jpg', 'jpeg', 'png', 'gif', 'svg'].includes(extension)) {
            fileIcon = 'fa-file-image';
        }
        
        // Count additions and deletions
        const content = fileChanges[filePath];
        const lines = content.split('\n');
        let additions = 0;
        let deletions = 0;
        
        lines.forEach(line => {
            if (line.startsWith('+')) additions++;
            if (line.startsWith('-')) deletions++;
        });
        
        // Create the file item HTML
        fileItem.innerHTML = `
            <i class="fa ${fileIcon} me-2"></i>
            <span class="file-name">${filePath}</span>
            <div class="file-changes">
                ${additions > 0 ? `<span class="additions">+${additions}</span>` : ''}
                ${deletions > 0 ? `<span class="deletions">-${deletions}</span>` : ''}
            </div>
        `;
        
        // Add click event to show the file diff
        fileItem.addEventListener('click', function() {
            // Highlight selected file
            document.querySelectorAll('.file-item').forEach(item => {
                item.classList.remove('active');
            });
            this.classList.add('active');
            
            // Show diff for this file
            showFileDiff(filePath, fileChanges[filePath]);
            
            // Update URL hash
            window.location.hash = encodeURIComponent(filePath);
        });
        
        filesList.appendChild(fileItem);
        
        // Add the file diff section to the diff container
        const diffSection = document.createElement('div');
        diffSection.className = 'diff-section';
        diffSection.id = `diff-${hashString(filePath)}`;
        
        const diffHeader = document.createElement('div');
        diffHeader.className = 'diff-header';
        diffHeader.innerHTML = `
            <div class="diff-file-header">
                <span class="diff-file-name">${filePath}</span>
                <div class="diff-file-stats">
                    <span class="additions">+${additions}</span>
                    <span class="deletions">-${deletions}</span>
                </div>
            </div>
        `;
        
        const diffContent = document.createElement('div');
        diffContent.className = 'diff-content';
        
        // Create diff table
        const table = document.createElement('table');
        table.className = 'diff-table';
        
        // Create diff content
        createDiffContent(table, content);
        
        diffContent.appendChild(table);
        diffSection.appendChild(diffHeader);
        diffSection.appendChild(diffContent);
        diffContainer.appendChild(diffSection);
        
        // Hide all diff sections initially
        diffSection.style.display = 'none';
    });
    
    // Show the first file's diff by default
    if (sortedFiles.length > 0) {
        const firstFileItem = document.querySelector('.file-item');
        if (firstFileItem) {
            firstFileItem.click();
        }
    }
}

// Show diff for a specific file
function showFileDiff(filePath, content) {
    // Hide all diff sections
    document.querySelectorAll('.diff-section').forEach(section => {
        section.style.display = 'none';
    });
    
    // Show the selected file's diff
    const diffId = `diff-${hashString(filePath)}`;
    const diffSection = document.getElementById(diffId);
    if (diffSection) {
        diffSection.style.display = 'block';
    }
}

// Create diff content table with improved hunk handling
function createDiffContent(table, diffContent) {
    const lines = diffContent.split('\n');
    let oldLineNum = 1;
    let newLineNum = 1;
    let inHunk = false;
    
    lines.forEach((line, index) => {
        const row = document.createElement('tr');
        row.className = 'diff-line';
        
        // Determine line type
        if (line.startsWith('@@')) {
            // This is a hunk header
            inHunk = true;
            row.classList.add('diff-hunk-header');
            
            // Parse hunk header to get line numbers
            // Example: @@ -1,7 +1,7 @@
            const match = line.match(/@@ -(\d+),\d+ \+(\d+),\d+ @@/);
            if (match) {
                oldLineNum = parseInt(match[1]);
                newLineNum = parseInt(match[2]);
            }
            
            // Hunk header spans both line number columns
            const leftNum = document.createElement('td');
            leftNum.className = 'diff-line-num';
            leftNum.textContent = '...';
            
            const rightNum = document.createElement('td');
            rightNum.className = 'diff-line-num';
            rightNum.textContent = '...';
            
            // Line content
            const content = document.createElement('td');
            content.className = 'diff-line-content';
            content.textContent = line;
            
            row.appendChild(leftNum);
            row.appendChild(rightNum);
            row.appendChild(content);
        } else if (!inHunk) {
            // Skip lines until we find a hunk header
            return;
        } else if (line.startsWith('+ ')) {
            // This is an added line
            row.classList.add('added');
            
            // Left line number (old version) - blank for additions
            const leftNum = document.createElement('td');
            leftNum.className = 'diff-line-num old';
            leftNum.textContent = '';
            
            // Right line number (new version)
            const rightNum = document.createElement('td');
            rightNum.className = 'diff-line-num new';
            rightNum.textContent = newLineNum++;
            
            // Line content (remove the leading "+ ")
            const content = document.createElement('td');
            content.className = 'diff-line-content';
            content.textContent = line.substring(2);
            
            row.appendChild(leftNum);
            row.appendChild(rightNum);
            row.appendChild(content);
        } else if (line.startsWith('- ')) {
            // This is a removed line
            row.classList.add('removed');
            
            // Left line number (old version)
            const leftNum = document.createElement('td');
            leftNum.className = 'diff-line-num old';
            leftNum.textContent = oldLineNum++;
            
            // Right line number (new version) - blank for deletions
            const rightNum = document.createElement('td');
            rightNum.className = 'diff-line-num new';
            rightNum.textContent = '';
            
            // Line content (remove the leading "- ")
            const content = document.createElement('td');
            content.className = 'diff-line-content';
            content.textContent = line.substring(2);
            
            row.appendChild(leftNum);
            row.appendChild(rightNum);
            row.appendChild(content);
        } else if (line.startsWith('  ')) {
            // This is a context (unchanged) line
            
            // Left line number (old version)
            const leftNum = document.createElement('td');
            leftNum.className = 'diff-line-num old';
            leftNum.textContent = oldLineNum++;
            
            // Right line number (new version)
            const rightNum = document.createElement('td');
            rightNum.className = 'diff-line-num new';
            rightNum.textContent = newLineNum++;
            
            // Line content (remove the leading "  ")
            const content = document.createElement('td');
            content.className = 'diff-line-content';
            content.textContent = line.substring(2);
            
            row.appendChild(leftNum);
            row.appendChild(rightNum);
            row.appendChild(content);
        } else {
            // Empty line or other line formats (shouldn't happen with proper diff format)
            
            // Left line number
            const leftNum = document.createElement('td');
            leftNum.className = 'diff-line-num old';
            leftNum.textContent = oldLineNum++;
            
            // Right line number
            const rightNum = document.createElement('td');
            rightNum.className = 'diff-line-num new';
            rightNum.textContent = newLineNum++;
            
            // Line content
            const content = document.createElement('td');
            content.className = 'diff-line-content';
            content.textContent = line;
            
            row.appendChild(leftNum);
            row.appendChild(rightNum);
            row.appendChild(content);
        }
        
        table.appendChild(row);
    });
}

// Update commit statistics
function updateCommitStats(commits, fileChanges) {
    // Calculate total additions and deletions
    let totalAdditions = 0;
    let totalDeletions = 0;
    let totalFiles = Object.keys(fileChanges).length;
    
    for (const [filepath, content] of Object.entries(fileChanges)) {
        const lines = content.split('\n');
        lines.forEach(line => {
            if (line.startsWith('+')) totalAdditions++;
            if (line.startsWith('-')) totalDeletions++;
        });
    }
    
    // Update stats display
    document.getElementById('filesChanged').textContent = totalFiles;
    document.getElementById('additionsCount').textContent = totalAdditions;
    document.getElementById('deletionsCount').textContent = totalDeletions;
}

// Set up event listeners
function setupEventListeners() {
    // Toggle file tree button
    const toggleFilesBtn = document.getElementById('toggleFilesBtn');
    if (toggleFilesBtn) {
        toggleFilesBtn.addEventListener('click', function() {
            const filesSidebar = document.querySelector('.files-sidebar');
            const mainContent = document.querySelector('.main-content');
            
            if (filesSidebar.classList.contains('d-none')) {
                filesSidebar.classList.remove('d-none');
                mainContent.classList.remove('col-md-12');
                mainContent.classList.add('col-md-9');
                this.innerHTML = '<i class="fa fa-chevron-left"></i>';
            } else {
                filesSidebar.classList.add('d-none');
                mainContent.classList.remove('col-md-9');
                mainContent.classList.add('col-md-12');
                this.innerHTML = '<i class="fa fa-chevron-right"></i>';
            }
        });
    }
}

// Helper function: Generate hash from string
function hashString(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        hash = ((hash << 5) - hash) + str.charCodeAt(i);
        hash |= 0; // Convert to 32bit integer
    }
    return Math.abs(hash).toString(16);
}

// Helper function: Escape HTML
function escapeHtml(unsafe) {
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// Helper function: Show error message
function showError(message) {
    document.getElementById('commitContent').innerHTML = `
        <div class="alert alert-danger">
            <i class="fa fa-exclamation-triangle me-2"></i>
            ${message}
        </div>
    `;
}
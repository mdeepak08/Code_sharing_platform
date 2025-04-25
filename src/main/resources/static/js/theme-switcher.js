/**
 * theme-switcher.js
 * Handles theme switching functionality across all pages of the CodeShare Platform
 */

// Function to set a theme
function setTheme(themeName) {
    document.documentElement.setAttribute('data-theme', themeName);
    localStorage.setItem('theme', themeName);
    
    // Update the toggle switch
    const themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.checked = themeName === 'dark';
    }
}

// Function to toggle between themes
function toggleTheme() {
    const currentTheme = localStorage.getItem('theme') || 'light';
    if (currentTheme === 'light') {
        setTheme('dark');
    } else {
        setTheme('light');
    }
}

// Initialize theme
function initializeTheme() {
    // First check if user previously selected a theme
    const savedTheme = localStorage.getItem('theme');
    
    if (savedTheme) {
        setTheme(savedTheme);
    } else {
        // If no saved preference, check OS preference
        const prefersDarkScheme = window.matchMedia('(prefers-color-scheme: dark)').matches;
        if (prefersDarkScheme) {
            setTheme('dark');
        } else {
            setTheme('light');
        }
    }
    
    // Add event listener for the theme toggle switch
    const themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.addEventListener('change', toggleTheme);
    }
}

// Function to inject theme toggle into navbar if it doesn't exist
function injectThemeToggle() {
    // Check if toggle already exists
    if (document.getElementById('theme-toggle')) {
        return; // Toggle already exists
    }
    
    // Look for the navbar elements where we can inject the toggle
    const navLinks = document.querySelector('.navbar-nav');
    const navButtons = document.querySelector('.navbar .d-flex');
    
    if (!navLinks && !navButtons) {
        console.warn('Could not find navigation elements to inject theme toggle');
        return;
    }
    
    // Create the theme toggle element
    const themeToggleContainer = document.createElement('div');
    themeToggleContainer.className = 'theme-toggle me-3 d-flex align-items-center';
    themeToggleContainer.innerHTML = `
        <label class="theme-switch" for="theme-toggle">
            <input type="checkbox" id="theme-toggle">
            <span class="slider"></span>
            <i class="fas fa-sun sun-icon"></i>
            <i class="fas fa-moon moon-icon"></i>
        </label>
    `;
    
    // Insert the toggle in the appropriate place
    if (navButtons) {
        navButtons.prepend(themeToggleContainer);
    } else if (navLinks) {
        // Create a list item for the toggle
        const toggleLi = document.createElement('li');
        toggleLi.className = 'nav-item d-flex align-items-center ms-2';
        toggleLi.appendChild(themeToggleContainer);
        navLinks.appendChild(toggleLi);
    }
    
    // Initialize the toggle state based on current theme
    const currentTheme = localStorage.getItem('theme') || 'light';
    const themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.checked = currentTheme === 'dark';
        themeToggle.addEventListener('change', toggleTheme);
    }
}

// Function to ensure required theme assets are loaded
function ensureThemeAssetsLoaded() {
    // Check if theme CSS is already loaded
    const themeStylesheet = document.querySelector('link[href*="dark-theme.css"]');
    if (!themeStylesheet) {
        // Create and append the theme stylesheet
        const linkElement = document.createElement('link');
        linkElement.rel = 'stylesheet';
        linkElement.href = '/css/dark-theme.css'; // Adjust path as needed
        document.head.appendChild(linkElement);
    }
    
    // Check if Font Awesome is loaded
    const fontAwesome = document.querySelector('link[href*="font-awesome"]');
    if (!fontAwesome) {
        // Load Font Awesome if not present
        const faLink = document.createElement('link');
        faLink.rel = 'stylesheet';
        faLink.href = 'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css';
        document.head.appendChild(faLink);
    }
}

// Listen for OS theme changes
function setupOSThemeListener() {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', event => {
        // Only change theme if user hasn't manually set a preference
        if (!localStorage.getItem('theme')) {
            setTheme(event.matches ? 'dark' : 'light');
        }
    });
}

// Run on page load
document.addEventListener('DOMContentLoaded', function() {
    // Ensure theme assets are loaded
    ensureThemeAssetsLoaded();
    
    // Inject theme toggle if needed
    injectThemeToggle();
    
    // Initialize theme based on preferences
    initializeTheme();
    
    // Listen for OS theme changes
    setupOSThemeListener();
});
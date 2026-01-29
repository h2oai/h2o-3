/**
 * Zeus Auth - Main Application
 *
 * Microsoft Entra ID authentication using MSAL.js
 * Because life is too short for SAML and Shibboleth.
 */

// Initialize MSAL instance
const msalInstance = new msal.PublicClientApplication(msalConfig);

// DOM Elements
const landingSection = document.getElementById('landing');
const dashboardSection = document.getElementById('dashboard');
const signInBtn = document.getElementById('signInBtn');
const signOutBtn = document.getElementById('signOutBtn');
const userAvatar = document.getElementById('userAvatar');
const userName = document.getElementById('userName');
const userEmail = document.getElementById('userEmail');
const tokenClaims = document.getElementById('tokenClaims');
const callGraphBtn = document.getElementById('callGraphBtn');
const getProfileBtn = document.getElementById('getProfileBtn');
const silentRefreshBtn = document.getElementById('silentRefreshBtn');
const apiResult = document.getElementById('apiResult');
const apiResultContent = document.getElementById('apiResultContent');

// Current account
let currentAccount = null;

/**
 * Initialize the app on page load
 */
async function initializeApp() {
    try {
        // Handle redirect response (if coming back from login)
        const response = await msalInstance.handleRedirectPromise();

        if (response) {
            // User just logged in via redirect
            handleLoginResponse(response);
        } else {
            // Check if user is already signed in
            const accounts = msalInstance.getAllAccounts();
            if (accounts.length > 0) {
                currentAccount = accounts[0];
                showDashboard();
            }
        }
    } catch (error) {
        console.error('Initialization error:', error);
        showToast('Initialization failed: ' + error.message, 'error');
    }
}

/**
 * Sign in with Microsoft
 * Two lines of code vs. hundreds of lines of Shibboleth config
 */
async function signIn() {
    try {
        // Lightning strike on click!
        if (window.zeusEffects) {
            window.zeusEffects.strikeLightning();
            window.zeusEffects.playClick();
        }

        // Option 1: Popup login (better UX, but may be blocked)
        // const response = await msalInstance.loginPopup(loginRequest);

        // Option 2: Redirect login (works everywhere)
        await msalInstance.loginRedirect(loginRequest);

        // That's it. No SAML request generation. No IdP discovery.
        // No parsing XML. No attribute mapping. Just... login.
    } catch (error) {
        console.error('Login error:', error);
        if (error.errorCode === 'popup_window_error') {
            // Fallback to redirect if popup blocked
            await msalInstance.loginRedirect(loginRequest);
        } else {
            showToast('Login failed: ' + error.message, 'error');
        }
    }
}

/**
 * Sign out
 */
async function signOut() {
    try {
        // Clear local session
        const logoutRequest = {
            account: currentAccount,
            postLogoutRedirectUri: msalConfig.auth.postLogoutRedirectUri,
        };

        await msalInstance.logoutRedirect(logoutRequest);
    } catch (error) {
        console.error('Logout error:', error);
        showToast('Logout failed: ' + error.message, 'error');
    }
}

/**
 * Handle login response
 */
function handleLoginResponse(response) {
    if (response && response.account) {
        currentAccount = response.account;
        showDashboard();

        // EPIC VICTORY CELEBRATION!
        if (window.zeusEffects) {
            window.zeusEffects.victory('Welcome, ' + response.account.name + '!');
        }
    }
}

/**
 * Show dashboard (logged in state)
 */
function showDashboard() {
    landingSection.style.display = 'none';
    dashboardSection.style.display = 'block';

    // Update UI with user info
    const name = currentAccount.name || 'User';
    const email = currentAccount.username || '';

    userName.textContent = 'Welcome, ' + name + '!';
    userEmail.textContent = email;
    userAvatar.textContent = getInitials(name);

    // Display ID token claims
    // Look at this beautiful JSON. No OIDs. No XML namespaces.
    const claims = currentAccount.idTokenClaims;
    const formattedClaims = {
        // Clean, human-readable claim names
        name: claims.name,
        email: claims.preferred_username,
        subject: claims.sub,
        issuer: claims.iss,
        audience: claims.aud,
        issuedAt: new Date(claims.iat * 1000).toISOString(),
        expiresAt: new Date(claims.exp * 1000).toISOString(),
        tenant: claims.tid,
        objectId: claims.oid,
    };

    tokenClaims.textContent = JSON.stringify(formattedClaims, null, 2);
}

/**
 * Show landing (logged out state)
 */
function showLanding() {
    landingSection.style.display = 'block';
    dashboardSection.style.display = 'none';
    apiResult.style.display = 'none';
}

/**
 * Get an access token silently
 * No SAML assertion parsing required!
 */
async function getAccessToken(scopes) {
    const request = {
        scopes: scopes,
        account: currentAccount,
    };

    try {
        // Try silent token acquisition first
        const response = await msalInstance.acquireTokenSilent(request);
        return response.accessToken;
    } catch (error) {
        // If silent fails, try interactive
        if (error instanceof msal.InteractionRequiredAuthError) {
            const response = await msalInstance.acquireTokenPopup(request);
            return response.accessToken;
        }
        throw error;
    }
}

/**
 * Call Microsoft Graph API
 * Because Graph API is what happens when you design APIs in the 21st century
 */
async function callMicrosoftGraph() {
    try {
        callGraphBtn.classList.add('loading');
        callGraphBtn.textContent = 'Calling API...';

        const accessToken = await getAccessToken(['User.Read']);

        const response = await fetch(graphConfig.graphMeEndpoint, {
            headers: {
                'Authorization': `Bearer ${accessToken}`,
            },
        });

        if (!response.ok) {
            throw new Error(`Graph API error: ${response.status}`);
        }

        const data = await response.json();

        // Display the beautiful JSON response
        apiResult.style.display = 'block';
        apiResultContent.textContent = JSON.stringify(data, null, 2);

        showToast('Graph API call successful!', 'success');

        // Celebration particles!
        if (window.zeusEffects) {
            window.zeusEffects.launchConfetti(30);
            window.zeusEffects.playSuccess();
        }
    } catch (error) {
        console.error('Graph API error:', error);
        showToast('API call failed: ' + error.message, 'error');
    } finally {
        callGraphBtn.classList.remove('loading');
        callGraphBtn.textContent = '📊 Call Microsoft Graph';
    }
}

/**
 * Get detailed user profile
 */
async function getFullProfile() {
    try {
        getProfileBtn.classList.add('loading');
        getProfileBtn.textContent = 'Loading...';

        const accessToken = await getAccessToken(['User.Read']);

        // Get both profile and organization info
        const [profileResponse] = await Promise.all([
            fetch(graphConfig.graphMeEndpoint + '?$select=displayName,givenName,surname,mail,userPrincipalName,jobTitle,department,officeLocation,mobilePhone,businessPhones,preferredLanguage', {
                headers: { 'Authorization': `Bearer ${accessToken}` },
            }),
        ]);

        const profile = await profileResponse.json();

        // Clean profile - no urn:oid:1.3.6.1.4.1.5923.1.1.1.x garbage!
        const cleanProfile = {
            displayName: profile.displayName,
            firstName: profile.givenName,
            lastName: profile.surname,
            email: profile.mail || profile.userPrincipalName,
            jobTitle: profile.jobTitle,
            department: profile.department,
            office: profile.officeLocation,
            mobile: profile.mobilePhone,
            businessPhones: profile.businessPhones,
            preferredLanguage: profile.preferredLanguage,
        };

        apiResult.style.display = 'block';
        apiResultContent.textContent = JSON.stringify(cleanProfile, null, 2);

        showToast('Profile loaded!', 'success');

        // Quick celebration
        if (window.zeusEffects) {
            window.zeusEffects.playSuccess();
        }
    } catch (error) {
        console.error('Profile error:', error);
        showToast('Failed to load profile: ' + error.message, 'error');
    } finally {
        getProfileBtn.classList.remove('loading');
        getProfileBtn.textContent = '👤 Get Full Profile';
    }
}

/**
 * Demonstrate silent token refresh
 * Unlike SAML where you need to do the whole dance again
 */
async function demonstrateSilentRefresh() {
    try {
        silentRefreshBtn.classList.add('loading');
        silentRefreshBtn.textContent = 'Refreshing...';

        const startTime = Date.now();

        // Force a new token acquisition
        const request = {
            scopes: ['User.Read'],
            account: currentAccount,
            forceRefresh: true, // Force refresh even if cached token is valid
        };

        const response = await msalInstance.acquireTokenSilent(request);
        const endTime = Date.now();

        const refreshResult = {
            message: 'Token refreshed successfully!',
            timeMs: endTime - startTime,
            newExpiration: new Date(response.expiresOn).toISOString(),
            scopes: response.scopes,
            // No need to parse XML, validate signatures manually, or decode base64
            // MSAL handles all of that. This is how auth should work.
        };

        apiResult.style.display = 'block';
        apiResultContent.textContent = JSON.stringify(refreshResult, null, 2);

        showToast(`Token refreshed in ${refreshResult.timeMs}ms!`, 'success');

        // Lightning for speed!
        if (window.zeusEffects) {
            window.zeusEffects.strikeLightning();
        }
    } catch (error) {
        console.error('Silent refresh error:', error);
        showToast('Silent refresh failed: ' + error.message, 'error');
    } finally {
        silentRefreshBtn.classList.remove('loading');
        silentRefreshBtn.textContent = '🔄 Silent Token Refresh';
    }
}

/**
 * Get user initials for avatar
 */
function getInitials(name) {
    return name
        .split(' ')
        .map(part => part.charAt(0))
        .join('')
        .toUpperCase()
        .substring(0, 2);
}

/**
 * Show toast notification
 */
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);

    setTimeout(() => {
        toast.remove();
    }, 4000);
}

// Event Listeners
signInBtn.addEventListener('click', signIn);
signOutBtn.addEventListener('click', signOut);
callGraphBtn.addEventListener('click', callMicrosoftGraph);
getProfileBtn.addEventListener('click', getFullProfile);
silentRefreshBtn.addEventListener('click', demonstrateSilentRefresh);

// Initialize on load
initializeApp();

// Log a victory message
console.log(`
⚡ Zeus Auth initialized

No SAML.
No Shibboleth.
No eduPerson.
No urn:oid nightmares.

Just Microsoft Entra ID + MSAL.js.
Authentication as it should be.

Keyboard shortcuts:
  L - Strike lightning
  C - Launch confetti
  V - Victory celebration
  M - Toggle sound
`);

// Add click effects to all buttons
document.querySelectorAll('.btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
        if (window.zeusEffects) {
            window.zeusEffects.burstParticles(e.clientX, e.clientY, 15);
        }
    });
});

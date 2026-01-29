/**
 * Zeus Auth - MSAL Configuration
 *
 * Replace these values with your own Azure AD / Entra ID app registration.
 *
 * To create your app registration:
 * 1. Go to https://portal.azure.com
 * 2. Navigate to "Microsoft Entra ID" > "App registrations" > "New registration"
 * 3. Name your app (e.g., "Zeus Auth")
 * 4. Set redirect URI to your Netlify URL (e.g., https://your-app.netlify.app)
 * 5. Copy the Application (client) ID below
 * 6. For tenant: use "common" for multi-tenant, or your specific tenant ID
 *
 * No client secret needed! This is a public SPA client using PKCE.
 * Take that, Shibboleth!
 */

const msalConfig = {
    auth: {
        // Replace with your app's client ID from Azure AD
        clientId: "YOUR_CLIENT_ID_HERE",

        // Authority URL - options:
        // - "https://login.microsoftonline.com/common" = Any Microsoft account
        // - "https://login.microsoftonline.com/organizations" = Work/school only
        // - "https://login.microsoftonline.com/consumers" = Personal accounts only
        // - "https://login.microsoftonline.com/{tenant-id}" = Specific tenant
        authority: "https://login.microsoftonline.com/common",

        // This will be set dynamically based on the current URL
        redirectUri: window.location.origin,

        // Where to redirect after logout
        postLogoutRedirectUri: window.location.origin,

        // PKCE is automatically enabled - no client secret needed!
        // Unlike SAML which requires certificates, metadata, and dark rituals
    },
    cache: {
        // Where to store tokens - sessionStorage is more secure, localStorage persists
        cacheLocation: "sessionStorage",

        // Set to true if you need IE11 support (why would you?)
        storeAuthStateInCookie: false,
    },
    system: {
        // Logging configuration for debugging
        loggerOptions: {
            loggerCallback: (level, message, containsPii) => {
                if (containsPii) return;
                switch (level) {
                    case msal.LogLevel.Error:
                        console.error('[MSAL]', message);
                        break;
                    case msal.LogLevel.Warning:
                        console.warn('[MSAL]', message);
                        break;
                    case msal.LogLevel.Info:
                        console.info('[MSAL]', message);
                        break;
                    case msal.LogLevel.Verbose:
                        console.debug('[MSAL]', message);
                        break;
                }
            },
            logLevel: msal.LogLevel.Warning,
            piiLoggingEnabled: false,
        }
    }
};

// Scopes to request during login
// Compare this to the nightmare of SAML attribute release policies:
// No urn:oid:1.3.6.1.4.1.5923.1.1.1.9 nonsense here!
const loginRequest = {
    scopes: [
        "openid",      // Standard OIDC claim
        "profile",     // Name, picture, etc.
        "email",       // Email address
        "User.Read"    // Microsoft Graph: read user profile
    ]
};

// Scopes for Microsoft Graph API calls
const graphConfig = {
    graphMeEndpoint: "https://graph.microsoft.com/v1.0/me",
    graphMePhotoEndpoint: "https://graph.microsoft.com/v1.0/me/photo/$value",
};

// That's it. That's the entire configuration.
// No metadata XML files. No IdP discovery. No WAYF pages.
// Just beautiful, simple JSON.

# Zeus Auth

**Microsoft Entra ID authentication that just works.**

Death to Shibboleth. Death to OpenAthens. Death to SAML nightmares.

[![Deploy to Netlify](https://www.netlify.com/img/deploy/button.svg)](https://app.netlify.com/start/deploy?repository=YOUR_REPO_URL)

## Why This Exists

Because academic identity providers are a crime against humanity:

| OpenAthens/Shibboleth | Zeus Auth (Microsoft) |
|-----------------------|------------------------|
| SAML 2.0 XML soup | OAuth 2.0 / OIDC |
| `urn:oid:1.3.6.1.4.1.5923.1.1.1.6` | `email` |
| Weeks to integrate | 5 minutes |
| Metadata exchange rituals | JSON config |
| WAYF discovery pages | One login button |
| Certificate management | PKCE (no secrets) |
| mod_shib + Apache config | Static HTML/JS |
| Federation trust agreements | App registration |

## Quick Start

### 1. Create Azure AD App Registration

1. Go to [Azure Portal](https://portal.azure.com)
2. Navigate to **Microsoft Entra ID** > **App registrations** > **New registration**
3. Configure:
   - **Name**: Zeus Auth (or whatever you want)
   - **Supported account types**: Choose based on your needs
     - "Accounts in any organizational directory and personal Microsoft accounts" for maximum compatibility
   - **Redirect URI**: Select "Single-page application (SPA)" and enter your Netlify URL

4. Copy the **Application (client) ID**

### 2. Configure the App

Edit `public/config.js`:

```javascript
const msalConfig = {
    auth: {
        clientId: "YOUR_CLIENT_ID_HERE",  // Paste your client ID
        authority: "https://login.microsoftonline.com/common",
        redirectUri: window.location.origin,
    },
    // ...
};
```

### 3. Deploy to Netlify

**Option A: One-Click Deploy**

[![Deploy to Netlify](https://www.netlify.com/img/deploy/button.svg)](https://app.netlify.com/start/deploy?repository=YOUR_REPO_URL)

**Option B: Manual Deploy**

```bash
# Install Netlify CLI
npm install -g netlify-cli

# Login to Netlify
netlify login

# Deploy (from microsoft-auth-app directory)
cd microsoft-auth-app
netlify deploy --prod --dir=public
```

**Option C: Git-based Deploy**

1. Push to GitHub/GitLab
2. Connect repo to Netlify
3. Set publish directory to `microsoft-auth-app/public`
4. Deploy

### 4. Update Azure AD Redirect URI

After deploying, add your Netlify URL to your app registration:

1. Go to Azure Portal > App registrations > Your app
2. Click **Authentication**
3. Add your Netlify URL (e.g., `https://your-app.netlify.app`)
4. Make sure it's set as **Single-page application**

## Features

- **Microsoft Entra ID (Azure AD)** authentication
- **MSAL.js 2.x** with PKCE (no client secret needed!)
- **Microsoft Graph API** integration
- **Silent token refresh** (no re-authentication interruptions)
- **Works with**:
  - Work/School accounts (Azure AD)
  - Personal Microsoft accounts
  - B2B guest users
- **Zero build step** - pure static HTML/CSS/JS
- **Netlify-ready** with proper security headers

## Configuration Options

### Authority URLs

```javascript
// Any Microsoft account (personal + work/school)
authority: "https://login.microsoftonline.com/common"

// Work/school accounts only
authority: "https://login.microsoftonline.com/organizations"

// Personal accounts only
authority: "https://login.microsoftonline.com/consumers"

// Specific tenant only
authority: "https://login.microsoftonline.com/YOUR_TENANT_ID"
```

### Scopes

```javascript
const loginRequest = {
    scopes: [
        "openid",           // Required for OIDC
        "profile",          // Name, picture
        "email",            // Email address
        "User.Read",        // Microsoft Graph: basic profile
        // Add more as needed:
        // "Calendars.Read",
        // "Mail.Read",
        // "Files.Read",
    ]
};
```

## Project Structure

```
microsoft-auth-app/
├── public/
│   ├── index.html      # Main SPA
│   ├── styles.css      # Modern dark theme
│   ├── config.js       # MSAL configuration
│   └── app.js          # Authentication logic
├── netlify.toml        # Netlify configuration
└── README.md           # You are here
```

## Comparison: Shibboleth vs Zeus Auth

### Shibboleth Integration (The Old Way)

```xml
<!-- shibboleth2.xml - one of MANY files you need -->
<SPConfig xmlns="urn:mace:shibboleth:3.0:native:sp:config">
    <ApplicationDefaults entityID="https://your-app/shibboleth"
        REMOTE_USER="eppn subject-id pairwise-id persistent-id">

        <Sessions lifetime="28800" timeout="3600"
            relayState="ss:mem"
            checkAddress="false"
            handlerSSL="true"
            cookieProps="https">

            <SSO discoveryProtocol="SAMLDS"
                 discoveryURL="https://wayf.example.org/WAYF">
                SAML2
            </SSO>

            <!-- Hours of configuration later... -->
        </Sessions>

        <AttributeExtractor type="XML" validate="true"
            reloadChanges="false" path="attribute-map.xml"/>

        <!-- More XML nightmares -->
    </ApplicationDefaults>
</SPConfig>
```

Plus: `attribute-map.xml`, `attribute-policy.xml`, Apache/Nginx config, metadata exchange, certificate management, federation registration...

### Zeus Auth Integration (The New Way)

```javascript
const msalConfig = {
    auth: {
        clientId: "your-client-id",
        authority: "https://login.microsoftonline.com/common",
        redirectUri: window.location.origin,
    }
};

// That's it. Deploy to Netlify. Done.
```

## Security

This app uses:

- **PKCE** (Proof Key for Code Exchange) - No client secrets needed
- **Secure token storage** in sessionStorage
- **CSP headers** configured in `netlify.toml`
- **HTTPS only** (enforced by Netlify)

## Local Development

```bash
# Option 1: Use any static file server
npx serve public

# Option 2: Use Netlify Dev
netlify dev

# Option 3: Python
cd public && python -m http.server 8888
```

Note: For local development, add `http://localhost:8888` (or your port) to your Azure AD app's redirect URIs.

## Troubleshooting

### "AADSTS50011: Reply URL does not match"

Add your exact URL to Azure AD:
1. Azure Portal > App registrations > Your app > Authentication
2. Add the exact URL (including trailing slash if present)
3. Set platform to "Single-page application"

### "Popup blocked"

The app falls back to redirect-based login if popups are blocked. This is handled automatically.

### "AADSTS65001: User consent required"

Either:
- Click "Accept" on the consent prompt
- Or have an admin grant consent for all users in Azure AD

## License

MIT - Do whatever you want. Just stop using Shibboleth.

---

*Built with rage against eduPersonScopedAffiliation and all who enabled it.*

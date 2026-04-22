package ai.h2o.org.eclipse.jetty.security.jaas;

import org.eclipse.jetty.security.AbstractLoginService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.RolePrincipal;
import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Session;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Minimal JAAS-based LoginService used by H2O standalone on Jetty 12.
 *
 * Jetty 12 dropped a stable {@code jetty-jaas} artifact (only alpha/beta tags of
 * {@code jetty-ee8-jaas} / {@code jetty-ee9-jaas} are published on Maven Central).
 * H2O's LDAP / Kerberos / PAM login modes depend on a JAAS-backed LoginService, so
 * we ship this small replacement instead of pulling an unreleased Jetty artifact.
 *
 * Authentication is delegated entirely to {@link javax.security.auth.login.LoginContext},
 * meaning the {@code -login_conf} JAAS configuration file continues to work unchanged.
 */
public class H2OJAASLoginService extends AbstractLoginService {

    private final String loginModuleName;

    public H2OJAASLoginService(String realmName) {
        setName(realmName);
        this.loginModuleName = realmName;
    }

    @Override
    public UserIdentity login(String username, Object credentials, Request request,
                              Function<Boolean, Session> getOrCreateSession) {
        CallbackHandler handler = callbacks -> {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback) {
                    ((NameCallback) cb).setName(username);
                } else if (cb instanceof PasswordCallback) {
                    char[] pwd = credentials == null
                        ? new char[0]
                        : credentials.toString().toCharArray();
                    ((PasswordCallback) cb).setPassword(pwd);
                } else {
                    throw new UnsupportedCallbackException(cb);
                }
            }
        };
        try {
            LoginContext ctx = new LoginContext(loginModuleName, handler);
            ctx.login();
            Subject subject = ctx.getSubject();
            UserPrincipal up = new UserPrincipal(username, null);
            IdentityService ids = getIdentityService();
            List<RolePrincipal> roles = Collections.emptyList();
            return ids.newUserIdentity(subject, up, roles.toArray(new String[0]));
        } catch (LoginException e) {
            return null;
        }
    }

    @Override
    protected UserPrincipal loadUserInfo(String username) {
        return null;
    }

    @Override
    protected List<RolePrincipal> loadRoleInfo(UserPrincipal user) {
        return Collections.emptyList();
    }
}

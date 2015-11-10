package water;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

public class BasicAuthSecurityHandlerFactory {

    private static final String USER_ROLE = "user";
    
    public static final SecurityHandler basicAuth(AuthConfig config) {
        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.setAuthenticator(new BasicAuthenticator());
        securityHandler.setRealmName(config.getRealm());

        ConstraintMapping constraintMapping = new ConstraintMapping();

        Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, USER_ROLE);
        constraint.setAuthenticate(true);

        constraintMapping.setConstraint(constraint);

        constraintMapping.setPathSpec("/*");

        securityHandler.addConstraintMapping(constraintMapping);

        HashLoginService loginService = new HashLoginService();
        loginService.putUser(config.getUsername(), Credential.getCredential(config.getPassword()),
                new String[] {USER_ROLE});
        loginService.setName(config.getRealm());

        securityHandler.setLoginService(loginService);

        return securityHandler;
    }
}

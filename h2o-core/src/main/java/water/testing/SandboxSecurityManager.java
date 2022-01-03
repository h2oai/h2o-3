package water.testing;

import java.security.Permission;

public class SandboxSecurityManager extends SecurityManager {

    private String[] _forbidden = new String[0];
    
    @Override
    public void checkPermission(Permission perm) {
        // noop
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        // noop
    }

    @Override
    public void checkRead(String file) {
        for (String forbidden : _forbidden) {
            if (file.startsWith(forbidden))
                throw new SecurityException("Access to '" + file + "' is forbidden (rule: '" + forbidden + "').");
        }
    }

    @Override
    public void checkRead(String file, Object context) {
        checkRead(file);
    }

    public void setForbiddenReadPrefixes(String[] forbidden) {
        _forbidden = forbidden;
    }

}

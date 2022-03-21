package water.rapids.ast.prims.testing;

import water.MRTask;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValStr;
import water.testing.SandboxSecurityManager;

/**
 * Internal operator that lets user set a given system property on all nodes of H2O cluster.
 * It is meant for debugging of running clusters and it is not meant to be directly exposed to users.
 */
public class AstSetReadForbidden extends AstBuiltin<AstSetReadForbidden> {

    @Override
    public String[] args() {
        return new String[]{"forbidden"};
    }

    @Override
    public int nargs() {
        return 1 + 1;
    } // (testing.setreadforbidden forbidden)

    @Override
    public String str() {
        return "testing.setreadforbidden";
    }

    @Override
    protected ValStr exec(Val[] args) {
        String[] forbidden = args[1].getStrs();
        if (forbidden.length > 0) {
            new SetForbiddenTask(forbidden).doAllNodes();
        } else {
            new ClearForbiddenTask().doAllNodes();
        }
        return new ValStr(String.join(", ", forbidden));
    }

    private static class SetForbiddenTask extends MRTask<SetForbiddenTask> {
        private final String[] _forbidden;

        private SetForbiddenTask(String[] forbidden) {
            _forbidden = forbidden;
        }

        @Override
        protected void setupLocal() {
            SecurityManager sm = System.getSecurityManager();
            if (sm == null) {
                System.setSecurityManager(new SandboxSecurityManager());
                sm = System.getSecurityManager();
            }
            if (!(sm instanceof SandboxSecurityManager)) {
                throw new IllegalStateException("Unexpected Security Manager: " + sm);
            }
            ((SandboxSecurityManager) sm).setForbiddenReadPrefixes(_forbidden);
        }
    }

    private static class ClearForbiddenTask extends MRTask<SetForbiddenTask> {
        @Override
        protected void setupLocal() {
            SecurityManager sm = System.getSecurityManager();
            if (sm == null)
                return;
            if (!(sm instanceof SandboxSecurityManager)) {
                throw new IllegalStateException("Unexpected Security Manager: " + sm);
            }
            System.setSecurityManager(null);
        }
    }
    
}

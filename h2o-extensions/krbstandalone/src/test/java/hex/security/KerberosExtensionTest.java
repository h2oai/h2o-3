package hex.security;

import org.junit.Test;
import water.H2O;

import static org.junit.Assert.*;

public class KerberosExtensionTest {

    @Test
    public void testIsDisabledOnHadoop() {
        H2O.OptArgs onHadoopArgs = new H2O.OptArgs() {
            @Override
            public boolean launchedWithHadoopJar() {
                return true;
            }
        };
        KerberosExtension ext = new KerberosExtension(onHadoopArgs);
        assertFalse(ext.isEnabled());
    }

    @Test
    public void testIsEnabledInStandalone() {
        H2O.OptArgs standaloneArgs = new H2O.OptArgs() {
            @Override
            public boolean launchedWithHadoopJar() {
                return false;
            }
        };
        KerberosExtension ext = new KerberosExtension(standaloneArgs);
        assertTrue(ext.isEnabled());
    }

}

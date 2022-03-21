package hex.security;

import org.junit.Test;
import water.H2O;
import water.init.StandaloneKerberosComponent;

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

    @Test
    public void testInitComponents() {
        assertFalse(TestComponent1._init_called);
        assertFalse(TestComponent2._init_called);
        KerberosExtension.initComponents(null, H2O.ARGS);
        assertTrue(TestComponent1._init_called);
        assertTrue(TestComponent2._init_called);
    }

    public static class TestComponent1 implements StandaloneKerberosComponent {
        static boolean _init_called;

        @Override
        public String name() {
            return "Component1";
        }

        @Override
        public int priority() {
            return 2_000;
        }

        @Override
        public boolean initComponent(Object conf, H2O.OptArgs args) {
            _init_called = true;
            return false;
        }
    }

    public static class TestComponent2 implements StandaloneKerberosComponent {
        static boolean _init_called;

        @Override
        public String name() {
            return "Component2";
        }

        @Override
        public int priority() {
            return 10_000;
        }

        @Override
        public boolean initComponent(Object conf, H2O.OptArgs args) {
            _init_called = true;
            return true;
        }
    }

}

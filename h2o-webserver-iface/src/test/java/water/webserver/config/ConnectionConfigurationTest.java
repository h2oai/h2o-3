package water.webserver.config;

import org.junit.Test;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.*;

public class ConnectionConfigurationTest {

    @Test
    public void testDefaults() {
        ConnectionConfiguration cfg = new MyConnectionConfiguration(new Properties(), true);
        assertEquals(32 * 1024, cfg.getRequestHeaderSize());
        assertEquals(32 * 1024, cfg.getRequestBufferSize());
        assertEquals(32 * 1024, cfg.getResponseHeaderSize());
        assertEquals(17, cfg.getOutputBufferSize(17));
        assertEquals(5 * 60 * 1000, cfg.getIdleTimeout());
        assertTrue(cfg.isRelativeRedirectAllowed());
    }

    @Test
    public void testParsing() {
        for (boolean isSecured : Arrays.asList(false, true)) {
            Properties props = new Properties();
            String prefix = isSecured ? "sys.ai.h2o.https." : "sys.ai.h2o.http.";
            props.put(prefix + "requestHeaderSize", "42");
            props.put(prefix + "requestBufferSize", "43");
            props.put(prefix + "responseHeaderSize", "44");
            props.put(prefix + "responseBufferSize", "45");
            props.put(prefix + "jetty.idleTimeout", "46");
            props.put(prefix + "relativeRedirectAllowed", "false");

            ConnectionConfiguration cfg = new MyConnectionConfiguration(props, isSecured);
            assertEquals(isSecured, cfg.isSecure());
            assertEquals(42, cfg.getRequestHeaderSize());
            assertEquals(43, cfg.getRequestBufferSize());
            assertEquals(44, cfg.getResponseHeaderSize());
            assertEquals(45, cfg.getOutputBufferSize(17));
            assertEquals(46, cfg.getIdleTimeout());
            assertFalse(cfg.isRelativeRedirectAllowed());
        }
    }

    private static class MyConnectionConfiguration extends ConnectionConfiguration {

        private final Properties _properties;

        private MyConnectionConfiguration(Properties properties, boolean isSecured) {
            super(isSecured);
            _properties = properties;
        }

        @Override
        protected String getProperty(String name, String defaultValue) {
            return _properties.getProperty(name, defaultValue);
        }
    }

}

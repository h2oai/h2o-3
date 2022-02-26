package water.rapids;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class SessionTest {

    @Test
    public void testProperties() {
        assertNull(new Session().getProperty("invalid", null));
        assertEquals("tst_default", new Session().getProperty("invalid", "tst_default"));

        Session s = new Session();
        s.setProperty("key1", "value1");
        assertEquals("value1", s.getProperty("key1", null));
        s.setProperty("key1", null);
        assertNull(s.getProperty("key1", null));
    }

}

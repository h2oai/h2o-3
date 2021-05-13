package water.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.parser.DecryptionTool;
import water.parser.NullDecryptionTool;

import static org.junit.Assert.*;

public class ReflectionUtilsTest {

    @Rule
    public ExpectedException ee = ExpectedException.none();
    
    @Test
    public void newInstance() throws Exception {
        DecryptionTool instance = ReflectionUtils.newInstance(NullDecryptionTool.class.getName(), DecryptionTool.class);
        assertNotNull(instance);
    }

    @Test
    public void newInstance_invalid() throws Exception {
        ee.expectMessage("Class water.parser.NullDecryptionTool is not an instance of java.lang.Number.");
        ReflectionUtils.newInstance(NullDecryptionTool.class.getName(), Number.class);
    }

}

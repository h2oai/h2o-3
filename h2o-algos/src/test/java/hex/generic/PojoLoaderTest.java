package hex.generic;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.NFSFileVec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.IOException;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PojoLoaderTest {

    @Rule
    public final ExpectedException ee = ExpectedException.none();

    @Test
    public void loadPojoFromSourceCode_invalid() throws IOException {
        ee.expectMessage("POJO compilation failed: Please make sure key 'Invalid.java' contains a valid POJO source code and you are running a Java JDK (compiler present: 'true', self-check passed: 'true').");
        try {
            Scope.enter();
            NFSFileVec v = TestUtil.makeNfsFileVec("smalldata/testng/prostate.csv");
            assertNotNull(v);
            Scope.track(v);
            PojoLoader.loadPojoFromSourceCode(v, Key.make("Invalid.java"));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void compilationSelfCheck() {
        assertTrue(PojoLoader.compilationSelfCheck());
    }
}

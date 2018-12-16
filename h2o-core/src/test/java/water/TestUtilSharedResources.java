package water;

import org.junit.AfterClass;
import org.junit.Ignore;

/**
 * A class for tests with static resourcec initialized @{@link org.junit.BeforeClass}
 * and tear down @{@link AfterClass}. In such cases, control for key leaks must then be also done @{@link AfterClass}.
 */
@Ignore("Support for tests, but no actual tests here")
public class TestUtilSharedResources extends TestUtil {
    
    @AfterClass
    public static void checkLeakedKeysStatic() {
        performLeakedKeysCheck();
    }

    @Override
    public void checkLeakedKeys() {
        // Overrides the method on parent in order for this method not to be performed @After each test
    }
}

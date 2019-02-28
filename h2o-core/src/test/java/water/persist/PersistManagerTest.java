package water.persist;

import org.junit.Before;
import org.junit.Test;
import water.H2O;
import water.TestUtil;

import java.util.List;

import static org.junit.Assert.*;

public class PersistManagerTest extends TestUtil {

    PersistManager persistManager;

    @Before
    public void setUp() {
        stall_till_cloudsize(1);
        persistManager = H2O.getPM();
    }

    @Test
    public void calcTypeaheadMatches_emptyPath() {
        // Completely empty path
        List<String> matches = persistManager.calcTypeaheadMatches("", 100);
        assertNotNull(matches);
        assertEquals(0, matches.size());
        
        // Path with spaces (testing trim is being done)
        matches = persistManager.calcTypeaheadMatches("   ", 100);
        assertNotNull(matches);
        assertEquals(0, matches.size());
    }

}

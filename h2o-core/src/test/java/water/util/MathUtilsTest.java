package water.util;

import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static water.util.StringUtils.join;

public class MathUtilsTest extends TestCase {


    @Test
    public void testCompare() throws Exception {
        assertTrue(MathUtils.compare(1,1, 0.1, 0.1));
        assertTrue(MathUtils.compare(1,1, 0.0, 0.1));
        assertTrue(MathUtils.compare(1,1, 0.1, 0.0));
        assertTrue(MathUtils.compare(1,1, 0.0, 0.0));

        assertTrue(MathUtils.compare(-1,-1, 0.1, 0.1));
        assertTrue(MathUtils.compare(-1,-1, 0.0, 0.1));
        assertTrue(MathUtils.compare(-1,-1, 0.1, 0.0));
        assertTrue(MathUtils.compare(-1,-1, 0.0, 0.0));

        assertTrue(MathUtils.compare(1, 0.900001, 0.1, 0));
        assertTrue(MathUtils.compare(1, 1.099, 0.1, 0));
        assertTrue(MathUtils.compare(-1, -0.90001, 0.1, 0));
        assertTrue(MathUtils.compare(-1, -1.099, 0.1, 0));
        
        assertFalse(MathUtils.compare(1, 0.8999999, 0.1, 0));
        assertFalse(MathUtils.compare(1, 1.10000001, 0.1, 0));
        assertFalse(MathUtils.compare(-1, -0.899999, 0.1, 0));
        assertFalse(MathUtils.compare(-1, -1.1000001, 0.1, 0));

        assertTrue(MathUtils.compare(0.99999, 0, 1, 0));
        assertTrue(MathUtils.compare(-0.99999, 0, 1, 0));
        assertTrue(MathUtils.compare(0, 0.99999, 1, 0));
        assertTrue(MathUtils.compare(0, -0.99999, 1, 0)); 
        
        assertFalse(MathUtils.compare(1.000001, 0, 1, 0));
        assertFalse(MathUtils.compare(-1.000001, 0, 1, 0));
        assertFalse(MathUtils.compare(0, 1.000001, 1, 0));
        assertFalse(MathUtils.compare(0, -1.000001, 1, 0)); 
        
        assertTrue(MathUtils.compare(1, 0.9000, 0.1, 0.1));
        assertTrue(MathUtils.compare(1, 1.099, 0, 0.1));
        assertTrue(MathUtils.compare(-1, -0.900, 0.1, 0.1));
        assertTrue(MathUtils.compare(-1, -1.099, 0, 0.1));
        
        assertFalse(MathUtils.compare(1, 0.8999999, 0, 0.1));
        assertFalse(MathUtils.compare(1,  1.1,0, 0.09));
        assertFalse(MathUtils.compare(-1, -0.899999, 0, 0.1));
        assertFalse(MathUtils.compare(-0.899999, -1, 0, 0.1));

        assertFalse(MathUtils.compare(1, 0, 0, 0.1));
        assertFalse(MathUtils.compare(-1, 0, 0, 0.1));
        assertFalse(MathUtils.compare(0, 1, 0, 0.1));
        assertFalse(MathUtils.compare(0, -1, 0, 0.1));
        
        assertTrue(MathUtils.compare(0, -0.1, 0.2, 0));
        assertFalse(MathUtils.compare(0, -0.2, 0.1, 0));
        assertFalse(MathUtils.compare(0, -0.1, 0.0, 0.1));
        assertFalse(MathUtils.compare(-1, 0, 0.1, 0.1));
    }

}

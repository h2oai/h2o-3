package water.util;

import org.junit.Test;
import water.H2O;

import static org.junit.Assert.*;

public class SBTest {

    @Test
    public void pj_float() {
        final String outputDoublesPropName = H2O.OptArgs.SYSTEM_PROP_PREFIX + "java.output.doubles";
        try {
            assertEquals("2841.083f", new SB().pj(2841.083f).toString());
            assertEquals("Float.NaN", new SB().pj(Float.NaN).toString());
            assertEquals("Float.POSITIVE_INFINITY", new SB().pj(Float.POSITIVE_INFINITY).toString());
            assertEquals("Float.NEGATIVE_INFINITY", new SB().pj(Float.NEGATIVE_INFINITY).toString());
            
            System.setProperty(outputDoublesPropName, "true");
            assertEquals("2841.0830078125", new SB().pj(2841.083f).toString());
            assertEquals("Double.NaN", new SB().pj(Float.NaN).toString());
            assertEquals("Double.POSITIVE_INFINITY", new SB().pj(Float.POSITIVE_INFINITY).toString());
            assertEquals("Double.NEGATIVE_INFINITY", new SB().pj(Float.NEGATIVE_INFINITY).toString());
        } finally {
            System.clearProperty(outputDoublesPropName);
        }
    }
    
}

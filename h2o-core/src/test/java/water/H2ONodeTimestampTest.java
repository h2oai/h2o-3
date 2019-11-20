package water;

import org.junit.Test;

import static org.junit.Assert.*;

public class H2ONodeTimestampTest extends TestBase {

    @Test
    public void decodeClientInfoNotClient(){
        short timestamp = H2ONodeTimestamp.calculateNodeTimestamp(1540375717281L, false);
        assertEquals(timestamp, 9633);
        assertFalse(H2ONodeTimestamp.decodeIsClient(timestamp));
    }

    @Test
    public void decodeClientInfoClient(){
        short timestamp = H2ONodeTimestamp.calculateNodeTimestamp(1540375717281L, true);
        assertEquals(timestamp, -9633);
        assertTrue(H2ONodeTimestamp.decodeIsClient(timestamp));
    }

    @Test
    public void decodeNotClientZeroTimestamp(){
        short timestamp = H2ONodeTimestamp.calculateNodeTimestamp(0L, false);
        assertEquals(timestamp, 1);
        assertFalse(H2ONodeTimestamp.decodeIsClient(timestamp));
    }

    @Test
    public void decodeClientZeroTimestamp(){
        short timestamp = H2ONodeTimestamp.calculateNodeTimestamp(0L, true);
        assertEquals(timestamp, -1);
        assertTrue(H2ONodeTimestamp.decodeIsClient(timestamp));
    }
}

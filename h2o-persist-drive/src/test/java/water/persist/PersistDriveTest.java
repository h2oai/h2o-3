package water.persist;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersistDriveTest {

    @Test
    public void load() {
        try {
            new PersistDrive().load(null);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals("PersistDrive#load should never be called", e.getMessage());
        }
    }

}

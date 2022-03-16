package water.persist.security;

import org.junit.Test;

import static org.junit.Assert.*;

public class HdfsComponentTest {

    @Test
    public void parseRefreshIntervalToSecs() {
        assertEquals(10 * 60, HdfsComponent.parseRefreshIntervalToSecs("PT10M"));
        assertEquals(42 * 60, HdfsComponent.parseRefreshIntervalToSecs("42M"));
    }

}

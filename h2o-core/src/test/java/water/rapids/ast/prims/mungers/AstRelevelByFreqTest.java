package water.rapids.ast.prims.mungers;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class AstRelevelByFreqTest {

    @Test
    public void takeTopN() {
        assertArrayEquals(new int[]{6, 5, 4, 0, 3, 2, 1}, AstRelevelByFreq.takeTopN(new int[]{5, 4, 3, 2, 1}, 3, 7));
        assertArrayEquals(new int[]{6, 0, 5, 4, 3, 2, 1}, AstRelevelByFreq.takeTopN(new int[]{5, 4, 3, 2, 1}, 5, 7));
    }
}

package water.rapids.ast.prims.mungers;

import org.junit.Test;

import static org.junit.Assert.*;

public class AstRelevelByFreqTest {

    @Test
    public void takeTopN() {
        assertArrayEquals(new int[]{5, 4, 3, 0, 1, 2, 6}, AstRelevelByFreq.takeTopNMostFrequentDomain(new int[]{5, 4, 3, 2, 1, 0, 6}, 3));
        assertArrayEquals(new int[]{5, 4, 3, 2, 1, 0, 6}, AstRelevelByFreq.takeTopNMostFrequentDomain(new int[]{5, 4, 3, 2, 1, 0, 6}, 5));
    }
}

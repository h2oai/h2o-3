package water.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static water.util.CollectionUtils.setOfUniqueRandomNumbers;

public class CollectionUtilsTest {

    @Test
    public void setOfUniqueRandomNumbersTest() {
        long[] uniqueRandom = setOfUniqueRandomNumbers(5, 100L, 0xBEEF);
        assertArrayEquals(new long[]{10L, 16L, 41L, 48L, 88L}, uniqueRandom);

        uniqueRandom = setOfUniqueRandomNumbers(5, 6L, 0xBEEF);
        assertArrayEquals(new long[]{0L, 1L, 2L, 4L, 5L}, uniqueRandom);
    }

}

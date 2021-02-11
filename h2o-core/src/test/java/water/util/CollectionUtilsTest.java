package water.util;

import org.junit.Test;

import java.util.Set;

import static water.util.CollectionUtils.*;
import static org.junit.Assert.*;

public class CollectionUtilsTest {

    @Test
    public void setOfUniqueRandomNumbersTest() {
        Set<Long> uniqueRandom = setOfUniqueRandomNumbers(5, 100, 0xBEEF);
        assertArrayEquals("", new Long[]{88L, 48L, 16L, 41L, 10L}, uniqueRandom.toArray());

        uniqueRandom = setOfUniqueRandomNumbers(5, 6, 0xBEEF);
        assertArrayEquals("", new Long[]{0L, 1L, 2L, 4L, 5L}, uniqueRandom.toArray());
    }

}

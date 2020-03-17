package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.IcedLong;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TermFrequencyTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testTermFrequencies() {
        Frame fr = getSimpleTestFrame();

        Map<String, IcedLong>[] expectedTermFrequencies = new HashMap[] {
                new HashMap<String, IcedLong>() {{
                    put("A", new IcedLong(1));
                    put("B", new IcedLong(1));
                    put("C", new IcedLong(1));
                }},
                new HashMap<String, IcedLong>() {{
                    put("A", new IcedLong(3));
                    put("Z", new IcedLong(1));
                }},
                new HashMap<String, IcedLong>() {{
                    put("B", new IcedLong(1));
                    put("C", new IcedLong(3));
                }}
        };

        try {
            TermFrequency tf = new TermFrequency(3).doAll(fr);

            for (int docIdx = 0; docIdx < tf._termFrequencies.length; docIdx++) {
                for (Map.Entry<String, IcedLong> expEntry : expectedTermFrequencies[docIdx].entrySet())
                    assertEquals(expEntry.getValue(), tf._termFrequencies[docIdx].get(new BufferedString(expEntry.getKey())));
            }
        } finally {
            fr.remove();
        }
    }

    private Frame getSimpleTestFrame() {
        String[] documents = new String[] {
                "A B C", "A B C", "A B C",
                "A A A Z", "A A A Z", "A A A Z", "A A A Z",
                "C C B C", "C C B C", "C C B C", "C C B C"
        };
        long[] docIds = new long[] {
                0L, 0L, 0L,
                1L, 1L, 1L, 1L,
                2L, 2L, 2L, 2L
        };
        String[] tokens = new String[] {
                "A", "B", "C",
                "A", "A", "A", "Z",
                "C", "C", "B", "C"
        };

        return new TestFrameBuilder()
                .withName("data")
                .withColNames("Document", "Document ID", "Token")
                .withVecTypes(Vec.T_STR, Vec.T_NUM, Vec.T_STR)
                .withDataForCol(0, documents)
                .withDataForCol(1, docIds)
                .withDataForCol(2, tokens)
                .withChunkLayout(4, 3, 2, 2)
                .build();
    }
}

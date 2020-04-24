package hex.tfidf;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class TermFrequencyTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testTermFrequenciesSmallData() {
        Frame fr = getSimpleTestFrame();

        Map<Pair<String, Long>, Long> expectedTermFrequencies = new HashMap() {{
                put(new Pair("A", 0L), 1L);
                put(new Pair("B", 0L), 1L);
                put(new Pair("C", 0L), 1L);
                
                put(new Pair("A", 1L), 3L);
                put(new Pair("Z", 1L), 1L);
                
                put(new Pair("B", 2L), 1L);
                put(new Pair("C", 2L), 3L);
        }};
        int expectedTfValuesCnt = expectedTermFrequencies.size();

        try {
            TermFrequency tf = new TermFrequency();
            Frame outputFrame = tf.compute(fr);
            long outputRowsCnt = outputFrame.numRows();

            Assert.assertEquals(expectedTfValuesCnt, outputRowsCnt);

            Vec outputDocIds = outputFrame.vec(0);
            Vec outputTokens = outputFrame.vec(1);
            Vec outputTFs = outputFrame.vec(2);

            for(int row = 0; row < outputRowsCnt; row++) {
                String token = outputTokens.stringAt(row);
                long docId = outputDocIds.at8(row);
                Pair key = new Pair<>(token, docId);

                Assert.assertTrue(expectedTermFrequencies.containsKey(key));
                long expectedTF = expectedTermFrequencies.get(key);

                Assert.assertEquals(expectedTF, outputTFs.at8(row));
            }

            outputFrame.remove();
        } finally {
            fr.remove();
        }
    }

    private Frame getSimpleTestFrame() {
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
                    .withColNames("Document ID", "Token")
                    .withVecTypes(Vec.T_NUM, Vec.T_STR)
                    .withDataForCol(0, docIds)
                    .withDataForCol(1, tokens)
                    .withChunkLayout(4, 3, 2, 2)
                    .build();
    }
}

package hex.tfidf;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.HashMap;
import java.util.Map;

public class DocumentFrequencyTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testDocumentFrequenciesSmallData() {
        Frame fr = getSimpleTestFrame();

        Map<String, Long> expectedDocumentFrequencies = new HashMap() {{
            put("A", 2L);
            put("B", 2L);
            put("C", 2L);
            put("Z", 1L);
        }};
        int expectedDfValuesCnt = expectedDocumentFrequencies.size();

        try {
            DocumentFrequency df = new DocumentFrequency();
            Frame outputFrame = df.compute(fr);
            long outputRowsCnt = outputFrame.numRows();

            Assert.assertEquals(expectedDfValuesCnt, outputRowsCnt);

            Vec outputTokens = outputFrame.vec(0);
            Vec outputDFs = outputFrame.vec(1);

            for(int row = 0; row < outputRowsCnt; row++) {
                String token = outputTokens.stringAt(row);

                Assert.assertTrue(expectedDocumentFrequencies.containsKey(token));
                long expectedTF = expectedDocumentFrequencies.get(token);

                Assert.assertEquals(expectedTF, outputDFs.at8(row));
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
                    .withChunkLayout(4, 3, 1, 2, 1)
                    .build();
    }
}

package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.Pair;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TermFrequencyTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testTermFrequencies() {
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

        byte[] outputTypes = new byte[]{ Vec.T_NUM, Vec.T_STR, Vec.T_STR, Vec.T_NUM };
        
        try {
            TermFrequency tf = new TermFrequency().doAll(outputTypes, fr);
            Frame outputFrame = tf.outputFrame();
            long outputRowsCnt = outputFrame.numRows();
            
            assertEquals(expectedTfValuesCnt, outputRowsCnt);

            // TODO: Columns ordering - NUM columns are first
            Vec outputDocIds = outputFrame.vec(0);
            Vec outputTFs = outputFrame.vec(1);
//            Vec outputDocs = outputFrame.vec(2);
            Vec outputTokens = outputFrame.vec(3);
            
//            for (int row = 0; row < outputRowsCnt; row++) {
//                System.out.println(outputTokens.stringAt(row) + " - " 
//                        + outputDocIds.at8(row) + "(" 
//                        + outputDocs.stringAt(row) + ") = " 
//                        + outputTFs.at8(row));
//            }

            for(int row = 0; row < outputRowsCnt; row++) {
                String token = outputTokens.stringAt(row);
                long docId = outputDocIds.at8(row);
                long expectedTF = expectedTermFrequencies.get(new Pair<>(token, docId));
                
                assertEquals(expectedTF, outputTFs.at8(row));
            }

            outputFrame.remove();
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

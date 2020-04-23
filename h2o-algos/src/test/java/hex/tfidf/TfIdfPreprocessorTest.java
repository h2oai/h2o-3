package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.Pair;

import static org.junit.Assert.assertEquals;

public class TfIdfPreprocessorTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testTfIdfPreprocessing() {
        Pair<Frame, Frame> testFramesPair = getSimpleTestFrames();
        Frame inputFrame = testFramesPair._1();
        Frame expectedOutputFrame = testFramesPair._2();

        byte[] outputTypes = new byte[]{ Vec.T_NUM, Vec.T_STR };

        try {
            TfIdfPreprocessor preprocessor = new TfIdfPreprocessor().doAll(outputTypes, inputFrame);
            Frame outputFrame = preprocessor.outputFrame();

            assertEquals(expectedOutputFrame.numCols(), outputFrame.numCols());
            assertEquals(expectedOutputFrame.numRows(), outputFrame.numRows());

            for(int i = 0; i < expectedOutputFrame.numCols(); i++) {
                Vec expectedVec = expectedOutputFrame.vec(i);

                if (expectedVec.get_type() == Vec.T_STR)
                    assertStringVecEquals(expectedVec, outputFrame.vec(i));
                else
                    assertVecEquals(expectedVec, outputFrame.vec(i), 0);
            }

            outputFrame.remove();
        } finally {
            inputFrame.remove();
            expectedOutputFrame.remove();
        }
    }

    private Pair<Frame, Frame> getSimpleTestFrames() {
        String[] documents = new String[] {
                "A B C",
                "A A A Z",
                "C C B C"
        };
        long[] docIds = new long[] { 0L, 1L, 2L };

        Frame inputFrame = new TestFrameBuilder()
                               .withName("data")
                               .withColNames("Document ID", "Document")
                               .withVecTypes(Vec.T_NUM, Vec.T_STR)
                               .withDataForCol(0, docIds)
                               .withDataForCol(1, documents)
                               .withChunkLayout(2, 1)
                               .build();

        long[] outDocIds = new long[] {
                0L, 0L, 0L,
                1L, 1L, 1L, 1L,
                2L, 2L, 2L, 2L
        };
        String[] outTokens = new String[] {
                "A", "B", "C",
                "A", "A", "A", "Z",
                "C", "C", "B", "C"
        };

        Frame expectedOutFrame = new TestFrameBuilder()
                                     .withName("expectedOutputFrame")
                                     .withColNames("Document ID", "Token")
                                     .withVecTypes(Vec.T_NUM, Vec.T_STR)
                                     .withDataForCol(0, outDocIds)
                                     .withDataForCol(1, outTokens)
                                     .build();

        return new Pair<>(inputFrame, expectedOutFrame);
    }
}

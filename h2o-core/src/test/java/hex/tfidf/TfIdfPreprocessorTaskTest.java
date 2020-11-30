package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.Pair;

import static org.junit.Assert.assertEquals;

public class TfIdfPreprocessorTaskTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testTfIdfPreprocessing() {
        Pair<Frame, Frame> testFramesPair = getSimpleTestFrames();
        Frame inputFrame = testFramesPair._1();
        Frame expectedOutputFrame = testFramesPair._2();

        byte[] outputTypes = new byte[]{ Vec.T_NUM, Vec.T_STR };

        try {
            TfIdfPreprocessorTask preprocessor = new TfIdfPreprocessorTask(0, 2).doAll(outputTypes, inputFrame);
            Frame outputFrame = preprocessor.outputFrame();

            assertEquals("Number of columns in the preprocessed frame does not match the expected number.", 
                         expectedOutputFrame.numCols(), outputFrame.numCols());
            assertEquals("Number of rows in the preprocessed frame does not match the expected number.",
                         expectedOutputFrame.numRows(), outputFrame.numRows());

            for(int i = 0; i < expectedOutputFrame.numCols(); i++) {
                Vec expectedVec = expectedOutputFrame.vec(i);

                if (expectedVec.get_type() == Vec.T_STR)
                    assertStringVecEquals(expectedVec, outputFrame.vec(i));
                else
                    assertVecEquals("Vector (at index " + i + ") in the preprocessed frame does not mismatch the expected one.", 
                                    expectedVec, outputFrame.vec(i), 0);
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
                                .withColNames("Document ID", "Col", "Document")
                                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR)
                                .withDataForCol(0, docIds)
                                .withDataForCol(1, new long[]{ 0, 0, 0 })
                                .withDataForCol(2, documents)
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

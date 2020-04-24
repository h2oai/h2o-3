package water.rapids.ast.prims.advmath;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.util.Pair;

import static org.junit.Assert.assertEquals;

public class AstTfIdfTest extends TestUtil {

    private static final double DELTA = 0.0001;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    static public void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testTfIdfSmallData() {
        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";
            
            Pair<Frame,Frame> testFrames = getSimpleTestFrames(frameName, sess);
            Frame inputFrame = testFrames._1();
            Frame expectedOutputFrame = testFrames._2();
            
            Scope.track(inputFrame);
            Scope.track(expectedOutputFrame);
            
            Val resVal = Rapids.exec("(tf-idf " + frameName + ")", sess);
            Assert.assertTrue(resVal instanceof ValFrame);
            Frame resFrame = resVal.getFrame();
            System.out.println(resFrame.toTwoDimTable().toString());
            Scope.track(resFrame);

            assertEquals(expectedOutputFrame.numCols(), resFrame.numCols());
            assertEquals(expectedOutputFrame.numRows(), resFrame.numRows());

            for(int i = 0; i < expectedOutputFrame.numCols(); i++) {
                Vec expectedVec = expectedOutputFrame.vec(i);

                if (expectedVec.get_type() == Vec.T_STR)
                    assertStringVecEquals(expectedVec, resFrame.vec(i));
                else
                    assertVecEquals(expectedVec, resFrame.vec(i), DELTA);
            }
        } finally {
            Scope.exit();
        }
    }

    private Pair<Frame, Frame> getSimpleTestFrames(final String frameName, final Session session) {
        String[] documents = new String[] {
                "A B C",
                "A A A Z",
                "C C B C"
        };
        long[] docIds = new long[] { 0L, 1L, 2L };

        Frame inputFrame = new TestFrameBuilder()
                                .withName(frameName, session)
                                .withColNames("Document ID", "Document")
                                .withVecTypes(Vec.T_NUM, Vec.T_STR)
                                .withDataForCol(0, docIds)
                                .withDataForCol(1, documents)
                                .withChunkLayout(2, 1)
                                .build();

        long[] outDocIds = new long[] {
                0L, 1L, 0L, 2L, 0L, 2L, 1L
        };
        String[] outTokens = new String[] {
                "A", "A", "B", "B", "C", "C", "Z"
        };
        long[] outTFs = new long[] {
                1L, 3L, 1L, 1L, 1L, 3L, 1L
        };
        double[] outIDFs = new double[]{ 
                0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314
        };
        double[] outTFIDFs = new double[]{ 
                0.28768, 0.86304, 0.28768, 0.28768, 0.28768, 0.86304, 0.69314
        };

        Frame expectedOutFrame = new TestFrameBuilder()
                                      .withName("expectedOutputFrame")
                                      .withColNames("Document ID", "Token", "TF", "IDF", "TF-IDF")
                                      .withVecTypes(Vec.T_NUM, Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                                      .withDataForCol(0, outDocIds)
                                      .withDataForCol(1, outTokens)
                                      .withDataForCol(2, outTFs)
                                      .withDataForCol(3, outIDFs)
                                      .withDataForCol(4, outTFIDFs)
                                      .build();

        return new Pair<>(inputFrame, expectedOutFrame);
    }
}

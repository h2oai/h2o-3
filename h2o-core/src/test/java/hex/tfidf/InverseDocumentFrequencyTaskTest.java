package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.assertEquals;


public class InverseDocumentFrequencyTaskTest extends TestUtil {

    private static final double IDF_DELTA = 0.0001;
    private static final int DOCUMENTS_CNT = 3;

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIDFSmallData() {
        Frame fr = getSimpleTestFrame();

        double[] expectedInverseDocumentFrequencies = new double[]{ 0.28768, 0.28768, 0.28768, 
                                                                    0.28768, 0.69314, 0.28768, 0.28768 };

        try {
            InverseDocumentFrequencyTask idfTask = new InverseDocumentFrequencyTask(DOCUMENTS_CNT)
                                                    .doAll(new byte[]{ Vec.T_NUM }, fr.vec(0));
            Vec idfVec = idfTask.outputFrame().anyVec();
            long idfVecSize = idfVec.length();

            assertEquals("Number of inverse document frequency values does not match the expected number.",
                         expectedInverseDocumentFrequencies.length, idfVecSize);

            for (int row = 0; row < idfVecSize; row++)
                assertEquals("Inverse document frequency value mismatch.",
                             expectedInverseDocumentFrequencies[row], idfVec.at(row), IDF_DELTA);

            idfVec.remove();
        } finally {
          fr.remove();
        }
    }

    private Frame getSimpleTestFrame() {
        long[] dfValues = new long[]{ 2, 2, 2, 2, 1, 2, 2 };

        return new TestFrameBuilder()
                    .withName("data")
                    .withColNames("DF")
                    .withVecTypes(Vec.T_NUM)
                    .withDataForCol(0, dfValues)
                    .withChunkLayout(2, 1, 3, 1)
                    .build();
    }
}

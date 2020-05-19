package water.rapids.ast.prims.advmath;

import org.junit.*;
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
import water.util.FileUtils;
import water.util.Pair;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class AstTfIdfTest extends TestUtil {

    private static final String BIGDATA_TESTS_ENV_VAR_NAME = "testTfIdfBigdata";
    private static final double DELTA = 0.0001;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    static public void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testTfIdfSmallData() {
        testTfIdfSmallData(true);
    }
    
    @Test
    public void testTfIdfSmallDataWithoutPreprocessing() {
        testTfIdfSmallData(false);
    }
    
    private static void testTfIdfSmallData(final boolean preprocess) {
        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";

            Pair<Frame,Frame> testFrames = getSimpleTestFrames(frameName, sess, preprocess);
            Frame inputFrame = testFrames._1();
            Frame expectedOutputFrame = testFrames._2();

            Scope.track(inputFrame);
            Scope.track(expectedOutputFrame);

            Val resVal = Rapids.exec("(tf-idf " + frameName + " " + preprocess + ")", sess);
            Assert.assertTrue("Rapid's output is not a H2OFrame.", resVal instanceof ValFrame);
            Frame resFrame = resVal.getFrame();
            Scope.track(resFrame);

            Assert.assertEquals("Number of columns in the TF-IDF output frame does not match the expected number.",
                    expectedOutputFrame.numCols(), resFrame.numCols());
            Assert.assertEquals("Number of rows in the TF-IDF output frame does not match the expected number.",
                    expectedOutputFrame.numRows(), resFrame.numRows());

            for(int i = 0; i < expectedOutputFrame.numCols(); i++) {
                Vec expectedVec = expectedOutputFrame.vec(i);

                if (expectedVec.get_type() == Vec.T_STR)
                    assertStringVecEquals(expectedVec, resFrame.vec(i));
                else
                    assertVecEquals("Vector (at index " + i + ") in the TF-IDF output frame does not mismatch the expected one.",
                            expectedVec, resFrame.vec(i), DELTA);
            }
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testTfIdfBigData() throws IOException {
        Assume.assumeThat("TF-IDF bigdata tests are enabled", System.getProperty(BIGDATA_TESTS_ENV_VAR_NAME), is(notNullValue()));

        System.out.println("Loading data...");
        File testFile = FileUtils.getFile("bigdata/laptop/text8.gz");
        List<String> tokens = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(testFile))))
                .lines().collect(Collectors.toList());
        int tokensCnt = tokens.size();
        System.out.println(tokensCnt + " words have been loaded.");

        int docSplitIndex = tokensCnt / 2;
        List<String> doc1Tokens = tokens.subList(0, docSplitIndex);
        List<String> doc2Tokens = tokens.subList(docSplitIndex, tokensCnt);

        String[] documents = new String[] {
                String.join(" ", doc1Tokens),
                String.join(" ", doc2Tokens)
        };
        long[] docIds = new long[] { 0L, 1L };

        Set<String> doc1UniqueTokens = new HashSet<>(doc1Tokens);
        Set<String> doc2UniqueTokens = new HashSet<>(doc2Tokens);

        Map<String, Long> doc1Counts = tokens.subList(0, docSplitIndex)
                .stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        Map<String, Long> doc2Counts = tokens.subList(docSplitIndex, tokensCnt)
                .stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        Map<String, Long>[] expectedTFs = new Map[]{ doc1Counts, doc2Counts };

        Map<String, Integer> expectedDFs = doc1UniqueTokens.stream().collect(Collectors.toMap(token -> token, token -> 1));
        doc2UniqueTokens.forEach(token -> expectedDFs.merge(token, 1, Integer::sum));
        Map<String, Double> expectedIDFs = expectedDFs.entrySet().stream()
                       .collect(Collectors.toMap(Map.Entry::getKey, entry -> idf(entry.getValue(), documents.length)));

        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";
            
            Frame inputFrame = new TestFrameBuilder()
                                    .withName(frameName, sess)
                                    .withColNames("Document ID", "Document")
                                    .withVecTypes(Vec.T_NUM, Vec.T_STR)
                                    .withDataForCol(0, docIds)
                                    .withDataForCol(1, documents)
                                    .withChunkLayout(1, 1)
                                    .build();
            Scope.track(inputFrame);

            Val resVal = Rapids.exec("(tf-idf " + frameName + " true)", sess);
            Assert.assertTrue(resVal instanceof ValFrame);
            Frame resFrame = resVal.getFrame();
            Scope.track(resFrame);
            
            int expectedNumCols = 5;
            Assert.assertEquals("Number of columns in the TF-IDF output frame does not match the expected number.",
                                expectedNumCols, resFrame.numCols());
            Assert.assertEquals("Number of rows in the TF-IDF output frame does not match the expected number.",
                                doc1UniqueTokens.size() + doc2UniqueTokens.size(), resFrame.numRows());
            
            Vec outDocIds = resFrame.vec(0);
            Vec outTokens = resFrame.vec(1);
            Vec outTF = resFrame.vec(2);
            Vec outIDF = resFrame.vec(3);
            Vec outTFIDF = resFrame.vec(4);

            for (int row = 0; row < resFrame.numRows(); row++) {
                long docId = outDocIds.at8(row);
                String token = outTokens.stringAt(row);
                long tf = outTF.at8(row);
                double idf = outIDF.at(row);
                double tfIdf = outTFIDF.at(row);

                Map<String, Long> docTfValues = expectedTFs[(int) docId];

                Assert.assertTrue("Output token is not in the expected tokens.",
                                  docTfValues.containsKey(token));
                long expectedTf = docTfValues.get(token);
                Assert.assertEquals("Term frequency value mismatch.", expectedTf, tf);

                Assert.assertTrue("Output token is not in the expected tokens.",
                                  expectedIDFs.containsKey(token));
                double expectedIdf = expectedIDFs.get(token);
                Assert.assertEquals("Inverse document frequency value mismatch.", expectedIdf, idf, DELTA);

                Assert.assertTrue("Output token is not in the expected tokens.",
                                  expectedIDFs.containsKey(token));
                Assert.assertEquals("TF-IDF value mismatch.", expectedTf * expectedIdf, tfIdf, DELTA);
            }
        } finally {
            Scope.exit();
        }
    }
    
    private static double idf(long df, long documentsCnt) {
        return Math.log(((double)(documentsCnt + 1)) / (df + 1));
    }

    private static Frame simpleTestFrame(final String frameName, final Session session) {
        String[] documents = new String[] {
                "A B C",
                "A A A Z",
                "C C B C"
        };
        long[] docIds = new long[] { 0L, 1L, 2L };

        return new TestFrameBuilder()
                .withName(frameName, session)
                .withColNames("Document ID", "Document")
                .withVecTypes(Vec.T_NUM, Vec.T_STR)
                .withDataForCol(0, docIds)
                .withDataForCol(1, documents)
                .withChunkLayout(2, 1)
                .build();
    }

    private static Frame preprocessedTestFrame(final String frameName, final Session session) {
        String[] tokens = new String[] {
                "A", "B", "C",
                "A", "A", "A", "Z",
                "C", "C", "B", "C"
        };
        long[] docIds = new long[] { 
                0, 0, 0,
                1, 1, 1, 1,
                2, 2, 2, 2
        };

        return new TestFrameBuilder()
                .withName(frameName, session)
                .withColNames("DocID", "Tokens")
                .withVecTypes(Vec.T_NUM, Vec.T_STR)
                .withDataForCol(0, docIds)
                .withDataForCol(1, tokens)
                .withChunkLayout(5, 3, 1, 2)
                .build();
    }
    
    private static Pair<Frame, Frame> getSimpleTestFrames(final String frameName, 
                                                          final Session session, 
                                                          final boolean preprocessed) {
        Frame inputFrame = preprocessed ? simpleTestFrame(frameName, session) : preprocessedTestFrame(frameName, session);
        
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

package water.rapids.ast.prims.advmath;

import org.apache.log4j.Logger;
import org.junit.Assume;
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
import water.util.FileUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

public class AstTfIdfTest extends TestUtil {
    
    private static final String BIGDATA_TESTS_ENV_VAR_NAME = "testTfIdfBigdata";
    private static final double DELTA = 0.0001;
    
    private static Logger log = Logger.getLogger(AstTfIdfTest.class);
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    static public void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testTfIdfSmallDataCaseIns() {
        testTfIdfSmallData(true, false);
    }
    
    @Test
    public void testTfIdfSmallDataCaseSens() {
        testTfIdfSmallData(true, true);
    }
    
    @Test
    public void testTfIdfSmallDataWithoutPreprocessingCaseIns() {
        testTfIdfSmallData(false, false);
    }
    
    @Test
    public void testTfIdfSmallDataWithoutPreprocessingCaseSens() {
        testTfIdfSmallData(false, true);
    }
    
    private static void testTfIdfSmallData(final boolean preprocess, final boolean caseSensitive) {
        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";
            
            Frame inputFrame = preprocess ? simpleTestFrame(frameName, sess) : preprocessedTestFrame(frameName, sess);
            Frame expectedOutputFrame = caseSensitive ? getCaseSensExpectedOutput() : getCaseInsensExpectedOutput();
            
            Scope.track(inputFrame);
            Scope.track(expectedOutputFrame);

            Val resVal = Rapids.exec(String.format("(tf-idf %s %d %d %b %b)", frameName,  1, 2, preprocess, caseSensitive), sess);
            assertTrue("Rapid's output is not a H2OFrame.", resVal instanceof ValFrame);
            Frame resFrame = resVal.getFrame();
            Scope.track(resFrame);

            assertEquals("Number of columns in the TF-IDF output frame does not match the expected number.",
                         expectedOutputFrame.numCols(), resFrame.numCols());
            assertEquals("Number of rows in the TF-IDF output frame does not match the expected number.",
                         expectedOutputFrame.numRows(), resFrame.numRows());

            for(int i = 0; i < expectedOutputFrame.numCols(); i++) {
                Vec expectedVec = expectedOutputFrame.vec(i);
                Scope.track(expectedVec);
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

        log.debug("Loading data...");
        File testFile = FileUtils.getFile("bigdata/laptop/text8.gz");
        List<String> tokens = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(testFile))))
                                    .lines().collect(Collectors.toList());
        int tokensCnt = tokens.size();
        log.debug(tokensCnt + " words have been loaded.");

        int docSplitIndex = tokensCnt / 2;
        List<String> doc1Tokens = tokens.subList(0, docSplitIndex);
        List<String> doc2Tokens = tokens.subList(docSplitIndex, tokensCnt);

        String[] documents = new String[] {
                String.join(" ", doc1Tokens),
                String.join(" ", doc2Tokens)
        };
        long[] docIds = new long[] { 0, 1 };

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

            log.debug("Computing TF-IDF...");
            Val resVal = Rapids.exec(String.format("(tf-idf %s %d %d true true)", frameName,  0, 1), sess);
            assertTrue(resVal instanceof ValFrame);
            Frame resFrame = resVal.getFrame();
            Scope.track(resFrame);

            int expectedNumCols = 5;
            assertEquals("Number of columns in the TF-IDF output frame does not match the expected number.",
                         expectedNumCols, resFrame.numCols());
            assertEquals("Number of rows in the TF-IDF output frame does not match the expected number.",
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

                assertTrue("Output token is not in the expected tokens.", docTfValues.containsKey(token));
                long expectedTf = docTfValues.get(token);
                assertEquals("Term frequency value mismatch.", expectedTf, tf);

                assertTrue("Output token is not in the expected tokens.", expectedIDFs.containsKey(token));
                double expectedIdf = expectedIDFs.get(token);
                assertEquals("Inverse document frequency value mismatch.", expectedIdf, idf, DELTA);

                assertTrue("Output token is not in the expected tokens.", expectedIDFs.containsKey(token));
                assertEquals("TF-IDF value mismatch.", expectedTf * expectedIdf, tfIdf, DELTA);
            }

            if (log.isDebugEnabled()) {
                log.debug(resFrame.toTwoDimTable().toString());
                log.debug("TF-IDF testing finished.");
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIncorrectInputTypes() {
        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";
            Frame inputFrame = new TestFrameBuilder()
                                    .withName(frameName, sess)
                                    .withVecTypes(Vec.T_STR, Vec.T_STR)
                                    .withDataForCol(0, new String[]{ "a" })
                                    .withDataForCol(1, new String[]{ "b" })
                                    .build();
            Scope.track(inputFrame);

            testIncorrectInput(sess, frameName, 0, 1);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testEmptyInput() {
        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";
            Frame inputFrame = new TestFrameBuilder()
                                    .withName(frameName, sess)
                                    .withVecTypes(Vec.T_NUM, Vec.T_STR)
                                    .withDataForCol(0, new long[]{})
                                    .withDataForCol(1, new String[]{})
                                    .build();
            Scope.track(inputFrame);

            testIncorrectInput(sess, frameName, 0, 1);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testOutOfBoundsColumn() {
        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";
            Frame inputFrame = new TestFrameBuilder()
                                    .withName(frameName, sess)
                                    .withVecTypes(Vec.T_NUM)
                                    .withDataForCol(0, new long[]{ 1, 2 })
                                    .build();
            Scope.track(inputFrame);

            testIncorrectInput(sess, frameName, 1, 2);
        } finally {
            Scope.exit();
        }
    }

    private static void testIncorrectInput(final Session sess, final String frameName, 
                                           final int docIdsColIdx, final int contentsColIdx) {
        try {
            Rapids.exec(String.format("(tf-idf %s %d %d true true)", frameName,  docIdsColIdx, contentsColIdx), sess);
            fail("IllegalArgumentException is expected for incorrect input.");
        } catch(IllegalArgumentException ignored) {}
    }

    private static double idf(long df, long documentsCnt) {
        return Math.log(((double)(documentsCnt + 1)) / (df + 1));
    }

    private static Frame simpleTestFrame(final String frameName, final Session session) {
        String[] documents = new String[] {
                "A B C",
                "A a a Z",
                "C c B C"
        };
        long[] docIds = new long[] { 0, 1, 2 };

        return getInputFrame(frameName, session, docIds, documents, new long[]{ 2, 1 });
    }

    private static Frame preprocessedTestFrame(final String frameName, final Session session) {
        String[] tokens = new String[] {
                "A", "B", "C",
                "A", "a", "a", "Z",
                "C", "c", "B", "C"
        };
        long[] docIds = new long[] { 
                0, 0, 0,
                1, 1, 1, 1,
                2, 2, 2, 2
        };
        
        return getInputFrame(frameName, session, docIds, tokens, new long[]{ 5, 3, 1, 2 });
    }
    
    private static Frame getInputFrame(final String frameName, 
                                       final Session session,
                                       final long[] docIds, 
                                       final String[] stringContent, 
                                       final long[] chunkLayout) {
        long[] colValues = new long[docIds.length];
        Arrays.fill(colValues, 1);

        return new TestFrameBuilder()
                    .withName(frameName, session)
                    .withColNames("Col", "DocID", "Str")
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR)
                    .withDataForCol(0, colValues)
                    .withDataForCol(1, docIds)
                    .withDataForCol(2, stringContent)
                    .withChunkLayout(chunkLayout)
                    .build();
    }
    
    private static Frame getCaseSensExpectedOutput() {
        long[] outDocIds = new long[] {
                0, 1, 0, 2, 0, 2, 1, 1, 2
        };
        String[] outTokens = new String[] {
                "A", "A", "B", "B", "C", "C", "Z", "a", "c"
        };
        long[] outTFs = new long[] {
                1, 1, 1, 1, 1, 2, 1, 2, 1
        };
        double[] outIDFs = new double[]{
                0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314, 0.69314, 0.69314
        };
        double[] outTFIDFs = new double[]{
                0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.57536, 0.69314, 1.38629, 0.69314
        };

        return getExpectedOutputFrame(outDocIds, outTokens, outTFs, outIDFs, outTFIDFs);
    }

    private static Frame getCaseInsensExpectedOutput() {
        long[] outDocIds = new long[] {
                0, 1, 0, 2, 0, 2, 1
        };
        String[] outTokens = new String[] {
                "a", "a", "b", "b", "c", "c", "z"
        };
        long[] outTFs = new long[] {
                1, 3, 1, 1, 1, 3, 1
        };
        double[] outIDFs = new double[]{
                0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314
        };
        double[] outTFIDFs = new double[]{
                0.28768, 0.86304, 0.28768, 0.28768, 0.28768, 0.86304, 0.69314
        };

        return getExpectedOutputFrame(outDocIds, outTokens, outTFs, outIDFs, outTFIDFs);
    }

    private static Frame getExpectedOutputFrame(final long[] outDocIds,
                                                final String[] outTokens,
                                                final long[] outTFs,
                                                final double[] outIDFs,
                                                final double[] outTFIDFs) {
        return new TestFrameBuilder()
                    .withName("expectedOutputFrame")
                    .withColNames("Document ID", "Token", "TF", "IDF", "TF-IDF")
                    .withVecTypes(Vec.T_NUM, Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, outDocIds)
                    .withDataForCol(1, outTokens)
                    .withDataForCol(2, outTFs)
                    .withDataForCol(3, outIDFs)
                    .withDataForCol(4, outTFIDFs)
                    .build();
    }
}

package hex.tfidf;

import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.FileUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

public class TermFrequencyTaskTest extends TestUtil {

    private static final String BIGDATA_TESTS_ENV_VAR_NAME = "testTFBigdata";

    private static Logger log = Logger.getLogger(TermFrequencyTaskTest.class);

    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testTermFrequenciesSmallData() {
        Frame fr = getSimpleTestFrame();

        Map<String, Long>[] expectedTermFrequencies = new Map[]{
                new HashMap() {{
                    put("A", 1L);
                    put("B", 1L);
                    put("C", 1L);
                }},
                new HashMap() {{
                    put("A", 3L);
                    put("Z", 1L);
                }},
                new HashMap() {{
                    put("B", 1L);
                    put("C", 3L);
                }},
        };

        try {
            Frame outputFrame = TermFrequencyTask.compute(fr);
            checkTfOutput(outputFrame, expectedTermFrequencies);

            outputFrame.remove();
        } finally {
            fr.remove();
        }
    }

    @Test
    public void testTermFrequenciesBigData() throws IOException {
        assumeThat("TF bigdata tests are enabled", System.getProperty(BIGDATA_TESTS_ENV_VAR_NAME), is(notNullValue()));

        log.debug("Loading data...");
        File testFile = FileUtils.getFile("bigdata/laptop/text8.gz");
        List<String> tokens = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(testFile))))
                                .lines().collect(Collectors.toList());
        int tokensCnt = tokens.size();
        log.debug(tokensCnt + " words have been loaded.");

        int docSplitIndex = tokensCnt / 2;
        long[] docIds = new long[tokensCnt];
        Arrays.fill(docIds, 0, docSplitIndex, 0);
        Arrays.fill(docIds, docSplitIndex, tokensCnt, 1);

        int chunksCnt = 30;
        List<Integer> chunkLayout = new ArrayList<>(Collections.nCopies(chunksCnt, tokensCnt / chunksCnt));

        int divRemainder = tokensCnt % chunksCnt;
        if (divRemainder != 0)
            chunkLayout.add(divRemainder);

        Frame fr = new TestFrameBuilder()
                        .withName("data")
                        .withColNames("DocID", "Token")
                        .withVecTypes(Vec.T_NUM, Vec.T_STR)
                        .withDataForCol(0, docIds)
                        .withDataForCol(1, tokens.toArray(new String[0]))
                        .withChunkLayout(chunkLayout.stream().mapToLong(Integer::intValue).toArray())
                        .build();

        Map<String, Long> doc1Counts = tokens.subList(0, docSplitIndex)
                .stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        Map<String, Long> doc2Counts = tokens.subList(docSplitIndex, tokensCnt)
                .stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        Map<String, Long>[] expectedTFValues = new Map[]{doc1Counts, doc2Counts};

        log.debug("Computing TF...");
        try {
            Frame outputFrame = TermFrequencyTask.compute(fr);
            checkTfOutput(outputFrame, expectedTFValues);

            if (log.isDebugEnabled()) {
                log.debug(outputFrame.toTwoDimTable().toString());
                log.debug("TF testing finished.");
            }
            outputFrame.remove();
        } finally {
            fr.remove();
        }
    }

    private static void checkTfOutput(final Frame outputFrame, final Map<String, Long>[] expectedTermFrequencies) {
        long outputRowsCnt = outputFrame.numRows();
        long expectedTfValuesCnt = Arrays.stream(expectedTermFrequencies).mapToLong(docTFs -> docTFs.size()).sum();
        assertEquals("Number of term frequency values does not match the expected number.",
                     expectedTfValuesCnt, outputRowsCnt);

        Vec outputDocIds = outputFrame.vec(0);
        Vec outputTokens = outputFrame.vec(1);
        Vec outputTFs = outputFrame.vec(2);

        for (int row = 0; row < outputRowsCnt; row++) {
            String token = outputTokens.stringAt(row);
            Map<String, Long> expectedDocTFs = expectedTermFrequencies[(int) outputDocIds.at8(row)];

            assertTrue("Output token is not in the expected tokens.",
                       expectedDocTFs.containsKey(token));
            long expectedTF = expectedDocTFs.get(token);

            assertEquals("Term frequency value mismatch.",
                         expectedTF, outputTFs.at8(row));
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

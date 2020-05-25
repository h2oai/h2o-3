package hex.tfidf;

import org.apache.log4j.Logger;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.FileUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DocumentFrequencyTaskTest extends TestUtil {

    private static final String BIGDATA_TESTS_ENV_VAR_NAME = "testDFBigdata";

    private static Logger log = Logger.getLogger(DocumentFrequencyTaskTest.class);
    
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

        try {
            Frame outputFrame = DocumentFrequencyTask.compute(fr);

            checkDfOutput(outputFrame, expectedDocumentFrequencies);

            outputFrame.remove();
        } finally {
            fr.remove();
        }
    }
    
    @Test
    public void testDocumentFrequenciesBigData() throws IOException {
        Assume.assumeThat("DF bigdata tests are enabled", System.getProperty(BIGDATA_TESTS_ENV_VAR_NAME), is(notNullValue()));

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
        List<Integer> chunkLayout = new ArrayList<>(Collections.nCopies(chunksCnt, tokensCnt/chunksCnt));

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
        Frame uniqueWordsPerDocFrame = new AstGroup().performGroupingWithAggregations(fr,
                                                                                      new int[]{ 0, 1 },
                                                                                      new AstGroup.AGG[]{}).getFrame();

        Set<String> doc1Tokens = new HashSet<>(tokens.subList(0, docSplitIndex));
        Set<String> doc2Tokens = new HashSet<>(tokens.subList(docSplitIndex, tokensCnt));
        
        Map<String, Long> expectedDFs = doc1Tokens.stream().collect(Collectors.toMap(token -> token, token -> 1L));
        doc2Tokens.forEach(token -> expectedDFs.merge(token, 1L, Long::sum));

        log.debug("Computing DF...");
        try {
            Frame outputFrame = DocumentFrequencyTask.compute(uniqueWordsPerDocFrame);

            checkDfOutput(outputFrame, expectedDFs);

            if (log.isDebugEnabled()) {
                log.debug(outputFrame.toTwoDimTable().toString());
                log.debug("DF testing finished.");
            }
            outputFrame.remove();
        } finally {
            fr.remove();
            uniqueWordsPerDocFrame.remove();
        }
    }
    
    private static void checkDfOutput(final Frame outputFrame, final Map<String, Long> expectedDocumentFrequencies) {
        int expectedDfValuesCnt = expectedDocumentFrequencies.size();
        long outputRowsCnt = outputFrame.numRows();

        assertEquals("Number of document frequency values does not match the expected number.",
                     expectedDfValuesCnt, outputRowsCnt);

        Vec outputTokens = outputFrame.vec(0);
        Vec outputDFs = outputFrame.vec(1);

        for(int row = 0; row < outputRowsCnt; row++) {
            String token = outputTokens.stringAt(row);

            assertTrue("Output token is not in the expected tokens.", expectedDocumentFrequencies.containsKey(token));
            long expectedDF = expectedDocumentFrequencies.get(token);

            assertEquals("Document frequency value mismatch.", expectedDF, outputDFs.at8(row));
        }
    }

    private static Frame getSimpleTestFrame() {
        long[] docIds = new long[] {
                0L, 0L, 0L,
                1L, 1L,
                2L, 2L
        };
        String[] tokens = new String[] {
                "A", "B", "C",
                "A", "Z",
                "C", "B"
        };

        return new TestFrameBuilder()
                    .withName("data")
                    .withColNames("Document ID", "Token")
                    .withVecTypes(Vec.T_NUM, Vec.T_STR)
                    .withDataForCol(0, docIds)
                    .withDataForCol(1, tokens)
                    .withChunkLayout(2, 3, 1, 1)
                    .build();
    }
}

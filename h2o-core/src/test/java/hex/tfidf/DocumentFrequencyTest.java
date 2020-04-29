package hex.tfidf;

import org.junit.Assert;
import org.junit.Assume;
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

public class DocumentFrequencyTest extends TestUtil {

    private static final String BIGDATA_TESTS_ENV_VAR_NAME = "testDFBigdata";
    
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
    
    @Test
    public void testDocumentFrequenciesBigData() throws IOException {
        Assume.assumeThat("DF bigdata tests are enabled", System.getProperty(BIGDATA_TESTS_ENV_VAR_NAME), is(notNullValue()));

        System.out.println("Loading data...");
        File testFile = FileUtils.getFile("bigdata/laptop/text8.gz");
        List<String> tokens = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(testFile))))
                .lines().collect(Collectors.toList());
        int tokensCnt = tokens.size();
        System.out.println(tokensCnt + " words have been loaded.");

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

        Set<String> doc1Tokens = new HashSet<>(tokens.subList(0, docSplitIndex));
        Set<String> doc2Tokens = new HashSet<>(tokens.subList(docSplitIndex, tokensCnt));
        
        Map<String, Integer> expectedDFs = doc1Tokens.stream().collect(Collectors.toMap(token -> token, token -> 1));
        doc2Tokens.forEach(token -> expectedDFs.merge(token, 1, Integer::sum));
        int expectedDfValuesCnt = expectedDFs.size();

        System.out.println("Computing DF...");
        try {
            DocumentFrequency df = new DocumentFrequency();
            Frame outputFrame = df.compute(fr);
            long outputRowsCnt = outputFrame.numRows();

            Assert.assertEquals(expectedDfValuesCnt, outputRowsCnt);

            Vec outputTokens = outputFrame.vec(0);
            Vec outputDFs = outputFrame.vec(1);

            for(int row = 0; row < outputRowsCnt; row++) {
                String token = outputTokens.stringAt(row);

                Assert.assertTrue(expectedDFs.containsKey(token));
                long expectedTF = expectedDFs.get(token);

                Assert.assertEquals(expectedTF, outputDFs.at8(row));
            }

            System.out.println(outputFrame.toTwoDimTable().toString());
            System.out.println("DF testing finished.");
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

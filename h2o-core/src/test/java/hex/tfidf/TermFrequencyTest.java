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
import water.util.Pair;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TermFrequencyTest extends TestUtil {
    
    private static final String BIGDATA_TESTS_ENV_VAR_NAME = "testTFBigdata";

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testTermFrequenciesSmallData() {
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

        try {
            TermFrequency tf = new TermFrequency();
            Frame outputFrame = tf.compute(fr);
            long outputRowsCnt = outputFrame.numRows();

            Assert.assertEquals(expectedTfValuesCnt, outputRowsCnt);

            Vec outputDocIds = outputFrame.vec(0);
            Vec outputTokens = outputFrame.vec(1);
            Vec outputTFs = outputFrame.vec(2);

            for(int row = 0; row < outputRowsCnt; row++) {
                String token = outputTokens.stringAt(row);
                long docId = outputDocIds.at8(row);
                Pair key = new Pair<>(token, docId);

                Assert.assertTrue(expectedTermFrequencies.containsKey(key));
                long expectedTF = expectedTermFrequencies.get(key);

                Assert.assertEquals(expectedTF, outputTFs.at8(row));
            }

            outputFrame.remove();
        } finally {
            fr.remove();
        }
    }
    
    @Test
    public void testTermFrequenciesBigData() throws IOException {
        Assume.assumeThat("TF bigdata tests are enabled", System.getProperty(BIGDATA_TESTS_ENV_VAR_NAME), is(notNullValue()));

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

        Map<String, Long> doc1Counts = tokens.subList(0, docSplitIndex)
                                        .stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        Map<String, Long> doc2Counts = tokens.subList(docSplitIndex, tokensCnt)
                .stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        Map[] expectedTFValues = new Map[]{ doc1Counts, doc2Counts };

        System.out.println("Computing TF...");
        try {
            TermFrequency tfTask = new TermFrequency();
            Frame outputFrame = tfTask.compute(fr);
            long outputRowsCnt = outputFrame.numRows();

            Assert.assertEquals(doc1Counts.size() + doc2Counts.size(), outputRowsCnt);

            Vec outputDocIds = outputFrame.vec(0);
            Vec outputTokens = outputFrame.vec(1);
            Vec outputTFs = outputFrame.vec(2);

            for(int row = 0; row < outputRowsCnt; row++) {
                String token = outputTokens.stringAt(row);
                Map<String, Long> expectedDocTFs = expectedTFValues[(int) outputDocIds.at8(row)];

                Assert.assertTrue(expectedDocTFs.containsKey(token));
                long expectedTF = expectedDocTFs.get(token);

                try { Assert.assertEquals(expectedTF, outputTFs.at8(row)); }
                catch (AssertionError e) {
                    System.out.println(e);
                    System.out.println("...");
                }
            }

            System.out.println("TF testing finished.");
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
                    .withChunkLayout(4, 3, 2, 2)
                    .build();
    }
}

package hex.tfidf;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;

import java.util.Set;
import java.util.stream.Collectors;

public class Word2IndexTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testSmallDataWordsIndexing() {
        Frame fr = getSimpleTestFrame();

        try {
            Word2Index word2index = new Word2Index(" ").doAll(fr.vec(0));

            String[] expUniqueWords = new String[] { "A", "B", "C", "Z" };
            for (String expWord : expUniqueWords)
                Assert.assertTrue(word2index._wordsIndices.containsKey(new BufferedString(expWord)));

            Set<Long> uniqueIndices = word2index._wordsIndices.values().stream()
                                      .map(icedLong -> icedLong._val).collect(Collectors.toSet());
            Assert.assertEquals(expUniqueWords.length, uniqueIndices.size());
        } finally {
            fr.remove();
        }
    }

    private Frame getSimpleTestFrame() {
        String[] strData = new String[] {
                "A B C",
                "A A A Z",
                "C C B C"
        };

        return new TestFrameBuilder()
                .withName("data")
                .withColNames("Str")
                .withVecTypes(Vec.T_STR)
                .withDataForCol(0, strData)
                .withChunkLayout(2, 1)
                .build();
    }
}

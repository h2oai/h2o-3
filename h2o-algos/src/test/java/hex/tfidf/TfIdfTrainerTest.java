package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.IcedHashMap;
import water.util.IcedInt;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TfIdfTrainerTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testWordsCounts() {
        Frame fr = getSimpleTestFrame();

        IcedHashMap<BufferedString, IcedInt> wordsIndices = new IcedHashMap<>();
        wordsIndices.put(new BufferedString("A"), new IcedInt(0));
        wordsIndices.put(new BufferedString("B"), new IcedInt(1));
        wordsIndices.put(new BufferedString("C"), new IcedInt(2));
        wordsIndices.put(new BufferedString("Z"), new IcedInt(3));

        try {
            TfIdfTrainer trainer = new TfIdfTrainer(wordsIndices).doAll(wordsIndices.size(), Vec.T_NUM, fr);
            Frame outputFrame = trainer.outputFrame();

            Map<String, int[]> expWordsCounts = new HashMap<String, int[]>() {{
                put("A", new int[]{1,3,0});
                put("B", new int[]{1,0,1});
                put("C", new int[]{1,0,3});
                put("Z", new int[]{0,1,0});
            }};

            expWordsCounts.forEach((word, expCounts) -> {
                Vec actualCounts = outputFrame.vec(wordsIndices.get(new BufferedString(word))._val);
                for (int i = 0; i < expCounts.length; i++)
                    assertEquals(expCounts[i], actualCounts.at8(i));
            });

            outputFrame.remove();
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

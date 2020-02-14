package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.IcedLong;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TfIdfTrainerTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testTotalWordsCounts() {
        Frame fr = getSimpleTestFrame();

        try {
            TfIdfTrainer trainer = new TfIdfTrainer().doAll(fr.vec(0));

            int[] expTotalWordsCounts = new int[] { 3, 4, 4 };
            for (int i = 0; i < expTotalWordsCounts.length; i++)
                assertEquals(expTotalWordsCounts[i], trainer._totalWordsCounts[i]._val);
        } finally {
            fr.remove();
        }
    }

    @Test
    public void testWordsCounts() {
        Frame fr = getSimpleTestFrame();

        try {
            TfIdfTrainer trainer = new TfIdfTrainer().doAll(fr.vec(0));

            Map<String, int[]> expWordsCounts = new HashMap<String, int[]>() {{
                put("A", new int[]{1,3,0});
                put("B", new int[]{1,0,1});
                put("C", new int[]{1,0,3});
                put("Z", new int[]{0,1,0});
            }};
            expWordsCounts.forEach((word, expCounts) -> {
                IcedLong[] actualCounts = trainer._wordsCounts.get(new BufferedString(word));
                for (int i = 0; i < expCounts.length; i++)
                    assertEquals(expCounts[i], actualCounts[i]._val);
            });
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
                .build();
    }
}

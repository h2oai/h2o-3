package water.parser.orc;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.parser.ParseSetup;

/**
 * Test suite for orc parser.
 * <p>
 * This test will attempt to perform multi-file parsing of a csv and orc file and compare
 * the frame summary statistics to make sure they are equivalent.
 * <p>
 * -- Requested by Tomas N.
 */
@RunWith(Parameterized.class)
public class ParseTestMultiFileOrc extends TestUtil {

    private static final String CSV_FILE = "smalldata/synthetic_perfect_separation/combined.csv";
    private static final String ORC_DIR = "smalldata/parser/orc/synthetic_perfect_separation/";

    @Parameterized.Parameters
    public static Object[] params() {
        return new Object[] { false, true };
    }

    @Parameterized.Parameter
    public boolean disableParallelParse;

    @BeforeClass
    static public void setup() {
        TestUtil.stall_till_cloudsize(1);
    }

    @Test
    public void testParseMultiFileOrcs() {
        final ParseSetupTransformer pst = new ParseSetupTransformer() {
            @Override
            public ParseSetup transformSetup(ParseSetup guessedSetup) {
                guessedSetup.disableParallelParse = disableParallelParse;
                return guessedSetup;
            }
        };
        Scope.enter();
        try {
            Frame csv_frame = Scope.track(parseTestFile(CSV_FILE, "\\N", 0, null, pst));
            byte[] types = csv_frame.types();
            for (int index = 0; index < types.length; index++) {
                if (types[index] == 0)
                    types[index] = 4;
            }
            Frame orc_frame = Scope.track(parseTestFolder(ORC_DIR, null, 0, types, pst));
            assertIdenticalUpToRelTolerance(csv_frame, orc_frame, 1e-5);
        } finally {
            Scope.exit();
        }
    }
}

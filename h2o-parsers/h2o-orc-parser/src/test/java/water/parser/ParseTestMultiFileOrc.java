package water.parser;


import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.assertTrue;

/**
 * Test suite for orc parser.
 *
 * This test will attempt to perform multi-file parsing of a csv and orc file and compare
 * the frame summary statistics to make sure they are equivalent.
 *
 * -- Requested by Tomas N.
 *
 */
public class ParseTestMultiFileOrc extends TestUtil {

    private double EPSILON = 1e-9;
    private long ERRORMARGIN = 1000L;  // error margin when compare timestamp.
    int totalFilesTested = 0;
    int numberWrong = 0;

    private String[] csvDirectories = {"smalldata/smalldata/synthetic_perfect_separation/"};
    private String[] orcDirectories = {"smalldata/parser/orc/synthetic_perfect_separation/"};

    @BeforeClass
    static public void _preconditionJavaVersion() { // NOTE: the `_` force execution of this check after setup
        // Does not run test on Java6 since we are running on Hadoop lib
        Assume.assumeTrue("Java6 is not supported", !System.getProperty("java.version", "NA").startsWith("1.6"));
    }

    @BeforeClass
    static public void setup() { TestUtil.stall_till_cloudsize(1); }

    @Test
    public void testParseMultiFileOrcs() {

        for (int f_index = 0; f_index < csvDirectories.length; f_index++) {
            Frame csv_frame = parse_test_folder(csvDirectories[f_index], "\\N", 0, null);

            byte[] types = csv_frame.types();

            for (int index = 0; index < types.length; index++) {
                if (types[index] == 0)
                    types[index] = 4;
            }

            Frame orc_frame = parse_test_folder(orcDirectories[f_index], null, 0, types);
            assertTrue(TestUtil.isIdenticalUpToRelTolerance(csv_frame, orc_frame, 1e-5));

            csv_frame.delete();
            orc_frame.delete();
        }
    }
}
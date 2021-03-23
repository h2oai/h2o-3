package water.parser.orc;


import org.joda.time.DateTimeZone;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.parser.ParseTime;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for orc parser.
 * <p>
 * This test will attempt to parse a bunch of files (orc and csv).  We compare the frames of these files and make
 * sure that they are equivalent.
 * <p>
 * -- Requested by Tomas N.
 */
public class ParseTestORCCSV extends TestUtil {

    private String[] csvFiles = {"smalldata/parser/orc/orc2csv/testTimeStamp.csv",
        "smalldata/parser/orc/orc2csv/testDate1900.csv",
        "smalldata/parser/orc/orc2csv/testDate2038.csv"};

    private String[] orcFiles = {"smalldata/parser/orc/testTimeStamp.orc",
        "smalldata/parser/orc/TestOrcFile.testDate1900.orc",
        "smalldata/parser/orc/TestOrcFile.testDate2038.orc"};

    @BeforeClass
    static public void setup() {
        TestUtil.stall_till_cloudsize(5);
    }

    @Test
    public void testSkippedAllColumns() {
        Scope.enter();
        try {
            int[] skipped_columns = new int[]{0, 1};
            Scope.track(parseTestFile(orcFiles[0], skipped_columns));
            fail("orc skipped all columns test failed...");
        } catch (Exception ex) {
            System.out.println("Skipped all columns test passed!");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testParseOrcCsvFiles() {
        Scope.enter();
        DateTimeZone currTimeZn = ParseTime.getTimezone();
        if (currTimeZn.getID().matches("America/Los_Angeles")) {
            try {
                for (int f_index = 0; f_index < csvFiles.length; f_index++) {

                    Frame csv_frame = Scope.track(parseTestFile(csvFiles[f_index], "\\N", 0, null));
                    Frame orc_frame = Scope.track(parseTestFile(orcFiles[f_index], null, 0, null));

                    assertIdenticalUpToRelTolerance(orc_frame, csv_frame, 0, 
                        "Fails: " + csvFiles[f_index] + " != " + orcFiles[f_index]);
                }
            } finally {
                Scope.exit();
            }
        }
    }
}

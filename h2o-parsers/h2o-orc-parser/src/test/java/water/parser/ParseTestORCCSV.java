package water.parser;


import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import static java.lang.Math.abs;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for orc parser.
 *
 * This test will attempt to parse a bunch of files (orc and csv) containing only timestamp and data data.
 * We compare the frames of these files and make sure that they are equivalent.
 *
 * -- Requested by Tomas N.
 *
 */
public class ParseTestORCCSV extends TestUtil {

    private String[] csvFiles = {"smalldata/parser/orc/orc2csv/testTimestamp.csv",
            "smalldata/parser/orc/orc2csv/testDate1900.csv",
            "smalldata/parser/orc/orc2csv/testDate2038.csv"};

    private String[] orcFiles = {"smalldata/parser/orc/testTimestamp.orc",
            "smalldata/parser/orc/TestOrcFile.testDate1900.orc",
            "smalldata/parser/orc/TestOrcFile.testDate2038.orc"};

    private Boolean[] forceColumnTypes = {false, false, false, false, true, true, true};

    @BeforeClass
    static public void _preconditionJavaVersion() { // NOTE: the `_` force execution of this check after setup
        // Does not run test on Java6 since we are running on Hadoop lib
        Assume.assumeTrue("Java6 is not supported", !System.getProperty("java.version",
                "NA").startsWith("1.6"));
    }

    @BeforeClass
    static public void setup() { TestUtil.stall_till_cloudsize(1); }

    @Test
    public void testParseOrcCsvFiles() {
        for (int f_index = 0; f_index < csvFiles.length; f_index++) {

            Frame csv_frame = parse_test_file(csvFiles[f_index], "\\N", 0, null);
            Frame orc_frame = parse_test_file(orcFiles[f_index], null, 0, null);

            // make sure column types are the same especially the enums
            byte[] csv_types = csv_frame.types();
            byte[] orc_types = orc_frame.types();

            for (int index = 0; index < csv_frame.numCols(); index++) {
                if ((csv_types[index] == 4) && (orc_types[index] == 2)) {
                    orc_frame.replace(index, orc_frame.vec(index).toCategoricalVec().toNumericVec());
                    csv_frame.replace(index, csv_frame.vec(index).toNumericVec());
                }
            }

            for (int col_index = 0; col_index < 2; col_index++) {
                for (int row_index = 0; row_index < orc_frame.numRows(); row_index++) {
                    long orc = orc_frame.vec(col_index).at8(row_index);
                    long csv = csv_frame.vec(col_index).at8(row_index);
                    long diff = orc - csv;
                    if (abs(diff) > 0) {
                        Log.info("**orc csv ts differ at row " + row_index + " col " + col_index + " and the difference is "
                                + Long.toString(diff));
                    }
                }
            }

            assertTrue(TestUtil.isBitIdentical(orc_frame, csv_frame));

            csv_frame.delete();
            orc_frame.delete();
        }
    }
}
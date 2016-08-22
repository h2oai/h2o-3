package water.parser;


import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.assertTrue;

/**
 * Test suite for orc parser.
 *
 * This test will attempt to parse a bunch of files (orc and csv).  We compare the frames of these files and make
 * sure that they are equivalent.
 *
 * -- Requested by Tomas N.
 *
 */
public class ParseTestORCCSV extends TestUtil {

    private double EPSILON = 1e-9;
    private long ERRORMARGIN = 1000L;  // error margin when compare timestamp.
    int totalFilesTested = 0;
    int numberWrong = 0;

    private String[] csvFiles = {"smalldata/parser/orc/orc2csv/TestOrcFile.testDate1900.csv",
            "smalldata/parser/orc/orc2csv/TestOrcFile.testDate2038.csv",
            "smalldata/parser/orc/orc2csv/orc_split_elim.csv", "smalldata/parser/csv2orc/prostate_NA.csv",
            "smalldata/iris/iris.csv", "smalldata/jira/hexdev_29.csv"};

    private String[] orcFiles = {"smalldata/parser/orc/TestOrcFile.testDate1900.orc",
            "smalldata/parser/orc/TestOrcFile.testDate2038.orc", "smalldata/parser/orc/orc_split_elim.orc",
            "smalldata/parser/orc/prostate_NA.orc", "smalldata/parser/orc/iris.orc",
            "smalldata/parser/orc/hexdev_29.orc"};

    private Boolean[] forceColumnTypes = {false, false, false, true, true, true};

    @BeforeClass
    static public void setup() { TestUtil.stall_till_cloudsize(5); }

    @Test
    public void testParseOrcCsvFiles() {
        int f_index = 0;
        Frame csv_frame = parse_test_file(csvFiles[f_index], "\\N", 0, null);
        Frame orc_frame = null;

        if (forceColumnTypes[f_index]) {
            byte[] types = csv_frame.types();

            for (int index = 0; index < types.length; index++) {
                if (types[index] == 0)
                    types[index] = 3;
            }

            orc_frame = parse_test_file(orcFiles[f_index], null, 0, types);
        } else {
            orc_frame = parse_test_file(orcFiles[f_index], null, 0, null);
        }


        // make sure column types are the same especially the enums
            byte[] csv_types = csv_frame.types();
            byte[] orc_types = orc_frame.types();

            for (int index = 0; index < csv_frame.numCols(); index++) {
                if ((csv_types[index] == 4) && (orc_types[index] == 2)) {
                    orc_frame.replace(index, orc_frame.vec(index).toCategoricalVec().toNumericVec());
                    csv_frame.replace(index, csv_frame.vec(index).toNumericVec());
                }
            }

        assertTrue(TestUtil.isIdenticalUpToRelTolerance(csv_frame, orc_frame, 1e-5));

        csv_frame.delete();
        orc_frame.delete();
    }
}
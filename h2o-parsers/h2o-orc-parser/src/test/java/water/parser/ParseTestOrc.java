package water.parser;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static water.parser.OrcTestUtils.compareOrcAndH2OFrame;

/**
 * Test suite for orc parser.
 *
 * This test will build a H2O frame for all orc files found in smalldata/parser/orc directory
 * and compare the H2O frame content with the orc file content read with Core Java commands.
 * Test is declared a success if the content of H2O frame is the same as the contents read
 * by using core Java commands off the Orc file itself.  No multi-threading is used in reading
 * off the Orc file using core Java commands.
 */
public class ParseTestOrc extends TestUtil {

    int totalFilesTested = 0;
    int numberWrong = 0;

    // list all orc files in smalldata/parser/orc directory
    private String[] allOrcFiles = {
            "smalldata/parser/orc/TestOrcFile.columnProjection.orc",
            "smalldata/parser/orc/bigint_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.emptyFile.orc",
            "smalldata/parser/orc/bool_single_col.orc",
//      "smalldata/parser/orc/TestOrcFile.metaData.orc", // do not support metadata from user
//      "smalldata/parser/orc/decimal.orc",
//      "smalldata/parser/orc/TestOrcFile.test1.orc",    // do not support metadata from user
            "smalldata/parser/orc/demo-11-zlib.orc",
            "smalldata/parser/orc/TestOrcFile.testDate1900.orc",
            "smalldata/parser/orc/demo-12-zlib.orc",
            "smalldata/parser/orc/TestOrcFile.testDate2038.orc",
            "smalldata/parser/orc/double_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testMemoryManagementV11.orc",
            "smalldata/parser/orc/float_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testMemoryManagementV12.orc",
            "smalldata/parser/orc/int_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testPredicatePushdown.orc",
            "smalldata/parser/orc/nulls-at-end-snappy.orc",
//      "smalldata/parser/orc/TestOrcFile.testSeek.orc", // do not support metadata from user
//      "smalldata/parser/orc/orc-file-11-format.orc",   // different column names are used between stripes
            "smalldata/parser/orc/TestOrcFile.testSnappy.orc",
            "smalldata/parser/orc/orc_split_elim.orc",
            "smalldata/parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc",
//          "smalldata/parser/orc/over1k_bloom.orc", // do not support metadata from user
            "smalldata/parser/orc/TestOrcFile.testStripeLevelStats.orc",
            "smalldata/parser/orc/smallint_single_col.orc",
//          "smalldata/parser/orc/TestOrcFile.testTimestamp.orc", // abnormal orc file, no inpsector structure available
            "smalldata/parser/orc/string_single_col.orc",
//          "smalldata/parser/orc/TestOrcFile.testUnionAndTimestamp.orc", // do not support metadata from user
            "smalldata/parser/orc/tinyint_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testWithoutIndex.orc",
//          "smalldata/parser/orc/version1999.orc" // contain only orc header, no column and no row, total file size is 0.
    };

    @BeforeClass
    static public void setup() { TestUtil.stall_till_cloudsize(1); }

    @BeforeClass
    static public void _preconditionJavaVersion() { // NOTE: the `_` force execution of this check after setup
        // Does not run test on Java6 since we are running on Hadoop lib
        Assume.assumeTrue("Java6 is not supported", !System.getProperty("java.version", "NA").startsWith("1.6"));
    }

    @Test
    public void testParseAllOrcs() {
        Set<String> failedFiles = new TreeSet<>();
        int numOfOrcFiles = allOrcFiles.length; // number of Orc Files to test

        for (int fIndex = 0; fIndex < numOfOrcFiles; fIndex++) {

            String fileName = allOrcFiles[fIndex];
            Log.info("Orc Parser parsing " + fileName);
            File f = find_test_file_static(fileName);

            if (f != null && f.exists()) {
                try {
                    numberWrong += compareOrcAndH2OFrame(fileName, f, failedFiles);
                    totalFilesTested++;
                } catch (IOException e) {
                    e.printStackTrace();
                    failedFiles.add(fileName);
                    numberWrong++;
                }
            } else {
                Log.warn("The following file was not found: " + fileName);
                failedFiles.add(fileName);
                numberWrong++;
            }
        }

        if (numberWrong > 0) {
            Log.warn("There are errors in your test.");
            assertEquals("Number of orc files failed to parse is: " + numberWrong + ", failed files = " +
                    failedFiles.toString(), 0, numberWrong);
        } else {
            Log.info("Parser test passed!  Number of files parsed is " + totalFilesTested);
        }
    }
}
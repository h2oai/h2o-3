package water.parser;


import org.joda.time.DateTimeZone;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
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
		static public void _preconditionJavaVersion() { // NOTE: the `_` force execution of this check after setup
				// Does not run test on Java6 since we are running on Hadoop lib
				Assume.assumeTrue("Java6 is not supported", !System.getProperty("java.version",
												"NA").startsWith("1.6"));
		}

		@BeforeClass
		static public void setup() {
				TestUtil.stall_till_cloudsize(5);
		}

		@Test
		public void testSkippedAllColumns() {
			Scope.enter();
			try {
				int[] skipped_columns = new int[]{0,1};
				 Frame f1 = parse_test_file(orcFiles[0], skipped_columns);
				 fail("orc skipped all columns test failed...");
			} catch(Exception ex) {
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

										Frame csv_frame = parse_test_file(csvFiles[f_index], "\\N", 0, null);
										Frame orc_frame = parse_test_file(orcFiles[f_index], null, 0, null);

										Scope.track(csv_frame);
										Scope.track(orc_frame);

										assertTrue("Fails: " + csvFiles[f_index] + " != " + orcFiles[f_index],
																		TestUtil.isBitIdentical(orc_frame, csv_frame));  // both frames should equal
								}
						} finally {
								Scope.exit();
						}
				}
		}
}

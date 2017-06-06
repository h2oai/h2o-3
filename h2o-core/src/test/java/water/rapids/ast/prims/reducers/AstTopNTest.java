package water.rapids.ast.prims.reducers;

import hex.DMatrix;
import hex.SplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.FrameUtils;
import java.util.Random;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
	* Test the AstTopN.java class
	*/
public class AstTopNTest extends TestUtil {
		static Frame _train;    // store training data
		double _tolerance = 1e-12;
		public Random _rand = new Random();

		@BeforeClass
		public static void setup() {   // randomly generate a frame here.
				stall_till_cloudsize(1);
		}

		//--------------------------------------------------------------------------------------------------------------------
		// Tests
		//--------------------------------------------------------------------------------------------------------------------

		/**
			* Loading in a dataset containing data from -1000000 to 1000000 multiplied by 1.1 as the float column in column 1.
			* The other column (column 0) is a long data type with maximum data value at 2^63.
			*/
		@Test
		public void TestTopBottomN() {
				Scope.enter();
				double[] checkPercent = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19}; // complete test
				int numRuns = 2;
				double testPercent = 0;      // store test percentage
				Frame topLong = null, topFloat = null, bottomLong = null, bottomFloat = null;

				// load in the datasets with the answers
				_train = parse_test_file(Key.make("topbottom"), "smalldata/jira/TopBottomN.csv.zip");
				topFloat = parse_test_file(Key.make("top20"), "smalldata/jira/Top20Per.csv.zip");
				topLong = topFloat.extractFrame(0, 1);
				bottomFloat = parse_test_file(Key.make("bottom20"), "smalldata/jira/Bottom20Per.csv.zip");
				bottomLong = bottomFloat.extractFrame(0, 1);

				Scope.track(_train);
				Scope.track(topLong);
				Scope.track(topFloat);
				Scope.track(bottomFloat);
				Scope.track(bottomLong);

				try {
						for (int index = 0; index < numRuns; index++) { // randomly choose 4 percentages to test
								testPercent = checkPercent[_rand.nextInt(checkPercent.length)];
								testTopBottom(topLong, testPercent, 0, "0", _tolerance);
								testTopBottom(topFloat, testPercent, 0, "1", _tolerance);  // test top % Float
								testTopBottom(bottomLong, testPercent, 1, "0", _tolerance);  // test bottom % Long
								testTopBottom(bottomFloat, testPercent, 1, "1", _tolerance);  // test bottom % Float
						}
				} finally {
						Scope.exit();
				}
		}

		public void testTopBottom(Frame topBottom, double testPercent, int getBottom, String columnIndex,
																																			double tolerance) {
				Scope.enter();
				Frame topBN = null, topBL = null;
				try {
						String x = "(topn " + _train._key + " " + columnIndex + " " + testPercent + " " + getBottom + ")";
						Val res = Rapids.exec(x);         // make the call to grab top/bottom N percent
						topBN = res.getFrame();            // get frame that contains top N elements
						Scope.track(topBN);
						topBL = topBN.extractFrame(1, 2);
						Scope.track(topBL);
						checkTopBottomN(topBottom, topBL, tolerance, getBottom);
				} finally {
						Scope.exit();
				}
		}

		/*
		Helper function to compare test frame result with correct answer
			*/
		public void checkTopBottomN(Frame answerF, Frame grabF, double tolerance, int getBottom) {
				Scope.enter();
				try {
						double nfrac = (getBottom > 0) ? 1.0 * grabF.numRows() / answerF.numRows() : (1 - 1.0 * grabF.numRows() / answerF.numRows());   // translate percentage to actual fraction

						SplitFrame sf = new SplitFrame(answerF, new double[]{nfrac, 1 - nfrac}, new Key[]{
														Key.make("topN.hex"), Key.make("bottomN.hex")});
						// Invoke the job
						sf.exec().get();
						Key[] ksplits = sf._destination_frames;
						Frame topN = (Frame) ((getBottom > 0) ? DKV.get(ksplits[0]).get() : DKV.get(ksplits[1]).get());
						double[] bottomN = FrameUtils.asDoubles(grabF.vec(0));
						Arrays.sort(bottomN);
						Frame sortedF = new water.util.ArrayUtils().frame(bottomN);
						Scope.track(sortedF);
						Frame sortedFT = DMatrix.transpose(sortedF);
						Scope.track(sortedFT);
						assertTrue(isIdenticalUpToRelTolerance(topN, sortedFT, tolerance));
						Scope.track(topN);
						Scope.track_generic(ksplits[1].get());
				} finally {
						Scope.exit();
				}
		}
}
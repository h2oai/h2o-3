package water.rapids.ast.prims.mungers;

import hex.CreateFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

public class AstFillNATest extends TestUtil {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }


  @Test
  public void TestFillNA() {
    Scope.enter();
    try {
      Session sess = new Session();
      Frame fr = Scope.track(new TestFrameBuilder()
              .withName("$fr", sess)
              .withColNames("C1", "C2", "C3")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, Double.NaN, Double.NaN, Double.NaN, Double.NaN))
              .withDataForCol(1, ard(Double.NaN, 1, Double.NaN, Double.NaN, Double.NaN))
              .withDataForCol(2, ard(Double.NaN, Double.NaN, 1, Double.NaN, Double.NaN))
              .build());
      Val val = Rapids.exec("(h2o.fillna $fr 'forward' 0 2)", sess);
      Assert.assertTrue(val instanceof ValFrame);
      Frame res = Scope.track(val.getFrame());
      // check the first column. should be all unique indexes in order
      assertVecEquals(res.vec(0), dvec(1.0, 1.0, 1.0, Double.NaN, Double.NaN), 0.0);
      assertVecEquals(res.vec(1), dvec(Double.NaN, 1.0, 1.0, 1.0, Double.NaN), 0.0);
      assertVecEquals(res.vec(2), dvec(Double.NaN, Double.NaN, 1.0, 1.0, 1.0), 0.0);
    } finally {
      Scope.exit();
    }
  }

  /**
   * This test will build a big frame spanning across multiple chunks.  It will perform fillna backwards for both
   * columnwise and row-wise.  It will grab the result as the answer frame from the single thread implementation
   * of fillna backwards.  Finally, it will compare the two frames and they should be the same.
   *
   * A collection of maxlen is considered.
   */
  @Test
  public void testBackwardRowColLarge() {
    Scope.enter();

    try {
      long seed=12345;
      Session sess = new Session();
      CreateFrame cf = new CreateFrame();
      cf.rows= 10000;
      cf.cols = 20;
      cf.binary_fraction = 0;
      cf.integer_fraction = 0.3;
      cf.categorical_fraction = 0.3;
      cf.has_response=false;
      cf.missing_fraction = 0.7;
      cf.integer_range=10000;
      cf.factors=100;
      cf.seed = seed;

      Frame testFrame =  cf.execImpl().get();
      Scope.track(testFrame);

      int[] maxLenArray = new int[]{0, 1, 2, 17, 10000000};

      for (int maxlen:maxLenArray) {
        // first perform col fillna
        String rapidStringCol= "(h2o.fillna "+testFrame._key+" 'backward' 0 "+maxlen+")";
        assertFillNACorrect(testFrame, rapidStringCol, maxlen, false, sess);
        String rapidStringRow = "(h2o.fillna "+testFrame._key+" 'backward' 1 "+maxlen+")";
        assertFillNACorrect(testFrame, rapidStringRow, maxlen, true, sess);
      }
    } finally {
      Scope.exit();
    }
  }


  public void assertFillNACorrect(Frame testFrame, String rapidString, int maxlen, boolean rowOper, Session sess) {
    Scope.enter();
    try {
      double startMs = System.currentTimeMillis();
      Val val = Rapids.exec(rapidString, sess);
      Frame res = Scope.track(val.getFrame());
      System.out.println("Time(ms) taken to perform multi-thread fillna is "+(System.currentTimeMillis()-startMs));
      startMs = System.currentTimeMillis();
      Frame singleThreadResult = Scope.track(rowOper ? genFillNARow(testFrame, maxlen) : genFillNACol(testFrame, maxlen));
      System.out.println("Time(ms) taken to perform single-thread fillna is "+(System.currentTimeMillis()-startMs));
      isBitIdentical(res, singleThreadResult);
    } finally {
      Scope.exit();
    }
  }

  /**
   * Purpose here is to carry out short tests to make sure the code works and the single thread code works.
   */
  @Test
  public void testBackwardMethodRowAll() {
    Scope.enter();
    try {
      Session sess = new Session();
      Frame frAllNA = Scope.track(new TestFrameBuilder()
              .withName("$fr", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN))
              .withDataForCol(1, ard(Double.NaN))
              .withDataForCol(2, ard(Double.NaN))
              .withDataForCol(3, ard(Double.NaN))
              .withDataForCol(4, ard(Double.NaN))
              .withDataForCol(5, ard(Double.NaN))
              .withDataForCol(6, ard(Double.NaN))
              .build());

      assertNFillNACorrect(sess, frAllNA, frAllNA, 0,
              "(h2o.fillna $fr 'backward' 1 0)", true); // h2o.fillna with maxlen 0
      assertNFillNACorrect(sess, frAllNA, frAllNA, 100,
              "(h2o.fillna $fr 'backward' 1 100)", true); // h2o.fillna with maxlen 100
      // correct answers
      Frame fr1NA = Scope.track(new TestFrameBuilder()
              .withName("$fr2", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN))
              .withDataForCol(1, ard(Double.NaN))
              .withDataForCol(2, ard(Double.NaN))
              .withDataForCol(3, ard(Double.NaN))
              .withDataForCol(4, ard(Double.NaN))
              .withDataForCol(5, ard(Double.NaN))
              .withDataForCol(6, ard(1.234))
              .build());
      Frame fr1NA1na = Scope.track(new TestFrameBuilder()
              .withName("$fr1NA1na", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN))
              .withDataForCol(1, ard(Double.NaN))
              .withDataForCol(2, ard(Double.NaN))
              .withDataForCol(3, ard(Double.NaN))
              .withDataForCol(4, ard(Double.NaN))
              .withDataForCol(5, ard(1.234))
              .withDataForCol(6, ard(1.234))
              .build());
      Frame fr1NA3na = Scope.track(new TestFrameBuilder()
              .withName("$fr1NA3na", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN))
              .withDataForCol(1, ard(Double.NaN))
              .withDataForCol(2, ard(Double.NaN))
              .withDataForCol(3, ard(1.234))
              .withDataForCol(4, ard(1.234))
              .withDataForCol(5, ard(1.234))
              .withDataForCol(6, ard(1.234))
              .build());
      Frame fr1NA100na = Scope.track(new TestFrameBuilder()
              .withName("$fr1NA100na", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM,Vec.T_NUM)
              .withDataForCol(0, ard(1.234))
              .withDataForCol(1, ard(1.234))
              .withDataForCol(2, ard(1.234))
              .withDataForCol(3, ard(1.234))
              .withDataForCol(4, ard(1.234))
              .withDataForCol(5, ard(1.234))
              .withDataForCol(6, ard(1.234))
              .build());

      assertNFillNACorrect(sess, fr1NA, fr1NA, 0,
              "(h2o.fillna $fr2 'backward' 1 0)", true); // h2o.fillna with maxlen 0
      assertNFillNACorrect(sess, fr1NA, fr1NA1na, 1,
              "(h2o.fillna $fr2 'backward' 1 1)", true); // h2o.fillna with maxlen 1
      assertNFillNACorrect(sess, fr1NA, fr1NA3na, 3,
              "(h2o.fillna $fr2 'backward' 1 3)", true); // h2o.fillna with maxlen 3
      assertNFillNACorrect(sess, fr1NA, fr1NA100na, 100,
              "(h2o.fillna $fr2 'backward' 1 100)", true); // h2o.fillna with maxlen 100

      // frame with multiple numbers and NA blocks
      Frame frMultipleNA = Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1))
              .withDataForCol(1, ard(Double.NaN))
              .withDataForCol(2, ard(2))
              .withDataForCol(3, ard(Double.NaN))
              .withDataForCol(4, ard(Double.NaN))
              .withDataForCol(5, ard(3))
              .withDataForCol(6, ard(Double.NaN))
              .withDataForCol(7, ard(Double.NaN))
              .withDataForCol(8, ard(Double.NaN))
              .withDataForCol(9, ard(4))
              .withDataForCol(10, ard(Double.NaN))
              .withDataForCol(11, ard(Double.NaN))
              .withDataForCol(12, ard(Double.NaN))
              .withDataForCol(13, ard( Double.NaN))
              .withDataForCol(14, ard(5))
              .withDataForCol(15, ard(Double.NaN))
              .withDataForCol(16, ard(Double.NaN))
              .withDataForCol(17, ard(Double.NaN))
              .withDataForCol(18, ard(Double.NaN))
              .withDataForCol(19, ard(Double.NaN))
              .withDataForCol(20, ard(6))
              .withDataForCol(21, ard(Double.NaN))
              .withDataForCol(22, ard(Double.NaN))
              .withDataForCol(23, ard(Double.NaN))
              .withDataForCol(24, ard(Double.NaN))
              .withDataForCol(25, ard(Double.NaN))
              .withDataForCol(26, ard(Double.NaN))
              .withDataForCol(27, ard(7))
              .withDataForCol(28, ard(Double.NaN))
              .build());
      // correct answer
      Frame frMultipleNA1Fill = Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA1Fill", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1))
              .withDataForCol(1, ard(2))
              .withDataForCol(2, ard(2))
              .withDataForCol(3, ard(Double.NaN))
              .withDataForCol(4, ard(3))
              .withDataForCol(5, ard(3))
              .withDataForCol(6, ard(Double.NaN))
              .withDataForCol(7, ard(Double.NaN))
              .withDataForCol(8, ard(4))
              .withDataForCol(9, ard(4))
              .withDataForCol(10, ard(Double.NaN))
              .withDataForCol(11, ard(Double.NaN))
              .withDataForCol(12, ard(Double.NaN))
              .withDataForCol(13, ard(5))
              .withDataForCol(14, ard(5))
              .withDataForCol(15, ard(Double.NaN))
              .withDataForCol(16, ard(Double.NaN))
              .withDataForCol(17, ard(Double.NaN))
              .withDataForCol(18, ard(Double.NaN))
              .withDataForCol(19, ard(6))
              .withDataForCol(20, ard(6))
              .withDataForCol(21, ard(Double.NaN))
              .withDataForCol(22, ard(Double.NaN))
              .withDataForCol(23, ard(Double.NaN))
              .withDataForCol(24, ard(Double.NaN))
              .withDataForCol(25, ard(Double.NaN))
              .withDataForCol(26, ard(7))
              .withDataForCol(27, ard(7))
              .withDataForCol(28, ard(Double.NaN))
              .build());
      Frame frMultipleNA2Fill =  Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA2Fill", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1))
              .withDataForCol(1, ard(2))
              .withDataForCol(2, ard(2))
              .withDataForCol(3, ard(3))
              .withDataForCol(4, ard(3))
              .withDataForCol(5, ard(3))
              .withDataForCol(6, ard(Double.NaN))
              .withDataForCol(7, ard(4))
              .withDataForCol(8, ard(4))
              .withDataForCol(9, ard(4))
              .withDataForCol(10, ard(Double.NaN))
              .withDataForCol(11, ard(Double.NaN))
              .withDataForCol(12, ard(5))
              .withDataForCol(13, ard(5))
              .withDataForCol(14, ard(5))
              .withDataForCol(15, ard(Double.NaN))
              .withDataForCol(16, ard(Double.NaN))
              .withDataForCol(17, ard(Double.NaN))
              .withDataForCol(18, ard(6))
              .withDataForCol(19, ard(6))
              .withDataForCol(20, ard(6))
              .withDataForCol(21, ard(Double.NaN))
              .withDataForCol(22, ard(Double.NaN))
              .withDataForCol(23, ard(Double.NaN))
              .withDataForCol(24, ard(Double.NaN))
              .withDataForCol(25, ard(7))
              .withDataForCol(26, ard(7))
              .withDataForCol(27, ard(7))
              .withDataForCol(28, ard(Double.NaN))
              .build());
      Frame frMultipleNA3Fill =   Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA3Fill", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1))
              .withDataForCol(1, ard(2))
              .withDataForCol(2, ard(2))
              .withDataForCol(3, ard(3))
              .withDataForCol(4, ard(3))
              .withDataForCol(5, ard(3))
              .withDataForCol(6, ard(4))
              .withDataForCol(7, ard(4))
              .withDataForCol(8, ard(4))
              .withDataForCol(9, ard(4))
              .withDataForCol(10, ard(5))
              .withDataForCol(11, ard(5))
              .withDataForCol(12, ard(5))
              .withDataForCol(13, ard(5))
              .withDataForCol(14, ard(5))
              .withDataForCol(15, ard(6))
              .withDataForCol(16, ard(6))
              .withDataForCol(17, ard(6))
              .withDataForCol(18, ard(6))
              .withDataForCol(19, ard(6))
              .withDataForCol(20, ard(6))
              .withDataForCol(21, ard(7))
              .withDataForCol(22, ard(7))
              .withDataForCol(23, ard(7))
              .withDataForCol(24, ard(7))
              .withDataForCol(25, ard(7))
              .withDataForCol(26, ard(7))
              .withDataForCol(27, ard(7))
              .withDataForCol(28, ard(Double.NaN))
              .build());
      Frame frMultipleNA100Fill = Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA100Fill", sess)
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,
                      Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1))
              .withDataForCol(1, ard(2))
              .withDataForCol(2, ard(2))
              .withDataForCol(3, ard(3))
              .withDataForCol(4, ard(3))
              .withDataForCol(5, ard(3))
              .withDataForCol(6, ard(4))
              .withDataForCol(7, ard(4))
              .withDataForCol(8, ard(4))
              .withDataForCol(9, ard(5))
              .withDataForCol(10, ard(5))
              .withDataForCol(11, ard(5))
              .withDataForCol(12, ard(5))
              .withDataForCol(13, ard(5))
              .withDataForCol(14, ard(6))
              .withDataForCol(15, ard(6))
              .withDataForCol(16, ard(6))
              .withDataForCol(17, ard(6))
              .withDataForCol(18, ard(6))
              .withDataForCol(19, ard(6))
              .withDataForCol(20, ard(7))
              .withDataForCol(21, ard(7))
              .withDataForCol(22, ard(7))
              .withDataForCol(23, ard(7))
              .withDataForCol(24, ard(7))
              .withDataForCol(25, ard(7))
              .withDataForCol(26, ard(7))
              .withDataForCol(27, ard(Double.NaN))
              .build());
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA, 0,
              "(h2o.fillna $frMultipleNA 'backward' 1 0)", true); // h2o.fillna with maxlen 0
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA1Fill, 1,
              "(h2o.fillna $frMultipleNA 'backward' 1 1)", true); // h2o.fillna with maxlen 1
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA2Fill, 2,
              "(h2o.fillna $frMultipleNA 'backward' 1 2)", true); // h2o.fillna with maxlen 2
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA3Fill, 3,
              "(h2o.fillna $frMultipleNA 'backward' 1 3)", true); // h2o.fillna with maxlen 3
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA100Fill, 100,
              "(h2o.fillna $frMultipleNA 'backward' 1 100)", true); // h2o.fillna with maxlen 100
    } finally {
      Scope.exit();
    }
  }

  /**
   * Purpose here is to carry out short tests to make sure the code works and the single thread code works.
   */
  @Test
  public void testBackwardMethodColAll() {
    Scope.enter();
    try {
      Session sess = new Session();
      Frame frAllNA = Scope.track(new TestFrameBuilder()
              .withName("$fr", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                      Double.NaN, Double.NaN))
              .build());
      assertNFillNACorrect(sess, frAllNA, frAllNA, 0,
              "(h2o.fillna $fr 'backward' 0 0)", false); // h2o.fillna with maxlen 0
      assertNFillNACorrect(sess, frAllNA, frAllNA, 100,
              "(h2o.fillna $fr 'backward' 0 100)", false); // h2o.fillna with maxlen 100

      // correct answers
      Frame fr1NA = Scope.track(new TestFrameBuilder()
              .withName("$fr2", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                      Double.NaN, Double.NaN, Double.NaN, 1.234))
              .build());
      Frame fr1NA1Ans = Scope.track(new TestFrameBuilder()
              .withName("$fr1NA1Ans", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                      Double.NaN, Double.NaN, 1.234, 1.234))
              .build());
      Frame fr1NA3Ans = Scope.track(new TestFrameBuilder()
              .withName("$fr1NA3Ans", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                      1.234, 1.234, 1.234, 1.234))
              .build());
      Frame fr1NA100Ans = Scope.track(new TestFrameBuilder()
              .withName("$fr1NA100Ans", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(1.234, 1.234, 1.234, 1.234, 1.234, 1.234, 1.234, 1.234, 1.234, 1.234))
              .build());

      assertNFillNACorrect(sess, fr1NA, fr1NA, 0,
              "(h2o.fillna $fr 'backward' 0 0)", false); // h2o.fillna with maxlen 0
      assertNFillNACorrect(sess, fr1NA, fr1NA1Ans, 1,
              "(h2o.fillna $fr 'backward' 0 1)", false); // h2o.fillna with maxlen 1
      assertNFillNACorrect(sess, fr1NA, fr1NA3Ans, 3,
              "(h2o.fillna $fr 'backward' 0 3)", false); // h2o.fillna with maxlen 3
      assertNFillNACorrect(sess, fr1NA, fr1NA100Ans, 100,
              "(h2o.fillna $fr 'backward' 0 100)", false); // h2o.fillna with maxlen 100

      // frame with multiple numbers and NA blocks
      Frame frMultipleNA = Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(1, Double.NaN, 2, Double.NaN, Double.NaN, 3, Double.NaN, Double.NaN,
                      4, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 5, Double.NaN, Double.NaN, Double.NaN,
                      Double.NaN, Double.NaN, 6, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                      7, Double.NaN))
              .build());
      // correct answer
      Frame frMultipleNA1Fill = Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA1Fill", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 2, Double.NaN, 3, 3, Double.NaN, 4,
                      4, Double.NaN, Double.NaN, Double.NaN, 5, 5, Double.NaN, Double.NaN, Double.NaN,
                      Double.NaN, 6, 6, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 7,
                      7, Double.NaN))
              .build());
      Frame frMultipleNA2Fill =  Scope.track(new TestFrameBuilder()
                            .withName("$frMultipleNA2Fill", sess)
               .withColNames("C1")
                            .withVecTypes(Vec.T_NUM)
                            .withDataForCol(0, ard(1, 2, 2, 3, 3, 3, 4, 4,
                 4, Double.NaN, Double.NaN,5, 5, 5, Double.NaN, Double.NaN, Double.NaN,
                       6, 6, 6, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 7, 7,
                      7, Double.NaN))
                          .build());
      Frame frMultipleNA3Fill =   Scope.track(new TestFrameBuilder()
              .withName("$frMultipleNA3Fill", sess)
              .withColNames("C1")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 2, 3, 3, 3, 4, 4,4, Double.NaN, 5,5, 5, 5, Double.NaN,
                      Double.NaN, 6,6, 6, 6, Double.NaN, Double.NaN, Double.NaN, 7, 7, 7,7, Double.NaN))
              .build());
      Frame frMultipleNA100Fill =   Scope.track(new TestFrameBuilder()
                      .withName("$frMultipleNA100Fill", sess)
                      .withColNames("C1")
                      .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 2, 3, 3, 3, 4, 4,4, 5, 5,5, 5, 5, 6, 6, 6,6, 6, 6, 7, 7, 7,
                      7, 7, 7,7, Double.NaN))
              .build());

      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA, 0,
              "(h2o.fillna $frMultipleNA 'backward' 0 0)", false); // h2o.fillna with maxlen 0
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA1Fill, 1,
              "(h2o.fillna $frMultipleNA 'backward' 0 1)", false); // h2o.fillna with maxlen 1
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA2Fill, 2,
              "(h2o.fillna $frMultipleNA 'backward' 0 2)", false); // h2o.fillna with maxlen 2
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA3Fill, 3,
              "(h2o.fillna $frMultipleNA 'backward' 0 3)", false); // h2o.fillna with maxlen 3
      assertNFillNACorrect(sess, frMultipleNA, frMultipleNA100Fill, 100,
              "(h2o.fillna $frMultipleNA 'backward' 0 100)", false); // h2o.fillna with maxlen 100
    } finally {
      Scope.exit();
    }
  }

  /***
   * This method will perform fillna operation in single thread and will be used to verify correct implementation
   * of the multithread one.
   * @param fr
   * @param maxlen
   * @return
   */
  public Frame genFillNARow(Frame fr, int maxlen) {
    Frame newFrame = fr.deepCopy(Key.make().toString());
    long nrow = fr.numRows();
    int lastColInd = fr.numCols() - 1;
    int naColStart = -1;
    int lastNonNaCol = Integer.MAX_VALUE;
    int cindex = lastColInd - 1;
    double fillVal = Double.NaN;
    int naBlockLen = 0;

    if (maxlen==0)
      return newFrame;

    Vec[] allVecs = new Vec[newFrame.numCols()];
    Vec.Writer[] allWriters = new Vec.Writer[newFrame.numCols()];

    for (int cind = 0; cind < allVecs.length; cind++) {
      allVecs[cind] = newFrame.vec(cind);
      Scope.track(allVecs[cind]);
      allWriters[cind] = allVecs[cind].open();
    }
    for (long rindex = 0; rindex < nrow; rindex++) {
      if (!fr.vec(lastColInd).isNA(rindex)) {
        lastNonNaCol = lastColInd;
        fillVal = fr.vec(lastColInd).at(rindex);
      }
      while (cindex >= 0) {    // start from second to last column
        if (fr.vec(cindex).isNA(rindex)) { // found a na, could be more to follow
          naColStart = cindex;
          naBlockLen++;
          cindex--;
          while ((cindex >=0) && fr.vec(cindex).isNA(rindex)) { // keep looping to find NA blocks
            cindex--;
            naBlockLen++;
          } // no more Na here.  Fill in NA with future column value

          int cend = naColStart - naBlockLen;
          for (int cind = naColStart; cind > cend; cind--) {
            int colIndDiff = lastNonNaCol - cind;
            if (colIndDiff <= maxlen) {
             // newFrame.vec(cind).set(rindex, fillVal);
              allWriters[cind].set(rindex, fillVal);
            } else {
              break;  // done filling in NAs
            }
          }
        } else {
          lastNonNaCol = cindex;
          fillVal = fr.vec(cindex).at(rindex);
          naBlockLen = 0;
          cindex--;
        }
      }
    }

    for (int cind = 0; cind < allVecs.length; cind++) {
      allWriters[cind].close();
    }
    return newFrame;
  }

  public void assertNFillNACorrect(Session sess, Frame origF, Frame answerFrame, int maxLen, String rapidString,
                                   boolean rowTest) {
    Scope.enter();
    try {
      Val val = Rapids.exec(rapidString, sess);
      Assert.assertTrue(val instanceof ValFrame);
      Frame res =  Scope.track(val.getFrame());
      Scope.track(res);
      Frame ansSingleThread = rowTest?genFillNARow(origF, maxLen):genFillNACol(origF, maxLen);
      Scope.track(ansSingleThread);
      isBitIdentical(res, answerFrame);  // fillna result is correct
      isBitIdentical(ansSingleThread, answerFrame);   // fillna from single thread is correct
    } finally {
      Scope.exit();
    }
  }

  public Frame genFillNACol(Frame fr, int maxlen) {
    Frame newFrame = fr.deepCopy(Key.make().toString());
    long ncol = fr.numCols();
    long lastRowInd = fr.numRows() - 1;
    long naRowStart = -1;
    long lastNonNaRow = Long.MAX_VALUE;
    long rindex = lastRowInd - 1;
    double fillVal = Double.NaN;
    long naBlockLen = 0;

    if (maxlen == 0)
      return newFrame;

    for (int cindex = 0; cindex < ncol; cindex++) { // go through each column to do the na fill
      Vec oneColumn = newFrame.vec(cindex);
      Vec.Writer writeVec = oneColumn.open();
      Scope.track(oneColumn);
      if (!fr.vec(cindex).isNA(lastRowInd)) {
        fillVal = fr.vec(cindex).at(lastRowInd);
        lastNonNaRow = lastRowInd;
      }

      while (rindex >= 0) {    // start from second to last row
        if (fr.vec(cindex).isNA(rindex)) { // found a na, could be more to follow
          naRowStart = rindex;
          naBlockLen++;
          rindex--;
          while ((rindex >= 0) && fr.vec(cindex).isNA(rindex)) { // keep looping to find NA blocks
            rindex--;
            naBlockLen++;
          } // no more Na here.  Fill in NA with future column value

          long rend = naRowStart - naBlockLen;
          for (long rind = naRowStart; rind > rend; rind--) {
            long rowIndDiff = lastNonNaRow - rind;
            if (rowIndDiff <= maxlen) {
              //newFrame.vec(cindex).set(rind, fillVal);
              writeVec.set(rind, fillVal);
            } else {
              break;  // done filling in NAs
            }
          }
        } else {
          lastNonNaRow = rindex;
          fillVal = fr.vec(cindex).at(rindex);
          naBlockLen = 0;
          rindex--;
        }
      }
      writeVec.close();
    }
    return newFrame;
  }
}

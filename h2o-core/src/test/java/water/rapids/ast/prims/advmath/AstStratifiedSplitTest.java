package water.rapids.ast.prims.advmath;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.StratifiedSplit;

import static water.rapids.StratifiedSampler.sample;

public class AstStratifiedSplitTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Before
  public void beforeEach() {
    System.out.println("Before each setup");
  }

  @Test
  public void stratifiedSplitIsWorkingForNumericalColumn() {

    Scope.enter();
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3))
              .build();

      final String[] OUTPUT_COLUMN_DOMAIN = new String[]{"train", "test"};


      Vec stratifiedSplit = StratifiedSplit.split(fr.vec("ColA"), 0.33, 1234L, OUTPUT_COLUMN_DOMAIN);

      printOutFrameAsTable(new Frame(stratifiedSplit));
      assertStringVecEquals(cvec("train", "train", "test", "train", "test", "train", "train", "train", "test", "train", "train", "test"), stratifiedSplit);
      
      fr.add("stratified", stratifiedSplit);

      printOutFrameAsTable(fr);

    }
    finally {
      Scope.exit();
    }
    
  }
  
  @Test
  public void stratifiedSplitIsWorkingForCategoricalColumn() {

    Scope.enter();
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("yes", "yes", "no", "no", "no", "no"))
              .build();

      final String[] OUTPUT_COLUMN_DOMAIN = new String[]{"train", "test"};


      Vec stratifiedSplit = StratifiedSplit.split(fr.vec("ColA"), 0.5, 1234L, OUTPUT_COLUMN_DOMAIN);

      printOutFrameAsTable(new Frame(stratifiedSplit));
      assertStringVecEquals(cvec("train", "test", "train", "test", "train", "test"), stratifiedSplit);
      
      fr.add("stratified", stratifiedSplit);
      printOutFrameAsTable(fr);
    }
    finally {
      Scope.exit();
    }
  }
  
  @Test
  public void stratifiedSampleIsWorkingForNumColumn() {

    Scope.enter();
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("yes", "yes", "no", "no", "no", "no"))
              .build();


      printOutFrameAsTable(fr);

      Frame filtered = sample(fr,"ColA",0.5, 1234);

      printOutFrameAsTable(filtered);
      filtered.delete();
    }
    finally {
      Scope.exit();
    }
  }


  @After
  public void afterEach() {
    fr.delete();
  }
}

package hex;

import org.junit.*;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

public class ModelAdaptTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }


  // Private junk model class to test Adaption logic
  private static class AModel extends Model {
    AModel( Key key, Parameters p, Output o ) { super(key,p,o); }
    @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) { throw H2O.unimpl(); }
    @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) { throw H2O.unimpl(); }
    static class AParms extends Model.Parameters { }
    static class AOutput extends Model.Output { }
  }

  @Test public void testModelAdaptMultinomial() {
    Frame trn = parse_test_file("smalldata/junit/mixcat_train.csv");
    AModel.AParms p = new AModel.AParms();
    AModel.AOutput o = new AModel.AOutput();
    o._names = trn.names();
    o._domains = trn.domains();
    trn.remove();
    AModel am = new AModel(Key.make(),p,o);
    
    Frame tst = parse_test_file("smalldata/junit/mixcat_test.csv");
    Frame adapt = new Frame(tst);
    String[] warns = am.adaptTestForTrain(adapt,true, true);
    Assert.assertTrue(ArrayUtils.find(warns,"Validation column Feature_1 has levels not trained on: [D]")!= -1);
    Assert.assertTrue(ArrayUtils.find(warns, "Validation set is missing training column Const: substituting in a column of NAs") != -1);
    Assert.assertTrue(ArrayUtils.find(warns, "Validation set is missing training column Useless: substituting in a column of NAs") != -1);
    Assert.assertTrue(ArrayUtils.find(warns, "Validation column Response has levels not trained on: [W]") != -1);
    // Feature_1: merged test & train domains
    Assert.assertArrayEquals(adapt.vec("Feature_1").domain(),new String[]{"A","B","C","D"});
    // Const: all NAs
    Assert.assertTrue(adapt.vec("Const").isBad());
    // Useless: all NAs
    Assert.assertTrue(adapt.vec("Useless").isBad());
    // Response: merged test & train domains
    Assert.assertArrayEquals(adapt.vec("Response").domain(),new String[]{"X","Y","Z","W"});

    Model.cleanup_adapt( adapt, tst );
    tst.remove();
  }

  // If the train set has a categorical, and the test set column is all missing
  // then by-default it is treated as a numeric column (no domain).  Verify that
  // we make an empty domain mapping
  @Test public void testModelAdaptMissing() {
    AModel.AParms p = new AModel.AParms();
    AModel.AOutput o = new AModel.AOutput();

    Vec cat = vec(new String[]{"A","B"},0,1,0,1);
    Frame trn = new Frame();
    trn.add("cat",cat);
    o._names = trn.names();
    o._domains = trn.domains();
    trn.remove();
    AModel am = new AModel(Key.make(),p,o);
    
    Frame tst = new Frame();
    tst.add("cat", cat.makeCon(Double.NaN)); // All NAN/missing column
    Frame adapt = new Frame(tst);
    String[] warns = am.adaptTestForTrain(adapt,true, true);
    Assert.assertTrue(warns.length == 0); // No errors during adaption

    Model.cleanup_adapt( adapt, tst );
    tst.remove();
  }

  // If the train set has a categorical, and the test set column is numeric
  // then convert it to a categorical
  @Test public void testModelAdaptConvert() {
    AModel.AParms p = new AModel.AParms();
    AModel.AOutput o = new AModel.AOutput();

    Frame trn = new Frame();
    trn.add("dog",vec(new String[]{"A","B"},0,1,0,1));
    o._names = trn.names();
    o._domains = trn.domains();
    trn.remove();
    AModel am = new AModel(Key.make(),p,o);
    
    Frame tst = new Frame();
    tst.add("dog",vec(2, 3, 2, 3));
    Frame adapt = new Frame(tst);
    boolean saw_iae = false;
    try { am.adaptTestForTrain(adapt, true, true); }
    catch( IllegalArgumentException iae ) { saw_iae = true; }
    Assert.assertTrue(saw_iae);

    Model.cleanup_adapt( adapt, tst );
    tst.remove();
  }

}

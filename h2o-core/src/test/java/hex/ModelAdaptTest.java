package hex;

import org.junit.*;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.FrameUtils;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ModelAdaptTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  // Private junk model class to test Adaption logic
  private static class AModel extends Model {
    AModel( Key key, Parameters p, Output o ) { super(key,p,o); }
    @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) { throw H2O.unimpl(); }
    @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) { throw H2O.unimpl(); }
    static class AParms extends Model.Parameters {
      public String algoName() { return "A"; }
      public String fullName() { return "A"; }
      public String javaName() { return AModel.class.getName(); }
      @Override public long progressUnits() { return 0; }
    }
    static class AOutput extends Model.Output { }
  }

  @Test public void testModelAdaptMultinomial() {
    Frame trn = parseTestFile("smalldata/junit/mixcat_train.csv");
    AModel.AParms p = new AModel.AParms();
    AModel.AOutput o = new AModel.AOutput();
    o.setNames(trn.names(), trn.typesStr());
    o._domains = trn.domains();
    trn.remove();
    AModel am = new AModel(Key.make(),p,o);
    
    Frame tst = parseTestFile("smalldata/junit/mixcat_test.csv");
    Frame adapt = new Frame(tst);
    String[] warns = am.adaptTestForTrain(adapt,true, true);
    
    assertTrue(ArrayUtils.find(warns,"Test/Validation dataset column 'Feature_1' has levels not trained on: [\"D\"]")!= -1);
    assertTrue(ArrayUtils.find(warns, "Test/Validation dataset is missing column 'Const': substituting in a column of NaN") != -1);
    assertTrue(ArrayUtils.find(warns, "Test/Validation dataset is missing column 'Useless': substituting in a column of NaN") != -1);
    assertTrue(ArrayUtils.find(warns, "Test/Validation dataset column 'Response' has levels not trained on: [\"W\"]") != -1);
    // Feature_1: merged test & train domains
    assertArrayEquals(adapt.vec("Feature_1").domain(),new String[]{"A","B","C","D"});
    // Const: all NAs
    assertTrue(adapt.vec("Const").isBad());
    // Useless: all NAs
    assertTrue(adapt.vec("Useless").isBad());
    // Response: merged test & train domains
    assertArrayEquals(adapt.vec("Response").domain(),new String[]{"X","Y","Z","W"});

    Frame.deleteTempFrameAndItsNonSharedVecs(adapt, tst);
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
    o.setNames(trn.names(), trn.typesStr());
    o._domains = trn.domains();
    trn.remove();
    AModel am = new AModel(Key.make(),p,o);
    
    Frame tst = new Frame();
    tst.add("cat", cat.makeCon(Double.NaN)); // All NAN/missing column
    Frame adapt = new Frame(tst);
    String[] warns = am.adaptTestForTrain(adapt,true, true);
    assertEquals(0, warns.length); // No errors during adaption

    Frame.deleteTempFrameAndItsNonSharedVecs(adapt, tst);
    tst.remove();
  }

  // If the train set has a categorical, and the test set column is numeric
  // then convert it to a categorical
  @Test public void testModelAdaptConvert() {
    AModel.AParms p = new AModel.AParms();
    AModel.AOutput o = new AModel.AOutput();

    Frame trn = new Frame();
    trn.add("dog",vec(new String[]{"A","B"},0,1,0,1));
    o.setNames(trn.names(), trn.typesStr());
    o._domains = trn.domains();
    trn.remove();
    AModel am = new AModel(Key.make(),p,o);
    
    Frame tst = new Frame();
    tst.add("dog",vec(2, 3, 2, 3));
    Frame adapt = new Frame(tst);
    boolean saw_iae = false;
    try { am.adaptTestForTrain(adapt, true, true); }
    catch( IllegalArgumentException iae ) { saw_iae = true; }
    assertTrue(saw_iae);

    Frame.deleteTempFrameAndItsNonSharedVecs(adapt, tst);
    tst.remove();
  }

  @Test public void testModelAdaptOneHotExplicit() {
    AModel.AParms p = new AModel.AParms();
    p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;
    p._response_column = "resp";
    AModel.AOutput o = new AModel.AOutput();

    Frame trn = new Frame(Key.make("trn01"));
    trn.add("ignored", dvec(0, 0, 0, 0));
    trn.add("id", vec(42,43, 1, 2));
    trn.add("dog", vec(new String[]{"A", "B"}, 0, 1, 0, 1));
    trn.add("resp", dvec(.1, .2, .3, .99));

    String[] skipCols = new String[] { "resp" };
    Frame trnToEncode = new Frame(new String[] {"id", "dog", "resp"}, trn.vecs(new int[]{1, 2, 3}));
    Frame trnEncoded = new FrameUtils.CategoricalOneHotEncoder(trnToEncode, skipCols).exec().get();
    trnToEncode.remove();

    o.setNames(trnEncoded.names(), trnEncoded.typesStr());
    o._domains = trnEncoded.domains();
    o._origNames = trn.names();
    o._origDomains = trn.domains();
    trn.remove();
    AModel am = new AModel(Key.make(), p, o);

    Frame tst = new Frame(Key.make("tst01"));
    tst.add("ignored", dvec(0, 0, 0, 0));
    tst.add("id", vec(42, 2, 5, 6));
    tst.add("dog", vec(new String[]{"A", "B"}, 0, 0, 1, 1));
    tst.add("resp", dvec(.1, .2, .3, .99));
    Frame adapt = new Frame(tst);

    String[] messages = am.adaptTestForTrain(adapt, true, false);
    assertEquals("Error messages not empty: " + Arrays.toString(messages), 0, messages.length);

    assertArrayEquals(trnEncoded.names(), adapt.names());
    trnEncoded.remove();

    Frame.deleteTempFrameAndItsNonSharedVecs(adapt, tst);
    tst.remove();
    FrameUtils.cleanUp(am._toDelete.keySet());
  }

}

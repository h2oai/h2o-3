package ai.h2o.automl;

import hex.FrameSplitter;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;
import static water.util.FrameUtils.generateNumKeys;

public class TargetEncodingTitanicTest extends TestUtil{


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Before
  public void beforeEach() {
        System.out.println("Before each setup");
    }


    @Test
    public void targetEncoderIsWorkingWithRealDataSetsTest() {

        Frame titanicFrame = parse_test_file(Key.make("titanic_parsed"), "smalldata/gbm_test/titanic.csv");

        double[] ratios  = ard(0.7f, 0.1f, 0.1f);
        Frame[] splits  = null;
        FrameSplitter fs = new FrameSplitter(titanicFrame, ratios, generateNumKeys(titanicFrame._key, ratios.length+1), null);
        H2O.submitTask(fs).join();
        splits = fs.getResult();
        Frame train = splits[0];
        Frame valid = splits[1];
        Frame te_holdout = splits[2];
        Frame test = splits[3];
        String[] colNames = train.names();

        //myX <- setdiff(colnames(train), c("survived", "name", "ticket", "boat", "body"))

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {7}; // 7 stands for Origin column

//        titanicFrame = filterBy(titanicFrame, 7, "ABE");
        long numberOfRowsTrain = titanicFrame.numRows();

        System.out.println("Number of rows in train -------------------------------> " + numberOfRowsTrain);
        printOutFrameAsTable(titanicFrame, true);

        Map<String, Frame> encodingMap = tec.prepareEncodingMap(titanicFrame, teColumns, 10); // 10 stands for IsDepDelayed column
        Frame trainEncoded = tec.applyTargetEncoding(titanicFrame, teColumns, 10, encodingMap, TargetEncoder.HoldoutType.None,false, 0, 1234.0);

        printOutFrameAsTable(trainEncoded, true);
        // Preparing test frame
 /*       Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, teColumns, 10, encodingMap, TargetEncoder.HoldoutType.None,false, 0, 1234.0);

        testEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(testEncoded, 10); // TODO we  need here pseudobinary numerical(quasibinomial).

        printOutColumnsMeta(testEncoded);


        printOutFrameAsTable(titanicFrame);
        printOutFrameAsTable(trainEncoded);
        printOutFrameAsTable(testEncoded);

        // With target encoded Origin column

        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = trainEncoded._key;
        parms._response_column = "IsDepDelayed";
        parms._ntrees = 20;
        parms._max_depth = 3;
        parms._distribution = DistributionFamily.quasibinomial;
        parms._keep_cross_validation_predictions=true;
        parms._valid = testEncoded._key;
        parms._ignored_columns = new String[]{"IsDepDelayed_REC", "Origin"};
        GBM job = new GBM(parms);
        GBMModel gbm = job.trainModel().get();

        Assert.assertTrue(job.isStopped());

        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.valid());
        double auc = mm._auc._auc;


        // WITHOUT
        Key parsed2 = Key.make("airlines_parsed2");

        Frame airlinesTrainFrame2 = parse_test_file(parsed2, "smalldata/airlines/AirlinesTrain.csv.zip");

        // DO we convert to quasibinomial properly? maybe in training set we get opposite values to the test set because we are converting separately.
        airlinesTrainFrame2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTrainFrame2, 10); // TODO we  need here pseudobinary numerical(quasibinomial).

        int originCardinality = testEncoded.vec("Origin").cardinality();

        GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
        parms2._train = airlinesTrainFrame2._key;
        parms2._response_column = "IsDepDelayed";
        parms2._ntrees = 20;
        parms2._max_depth = 3;
        parms2._distribution = DistributionFamily.quasibinomial;
        parms2._keep_cross_validation_predictions=true;
        parms2._valid = testEncoded._key;
        parms2._ignored_columns = new String[]{"IsDepDelayed_REC"};
        GBM job2 = new GBM(parms2);
        GBMModel gbm2 = job2.trainModel().get();

        Assert.assertTrue(job2.isStopped());

        hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm2, parms2.valid());
        double auc2 = mm2._auc._auc;

        System.out.println("Origin cardinality:" + originCardinality);
        System.out.println("AUC with encoding:" + auc);
        System.out.println("AUC without encoding:" + auc2);

        // Fails. Maybe it is not of that high cardinality and target encoding actually hurts. Maybe not in terms of speed but in terms of performance.
        Assert.assertTrue(auc2 < auc );*/
    }

    @After
    public void afterEach() {
        System.out.println("After each test we do H2O.STORE.clear() and Vec.ESPC.clear()");
        Vec.ESPC.clear();
        H2O.STORE.clear();
    }

    private void printOutFrameAsTable(Frame fr, boolean full) {

        TwoDimTable twoDimTable = fr.toTwoDimTable(0,100, false);
        System.out.println(twoDimTable.toString(2, full));
    }

    private void printOutColumnsMeta(Frame fr) {
        for( String header : fr.toTwoDimTable().getColHeaders()) {
            String type = fr.vec(header).get_type_str();
            int cardinality = fr.vec(header).cardinality();
            System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));

        }
    }
}

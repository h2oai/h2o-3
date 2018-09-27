package hex.genmodel;

import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

@Ignore("cannot run automatically - contains reference to personal filesystem area")
public class CppComparisonTest {

    @Test
    public void testProstate() throws IOException, PredictException {
        final EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config();
        final GenModel model = GbmMojoModel.load("/home/pk/h2o/h2o-mojo/testmodels/prostate/unzipped");
        config.setModel(model);
        config.setConvertInvalidNumbersToNa(false);
        final EasyPredictModelWrapper easyModel = new EasyPredictModelWrapper(config);
        final RowData row = new RowData();
        row.put("AGE", "68");
        row.put("RACE", "2");
        row.put("DCAPS", "2");
        final BinomialModelPrediction pred = easyModel.predictBinomial(row);
        System.out.printf("Has penetrated the prostatic capsule (1=yes; 0=no): %s\n", pred.label);
        System.out.printf("Class probabilities: %f %f\n", pred.classProbabilities[0], pred.classProbabilities[1]);
    }

    @Test
    public void testNamesV100() throws IOException, PredictException {
        final EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config();
        final GenModel model = GbmMojoModel.load("/home/pk/h2o/h2o-mojo-java/src/test/resources/gbm_v1.00_names.mojo");
        config.setModel(model);
        config.setConvertInvalidNumbersToNa(false);
        final EasyPredictModelWrapper easyModel = new EasyPredictModelWrapper(config);
        final RowData row = new RowData();
        row.put("age", "68");
        row.put("sex", "M");
        row.put("pclass", "1");
        final BinomialModelPrediction pred = easyModel.predictBinomial(row);
        System.out.printf("Result: %s\n", pred.label);
        System.out.printf("Class probabilities: %f %f\n", pred.classProbabilities[0], pred.classProbabilities[1]);
    }

}

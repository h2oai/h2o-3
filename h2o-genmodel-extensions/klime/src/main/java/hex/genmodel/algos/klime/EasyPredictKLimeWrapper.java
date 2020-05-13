package hex.genmodel.algos.klime;

import hex.ModelCategory;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.RowData;
import hex.genmodel.GenModel;

public class EasyPredictKLimeWrapper extends EasyPredictModelWrapper {

    public EasyPredictKLimeWrapper(Config config) {
        super(config);
    }

    public EasyPredictKLimeWrapper(GenModel genmodel) {
        super(genmodel);
    }

    /**
     * Make a prediction on a new data point using a k-LIME model.
     *
     * @param data A new data point.
     * @return The prediction.
     * @throws PredictException
     */
    public KLimeModelPrediction predictKLime(RowData data) throws PredictException {
        double[] preds = preamble(ModelCategory.Regression, data);

        KLimeModelPrediction p = new KLimeModelPrediction();
        p.value = preds[0];
        p.cluster = (int) preds[1];
        p.reasonCodes = new double[preds.length - 2];
        System.arraycopy(preds, 2, p.reasonCodes, 0, p.reasonCodes.length);

        return p;
    }
}


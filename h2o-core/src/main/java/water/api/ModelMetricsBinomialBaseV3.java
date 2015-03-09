package water.api;

import hex.ModelMetricsBinomial;
import water.util.TwoDimTable;

abstract public class ModelMetricsBinomialBaseV3<I extends ModelMetricsBinomial, S extends ModelMetricsBinomialBaseV3<I, S>> extends ModelMetricsBase<I, S> {
    @API(help = "The Mean Squared Error of the prediction for this scoring run.", direction = API.Direction.OUTPUT)
    public double mse;

    @API(help="The Confusion Matrices for this scoring run.", direction=API.Direction.OUTPUT)
    public long[][][] confusion_matrices;

    @API(help="The AUC for this scoring run.", direction=API.Direction.OUTPUT)
    public double AUC;

    @API(help="The Gini score for this run.", direction=API.Direction.OUTPUT)
    public double Gini;

    @API(help = "The Metrics for various thresholds.", direction = API.Direction.OUTPUT)
    public TwoDimTableV1 thresholdsAndMetricScores;

    @API(help = "The Metrics for various criteria.", direction = API.Direction.OUTPUT)
    public TwoDimTableV1 maxCriteriaAndMetricScores;

    @Override
    public S fillFromImpl(I modelMetrics) {
        super.fillFromImpl(modelMetrics);
        this.mse = modelMetrics._mse;

        if (null != modelMetrics._aucdata) {
            // Fill AUC
            this.AUC = modelMetrics._aucdata.AUC;

            // Fill Gini
            this.Gini = modelMetrics._aucdata.Gini;

            // Fill confusion matrices
            this.confusion_matrices = modelMetrics._aucdata.confusion_matrices;

            // Fill TwoDimTable thresholdsByMetrics
            int numThresholds = modelMetrics._aucdata.thresholds.length;
            String[] thresholds = new String[numThresholds];
            for(int i=0; i<numThresholds; i++){
                thresholds[i] = Float.toString(modelMetrics._aucdata.thresholds[i]);
            }
            String[] metrics = new String[]{"F1", "F2", "F0point5", "accuracy", "error", "precision", "recall", "specificity", "mcc", "max_per_class_error"};
            TwoDimTable thresholdsByMetrics = new TwoDimTable(
                    "Thresholds x Metric Scores",
                    thresholds,
                    metrics,
                    new String[]{"double", "double", "double", "double", "double", "double", "double", "double", "double", "double"},
                    new String[]{"%f", "%f", "%f", "%f", "%f", "%f", "%f", "%f", "%f", "%f"}, "");
            int row = 0;
            for(String threshold : thresholds){
                int col = 0;
                for(String metric : metrics) {
                    switch(metric) {
                        case "F1":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.F1[row]);
                            break;
                        case "F2":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.F2[row]);
                            break;
                        case "F0point5":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.F0point5[row]);
                            break;
                        case "accuracy":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.accuracy[row]);
                            break;
                        case "error":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.errorr[row]);
                            break;
                        case "precision":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.precision[row]);
                            break;
                        case "recall":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.recall[row]);
                            break;
                        case "specificity":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.specificity[row]);
                            break;
                        case "mcc":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.mcc[row]);
                            break;
                        case "max_per_class_error":
                            thresholdsByMetrics.set(row, col, modelMetrics._aucdata.max_per_class_error[row]);
                            break;
                    }
                    col = col + 1;
                }
                row = row + 1;
            }
            this.thresholdsAndMetricScores = new TwoDimTableV1().fillFromImpl(thresholdsByMetrics);


            // Fill TwoDimTable criteriaByThresholdAndScore
            String[] criteria = new String[]{"maximum F1", "maximum F2", "maximum F0point5", "maximum Accuracy",
                    "maximum Precision", "maximum Recall", "maximum Specificity", "maximum absolute MCC",
                    "minimizing max per class Error"};
            String[] columnNames = new String[]{"threshold", "F1", "F2", "F0point5", "accuracy", "error", "precision", "recall", "specificity", "mcc", "max_per_class_error"};
            TwoDimTable criteriaByThresholdAndScore = new TwoDimTable(
                    "Max Criteria x Metric Scores",
                    criteria,
                    columnNames,
                    new String[]{"double", "double", "double", "double", "double", "double", "double", "double", "double", "double", "double"},
                    new String[]{"%f", "%f", "%f", "%f", "%f", "%f", "%f", "%f", "%f", "%f", "%f"}, "");
            row = 0;
            for(String criterion : criteria){
                int col = 0;
                for(String colName : columnNames){
                    if(col==0){
                        criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.threshold_for_criteria[row]);
                    }
                    else {
                        switch(col) {
                            case 1:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.F1_for_criteria[row]);
                                break;
                            case 2:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.F2_for_criteria[row]);
                                break;
                            case 3:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.F0point5_for_criteria[row]);
                                break;
                            case 4:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.accuracy_for_criteria[row]);
                                break;
                            case 5:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.accuracy_for_criteria[row]);
                                break;
                            case 6:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.precision_for_criteria[row]);
                                break;
                            case 7:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.recall_for_criteria[row]);
                                break;
                            case 8:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.specificity_for_criteria[row]);
                                break;
                            case 9:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.mcc_for_criteria[row]);
                                break;
                            case 10:
                                criteriaByThresholdAndScore.set(row, col, modelMetrics._aucdata.max_per_class_error_for_criteria[row]);
                                break;
                        }
                    }
                    col = col + 1;
                }
                row = row + 1;
            }
            this.maxCriteriaAndMetricScores = new TwoDimTableV1().fillFromImpl(criteriaByThresholdAndScore);
        }
        return (S)this;
    }
}

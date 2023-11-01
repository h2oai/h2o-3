from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def thresholds_gbm():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate[1] = prostate[1].asfactor()
    prostate.summary()

    prostate_gbm = H2OGradientBoostingEstimator(nfolds=5, distribution="bernoulli")
    prostate_gbm.train(x=list(range(2, 9)), y=1, training_frame=prostate)
    prostate_gbm.show()

    ths_model = prostate_gbm.thresholds_and_metric_scores()
    ths_perf = prostate_gbm.model_performance(train=True).thresholds_and_metric_scores()
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(ths_model, ths_perf, ths_model.col_header)

    
if __name__ == "__main__":
    pyunit_utils.standalone_test(thresholds_gbm)
else:
    thresholds_gbm()

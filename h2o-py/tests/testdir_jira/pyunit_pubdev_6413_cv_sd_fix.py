import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import math


def cv_nfolds_sd_check():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate[1] = prostate[1].asfactor()
    prostate.summary()

    prostate_gbm = H2OGradientBoostingEstimator(nfolds=4, distribution="bernoulli")
    prostate_gbm.train(x=list(range(2, 9)), y=1, training_frame=prostate)
    prostate_gbm.show()
    prostate_gbm.model_performance(xval=True)
    # extract mean and std column calculated by cross-validation metric
    meanCol = pyunit_utils.extract_col_value_H2OTwoDimTable(prostate_gbm._model_json["output"]["cross_validation_metrics_summary"], "mean")
    stdCol = pyunit_utils.extract_col_value_H2OTwoDimTable(prostate_gbm._model_json["output"]["cross_validation_metrics_summary"], "sd")
    # extract actual values from all folds
    cv1 = pyunit_utils.extract_col_value_H2OTwoDimTable(prostate_gbm._model_json["output"]["cross_validation_metrics_summary"], "cv_1_valid")
    cv2 = pyunit_utils.extract_col_value_H2OTwoDimTable(prostate_gbm._model_json["output"]["cross_validation_metrics_summary"], "cv_2_valid")
    cv3 = pyunit_utils.extract_col_value_H2OTwoDimTable(prostate_gbm._model_json["output"]["cross_validation_metrics_summary"], "cv_3_valid")
    cv4 = pyunit_utils.extract_col_value_H2OTwoDimTable(prostate_gbm._model_json["output"]["cross_validation_metrics_summary"], "cv_4_valid")
    cvVals = [cv1, cv2, cv3, cv4]
    assertMeanSDCalculation(meanCol, stdCol, cvVals) # compare manual mean/std calculation from cross-validation calculation

def assertMeanSDCalculation(meanCol, stdCol, cvVals, tol=1e-6):
    '''
    For performance metrics calculated by cross-validation, we take the actual values and calculated the mean and
    variance manually.  Next we compare the two and make sure they are equal
    
    :param meanCol: mean values over all nfolds
    :param stdCol: std values over all nfolds
    :param cvVals: actual values over all nfolds
    :param tol: error tolerance
    :return: error if the two sets of values are different.
    '''
    nfolds = len(cvVals)
    nItems = len(meanCol)
    oneOverNm1 = 1.0/(nfolds-1.0)
    
    for itemIndex in range(nItems):
        xsum = 0
        xsumSquare = 0
        for foldIndex in range(nfolds):
            temp = float(cvVals[foldIndex][itemIndex])
            xsum += temp
            xsumSquare += temp*temp
        xmean = xsum/nfolds
        if math.isnan(xmean) and math.isnan(float(meanCol[itemIndex])):
            continue
        assert abs(xmean-float(meanCol[itemIndex])) < tol, "Expected mean: {0}, Actual mean: {1}".format(xmean, float(meanCol[itemIndex]))
        xstd = math.sqrt((xsumSquare-nfolds*xmean*xmean)*oneOverNm1)
        assert abs(xstd-float(stdCol[itemIndex])) < tol, "Expected SD: {0}, Actual SD: {1}".format(xstd, float(stdCol[itemIndex]))       

if __name__ == "__main__":
    pyunit_utils.standalone_test(cv_nfolds_sd_check)
else:
    cv_nfolds_sd_check()

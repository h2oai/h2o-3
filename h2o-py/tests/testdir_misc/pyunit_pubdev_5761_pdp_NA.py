import sys
sys.path.insert(1,"../../")
import h2o
import math
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import random
from six import string_types


'''
This test is written to check the following:
1. We can specify the weight column index and the correct weights will be applied to the pdp;
2. We can choose to add a missing values to our predictor of interest if NAs are found in our data.

We will test the following:
1. build a pdp without weight or missing value enabled
2. build a pdp with missing value enabled and with constant weight enabled.
3. build a pdp with missing value enabled and with changing weight enabled.

The mean/std/stderr should agree between the two models except for the missing(NA) level as the first pdp will
not have one.

Implemented the weight mean and std and compare this result with the model found in 3.  In Python test, we will
test a classifier.  In R test, we will test a regressor.
'''
def partial_plot_test():
    # Import data set that contains NAs
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate_cat_NA.csv'))
    x = data.names
    y = 'CAPSULE'
    x.remove(y)

    weights = h2o.H2OFrame([3.0]*data.nrow)
    tweight2 = [1.0]*data.nrow
    random.seed(12345)
    for ind in range(len(tweight2)):
        tweight2[ind] = random.randint(0,5)
    weights2 = h2o.H2OFrame(tweight2)
    data = data.cbind(weights)
    data = data.cbind(weights2)
    data.set_name(data.ncol-2, "constWeight")
    data.set_name(data.ncol-1, "variWeight")

    # Build a GBM model predicting for response CAPSULE
    gbm_model = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.05, seed=12345)
    gbm_model.train(x=x, y=y, training_frame=data)

    # pdp without weight or NA
    pdpOrig = gbm_model.partial_plot(data=data,cols=['AGE', 'RACE'],server=True, plot=True)
    # pdp with constant weight and NA
    pdpcWNA = gbm_model.partial_plot(data=data, cols=['AGE', 'RACE'], server=True, plot=True,
                                     weight_column="constWeight", include_na=True)

    # compare results
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpOrig[0], pdpcWNA[0], pdpOrig[0].col_header, tolerance=1e-10)
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpOrig[1], pdpcWNA[1], pdpOrig[1].col_header, tolerance=1e-10)
    # pdp with changing weight NA
    pdpvWNA = gbm_model.partial_plot(data=data, cols=['AGE', 'RACE'], server=True, plot=True,
                                     weight_column="variWeight", include_na=True)
    ageList = pyunit_utils.extract_col_value_H2OTwoDimTable(pdpvWNA[0], "age")
    raceList = pyunit_utils.extract_col_value_H2OTwoDimTable(pdpvWNA[1], "race")
    raceList.remove(raceList[2])
    raceList.append(data[21,"RACE"]) # replace with NA word
    ageList[len(ageList)-1] = float('nan') # replace nan with proper form for python

    compare_weightedStats(gbm_model, 'smalldata/prostate/prostate_cat_NA.csv', raceList, "RACE", tweight2, pdpvWNA[1], tol=1e-10)
    compare_weightedStats(gbm_model, 'smalldata/prostate/prostate_cat_NA.csv', ageList, "AGE", tweight2, pdpvWNA[0], tol=1e-10)


def compare_weightedStats(model, datafile, xlist, xname, weightV, pdpTDTable, tol=1e-6):
    weightStat =  manual_partial_dependence(model, datafile, xlist, xname, weightV) # calculate theoretical weighted sts
    wMean = pyunit_utils.extract_col_value_H2OTwoDimTable(pdpTDTable, "mean_response") # stats for age predictor
    wStd = pyunit_utils.extract_col_value_H2OTwoDimTable(pdpTDTable, "stddev_response")
    wStdErr = pyunit_utils.extract_col_value_H2OTwoDimTable(pdpTDTable, "std_error_mean_response")
    pyunit_utils.equal_two_arrays(weightStat[0], wMean, tol, tol, throwError=True)
    pyunit_utils.equal_two_arrays(weightStat[1], wStd, tol, tol, throwError=True)
    pyunit_utils.equal_two_arrays(weightStat[2], wStdErr, tol, tol, throwError=True)


def manual_partial_dependence(model, datafile, xlist, xname, weightV):
    dataframe = h2o.import_file(pyunit_utils.locate(datafile))
    meanV = []
    stdV = []
    stderrV = []
    nRows = dataframe.nrow
    nCols = dataframe.ncol-1

    for xval in xlist:
        cons = [xval]*nRows
        if xname in dataframe.names:
            dataframe=dataframe.drop(xname)
        if not((isinstance(xval, string_types) and xval=='NA') or (isinstance(xval, float) and math.isnan(xval))):
            dataframe = dataframe.cbind(h2o.H2OFrame(cons))
            dataframe.set_name(nCols, xname)

        pred = model.predict(dataframe).as_data_frame(use_pandas=False, header=False)
        pIndex = len(pred[0])-1
        sumEle = 0.0
        sumEleSq = 0.0
        sumWeight = 0.0
        numNonZeroWeightCount = 0.0
        m = 1.0/math.sqrt(dataframe.nrow*1.0)
        for rindex in range(len(pred)):
            val = float(pred[rindex][pIndex]);
            weight = weightV[rindex]
            if (abs(weight) > 0) and isinstance(val, float) and not(math.isnan(val)):
                temp = val*weight
                sumEle = sumEle+temp
                sumEleSq = sumEleSq+temp*val
                sumWeight = sumWeight+weight
                numNonZeroWeightCount = numNonZeroWeightCount+1
        wMean = sumEle/sumWeight
        scale = numNonZeroWeightCount*1.0/(numNonZeroWeightCount-1)
        wSTD = math.sqrt((sumEleSq/sumWeight-wMean*wMean)*scale)
        meanV.append(wMean)
        stdV.append(wSTD)
        stderrV.append(wSTD*m)

    return meanV, stdV, stderrV

if __name__ == "__main__":
  pyunit_utils.standalone_test(partial_plot_test)
else:
  partial_plot_test()
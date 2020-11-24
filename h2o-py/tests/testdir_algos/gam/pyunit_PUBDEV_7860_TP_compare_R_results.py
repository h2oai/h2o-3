import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# in this test, we compare binomial and gaussian families with R performance which I recorded earlier.
# since I cannot force jenkins machines to install the R libraries, it is best to do it this way.
def test_compare_R():
    myX = ['c_0','c_1','c_2','c_3','c_4','c_5','c_6','c_7','c_8','c_9','C1','C2','C3','C4','C5','C6','C7','C8','C9','C10']
    myY = 'response'
    gamCols = [["c_0"], ["c_1", "c_2"], ["c_3", "c_4", "c_5"]]  
    bsT = [1, 1, 1]
    scaleP = [0.001, 0.001, 0.001]
    numKnots = [10, 10, 12]
    print("Comparing H2O and R GAM performance for binomial")
    dataBinomial = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv"))
    dataBinomial["C3"] = dataBinomial["C3"].asfactor()
    dataBinomial["C7"] = dataBinomial["C7"].asfactor()
    dataBinomial["C8"] = dataBinomial["C8"].asfactor()
    dataBinomial["C10"] = dataBinomial["C10"].asfactor()
    dataBinomial["response"] = dataBinomial["response"].asfactor()
    frames = dataBinomial.split_frame(ratios=[0.8], seed=1234)
    trainB = frames[0]
    testB = frames[1]
    gamB = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns = gamCols, bs = bsT, scale = scaleP, 
                                           num_knots = numKnots, lambda_search=True)
    gamB.train(x = myX, y = myY, training_frame = trainB, validation_frame = testB)
    gamPred = gamB.predict(testB)
    temp = gamPred['predict'] == testB['response']
    gamBacc = 1-temp.mean()[0,0]
    rAcc = 0.01457801
    print("R accuracy: {0}, H2O accuracy: {1}.".format(rAcc, gamBacc))
    assert gamBacc <= rAcc, "R mean error rate: {0}, H2O mean error rate: {1}. R performs better." \
                                          "".format(rAcc, gamBacc)
    print("Comparing H2O and R GAM performance for gaussian")
    dataGaussian = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/synthetic_20Cols_gaussian_20KRows.csv"))
    dataGaussian["C3"] = dataGaussian["C3"].asfactor()
    dataGaussian["C7"] = dataGaussian["C7"].asfactor()
    dataGaussian["C8"] = dataGaussian["C8"].asfactor()
    dataGaussian["C10"] = dataGaussian["C10"].asfactor()
    frames = dataGaussian.split_frame(ratios=[0.8], seed=1234)
    trainB = frames[0]
    testB = frames[1]
    gamG = H2OGeneralizedAdditiveEstimator(family='gaussian', gam_columns = gamCols, bs = bsT, scale = scaleP,
                                       num_knots = numKnots, lambda_search=True)
    gamG.train(x = myX, y = myY, training_frame = trainB, validation_frame = testB)
    gamMSE = gamG.model_performance(valid=True).mse()
    rMSE = 0.0006933308
    print("R MSE: {0}, H2O MSE: {1}.".format(rMSE, gamMSE))
    assert gamMSE <= rMSE, "R MSE: {0}, H2O MSE: {1}. R performs better." \
                           "".format(rMSE, gamMSE)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_compare_R)
else:
    test_compare_R()

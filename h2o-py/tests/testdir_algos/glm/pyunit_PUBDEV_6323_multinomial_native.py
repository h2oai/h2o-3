import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_glm_multinomial():
    '''
    PUBDEV-6323: implement multinomial to update coefficients of all classes.  I will compare the results of my fix
    with the original implementation and see.  Note that:
    1. alpha = 1, l1pen only
    2. alpha = 0, l2pen only
    3. Lambda = 0, no pen
    4. default, have both l1pen and l2pen
    :return: 
    '''

    d = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_3Class_10KRow.csv")) # user data that shows the problem.
    dtest = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_3Class_test_set_5kRows.csv"))
    enumCols = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16",
                "C17", "C18", "C19", "C20", "C79"]
    
    responseCol = "C79"
    x = d.names
    x.remove(responseCol)
    for ind in range(len(enumCols)):
        d[enumCols[ind]] = d[enumCols[ind]].asfactor()
        dtest[enumCols[ind]] = dtest[enumCols[ind]].asfactor()

    print("######  compare multinomial GLMs with no penalty")
    compare2Algos(d, dtest, 0, 0, x, responseCol, False)   # no penalty, IRLSM_NATIVE does not perform well with no penalty due to gram matrix not being SPD
    print("######  compare multinomial GLMs with both l1 and l2 penalty")
    compare2Algos(d, dtest, 0.5, 0.001, x, responseCol, True)   # with both l1 and l2
    print("######  compare multinomial GLMs with l1")
    compare2Algos(d, dtest, 1, 0.001, x, responseCol, True)   # with l1
    print("######  compare multinomial GLMs with l2")
    compare2Algos(d, dtest, 0, 0.001, x, responseCol, True)   # with l2


def compare2Algos(trainingFrame, testFrame, alpha, Lambda, x, responseCol, compare=False):
    m_default = glm(family='multinomial', seed=12345, solver="IRLSM", alpha=alpha, Lambda=Lambda, nfolds=3)
    m_default.train(training_frame=trainingFrame, x=x, y=responseCol, validation_frame=testFrame)
    test_default = m_default.predict(testFrame)
    default_acc = printModelInfo(m_default, test_default, testFrame, responseCol, 
                                 "Multinomial GLM with IRLSM, alpha = {0}, lambda = {1}".format(alpha, Lambda))
    m_speedup = glm(family='multinomial', seed=12345, solver="IRLSM_NATIVE", alpha=alpha, Lambda=Lambda/3, nfolds=3)
    m_speedup.train(training_frame=trainingFrame, x=x, y=responseCol)
    test_speedup = m_speedup.predict(testFrame)
    speedup_acc = printModelInfo(m_speedup, test_speedup, testFrame, responseCol, 
                                 "Multinomial GLM with IRLSM_NATIVE, alpha = {0}, lambda = {1}".format(alpha, Lambda))
    if compare:
        assert abs(default_acc-speedup_acc) < 0.1, "Prediction accuracy of IRLSM {0} and Prediction accuracy of " \
                                                   "IRLSM_NATIVE {1} differs too much".format(default_acc, speedup_acc)
    
def printModelInfo(model, testResult, testFrame, responseCol, modelType):
    print("********* model information for {0} ********* ".format(modelType))
    print("Model train time (s) :{0}".format(model._model_json["output"]["run_time"]/1000.0))
    predictedVal = testResult[0].as_data_frame(use_pandas=True)
    responseVal = testFrame[responseCol].as_data_frame(use_pandas=True)
    totN = testFrame.nrow
    errS = 0
    for ind in range(0,totN):
        if not(predictedVal['predict'][ind] == responseVal[responseCol][ind]):
            errS =errS+1
    acc = errS*1.0/totN
    print("Model prediction accuracy on test data: {0}".format(acc))
    print(model)
    return acc
    
def fixInt2Enum(h2oframe):
    numCols = h2oframe.ncol

    for cind in range(numCols):
        ctype = h2oframe.type(cind)
        if ctype=='int':
            h2oframe[cind] = h2oframe[cind].asfactor()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_multinomial)
else:
    test_glm_multinomial()

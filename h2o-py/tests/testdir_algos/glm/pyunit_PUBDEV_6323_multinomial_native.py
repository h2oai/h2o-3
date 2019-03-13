import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
import os

def test_glm_multinomial():
    '''
    PUBDEV-6323: implement multinomial to update coefficients of all classes.  I will compare the results of my fix
    with the original implementation and see.  Right now I am going to do this just for IRLSM.
    :return: 
    '''

#    d = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm/multinomial_20Class_training_set_enum_trueOneHot.csv.zip")) # user data that shows the problem.
#    enumCols = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C79"]

    d = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_3Class_10KRow.csv")) # user data that shows the problem.
    enumCols = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16",
                "C17", "C18", "C19", "C20", "C79"]
    
    responseCol = "C79"
    x = d.names
    x.remove(responseCol)
    for ind in range(len(enumCols)):
        d[enumCols[ind]] = d[enumCols[ind]].asfactor()

    m_default = glm(family='multinomial', seed=12345, solver="IRLSM_SPEEDUP")
    m_default_no_admm = glm(family='multinomial', seed=12345, solver="IRLSM_SPEEDUP_NO_ADMM")
    m_default_no_admm.train(training_frame=d, x=x, y=responseCol)
    #m_default = glm(family='multinomial', seed=12345, solver="IRLSM", intercept=True)
    m_default.train(training_frame=d, x=x, y=responseCol)
    runtime_default = m_default._model_json["output"]["run_time"]/1000.0
    print("Training time (s) with default multinomial settings is {0}".format(runtime_default)) # 323 seconds
    print(m_default)

    # m_default_lambda = glm(family='multinomial', seed=12345, solver="irlsm")
    # m_default_lambda.train(training_frame=d, x=x, y=responseCol)
    # runtime_default_lambda = m_default_lambda._model_json["output"]["run_time"]/1000.0
    # print("Training time (s) with default multinomial settings is {0}".format(runtime_default_lambda)) # 323 seconds
    # print(m_default_lambda)
    # 
    # h2o.remove(m_default)
    # h2o.remove(m_default_lambda)




def trainGLRMGLM(d,x,k):
    acs_model = H2OGeneralizedLowRankEstimator(k = k, transform = 'STANDARDIZE')
    acs_model.train(x = x, training_frame= d)
    glrm_time = acs_model._model_json["output"]["run_time"]/1000.0
    print("GLRM build time(s) is "+glrm_time)
    zcta_arch_x = h2o.get_frame(acs_model._model_json['output']['representation_name']) # get x-factor
    xnames = zcta_arch_x.names
    new_train = zcta_arch_x.cbind(d[54])
    m_default = glm(family='multinomial', seed=12345, solver="coordinate_descent")
#    m_default.train(training_frame=new_train, x=xnames, y=responseCol)

def fixInt2Enum(h2oframe):
    numCols = h2oframe.ncol

    for cind in range(numCols):
        ctype = h2oframe.type(cind)
        if ctype=='int':
            h2oframe[cind] = h2oframe[cind].asfactor()


if __name__ == "__main__":
    h2o.init(ip="192.168.86.20", port=54321, strict_version_check=False)
    pyunit_utils.standalone_test(test_glm_multinomial)
else:
    h2o.init(ip="192.168.86.20", port=54321, strict_version_check=False)
    test_glm_multinomial()

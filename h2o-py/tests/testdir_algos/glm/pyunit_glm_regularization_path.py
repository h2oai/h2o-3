import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def reg_path_glm():
    # read in the dataset and construct training set (and validation set)
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    m = glm(family='binomial',lambda_search=True,solver='COORDINATE_DESCENT')
    m.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
    r = glm.getGLMRegularizationPath(m)
    m2 = glm.makeGLMModel(model=m,coefs=r['coefficients'][10])
    dev1 = r['explained_deviance_train'][10]
    p = m2.model_performance(d)
    dev2 = 1-p.residual_deviance()/p.null_deviance()
    print(dev1," =?= ",dev2)
    assert abs(dev1 - dev2) < 1e-6
    for l in range(0,len(r['lambdas'])):
        m = glm(family='binomial',lambda_search=False,Lambda=r['lambdas'][l],solver='COORDINATE_DESCENT')
        m.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
        cs = r['coefficients'][l]
        cs_norm = r['coefficients_std'][l]
        diff = 0
        diff2 = 0
        for n in cs.keys():
            diff = max(diff,abs((cs[n] - m.coef()[n])))
            diff2 = max(diff2,abs((cs_norm[n] - m.coef_norm()[n])))
        print(diff)
        print(diff2)
        assert diff < 1e-2
        assert diff2 < 1e-2
        p = m.model_performance(d)
        devm = 1-p.residual_deviance()/p.null_deviance()
        devn = r['explained_deviance_train'][l]
        print(devm)
        print(devn)
        assert abs(devm - devn) < 1e-4
if __name__ == "__main__":
    pyunit_utils.standalone_test(reg_path_glm)
else:
    reg_path_glm()

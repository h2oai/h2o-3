import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils
import pandas as pd
import statsmodels.api as sm


def test_prostate():

    h2o_data = h2o.upload_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    h2o_data.summary()

    sm_data = pd.read_csv(pyunit_utils.locate("smalldata/logreg/prostate.csv")).values
    sm_data_response = sm_data[:, 1]
    sm_data_features = sm_data[:, 2:]

    h2o_glm = H2OGeneralizedLinearEstimator(family="binomial", nfolds=10, alpha=0.5)
    h2o_glm.train(x=list(range(2, h2o_data.ncol)), y=1, training_frame=h2o_data)
    sm_glm = sm.GLM(endog=sm_data_response, exog=sm_data_features, family=sm.families.Binomial()).fit()

    print("statsmodels null deviance {0}".format(sm_glm.null_deviance))
    print("h2o null deviance {0}".format(h2o_glm.null_deviance()))
    assert abs(sm_glm.null_deviance - h2o_glm.null_deviance()) < 1e-5, "Expected null deviances to be the same"




if __name__ == "__main__":
    pyunit_utils.standalone_test(test_prostate)
else:
    test_prostate()

from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def h2odownload_pojo():
    """
    Python API test: h2o.download_pojo(model, path=u'', get_jar=True)

    Copied from glm_download_pojo.py
    """
    h2o_df = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
    binomial_fit = H2OGeneralizedLinearEstimator(family = "binomial")
    binomial_fit.train(y = "CAPSULE", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = h2o_df)
    try:
        results_dir = pyunit_utils.locate("results")    # find directory path to results folder
        h2o.download_pojo(binomial_fit,path=results_dir)
        assert os.path.isfile(os.path.join(results_dir, "h2o-genmodel.jar")), "h2o.download_pojo() " \
                                                                              "command is not working."
    except:
        h2o.download_pojo(binomial_fit)     # just print pojo to screen if directory does not exists


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2odownload_pojo)
else:
    h2odownload_pojo()

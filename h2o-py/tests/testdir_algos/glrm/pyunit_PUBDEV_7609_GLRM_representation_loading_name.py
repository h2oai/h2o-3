from builtins import range
import sys, os
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

def glrm_arrests():
    print("Importing USArrests.csv data...")
    arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
    arrestsH2O.describe()
    loading_name = "are_we_confused_yet"
    representation_name = "are_you_confused_yet"

    print("H2O initial Y matrix:\n")
    initial_y = [[5.412, 65.24, -7.54, -0.032],
                 [2.212, 92.24, -17.54, 23.268],
                 [0.312, 123.24, 14.46, 9.768],
                 [1.012, 19.24, -15.54, -1.732]]
    initial_y_h2o = h2o.H2OFrame(list(zip(*initial_y)))
    initial_y_h2o.show()

    print("H2O GLRM on de-meaned data with quadratic loss:\n")
    # user representation_name
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=4, transform="DEMEAN", loss="Quadratic", gamma_x=0, gamma_y=0,
                                              init="User", user_y=initial_y_h2o, recover_svd=True,
                                              representation_name=representation_name)
    glrm_h2o.train(x=arrestsH2O.names, training_frame=arrestsH2O)
    assert representation_name == str(glrm_h2o._model_json["output"]['representation_name']), \
        "user assigned x frame name: {0}, actual x frame name from model: {1}. They are not " \
        "equal".format(representation_name, glrm_h2o._model_json["output"]['representation_name'])

    # use loading_name
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=4, transform="DEMEAN", loss="Quadratic", gamma_x=0, gamma_y=0,
                                              init="User", user_y=initial_y_h2o, recover_svd=True,
                                              loading_name=loading_name)
    glrm_h2o.train(x=arrestsH2O.names, training_frame=arrestsH2O)
    assert loading_name == str(glrm_h2o._model_json["output"]['representation_name']), \
        "user assigned x frame name: {0}, actual x frame name from model: {1}. They are not " \
        "equal".format(loading_name, glrm_h2o._model_json["output"]['representation_name'])

    # use loading_name and representation_name but they are different, representation_name should be used
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=4, transform="DEMEAN", loss="Quadratic", gamma_x=0, gamma_y=0,
                                              init="User", user_y=initial_y_h2o, recover_svd=True,
                                              loading_name=loading_name, representation_name=representation_name)
    glrm_h2o.train(x=arrestsH2O.names, training_frame=arrestsH2O)
    assert representation_name == str(glrm_h2o._model_json["output"]['representation_name']), \
        "user assigned x frame name: {0}, actual x frame name from model: {1}. They are not " \
        "equal".format(representation_name, glrm_h2o._model_json["output"]['representation_name'])

if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_arrests)
else:
    glrm_arrests()

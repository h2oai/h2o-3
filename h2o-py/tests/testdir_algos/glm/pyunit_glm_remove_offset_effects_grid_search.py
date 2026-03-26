from collections import OrderedDict

import sys

from h2o.exceptions import H2OResponseError
from h2o.grid import H2OGridSearch

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_grid_search_remove_offset_effects():
    train = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    train = train.drop("ID")
    train["CAPSULE"] = train["CAPSULE"].asfactor()

    response = "CAPSULE"
    features = list(train.col_names)
    features.remove(response)

    hyper_parameters = OrderedDict()
    hyper_parameters["remove_offset_effects"] = [True, False]
    print("GLM grid with the following hyper_parameters:", hyper_parameters)

    gs = H2OGridSearch(H2OGeneralizedLinearEstimator, hyper_params=hyper_parameters)
    try:
        gs.train(x=features, y=response, training_frame=train)
    except H2OResponseError as e:
        assert "Illegal hyper parameter for grid search! The parameter 'remove_offset_effects is not gridable!" in str(e)
    assert True, "The test should fail."

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_grid_search_remove_offset_effects)
else:
    glm_grid_search_remove_offset_effects()

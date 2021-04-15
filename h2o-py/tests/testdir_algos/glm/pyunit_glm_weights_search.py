from __future__ import print_function

from collections import OrderedDict

from builtins import range
import sys

from h2o.grid import H2OGridSearch

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_grid_search_on_weights():
    train = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    train = train.drop("ID")
    train["CAPSULE"] = train["CAPSULE"].asfactor()

    response = "CAPSULE"
    features = train.col_names.copy()
    features.remove(response)

    train['wt_2'] = (train["CAPSULE"] == "1").ifelse(2, 1)
    train['wt_100'] = (train['CAPSULE'] == "1").ifelse(100, 1)

    hyper_parameters = OrderedDict()
    hyper_parameters["weights_column"] = ["wt_2", "wt_100"]
    print("GLM grid with the following hyper_parameters:", hyper_parameters)

    gs = H2OGridSearch(H2OGeneralizedLinearEstimator, hyper_params=hyper_parameters)
    gs.train(x=features, y=response, training_frame=train)
    for m in gs.get_grid().models:
        used_features = map(lambda x: x[1], m.varimp())
        assert not ("wt_2" in used_features)
        assert not ("wt_100" in used_features)
    loglosses = gs.sorted_metric_table()["logloss"]
    assert loglosses.nunique() == 2 # models are not identical (=> weights are considered)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_grid_search_on_weights)
else:
    glm_grid_search_on_weights()

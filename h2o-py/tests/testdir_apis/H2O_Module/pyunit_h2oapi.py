from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.utils.typechecks import assert_is_type
from h2o.backend.connection import H2OResponse

def h2oapi():
    """
    Python API test: h2o.api(endpoint, data=None, json=None, filename=None, save_to=None)
    """
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]

    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model.train(x=X, y=Y, training_frame=training_data)
    frame_api = h2o.api("GET /3/Frames/%s/summary" % training_data.frame_id)
    assert_is_type(frame_api, H2OResponse)
    hf_col_summary = h2o.api("GET /3/Frames/%s/summary" % training_data.frame_id)["frames"][0]
    # test h2o.api() getting frame information
    assert hf_col_summary["row_count"]==100, "row count is incorrect.  Fix h2o.api()."
    assert hf_col_summary["column_count"]==14, "column count is incorrect.  Fix h2o.api()."

    # test h2o.api() getting model information
    model_api = h2o.api("GET /3/GetGLMRegPath", data={"model": model._model_json["model_id"]["name"]})
    assert_is_type(model_api, H2OResponse)
    model_coefficients = model_api["coefficients"][0]
    assert len(model_coefficients)==11, "Number of coefficients is wrong.  h2o.api() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oapi)
else:
    h2oapi()

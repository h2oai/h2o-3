from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
import os
from tests import pyunit_utils, assert_equals
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator


def download_model_filename():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    model = H2OGradientBoostingEstimator(ntrees=10, seed=1234)
    model.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    # Default filename is model_id
    model_path = model.download_model()
    # It should be saved in server working directory
    assert model_path.endswith(model.model_id), "Not expected path: {0}".format(model_path)
    loaded_model = h2o.load_model(model_path)
    assert isinstance(loaded_model, H2OGradientBoostingEstimator)

    # Default filename is model_id
    tmpdir = tempfile.mkdtemp()
    model_path = model.download_model(tmpdir)
    assert_equals(os.path.join(tmpdir, model.model_id), model_path, "Not expected path")
    loaded_model = h2o.load_model(model_path)
    assert isinstance(loaded_model, H2OGradientBoostingEstimator)

    # Custom filename with custom path
    model_path = model.download_model(tmpdir, filename="gbm_prostate")
    assert_equals(os.path.join(tmpdir, "gbm_prostate"), model_path, "Not expected path")
    loaded_model = h2o.load_model(model_path)
    assert isinstance(loaded_model, H2OGradientBoostingEstimator)

    # Custom filename with custom path
    model_path = model.download_model(tmpdir, filename="gbm_prostate.model")
    assert_equals(os.path.join(tmpdir, "gbm_prostate.model"), model_path, "Not expected path")
    loaded_model = h2o.load_model(model_path)
    assert isinstance(loaded_model, H2OGradientBoostingEstimator)

    # Custom filename with custom path
    model_path = model.download_model(tmpdir, filename=os.path.join("not-existing-folder", "gbm_prostate.model"))
    assert_equals(os.path.join(tmpdir, "not-existing-folder", "gbm_prostate.model"), model_path, "Not expected path")
    loaded_model = h2o.load_model(model_path)
    assert isinstance(loaded_model, H2OGradientBoostingEstimator)

    # Custom filename with default path
    model_path = model.download_model(filename="gbm_prostate2.model")
    assert model_path.endswith("gbm_prostate2.model"), "Not expected path: {0}".format(model_path)
    loaded_model = h2o.load_model(model_path)
    assert isinstance(loaded_model, H2OGradientBoostingEstimator)


if __name__ == "__main__":
    pyunit_utils.standalone_test(download_model_filename)
else:
    download_model_filename()

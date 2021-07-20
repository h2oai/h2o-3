from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
import os
from tests import pyunit_utils, assert_equals
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator


def save_model_details_filename():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    model = H2OGradientBoostingEstimator(ntrees=10, seed=1234)
    model.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    # Default filename is model_id
    model_details_path = model.save_model_details()
    # It should be saved in server working directory
    assert model_details_path.endswith(model.model_id + ".json"), "Not expected path: {0}".format(model_details_path)
    assert os.path.isfile(model_details_path), "File not exists in path: {0}".format(model_details_path)

    # Default filename is model_id
    tmpdir = tempfile.mkdtemp()
    model_details_path = model.save_model_details(tmpdir)
    assert_equals(os.path.join(tmpdir, model.model_id + ".json"), model_details_path, "Not expected path")
    assert os.path.isfile(model_details_path), "File not exists in path: {0}".format(model_details_path)

    # Custom filename with custom path
    model_details_path = model.save_model_details(tmpdir, filename="gbm_prostate")
    assert_equals(os.path.join(tmpdir, "gbm_prostate"), model_details_path, "Not expected path")
    assert os.path.isfile(model_details_path), "File not exists in path: {0}".format(model_details_path)

    # Custom filename with custom path
    model_details_path = model.save_model_details(tmpdir, filename="gbm_prostate.json")
    assert_equals(os.path.join(tmpdir, "gbm_prostate.json"), model_details_path, "Not expected path")
    assert os.path.isfile(model_details_path), "File not exists in path: {0}".format(model_details_path)

    # Custom filename with custom path
    model_details_path = model.save_model_details(tmpdir, filename=os.path.join("not-existing-folder", "gbm_prostate.json"))
    assert_equals(os.path.join(tmpdir, "not-existing-folder", "gbm_prostate.json"), model_details_path, "Not expected path")
    assert os.path.isfile(model_details_path), "File not exists in path: {0}".format(model_details_path)

    # Custom filename with default path
    model_details_path = model.save_model_details(filename="gbm_prostate2.json")
    assert model_details_path.endswith("gbm_prostate2.json"), "Not expected path: {0}".format(model_details_path)
    assert os.path.isfile(model_details_path), "File not exists in path: {0}".format(model_details_path)


if __name__ == "__main__":
    pyunit_utils.standalone_test(save_model_details_filename)
else:
    save_model_details_filename()

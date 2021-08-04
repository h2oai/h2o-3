from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
import os
from tests import pyunit_utils, assert_equals
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator


def download_mojo_filename():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    model = H2OGradientBoostingEstimator(ntrees=10, seed=1234)
    model.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    # Default location is current working directory and filename is model_id
    mojo_path = model.download_mojo()
    assert_equals(os.path.join(os.getcwd(), model.model_id + ".zip"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Location is parent of current working directory and filename is model_id
    mojo_path = model.download_mojo("..")
    assert_equals(os.path.abspath(os.path.join(os.pardir, model.model_id + ".zip")), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Location is home directory and filename is model_id
    mojo_path = model.download_mojo("~")
    assert_equals(os.path.abspath(os.path.expanduser(os.path.join("~", model.model_id + ".zip"))), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Default locations is current working directory with custom filename
    mojo_path = model.download_mojo("gbm_prostate.zip")
    assert_equals(os.path.join(os.getcwd(), "gbm_prostate.zip"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Location is current working directory with custom filename
    mojo_path = model.download_mojo("./gbm_prostate.zip")
    assert_equals(os.path.join(os.getcwd(), "gbm_prostate.zip"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Location is parent of current working directory with custom filename
    mojo_path = model.download_mojo("../gbm_prostate.zip")
    assert_equals(os.path.abspath(os.path.join(os.pardir, "gbm_prostate.zip")), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Location is home directory with custom filename
    mojo_path = model.download_mojo("~/gbm_prostate.zip")
    assert_equals(os.path.abspath(os.path.expanduser(os.path.join("~", "gbm_prostate.zip"))), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with custom path
    tmpdir = tempfile.mkdtemp()
    mojo_path = model.download_mojo(os.path.join(tmpdir, "gbm_prostate.zip"))
    assert_equals(os.path.join(tmpdir, "gbm_prostate.zip"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)


if __name__ == "__main__":
    pyunit_utils.standalone_test(download_mojo_filename)
else:
    download_mojo_filename()

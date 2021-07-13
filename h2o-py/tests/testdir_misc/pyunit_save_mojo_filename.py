from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
import os
from tests import pyunit_utils, assert_equals
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator


def save_mojo_filename():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    model = H2OGradientBoostingEstimator(ntrees=10, seed=1234)
    model.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    # Default filename is model_id
    mojo_path = model.save_mojo()
    # It should be saved in server working directory
    assert mojo_path.endswith(model.model_id + ".zip"), "Not expected path: {0}".format(mojo_path)
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Default filename is model_id
    tmpdir = tempfile.mkdtemp()
    mojo_path = model.save_mojo(tmpdir)
    assert_equals(os.path.join(tmpdir, model.model_id + ".zip"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with custom path
    mojo_path = model.save_mojo(tmpdir, filename="gbm_prostate")
    assert_equals(os.path.join(tmpdir, "gbm_prostate"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with custom path
    mojo_path = model.save_mojo(tmpdir, filename="gbm_prostate.zip")
    assert_equals(os.path.join(tmpdir, "gbm_prostate.zip"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with custom path
    mojo_path = model.save_mojo(tmpdir, filename=os.path.join("not-existing-folder", "gbm_prostate.zip"))
    assert_equals(os.path.join(tmpdir, "not-existing-folder", "gbm_prostate.zip"), mojo_path, "Not expected path")
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with default path
    mojo_path = model.save_mojo(filename="gbm_prostate2.zip")
    assert mojo_path.endswith("gbm_prostate2.zip"), "Not expected path: {0}".format(mojo_path)
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)


if __name__ == "__main__":
    pyunit_utils.standalone_test(save_mojo_filename)
else:
    save_mojo_filename()

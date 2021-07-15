from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator


def save_mojo_filename():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    model = H2OGradientBoostingEstimator(ntrees=10, seed=1234)
    model.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    # Default filename is model_id
    tmpdir = tempfile.mkdtemp()
    mojo_path = model.save_mojo(tmpdir)
    assert (model.model_id + ".zip") in mojo_path
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with custom path
    mojo_path = model.save_mojo(tmpdir, filename="gbm_prostate.zip")
    assert "gbm_prostate.zip" in mojo_path
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with default path
    mojo_path = model.save_mojo(filename="gbm_prostate2.zip")
    assert "gbm_prostate2.zip" in mojo_path
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)


if __name__ == "__main__":
    pyunit_utils.standalone_test(save_mojo_filename)
else:
    save_mojo_filename()

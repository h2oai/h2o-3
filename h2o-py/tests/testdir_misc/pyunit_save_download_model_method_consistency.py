from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator


def test_consistency_of(model, model_save_download_method, i):
    # Default filename is model_id
    tmpdir = tempfile.mkdtemp()
    mojo_path = model_save_download_method(tmpdir)
    assert (model.model_id + ".zip") in mojo_path
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with custom path
    mojo_path = model_save_download_method(tmpdir, filename="gbm_prostate.zip")
    assert "gbm_prostate.zip" in mojo_path
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Custom filename with default path
    mojo_path = model_save_download_method(filename="gbm_prostate{0}.zip".format(i))
    assert "gbm_prostate{0}.zip".format(i) in mojo_path
    mojo_model = h2o.import_mojo(mojo_path)
    assert isinstance(mojo_model, H2OGenericEstimator)


def save_download_model_method_consistency_test():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    model = H2OGradientBoostingEstimator(ntrees=10, seed=1234)
    model.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    test_consistency_of(model, model.download_mojo, 0)
    test_consistency_of(model, model.save_mojo, 1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(save_download_model_method_consistency_test)
else:
    save_download_model_method_consistency_test()

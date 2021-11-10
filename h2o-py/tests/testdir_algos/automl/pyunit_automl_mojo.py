from __future__ import print_function
import os
import sys
import tempfile
import time

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset


def automl_mojo():
    ds = import_dataset()
    aml = H2OAutoML(max_models=2, project_name="py_lb_test_aml1", seed=1234)
    aml.train(y=ds.target, training_frame=ds.train)

    # download mojo
    model_zip_path = os.path.join(tempfile.mkdtemp(), 'model.zip')
    time0 = time.time()
    print("\nDownloading MOJO @... " + model_zip_path)
    mojo_file  = aml.download_mojo(model_zip_path)
    print("    => %s  (%d bytes)" % (mojo_file, os.stat(mojo_file).st_size))
    assert os.path.exists(mojo_file)
    print("    Time taken = %.3fs" % (time.time() - time0))
    assert os.path.isfile(model_zip_path)
    os.remove(model_zip_path)


pu.run_tests([
    automl_mojo
])

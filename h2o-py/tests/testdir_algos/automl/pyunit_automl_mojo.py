from __future__ import print_function
import os
import sys
sys.path.insert(1, os.path.join("..","..",".."))
import tempfile
import time
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

def automl_mojo():
    fr1 = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr1["CAPSULE"] = fr1["CAPSULE"].asfactor()
    aml = H2OAutoML(max_models=2, project_name="py_lb_test_aml1", seed=1234)
    aml.train(y="CAPSULE", training_frame=fr1)

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

if __name__ == "__main__":
    pyunit_utils.standalone_test(automl_mojo)
else:
    automl_mojo()
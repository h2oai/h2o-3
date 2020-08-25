import sys
import tempfile
import shutil
import time
import os

import pandas as pd
import numpy as np
from pandas.testing import assert_frame_equal

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator

genmodel_name = "h2o-genmodel.jar"


def download_mojo(model, mojo_zip_path, genmodel_path=None):
    mojo_zip_path = os.path.abspath(mojo_zip_path)
    parent_dir = os.path.dirname(mojo_zip_path)

    print("\nDownloading MOJO @... " + parent_dir)
    time0 = time.time()
    if genmodel_path is None:
        genmodel_path = os.path.join(parent_dir, genmodel_name)
    mojo_file = model.download_mojo(path=mojo_zip_path, get_genmodel_jar=True, genmodel_name=genmodel_path)

    print("    => %s  (%d bytes)" % (mojo_file, os.stat(mojo_file).st_size))
    assert os.path.exists(mojo_file)
    print("    Time taken = %.3fs" % (time.time() - time0))
    assert os.path.exists(mojo_zip_path)
    print("    => %s  (%d bytes)" % (mojo_zip_path, os.stat(mojo_zip_path).st_size))
    assert os.path.exists(genmodel_path)
    print("    => %s  (%d bytes)" % (genmodel_path, os.stat(genmodel_path).st_size))


def mojo_predict_csv_test(target_dir):
    mojo_file_name = "prostate_isofor_model.zip"
    mojo_zip_path = os.path.join(target_dir, mojo_file_name)

    data_path = pyunit_utils.locate("smalldata/logreg/prostate.csv")
    prostate = h2o.import_file(path=data_path)

    # =================================================================
    # Isolation Forest
    # =================================================================
    isofor = H2OIsolationForestEstimator()
    isofor.train(training_frame=prostate)

    pred_h2o = isofor.predict(prostate)
    pred_h2o_df = pred_h2o.as_data_frame(use_pandas=True)

    download_mojo(isofor, mojo_zip_path)

    output_csv = "%s/prediction.csv" % target_dir
    print("\nPerforming Isolation Forest Prediction using MOJO @... " + target_dir)
    pred_mojo_csv = h2o.mojo_predict_csv(input_csv_path=data_path, mojo_zip_path=mojo_zip_path,
                                         output_csv_path=output_csv)
    pred_mojo_df = pd.DataFrame(pred_mojo_csv, dtype=np.float64, columns=["predict", "mean_length"])
    print("*** pred_h2o_df ***")
    print(pred_h2o_df)
    print("***pred_mojo_df ***")
    print(pred_mojo_df)
    assert_frame_equal(pred_h2o_df, pred_mojo_df, check_dtype=False)


api_test_dir = tempfile.mkdtemp()
try:
    if __name__ == "__main__":
        pyunit_utils.standalone_test(lambda: mojo_predict_csv_test(api_test_dir))
    else:
        mojo_predict_csv_test(api_test_dir)
finally:
    shutil.rmtree(api_test_dir)

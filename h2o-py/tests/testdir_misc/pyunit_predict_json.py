from builtins import range
import sys
import tempfile
import shutil
import time
import os
import json
sys.path.insert(1,"../../")
import h2o
import pandas as pd
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def predict_json_test(target_dir):
    mojo_file_name = "prostate_gbm_model.zip"
    gen_model_name = "h2o-genmodel.jar"

    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    r = prostate[0].runif()
    train = prostate[r < 0.70]
    test = prostate[r >= 0.70]

    # Getting first row from test data frame
    pdf = test[1,2:]
    x  = pdf.as_data_frame()
    df_json = x.to_json(orient='records')
    print(df_json)

    # =================================================================
    # Regression
    # =================================================================
    regression_gbm1 = H2OGradientBoostingEstimator(distribution="gaussian")
    regression_gbm1.train(x=[2,3,4,5,6,7,8], y=1, training_frame=train)
    pred_reg = regression_gbm1.predict(pdf)
    p1 =  pred_reg[0,0]
    print("Regression prediction: " + str(p1))

    print("\nDownloading MOJO @... " + target_dir)
    time0 = time.time()
    model_with_path = target_dir + "/" + mojo_file_name
    genmodel_with_path = target_dir + "/" + gen_model_name
    mojo_file = regression_gbm1.download_mojo(path=model_with_path, get_genmodel_jar=True, genmodel_name=genmodel_with_path)
    print("    => %s  (%d bytes)" % (mojo_file, os.stat(mojo_file).st_size))
    assert os.path.exists(mojo_file)
    print("    Time taken = %.3fs" % (time.time() - time0))
    assert os.path.exists(model_with_path)
    print("    => %s  (%d bytes)" % (model_with_path, os.stat(model_with_path).st_size))
    assert os.path.exists(genmodel_with_path)
    print("    => %s  (%d bytes)" % (genmodel_with_path, os.stat(genmodel_with_path).st_size))


    print("\nPerforming Regression Prediction using MOJO @... " + target_dir)
    prediction_result = h2o.predict_json(target_dir,
                          mojo_file_name,
                          df_json)
    p2 = json.loads(prediction_result)[0]["value"]
    print("Prediction result: " + str(p2))
    assert p1 == p2, "expected predictions to be the same for binary and MOJO model for regression"

    # =================================================================
    # Binomial
    # =================================================================
    train[1] = train[1].asfactor()
    bernoulli_gbm1 = H2OGradientBoostingEstimator(distribution="bernoulli")

    bernoulli_gbm1.train(x=[2,3,4,5,6,7,8],y=1,training_frame=train)
    pred_bin = bernoulli_gbm1.predict(pdf)

    bin_p0 =  pred_bin[0,1]
    bin_p1 =  pred_bin[0,2]
    print("Binomial prediction: p0: " + str(bin_p0))
    print("Binomial prediction: p1: " + str(bin_p1))

    print("\nDownloading MOJO @... " + target_dir)
    time0 = time.time()
    model_with_path = target_dir + "/" + mojo_file_name
    genmodel_with_path = target_dir + "/" + gen_model_name
    mojo_file = bernoulli_gbm1.download_mojo(path=model_with_path, get_genmodel_jar=True, genmodel_name=genmodel_with_path)
    print("    => %s  (%d bytes)" % (mojo_file, os.stat(mojo_file).st_size))
    assert os.path.exists(mojo_file)
    print("    Time taken = %.3fs" % (time.time() - time0))
    assert os.path.exists(model_with_path)
    print("    => %s  (%d bytes)" % (model_with_path, os.stat(model_with_path).st_size))
    assert os.path.exists(genmodel_with_path)
    print("    => %s  (%d bytes)" % (genmodel_with_path, os.stat(genmodel_with_path).st_size))

    print("\nPerforming Binomial Prediction using MOJO @... " + target_dir)
    prediction_result = h2o.predict_json(target_dir,
                                         mojo_file_name,
                                         df_json)
    bin_values = json.loads(prediction_result)[0]["classProbabilities"]

    bin_p20 = bin_values[0]
    bin_p21 = bin_values[1]
    print("Binomial prediction: p0: " + str(bin_p20))
    print("Binomial prediction: p1: " + str(bin_p21))

    assert bin_p0 == bin_p20, "expected predictions to be the same for binary and MOJO model for Binomial - p0"
    assert bin_p1 == bin_p21, "expected predictions to be the same for binary and MOJO model for Binomial - p1"


try:
    target_dir = tempfile.mkdtemp()
    if __name__ == "__main__":
        pyunit_utils.standalone_test(lambda: predict_json_test(target_dir))
    else:
        predict_json_test(target_dir)
finally:
    #shutil.rmtree(target_dir)
    print("Done!!")


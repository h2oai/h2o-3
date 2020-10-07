import sys
import tempfile
import shutil
import time
import os

import pandas
from pandas.testing import assert_frame_equal

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


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


def mojo_predict_api_test(sandbox_dir):
    data = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    input_csv = "%s/in.csv" % sandbox_dir
    output_csv = "%s/prediction.csv" % sandbox_dir
    h2o.export_file(data[1, 2:], input_csv)

    data[1] = data[1].asfactor()
    model = H2OGradientBoostingEstimator(distribution="bernoulli")
    model.train(x=[2, 3, 4, 5, 6, 7, 8], y=1, training_frame=data)

    # download mojo
    model_zip_path = os.path.join(sandbox_dir, 'model.zip')
    genmodel_path = os.path.join(sandbox_dir, 'h2o-genmodel.jar')
    download_mojo(model, model_zip_path)
    assert os.path.isfile(model_zip_path)
    assert os.path.isfile(genmodel_path)

    # test that we can predict using default paths
    h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path, verbose=True)
    h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path, genmodel_jar_path=genmodel_path,
                               verbose=True)
    assert os.path.isfile(output_csv)
    os.remove(model_zip_path)
    os.remove(genmodel_path)
    os.remove(output_csv)

    # test that we can predict using custom genmodel path
    other_sandbox_dir = tempfile.mkdtemp()
    try:
        genmodel_path = os.path.join(other_sandbox_dir, 'h2o-genmodel-custom.jar')
        download_mojo(model, model_zip_path, genmodel_path)
        assert os.path.isfile(model_zip_path)
        assert os.path.isfile(genmodel_path)
        try:
            h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path, verbose=True)
            assert False, "There should be no h2o-genmodel.jar at %s" % sandbox_dir
        except RuntimeError:
            pass
        assert not os.path.isfile(output_csv)
        h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path,
                                   genmodel_jar_path=genmodel_path, verbose=True)
        assert os.path.isfile(output_csv)
        os.remove(output_csv)

        output_csv = "%s/out.prediction" % other_sandbox_dir

        # test that we can predict using default paths
        h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path,
                                   genmodel_jar_path=genmodel_path, verbose=True, output_csv_path=output_csv)
        assert os.path.isfile(output_csv)
        os.remove(model_zip_path)
        os.remove(genmodel_path)
        os.remove(output_csv)
    finally:
        shutil.rmtree(other_sandbox_dir)


def mojo_predict_csv_test(target_dir):
    mojo_file_name = "prostate_gbm_model.zip"
    mojo_zip_path = os.path.join(target_dir, mojo_file_name)

    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    r = prostate[0].runif()
    train = prostate[r < 0.70]
    test = prostate[r >= 0.70]

    # Getting first row from test data frame
    pdf = test[1, 2:]
    input_csv = "%s/in.csv" % target_dir
    output_csv = "%s/output.csv" % target_dir
    h2o.export_file(pdf, input_csv)

    # =================================================================
    # Regression
    # =================================================================
    regression_gbm1 = H2OGradientBoostingEstimator(distribution="gaussian")
    regression_gbm1.train(x=[2, 3, 4, 5, 6, 7, 8], y=1, training_frame=train)
    pred_reg = regression_gbm1.predict(pdf)
    contribs_reg = regression_gbm1.predict_contributions(pdf)
    p1 = pred_reg[0, 0]
    print("Regression prediction: " + str(p1))

    download_mojo(regression_gbm1, mojo_zip_path)

    print("\nPerforming Regression Prediction using MOJO @... " + target_dir)
    prediction_result = h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path,
                                             output_csv_path=output_csv)
    print("Prediction result: " + str(prediction_result))
    assert p1 == float(prediction_result[0]['predict']), "expected predictions to be the same for binary and MOJO model for regression"

    print("\nComparing Regression Contributions using MOJO @... " + target_dir)
    contributions_result = h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path,
                                                output_csv_path=output_csv, predict_contributions=True)
    assert contributions_result is not None
    contributions_pandas = pandas.read_csv(output_csv)
    assert_frame_equal(contribs_reg.as_data_frame(use_pandas=True), contributions_pandas, check_dtype=False)

    # =================================================================
    # Binomial
    # =================================================================
    train[1] = train[1].asfactor()
    bernoulli_gbm1 = H2OGradientBoostingEstimator(distribution="bernoulli")

    bernoulli_gbm1.train(x=[2, 3, 4, 5, 6, 7, 8], y=1, training_frame=train)
    pred_bin = bernoulli_gbm1.predict(pdf)
    contribs_bin = bernoulli_gbm1.predict_contributions(pdf)

    binary_prediction_0 = pred_bin[0, 1]
    binary_prediction_1 = pred_bin[0, 2]
    print("Binomial prediction: p0: " + str(binary_prediction_0))
    print("Binomial prediction: p1: " + str(binary_prediction_1))

    download_mojo(bernoulli_gbm1, mojo_zip_path)

    print("\nPerforming Binomial Prediction using MOJO @... " + target_dir)
    prediction_result = h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path,
                                             output_csv_path=output_csv)

    mojo_prediction_0 = float(prediction_result[0]['p0'])
    mojo_prediction_1 = float(prediction_result[0]['p1'])
    print("Binomial prediction: p0: " + str(mojo_prediction_0))
    print("Binomial prediction: p1: " + str(mojo_prediction_1))

    assert binary_prediction_0 == mojo_prediction_0, "expected predictions to be the same for binary and MOJO model for Binomial - p0"
    assert binary_prediction_1 == mojo_prediction_1, "expected predictions to be the same for binary and MOJO model for Binomial - p1"

    print("\nComparing Binary Classification Contributions using MOJO @... " + target_dir)
    contributions_bin_result = h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path,
                                                    output_csv_path=output_csv, predict_contributions=True)
    assert contributions_bin_result is not None
    contributions_bin_pandas = pandas.read_csv(output_csv)
    print(contributions_bin_pandas)
    print(contribs_bin.as_data_frame(use_pandas=True))
    assert_frame_equal(contribs_bin.as_data_frame(use_pandas=True), contributions_bin_pandas, check_dtype=False)

    # =================================================================
    # Multinomial
    # =================================================================
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    r = iris[0].runif()
    train = iris[r < 0.90]
    test = iris[r >= 0.10]

    # Getting first row from test data frame
    pdf = test[1, 0:4]
    input_csv = "%s/in-multi.csv" % target_dir
    output_csv = "%s/output.csv" % target_dir
    h2o.export_file(pdf, input_csv)

    multi_gbm = H2OGradientBoostingEstimator()
    multi_gbm.train(x=['C1', 'C2', 'C3', 'C4'], y='C5', training_frame=train)

    pred_multi = multi_gbm.predict(pdf)
    multinomial_prediction_1 = pred_multi[0, 1]
    multinomial_prediction_2 = pred_multi[0, 2]
    multinomial_prediction_3 = pred_multi[0, 3]
    print("Multinomial prediction (Binary): p0: " + str(multinomial_prediction_1))
    print("Multinomial prediction (Binary): p1: " + str(multinomial_prediction_2))
    print("Multinomial prediction (Binary): p2: " + str(multinomial_prediction_3))

    download_mojo(multi_gbm, mojo_zip_path)

    print("\nPerforming Multinomial Prediction using MOJO @... " + target_dir)
    prediction_result = h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path,
                                                   output_csv_path=output_csv)

    mojo_prediction_1 = float(prediction_result[0]['Iris-setosa'])
    mojo_prediction_2 = float(prediction_result[0]['Iris-versicolor'])
    mojo_prediction_3 = float(prediction_result[0]['Iris-virginica'])
    print("Multinomial prediction (MOJO): p0: " + str(mojo_prediction_1))
    print("Multinomial prediction (MOJO): p1: " + str(mojo_prediction_2))
    print("Multinomial prediction (MOJO): p2: " + str(mojo_prediction_3))

    assert multinomial_prediction_1 == mojo_prediction_1, "expected predictions to be the same for binary and MOJO model for Multinomial - p0"
    assert multinomial_prediction_2 == mojo_prediction_2, "expected predictions to be the same for binary and MOJO model for Multinomial - p1"
    assert multinomial_prediction_3 == mojo_prediction_3, "expected predictions to be the same for binary and MOJO model for Multinomial - p2"


def mojo_predict_pandas_test(sandbox_dir):
    data = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    input_csv = "%s/in.csv" % sandbox_dir
    pdf = data[1, 2:]
    h2o.export_file(pdf, input_csv)

    data[1] = data[1].asfactor()
    model = H2OGradientBoostingEstimator(distribution="bernoulli")
    model.train(x=[2, 3, 4, 5, 6, 7, 8], y=1, training_frame=data)

    h2o_prediction = model.predict(pdf)
    h2o_contributions = model.predict_contributions(pdf)
    
    # download mojo
    model_zip_path = os.path.join(sandbox_dir, 'model.zip')
    genmodel_path = os.path.join(sandbox_dir, 'h2o-genmodel.jar')
    download_mojo(model, model_zip_path)
    assert os.path.isfile(model_zip_path)
    assert os.path.isfile(genmodel_path)

    pandas_frame = pandas.read_csv(input_csv)
    mojo_prediction = h2o.mojo_predict_pandas(dataframe=pandas_frame, mojo_zip_path=model_zip_path, genmodel_jar_path=genmodel_path)
    print("Binomial Prediction (Binary) - p0: %f" % h2o_prediction[0,1])
    print("Binomial Prediction (Binary) - p1: %f" % h2o_prediction[0,2])
    print("Binomial Prediction (MOJO) - p0: %f" % mojo_prediction['p0'].iloc[0])
    print("Binomial Prediction (MOJO) - p1: %f" % mojo_prediction['p1'].iloc[0])
    assert h2o_prediction[0,1] == mojo_prediction['p0'].iloc[0], "expected predictions to be the same for binary and MOJO model - p0"
    assert h2o_prediction[0,2] == mojo_prediction['p1'].iloc[0], "expected predictions to be the same for binary and MOJO model - p0"

    mojo_contributions = h2o.mojo_predict_pandas(dataframe=pandas_frame, mojo_zip_path=model_zip_path,
                                                 genmodel_jar_path=genmodel_path, predict_contributions=True)
    assert_frame_equal(h2o_contributions.as_data_frame(use_pandas=True), mojo_contributions, check_dtype=False)


csv_test_dir = tempfile.mkdtemp()
api_test_dir = tempfile.mkdtemp()
pandas_test_dir = tempfile.mkdtemp()
try:
    if __name__ == "__main__":
        pyunit_utils.standalone_test(lambda: mojo_predict_api_test(api_test_dir))
        pyunit_utils.standalone_test(lambda: mojo_predict_csv_test(csv_test_dir))
        pyunit_utils.standalone_test(lambda: mojo_predict_pandas_test(pandas_test_dir))
    else:
        mojo_predict_api_test(api_test_dir)
        mojo_predict_csv_test(csv_test_dir)
        mojo_predict_pandas_test(pandas_test_dir)
finally:
    shutil.rmtree(csv_test_dir)
    shutil.rmtree(api_test_dir)
    shutil.rmtree(pandas_test_dir)

import sys
import tempfile
import shutil
import time
import os

import pandas
from pandas.testing import assert_frame_equal

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


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


def mojo_predict_csv_test(sandbox_dir):
    data = h2o.import_file(path=pyunit_utils.locate("smalldata/coxph_test/heart.csv"))

    input_csv = "%s/in.csv" % sandbox_dir
    output_csv = "%s/prediction.csv" % sandbox_dir
    h2o.export_file(data, input_csv)

    data['transplant'] = data['transplant'].asfactor()
    model = H2OCoxProportionalHazardsEstimator(stratify_by = ["transplant"], start_column="start", stop_column="stop")
    model.train(x=["age", "surgery", "transplant"], y="event", training_frame=data)
    
    h2o_prediction = model.predict(data)
    
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
        mojo_prediction = h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path,
                                   genmodel_jar_path=genmodel_path, verbose=True, output_csv_path=output_csv)
        assert os.path.isfile(output_csv)
        os.remove(model_zip_path)
        os.remove(genmodel_path)
        os.remove(output_csv)

        print(h2o_prediction)
        print(mojo_prediction)

        assert len(mojo_prediction) == h2o_prediction.nrows

        assert_frame_equal(h2o_prediction.as_data_frame(use_pandas=True), pandas.DataFrame([float(m['lp']) for m in mojo_prediction], columns=["lp"]), check_dtype=False)
    finally:
        shutil.rmtree(other_sandbox_dir)


def mojo_predict_pandas_test(sandbox_dir):
    data = h2o.import_file(path=pyunit_utils.locate("smalldata/coxph_test/heart.csv"))

    input_csv = "%s/in.csv" % sandbox_dir
    output_csv = "%s/prediction.csv" % sandbox_dir
    h2o.export_file(data, input_csv)

    data['transplant'] = data['transplant'].asfactor()
    model = H2OCoxProportionalHazardsEstimator(stratify_by = ["transplant"], start_column="start", stop_column="stop")
    model.train(x=["age", "surgery", "transplant"], y="event", training_frame=data)

    h2o_prediction = model.predict(data)

    # download mojo
    model_zip_path = os.path.join(sandbox_dir, 'model.zip')
    genmodel_path = os.path.join(sandbox_dir, 'h2o-genmodel.jar')
    download_mojo(model, model_zip_path)
    assert os.path.isfile(model_zip_path)
    assert os.path.isfile(genmodel_path)

    pandas_frame = pandas.read_csv(input_csv)
    mojo_prediction = h2o.mojo_predict_pandas(dataframe=pandas_frame, mojo_zip_path=model_zip_path, genmodel_jar_path=genmodel_path)
    
    assert len(mojo_prediction) == h2o_prediction.nrow
    assert_frame_equal(h2o_prediction.as_data_frame(use_pandas=True), mojo_prediction, check_dtype=False)

csv_test_dir = tempfile.mkdtemp()
api_test_dir = tempfile.mkdtemp()
pandas_test_dir = tempfile.mkdtemp()
try:
    if __name__ == "__main__":
        pyunit_utils.standalone_test(lambda: mojo_predict_csv_test(api_test_dir))
        pyunit_utils.standalone_test(lambda: mojo_predict_pandas_test(pandas_test_dir))
    else:
        mojo_predict_csv_test(api_test_dir)
        mojo_predict_pandas_test(pandas_test_dir)
finally:
    shutil.rmtree(csv_test_dir)
    shutil.rmtree(api_test_dir)
    shutil.rmtree(pandas_test_dir)

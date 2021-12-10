import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.rulefit import H2ORuleFitEstimator


def iris():
    df = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"),
                       col_types={'species': "enum"})
    x = df.columns
    y = "species"
    x.remove(y)

    # Split the dataset into train and test
    train, test = df.split_frame(ratios=[.8], seed=1234)

    rfit = H2ORuleFitEstimator(min_rule_length=4, max_rule_length=5, max_num_rules=3, seed=1234, model_type="rules")
    rfit.train(training_frame=train, x=x, y=y, validation_frame=test)

    assert rfit.rmse(valid=True) is not None, "validation metrics should be present"


    print(rfit.rule_importance())
    assert rfit._model_json["output"]["model_summary"] is not None, "model_summary should be present"
    assert len(rfit._model_json["output"]["model_summary"]._cell_values) > 0, "model_summary's content should be present"

    rfit_predictions = rfit.predict(test)

    frame = rfit.predict_rules(train, ['M0T38N5_Iris-virginica'])
    assert frame.sum().getrow()[0] == 49.0

    import tempfile
    tmpdir = tempfile.mkdtemp()

    try:
        mojo_path = rfit.save_mojo(tmpdir)
        mojo_model = h2o.upload_mojo(mojo_path)
    finally:
        import shutil
        shutil.rmtree(tmpdir)

    mojo_predictions = mojo_model.predict(test)

    assert pyunit_utils.compare_frames(rfit_predictions, mojo_predictions, 0)

    # test predict_rules also on linear variable input
    rfit = H2ORuleFitEstimator(min_rule_length=4, max_rule_length=5, max_num_rules=3, seed=1234, model_type="rules_and_linear")
    rfit.train(training_frame=train, x=x, y=y, validation_frame=test)
    print(rfit.rule_importance())
    frame = rfit.predict_rules(train, ['linear.petal_len_Iris-setosa', 'linear.petal_wid_Iris-virginica'])
    assert frame.sum().getrow()[0] == train.nrows
    

if __name__ == "__main__":
  pyunit_utils.standalone_test(iris)
else:
    iris()

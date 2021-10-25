import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.rulefit import H2ORuleFitEstimator


def ecology():
    df = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"),
                       col_types={'Angaus': "enum"})
    x = df.columns
    y = "Angaus"
    x.remove(y)
    x.remove("Site")

    # Split the dataset into train and test
    train, test = df.split_frame(ratios=[.8], seed=1234)

    rfit = H2ORuleFitEstimator(min_rule_length=1, max_rule_length=10, max_num_rules=100, seed=1234, model_type="rules")
    rfit.train(training_frame=train, x=x, y=y, validation_frame=test)

    assert rfit.rmse(valid=True) is not None, "validation metrics should be present"


    print(rfit.rule_importance())
    assert rfit._model_json["output"]["model_summary"] is not None, "model_summary should be present"
    assert len(rfit._model_json["output"]["model_summary"]._cell_values) > 0, "model_summary's content should be present"

    rfit_predictions = rfit.predict(test)

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

if __name__ == "__main__":
  pyunit_utils.standalone_test(ecology)
else:
    ecology()

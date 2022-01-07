from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML
from pandas.testing import assert_frame_equal


def xgboost_with_te_mojo():
    train = h2o.import_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"), header=1)
    target = "survived"

    aml = H2OAutoML(max_models=1, seed=917, preprocessing=["target_encoding"], include_algos=['xgboost'], nfolds=2)
    aml.train(y=target, training_frame=train)
    model = aml.get_best_model()
    print(model)

    results_dir = pyunit_utils.locate("results")
    mojo_path = model.save_mojo(results_dir)
    mojo = h2o.import_mojo(mojo_path, model_id="imported_mojo")

    mojo_p1 = mojo.predict(train)["Yes"].as_data_frame(use_pandas=True)
    model_p1 = model.predict(train)["Yes"].as_data_frame(use_pandas=True)

    assert_frame_equal(model_p1, mojo_p1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_with_te_mojo)
else:
    xgboost_with_te_mojo()

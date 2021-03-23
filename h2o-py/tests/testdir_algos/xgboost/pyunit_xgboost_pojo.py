import sys
import os
sys.path.insert(1, "../../../")
from h2o.estimators.xgboost import *
from tests import pyunit_utils


def compare_preds(train, test, x, y, booster, ntrees, max_depth, max_error):
    model = H2OXGBoostEstimator(
        booster=booster, seed=1, 
        ntrees=ntrees, max_depth=max_depth
    )
    model.train(training_frame=train, x=x, y=y)

    mojo_name = pyunit_utils.getMojoName(model._id)
    tmp_dir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", mojo_name))
    os.makedirs(tmp_dir)
    model.download_mojo(path=tmp_dir)

    h2o.download_csv(test[x], os.path.join(tmp_dir, 'in.csv'))
    pred_h2o = model.predict(test[x])
    h2o.download_csv(pred_h2o, os.path.join(tmp_dir, "out_h2o.csv"))
    pred_pojo = pyunit_utils.pojo_predict(model, tmp_dir, mojo_name)
    print("%s: Comparing pojo %s predict and h2o predict..." % (model._id, booster))
    pyunit_utils.compare_frames_local(pred_h2o, pred_pojo, 1, tol=max_error)


def test_booster(booster, max_error=1e-6):
    assert H2OXGBoostEstimator.available()

    # prostate - regression without categoricals
    prostate_data = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_train, prostate_test = prostate_data.split_frame(ratios=[.8], seed=1)
    prostate_x = ['AGE', 'RACE', 'DPROS', 'DCAPS']
    prostate_y = 'CAPSULE'
    compare_preds(
        prostate_train, prostate_test, prostate_x, prostate_y, 
        booster, ntrees=20, max_depth=3, max_error=max_error
    )
    compare_preds(
        prostate_train, prostate_test, prostate_x, prostate_y, 
        booster, ntrees=1, max_depth=10, max_error=max_error
    )
    compare_preds(
        prostate_train, prostate_test, prostate_x, prostate_y, 
        booster, ntrees=12, max_depth=12, max_error=max_error
    )

    # insurance - regression with categoricals
    insurance_train = h2o.import_file(pyunit_utils.locate("smalldata/testng/insurance_train1.csv"))
    insurance_test = h2o.import_file(pyunit_utils.locate("smalldata/testng/insurance_validation1.csv"))
    insurance_x = ['Age', 'District', 'Group', 'Holders']
    insurance_y = 'Claims'
    compare_preds(
        insurance_train, insurance_test, insurance_x, insurance_y, 
        booster, ntrees=20, max_depth=3, max_error=max_error
    )
    compare_preds(
        insurance_train, insurance_test, insurance_x, insurance_y, 
        booster, ntrees=1, max_depth=10, max_error=max_error
    )
    compare_preds(
        insurance_train, insurance_test, insurance_x, insurance_y, 
        booster, ntrees=12, max_depth=12, max_error=max_error
    )

    # cars classification (binomial)
    cars_data = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars_x = ["displacement", "power", "weight", "acceleration", "year"]
    cars_y1 = "economy_20mpg"
    cars_data[cars_data[cars_y1].isna(), cars_y1] = 0
    cars_data[cars_y1] = cars_data[cars_y1].asfactor()
    cars_train, cars_test = cars_data.split_frame(ratios=[.8], seed=1)
    compare_preds(
        cars_train, cars_test, cars_x, cars_y1, 
        booster, ntrees=1, max_depth=12, max_error=max_error
    )
    compare_preds(
        cars_train, cars_test, cars_x, cars_y1, 
        booster, ntrees=12, max_depth=6, max_error=max_error
    )

    # cars classification (binomial)
    cars_y2 = "cylinders"
    cars_data[cars_data[cars_y2].isna(), cars_y2] = 0
    cars_data[cars_y2] = cars_data[cars_y2].asfactor()
    cars_train, cars_test = cars_data.split_frame(ratios=[.8], seed=1)
    compare_preds(
        cars_train, cars_test, cars_x, cars_y2, 
        booster, ntrees=1, max_depth=12, max_error=max_error
    )
    compare_preds(
        cars_train, cars_test, cars_x, cars_y2, 
        booster, ntrees=12, max_depth=6, max_error=max_error
    )


def test_xgboost_pojo():
    test_booster("gbtree")
    test_booster("dart")
    test_booster("gblinear", 1e-5)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_xgboost_pojo)
else:
    test_xgboost_pojo()

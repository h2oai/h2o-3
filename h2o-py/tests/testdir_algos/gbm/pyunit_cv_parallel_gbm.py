import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def cv_nfolds_gbm():
    loan_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    loan_data["CAPSULE"] = loan_data["CAPSULE"].asfactor()

    model_default = H2OGradientBoostingEstimator(nfolds=5, distribution="bernoulli", ntrees=500,
                                                 score_tree_interval=3, stopping_rounds=2, seed=42,
                                                 ignored_columns=["ID"])
    model_default.train(y="CAPSULE", training_frame=loan_data)
    preds_default = model_default.predict(loan_data)

    model_sequential = H2OGradientBoostingEstimator(nfolds=5, distribution="bernoulli", ntrees=500,
                                                    score_tree_interval=3, stopping_rounds=2, seed=42,
                                                    ignored_columns=["ID"],
                                                    parallel_main_model_building=False)
    model_sequential.train(y="CAPSULE", training_frame=loan_data)
    preds_sequential = model_sequential.predict(loan_data)

    assert model_default.actual_params["ntrees"] == model_sequential.actual_params["ntrees"]
    pyunit_utils.compare_frames_local(preds_default, preds_sequential, prob=1.0)


if __name__ == "__main__":
    pyunit_utils.standalone_test(cv_nfolds_gbm)
else:
    cv_nfolds_gbm()

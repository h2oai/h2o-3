import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def cv_ntrees_gbm():
    loan_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    loan_data["CAPSULE"] = loan_data["CAPSULE"].asfactor()

    model = H2OGradientBoostingEstimator(nfolds=5, distribution="auto", ntrees=500,
                                                    score_tree_interval=3, stopping_rounds=2, seed=42,
                                                    ignored_columns=["ID"])
    model.train(y="CAPSULE", training_frame=loan_data)

    # ntrees based on best score:
    assert model.cross_validation_models()[0].summary()['number_of_trees'][0] == 21
    # ntrees before some trees removed:
    assert model.cross_validation_models()[0].summary()['number_of_internal_trees'][0] == 24




if __name__ == "__main__":
    pyunit_utils.standalone_test(cv_ntrees_gbm)
else:
    cv_ntrees_gbm()

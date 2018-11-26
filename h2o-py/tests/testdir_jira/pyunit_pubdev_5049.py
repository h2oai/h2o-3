from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def get_ntrees(model):
    return max(model._model_json["output"]["scoring_history"]["number_of_trees"])


def demo_xval_with_validation_frame():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate[1] = prostate[1].asfactor()
    print(prostate.summary())

    # invert the response
    prostate_inverse = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    resp = (1 - prostate_inverse[1])
    prostate_inverse[1] = resp.asfactor()
    print(prostate_inverse.summary())

    # 50 is a default but lets be explict
    ntrees = 50
    X = list(range(2,9))
    y = 1

    # 1. Train a model with 5-fold xval, no validation frame
    prostate_gbm = H2OGradientBoostingEstimator(nfolds=5,
                                                ntrees=ntrees,
                                                distribution="bernoulli",
                                                seed=1,
                                                score_each_iteration=True,
                                                stopping_rounds=3)
    prostate_gbm.train(x=X, y=y, training_frame=prostate)
    prostate_gbm.show()
    # stopped early
    assert get_ntrees(prostate_gbm) < ntrees

    # 2. Show that training a model without xval with inverted validation frame triggers early stopping earlier
    # Validation frame contradicts the training frame and training should stop right away
    prostate_gbm_noxval = H2OGradientBoostingEstimator(ntrees=ntrees,
                                                       distribution="bernoulli",
                                                       seed=1,
                                                       score_each_iteration=True,
                                                       stopping_rounds=3)
    prostate_gbm_noxval.train(x=X, y=y, training_frame=prostate, validation_frame=prostate_inverse)
    prostate_gbm_noxval.show()
    # stopped almost immediately
    assert get_ntrees(prostate_gbm_noxval) == 6
    # earlier then in case of (1)
    assert get_ntrees(prostate_gbm_noxval) < get_ntrees(prostate_gbm)

    # 3. Train a model with 5-fold xval this time with inverted frame as the validation frame
    prostate_gbm_v = H2OGradientBoostingEstimator(nfolds=5,
                                                  ntrees=ntrees,
                                                  distribution="bernoulli",
                                                  seed=1,
                                                  score_each_iteration=True,
                                                  stopping_rounds=3)
    prostate_gbm_v.train(x=X, y=y, training_frame=prostate, validation_frame=prostate_inverse)
    prostate_gbm_v.show()

    # Models (1) and (3) are the same => validation cannot be used for early stopping in xval setting
    # Otherwise we would have stopped earlier as we see in (2)
    pyunit_utils.check_models(prostate_gbm, prostate_gbm_v)
    # Stopped early with same number of trees build as in (1)
    assert get_ntrees(prostate_gbm) == get_ntrees(prostate_gbm_v)


if __name__ == "__main__":
    pyunit_utils.standalone_test(demo_xval_with_validation_frame)
else:
    demo_xval_with_validation_frame()

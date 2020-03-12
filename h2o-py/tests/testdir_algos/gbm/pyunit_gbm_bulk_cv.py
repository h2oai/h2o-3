import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.model.segment_models import H2OSegmentModels


# test bulk model building with CV and also providing a validation frame 
def test_gbm_bulk_cv():
    response = "survived"
    titanic = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/titanic.csv"))
    titanic[response] = titanic[response].asfactor()
    predictors = ["survived", "name", "sex", "age", "sibsp", "parch", "ticket", "fare", "cabin"]
    train, valid = titanic.split_frame(ratios=[.8], seed=1234)
    titanic_gbm = H2OGradientBoostingEstimator(seed=1234, nfolds=2, build_tree_one_node=True)
    titanic_gbm.bulk_train(segments=["pclass"],
                           x=predictors,
                           y=response,
                           training_frame=train,
                           validation_frame=valid,
                           segment_models_id="titanic_by_pclass")

    train_cl1 = train[train["pclass"] == 1]
    valid_cl1 = valid[valid["pclass"] == 1]
    titanic_cl1_gbm = H2OGradientBoostingEstimator(seed=1234, nfolds=2)
    titanic_cl1_gbm.train(x=predictors,
                          y=response,
                          training_frame=train_cl1,
                          validation_frame=valid_cl1)

    titanic_models = H2OSegmentModels(segment_models_id="titanic_by_pclass")
    bulk_models = titanic_models.as_frame()
    titanic_bulk_cl1_gbm_id = (bulk_models[bulk_models["pclass"] == 1]["Model"])
    titanic_bulk_cl1_gbm = h2o.get_model(titanic_bulk_cl1_gbm_id.flatten())

    pyunit_utils.check_models(titanic_cl1_gbm, titanic_bulk_cl1_gbm, use_cross_validation=True)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gbm_bulk_cv)
else:
    test_gbm_bulk_cv()

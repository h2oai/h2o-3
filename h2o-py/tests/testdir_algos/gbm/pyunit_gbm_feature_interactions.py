import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils
import sys

sys.path.insert(1, "../../../")


def gbm_feature_interaction_converged_with_less_trees():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    train["C55"] = train["C55"].asfactor()

    model = H2OGradientBoostingEstimator(ntrees=200, min_split_improvement=1e-1)
    model.train(x=list(range(55)), y="C55", training_frame=train)
    print(model)
    
    feature_interactions = model.feature_interaction()
    print(feature_interactions[0])


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_feature_interaction_converged_with_less_trees)
else:
    gbm_feature_interaction_converged_with_less_trees()

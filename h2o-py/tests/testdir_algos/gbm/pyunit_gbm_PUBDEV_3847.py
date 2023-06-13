import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from random import randint

# Turn the PUBDEV-3847 issue into the test and check if it fails
def test_pubdev_3847():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/pubdev_3847.csv"), destination_frame="train")
    train.describe()

    ntrees = 100
    max_depth = 6
    min_rows = 5
    learn_rate = 0.1
    sample_rate = 0.8
    col_sample_rate_per_tree = 0.6
    nfolds = 2
    min_split_improvement = 1e-04
    response = "class"
    features = train.col_names.remove(response)

    print("Train 100 GBM models to test if it fails.")
    for i in range(1,100):
        seed = randint(1000, 2000)
        print(i, ": train model with random seed: ",seed)
        my_gbm = H2OGradientBoostingEstimator(ntrees=ntrees,
                                          max_depth=max_depth,
                                          min_rows=min_rows,
                                          learn_rate=learn_rate,
                                          sample_rate=sample_rate,
                                          col_sample_rate_per_tree=col_sample_rate_per_tree,
                                          nfolds=nfolds,
                                          min_split_improvement=min_split_improvement, seed=seed)
        my_gbm.train(x=features, y=response, training_frame=train, validation_frame=train)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_pubdev_3847)
else:
    test_pubdev_3847()

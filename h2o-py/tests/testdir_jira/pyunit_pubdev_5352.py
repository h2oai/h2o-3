import h2o
from h2o.estimators.random_forest import H2ORandomForestEstimator
from tests import pyunit_utils

def pubdev_5352():

    mnist_original = h2o.import_file(pyunit_utils.locate("smalldata/flow_examples/mnist/test.csv.gz"));
    predictors = mnist_original.columns[0:-1]
    target = 'C785'
    train, new_data = mnist_original.split_frame(ratios=[.5], seed=1234)
    drf = H2ORandomForestEstimator(model_id='drf', ntrees=3, seed=1234)
    drf.train(x=predictors, y=target, training_frame=train)
    drf_checkpoint = H2ORandomForestEstimator(model_id='drf_checkpoint',checkpoint=drf.model_id, ntrees=4, seed=1234)
    drf_checkpoint.train(x=predictors, y=target, training_frame=new_data)

    assert drf_checkpoint.ntrees == 4




if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5352)
else:
    pubdev_5352()

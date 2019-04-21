from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator

# This test does not have an assert at the end and it is an okay.  In PUBDEV-4456, Nidhi noticed that when
# we have a weight column, this test will fail.  This is caused by the assertion error of fs[1]>=0 && fs[1]>=1.
# This failure manisfested as: When fs[1] is 1, it is read back as 1.0000000000000002. To get around this error,
# I just added a tolerance at the end of the bound and changed the assertion to:
#     assert(fs[1]>=-1e-12 && fs[1] <= 1+1e-12.
# This solves the problem for now.
#
# I filed a JIRA PUBDEV-4142 to tackle this problem.  Once this JIRA is completed, I will go in and remove the hack that
# I put in.

def test_data_with_weights():

  train  = h2o.upload_file(pyunit_utils.locate("smalldata/airlines/modified_airlines.csv"))
  splits = train.split_frame(ratios=[0.7])
  train = splits[0]
  test = splits[1]

  hh1 = H2ORandomForestEstimator(ntrees=1000, seed=1234, score_tree_interval=10, stopping_rounds=20,
                                 stopping_metric="AUC", stopping_tolerance=0.001, max_runtime_secs=20*60)


  hh1.train(x=list(range(10)), y=30, training_frame=train, validation_frame=test, weights_column="weight")


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_data_with_weights)
else:
  test_data_with_weights()

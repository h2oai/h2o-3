import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def nb_init_err():


  print "Importing iris_wheader.csv data...\n"
  iris = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  iris.describe

  print "Laplace smoothing parameter is negative"
  from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator
  try:
    H2ONaiveBayesEstimator(laplace=-1).train(x=range(4), y=4, training_frame=iris)
    assert False, "Expected naive bayes algo to fail on negative laplace training parameter"
  except:
    pass

  print "Minimum standard deviation is zero"
  try:
    H2ONaiveBayesEstimator().train(x=range(4), y=4, training_frame=iris, min_sdev=0)
    assert False, "Expected naive bayes algo to fail on min_sdev = 0"
  except:
    pass

  print "Response column is not categorical"
  try:
    H2ONaiveBayesEstimator().train(x=range(3), y=3, min_sdev=0)
    assert False, "Expected naive bayes algo to fail on response not categorical"
  except:
    pass



if __name__ == "__main__":
  pyunit_utils.standalone_test(nb_init_err)
else:
  nb_init_err()

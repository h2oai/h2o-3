from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator



def nb_iris():


  print("Importing iris_wheader.csv data...\n")
  iris = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  iris.describe()

  iris_nbayes = H2ONaiveBayesEstimator()
  iris_nbayes.train(x=list(range(4)), y=4, training_frame=iris, validation_frame=iris)
  iris_nbayes.show()

  iris_nbayes = H2ONaiveBayesEstimator(nfolds=3)
  iris_nbayes.train(x=list(range(4)), y=4, training_frame=iris, validation_frame=iris, seed=1234)
  iris_nbayes.show()

  iris_nbayes = H2ONaiveBayesEstimator(nfolds=3)
  iris_nbayes.train(x=list(range(4)), y=4, training_frame=iris, seed=1234)
  iris_nbayes.show()

  iris_nbayes = H2ONaiveBayesEstimator(nfolds=3,fold_assignment="Modulo")
  iris_nbayes.train(x=list(range(4)), y=4, training_frame=iris)
  iris_nbayes.show()

  print("And here it is:")
  print(iris_nbayes.cross_validation_metrics_summary())
  print(iris_nbayes.cross_validation_metrics_summary().as_data_frame())
  print(iris_nbayes.cross_validation_metrics_summary().as_data_frame()['mean'])

if __name__ == "__main__":
  pyunit_utils.standalone_test(nb_iris)
else:
  nb_iris()

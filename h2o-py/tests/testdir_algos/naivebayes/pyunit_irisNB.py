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


  laplace_range = [0, 1, 0.25]
  for i in laplace_range:
    print("H2O Naive Bayes with Laplace smoothing = {0}".format(i))
    iris_nbayes = H2ONaiveBayesEstimator(laplace=i)
    iris_nbayes.train(x=list(range(4)), y=4, training_frame=iris)
    iris_nbayes.show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(nb_iris)
else:
  nb_iris()

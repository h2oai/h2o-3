import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def checkpoint_new_category_in_response():

<<<<<<< HEAD
  from h2o.estimators.deeplearning import H2ODeepLearningEstimator
=======
    sv = h2o.upload_file(pyunit_utils.locate("smalldata/iris/setosa_versicolor.csv"))
    iris = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
>>>>>>> 4ce985f40b6c8f18cf4c40ca27ba158ffd1f04f4

  sv = h2o.upload_file(tests.locate("smalldata/iris/setosa_versicolor.csv"))
  iris = h2o.upload_file(tests.locate("smalldata/iris/iris.csv"))

  m1 = H2ODeepLearningEstimator(epochs=100)
  m1.train(X=[0,1,2,3], y=4, training_frame=sv)


<<<<<<< HEAD
  # attempt to continue building model, but with an expanded categorical response domain.
  # this should fail
  try:
    m2 = H2ODeepLearningEstimator(checkpoint=m1.model_id,epochs=200)
    m2.train(X=[0,1,2,3], y=4, training_frame=iris)
    assert False, "Expected continued model-building to fail with new categories introduced in response"
  except EnvironmentError:
    pass

<<<<<<< HEAD
if __name__ == '__main__':
  tests.run_test(sys.argv, checkpoint_new_category_in_response)
=======

checkpoint_new_category_in_response()
>>>>>>> 4ce985f40b6c8f18cf4c40ca27ba158ffd1f04f4
=======

if __name__ == "__main__":
    pyunit_utils.standalone_test(checkpoint_new_category_in_response)
else:
    checkpoint_new_category_in_response()
>>>>>>> d4ce8c93ff1691521387bb6b5767f34548251eb4

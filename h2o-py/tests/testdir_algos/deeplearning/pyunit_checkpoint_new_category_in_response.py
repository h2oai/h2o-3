import sys
sys.path.insert(1,"../../../")
import h2o, tests

def checkpoint_new_category_in_response():

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator

  sv = h2o.upload_file(tests.locate("smalldata/iris/setosa_versicolor.csv"))
  iris = h2o.upload_file(tests.locate("smalldata/iris/iris.csv"))

  m1 = H2ODeepLearningEstimator(epochs=100)
  m1.train(X=[0,1,2,3], y=4, training_frame=sv)


  # attempt to continue building model, but with an expanded categorical response domain.
  # this should fail
  try:
    m2 = H2ODeepLearningEstimator(checkpoint=m1.model_id,epochs=200)
    m2.train(X=[0,1,2,3], y=4, training_frame=iris)
    assert False, "Expected continued model-building to fail with new categories introduced in response"
  except EnvironmentError:
    pass

if __name__ == '__main__':
  tests.run_test(sys.argv, checkpoint_new_category_in_response)

import sys
sys.path.insert(1,"../../../")
import h2o, tests

def checkpoint_new_category_in_response():

    sv = h2o.upload_file(h2o.locate("smalldata/iris/setosa_versicolor.csv"))
    iris = h2o.upload_file(h2o.locate("smalldata/iris/iris.csv"))

    m1 = h2o.deeplearning(x=sv[[0,1,2,3]], y=sv[4], epochs=100)

    # attempt to continue building model, but with an expanded categorical response domain.
    # this should fail
    try:
        m2 = h2o.deeplearning(x=iris[[0,1,2,3]], y=iris[4], epochs=200, checkpoint=m1.id)
        assert False, "Expected continued model-building to fail with new categories introduced in response"
    except EnvironmentError:
        pass

if __name__ == '__main__':
    tests.run_test(sys.argv, checkpoint_new_category_in_response)
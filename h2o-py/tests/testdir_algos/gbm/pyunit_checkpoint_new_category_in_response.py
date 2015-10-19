

import h2o, tests

def checkpoint_new_category_in_response():

    sv = h2o.upload_file(tests.locate("smalldata/iris/setosa_versicolor.csv"))
    iris = h2o.upload_file(tests.locate("smalldata/iris/iris.csv"))

    m1 = h2o.gbm(x=sv[[0,1,2,3]], y=sv[4], ntrees=100)

    # attempt to continue building model, but with an expanded categorical response domain.
    # this should fail
    try:
        m2 = h2o.gbm(x=iris[[0,1,2,3]], y=iris[4], ntrees=200, checkpoint=m1.model_id)
        assert False, "Expected continued model-building to fail with new categories introduced in response"
    except EnvironmentError:
        pass


pyunit_test = checkpoint_new_category_in_response

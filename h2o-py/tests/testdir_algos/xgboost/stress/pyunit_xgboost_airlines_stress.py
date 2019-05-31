import h2o
from timeit import default_timer as timer
from h2o.estimators import H2OXGBoostEstimator


from tests import pyunit_utils

# Create many small models
def models_stress_test():
    data = h2o.import_file(pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    # Ulimit of stress tests should be set low enough, e.g. 100, in case of file descriptors leaks, this test should fail.
    num_models = 1000
    start = timer()

    for i in range(0,num_models):
        xgb = H2OXGBoostEstimator(ntrees = 1, max_depth= 2)
        xgb.train(x = ["Origin","Distance"],y="IsDepDelayed", training_frame=data)
        h2o.remove(xgb)

    end = timer()
    print ('Trained {} models in {} seconds.'.format(num_models, end - start))
if __name__ == "__main__":
    pyunit_utils.standalone_test(models_stress_test)
else:
    models_stress_test()

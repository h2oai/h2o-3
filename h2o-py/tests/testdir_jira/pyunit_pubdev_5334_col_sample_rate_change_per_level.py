import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import random
from h2o.estimators.random_forest import H2ORandomForestEstimator

def pubdev_5334():
    '''
    This pyunit test is used to make sure that the parameter col_sample_rate_change_per_level is set
    correctly > 0.0 and <= 2.  Another other setting will bring an error.
    '''
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris2.csv"))
    tval = random.uniform(-3,3)
    print("col_sample_rate_change_per_level is {0}".format(tval))
    try:
        model = H2ORandomForestEstimator(ntrees=5, max_depth=3, col_sample_rate_change_per_level=tval)
        model.train(y=4, x=list(range(4)), training_frame=iris)
        if tval > 0 and tval <= 2:
            print("col_sample_rate_change_per_level is set correctly.")
        else:
            exit(1)
    except:
        if tval <= 0 or tval > 2:
            print("An error has been thrown: col_sample_rate_change_per_level must be > 0 and <= 2.0.")
        else:
            exit(1)




if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5334)
else:
    pubdev_5334()

import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import random
import string

def nb_baddata():


    rawdata = [[random.gauss(0,1) for r in range(100)] for c in range(10)]

    print "Training data with all NA's"
    train = [["NA" for r in range(100)] for c in range(10)]
    train_h2o = h2o.H2OFrame(train)
    try:
        h2o.naive_bayes(x=train_h2o[1:10], y=train_h2o[0])
        assert False, "Expected naive bayes algo to fail on training data of all NA's"
    except:
        pass

    # Response column must be categorical
    print "Training data with a numeric response column"
    train_h2o = h2o.H2OFrame(rawdata)
    try:
        h2o.naive_bayes(x=train_h2o[1:10], y=train_h2o[0])
        assert False, "Expected naive bayes algo to fail on training data with a numeric response column"
    except:
        pass

    # Constant response dropped before model building
    print "Training data with a constant response: drop and throw error"
    rawdata[0] = 100 * ["A"]
    train_h2o = h2o.H2OFrame(rawdata)
    try:
        h2o.naive_bayes(x=train_h2o[1:10], y=train_h2o[0])
        assert False, "Expected naive bayes algo to fail on training data with a constant response: drop and throw error"
    except:
        pass

    # Predictors with constant value automatically dropped
    print "Training data with 1 col of all 5's: drop automatically"
    rawdata = [[random.gauss(0,1) for r in range(100)] for c in range(10)]
    rawdata[4] = 100 * [5]
    rawdata[0] = [random.choice(string.letters) for _ in range(100)]
    train_h2o = h2o.H2OFrame(rawdata)
    model = h2o.naive_bayes(x=train_h2o[1:10], y=train_h2o[0])
    assert len(model._model_json['output']['pcond']) == 8, "Expected 8 predictors, but got {0}" \
                                                           "".format(len(model._model_json['output']['pcond']))



if __name__ == "__main__":
    pyunit_utils.standalone_test(nb_baddata)
else:
    nb_baddata()

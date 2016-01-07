from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import string
import random
import pandas as pd
from pandas.util.testing import assert_frame_equal

'''
Export file with h2o.export_file and compare with Python counterpart when re importing file to check for parity.
'''

def export_file():
    pros_hex = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    pros_hex[1] = pros_hex[1].asfactor()
    pros_hex[3] = pros_hex[3].asfactor()
    pros_hex[4] = pros_hex[4].asfactor()
    pros_hex[5] = pros_hex[5].asfactor()
    pros_hex[8] = pros_hex[8].asfactor()

    p_sid = pros_hex.runif()
    pros_train = pros_hex[p_sid > .2, :]
    pros_test = pros_hex[p_sid <= .2, :]

    glm = H2OGeneralizedLinearEstimator(family="binomial")
    myglm = glm.train(x=list(range(2, pros_hex.ncol)), y=1, training_frame=pros_train)
    mypred = glm.predict(pros_test)

    def id_generator(size=6, chars=string.ascii_uppercase + string.digits):
        return ''.join(random.choice(chars) for _ in range(size))

    fname = id_generator() + "_prediction.csv"

    path = pyunit_utils.locate("results")
    dname = path + "/" + fname

    h2o.export_file(mypred,dname)

    py_pred = pd.read_csv(dname)
    print(py_pred.head())
    h_pred = mypred.as_data_frame(True)
    print(h_pred.head())

    #Test to check if py_pred & h_pred are identical
    try:
        assert_frame_equal(py_pred,h_pred)
        return True
    except:
        return False

if __name__ == "__main__":
    pyunit_utils.standalone_test(export_file)
else:
    export_file()




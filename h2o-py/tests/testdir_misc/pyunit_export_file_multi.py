from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import string
import os
import glob
import random
import pandas as pd
from pandas.util.testing import assert_frame_equal

'''
Export file with h2o.export_file and compare with Python counterpart when re importing file to check for parity.
This is a variation of a default h2o.export_file test. This tests makes sure that support for export to multiple
'part' files is working. This test checks that when user specifies number of part files a directory is created
instead of just a single file. It doesn't check the actual number of part files.
'''

def export_file_multipart():
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

    path = pyunit_utils.locate("results")
    dname = os.path.join(path, id_generator() + "_prediction")

    h2o.export_file(mypred, dname, parts=-1)

    assert os.path.isdir(dname)

    part_files = glob.glob(os.path.join(dname, "part-m-?????"))
    print(part_files)
    py_pred = pd.concat((pd.read_csv(f) for f in part_files))
    print(py_pred.head())
    h_pred = mypred.as_data_frame(True)
    print(h_pred.head())

    #Test to check if py_pred & h_pred are identical
    assert_frame_equal(py_pred,h_pred)

if __name__ == "__main__":
    pyunit_utils.standalone_test(export_file_multipart)
else:
    export_file_multipart()




from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

def test_fold_column():

    train = h2o.import_file(path=pyunit_utils.locate("smalldata/census_income/adult_data.csv"))

    aml = H2OAutoML(max_runtime_secs = 420, stopping_rounds=3,stopping_tolerance=0.05,seed=42)
    aml.train(y='income', training_frame=train, fold_column = 'education-num')

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_fold_column)
else:
    test_fold_column()

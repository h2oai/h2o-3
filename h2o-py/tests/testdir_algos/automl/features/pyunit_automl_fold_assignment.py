from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

def test_fold_column():

    train = h2o.import_file(path=pyunit_utils.locate("smalldata/census_income/adult_data.csv"))

    # Name the project so that we can run this multiple times, with multiple imports, and have the same leaderboard.
    # Note that to do so I've changed the test in the __main__ case to do its own init(), since pyunit_utils.standalone_test()
    # clears the DKV.
    aml = H2OAutoML(project_name = "adult_data", seed = 42, stopping_rounds = 3, stopping_tolerance = 0.00001, max_models = 2, max_runtime_secs = 100000)
    aml.train(y='income', training_frame=train, fold_column = 'education-num')

if __name__ == "__main__":
# See above for the reason I don't do pyunit_utils.standalone_test() here:
#    pyunit_utils.standalone_test(test_fold_column)
    h2o.init(strict_version_check=False)
    test_fold_column()
else:
    test_fold_column()

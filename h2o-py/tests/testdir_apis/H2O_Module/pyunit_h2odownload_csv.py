from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2odownload_csv():
    """
    Python API test: h2o.download_csv(data, filename)
    """
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    try:
        results_dir = pyunit_utils.locate("results")    # find directory path to results folder
        filename = os.path.join(results_dir, "benign.csv")
        h2o.download_csv(training_data, filename)       # save csv
        assert os.path.isfile(filename), "h2o.download_csv() command is not working."
    except Exception as e:
        if 'File not found' in e.args[0]:
            print("Directory is not writable.  h2o.download_csv() command is not tested.")
        else:
            assert False, "h2o.download_csvresult() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2odownload_csv)
else:
    h2odownload_csv()

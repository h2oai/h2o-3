from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2oexport_file():
    """
    Python API test: h2o.export_file(frame, path, force=False, parts=1).  Note taht force=True is only honored if
    parts=1.  Otherwise, an error will be thrown.
    """
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    try:
        results_dir = pyunit_utils.locate("results")    # find directory path to results folder
        final_path = os.path.join(results_dir, 'frameData')
        h2o.export_file(training_data, final_path, force=True, parts=1)       # save data
        assert os.path.isfile(final_path), "h2o.export_file() command is not working."
        final_dir_path = os.path.join(results_dir, 'multiFrame')
        h2o.export_file(training_data, final_dir_path, force=True, parts=-1)
        assert len(os.listdir(final_dir_path))>0, "h2o.export_file() command is not working."
    except Exception as e:
        if e.__class__.__name__=='ValueError' and 'File not found' in e.args[0]:
            print("Directory is not writable.  h2o.export_file() command is not tested.")
        else :
            assert e.__class__.__name__=='H2OResponseError' and \
                   'exportFrame: Cannot use path' in e.args[0]._props['dev_msg'], \
                "h2o.export_file() command is not working."
            print("Directory: {0} is not empty.  Delete or empy it before re-run.  h2o.export_file() "
                  "is not tested with multi-part export.".format(final_dir_path))


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oexport_file)
else:
    h2oexport_file()

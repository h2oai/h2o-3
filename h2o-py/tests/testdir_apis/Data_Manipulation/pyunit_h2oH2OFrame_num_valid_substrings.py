from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import inspect
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_num_valid_substrings():
    """
    Python API test: h2o.frame.H2OFrame.num_valid_substrings(i)
    """
    try:
        # generate files to write to
        results_dir = pyunit_utils.locate("results")    # real test when result directory is there
        full_path = os.path.join(results_dir, "test_num_valid_substrings.txt")
        with open(full_path, "w") as text_file:
            text_file.write("setosa")
            text_file.write('\n')
            text_file.write("virginica")
        iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader_NA_2.csv"))
        temp = iris[4].num_valid_substrings(path_to_words=full_path)
        assert_is_type(temp, H2OFrame)
        assert temp.sum().flatten()==100, "h2o.H2OFrame.num_valid_substrings command is not working."
    except Exception as e:
        if 'File not found' in e.args[0]:
            print("Directory is not writable.  h2o.H2OFrame.num_valid_substrings is tested for number of argument "
                  "and argument name only.")
            allargs = inspect.getargspec(h2o.H2OFrame.num_valid_substrings)
            assert len(allargs.args)==2 and allargs.args[1]=='path_to_words', \
                "h2o.H2OFrame.num_valid_substrings() contains only one argument, path_to_words!"
        else:
            assert False, "h2o.H2OFrame.num_valid_substrings() contains only one argument, path_to_words!"

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_num_valid_substrings())
else:
    h2o_H2OFrame_num_valid_substrings()

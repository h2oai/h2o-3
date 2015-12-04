from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o
from tests import pydemo_utils

def rf_balance_classes():
    # Connect to a pre-existing cluster


    # execute ipython notebook
    pydemo_utils.ipy_notebook_exec(pyunit_utils.locate("h2o-py/demos/rf_balance_classes.ipynb"),save_and_norun=None)


if __name__ == "__main__":
	pyunit_utils.standalone_test(rf_balance_classes)
else:
	rf_balance_classes()

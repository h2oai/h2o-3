from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o
from tests import pydemo_utils

def demo_deeplearning():



    # execute ipython notebook
    pydemo_utils.ipy_notebook_exec(pyunit_utils.locate("h2o-py/demos/deeplearning.ipynb"),save_and_norun=None)


if __name__ == "__main__":
	pyunit_utils.standalone_test(demo_deeplearning)
else:
	demo_deeplearning()

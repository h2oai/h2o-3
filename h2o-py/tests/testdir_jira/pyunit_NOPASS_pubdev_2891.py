import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import numpy as np
import pandas as pd

def pubdev_2891():

    #check the dimension of the frame
    python_obj = [["a", "b","c","asdfasdf"]]
    the_frame_1 = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame_1, rows=1, cols=4)

    python_obj_2 = [["a", "b","c"],["2", "b","c"],["t", "b","c"]]
    the_frame_2 = h2o.H2OFrame(python_obj_2)
    pyunit_utils.check_dims_values(python_obj, the_frame_2, rows=3, cols=3)

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_2891)
else:
    pubdev_2891()
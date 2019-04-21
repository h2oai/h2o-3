from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from random import randrange
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type
import numpy as np


def h2o_H2OFrame_ifelse():
    """
    Python API test: h2o.frame.H2OFrame.ifelse(yes, no)

    Copied from pyunit_ifelse.py
    """
    python_lists = np.random.uniform(-1,1, (5,5))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)

    newFrame = (h2oframe>0).ifelse(1, -1)
    assert_is_type(h2oframe, H2OFrame)  # check return type
    # randomly check one entry
    rowInd = randrange(0, h2oframe.nrow)
    colInd = randrange(0, h2oframe.ncol)
    assert newFrame[rowInd, colInd]==np.sign(h2oframe[rowInd, colInd]), "h2o.H2OFrame.ifelse() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_ifelse())
else:
    h2o_H2OFrame_ifelse()

from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np


def h2o_H2OFrame_relevel():
    """
    Python API test: h2o.frame.H2OFrame.relevel(y)
    """
    python_lists = np.random.randint(-5,5, (100, 2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newFrame = h2oframe.asfactor()
    allLevels = newFrame.levels()
    lastLevels = len(allLevels[0])-1
    newZeroLevel = allLevels[0][lastLevels]
    newFrame[0] = newFrame[0].relevel(newZeroLevel)    # set last level as 0
    newLevels = newFrame.levels()

    assert allLevels != newLevels, "h2o.H2OFrame.relevel() command is not working." # should not equal
    assert newLevels[0][0]==allLevels[0][lastLevels], "h2o.H2OFrame.relevel() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_relevel())
else:
    h2o_H2OFrame_relevel()

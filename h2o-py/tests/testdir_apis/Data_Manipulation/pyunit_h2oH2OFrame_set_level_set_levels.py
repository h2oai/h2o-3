from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
import random

def h2o_H2OFrame_set_level():
    """
    Python API test: h2o.frame.H2OFrame.set_level(level), h2o.frame.H2OFrame.set_levels(levels)
    """
    python_lists = np.random.randint(-5,5, (10000, 2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newFrame = h2oframe.asfactor()
    allLevels = newFrame.levels()

    newLevel0 = random.sample(allLevels[0], len(allLevels[0]))
    newLevel1 = random.sample(allLevels[1], len(allLevels[1]))

    newFrame[0] = newFrame[0].set_levels(levels=newLevel0)
    newFrame[1] = newFrame[1].set_levels(levels=newLevel1)

    assert newFrame[0].levels()[0]==newLevel0, "h2o.H2OFrame.set_levels() command is not working."
    assert newFrame[1].levels()[0]==newLevel1, "h2o.H2OFrame.set_levels() command is not working."

    allLevels = newFrame.levels()
    lastLevel = allLevels[0][len(allLevels[0])-1]
    firstLevel = allLevels[1][0]

    newFrame[0] = newFrame[0].set_level(level=lastLevel)
    newFrame[1] = newFrame[1].set_level(level=firstLevel)

    assert (newFrame[0]==lastLevel).all(), "h2o.H2OFrame.set_level() command is not working."
    assert (newFrame[1]==firstLevel).all, "h2o.H2OFrame.set_level() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_set_level())
else:
    h2o_H2OFrame_set_level()

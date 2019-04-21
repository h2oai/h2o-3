from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrameAsin():
    """
    Python API tests for: h2o.frame.H2OFrame.abs(), h2o.frame.H2OFrame.acos(), h2o.frame.H2OFrame.acosh(),
    h2o.frame.H2OFrame.asin(), h2o.frame.H2OFrame.asinh(), h2o.frame.H2OFrame.atan(), h2o.frame.H2OFrame.atanh(),
    h2o.frame.H2OFrame.ceil(), h2o.frame.H2OFrame.cos(), h2o.frame.H2OFrame.cosh(), h2o.frame.H2OFrame.cospi(),
    h2o.frame.H2OFrame.digamma(), h2o.frame.H2OFrame.exp(), h2o.frame.H2OFrame.expm1(), h2o.frame.H2OFrame.floor(),
    h2o.frame.H2OFrame.gamma(), h2o.frame.H2OFrame.lgamma(), h2o.frame.H2OFrame.log(), h2o.frame.H2OFrame.log(),
    h2o.frame.H2OFrame.log1p(), h2o.frame.H2OFrame.log2(), h2o.frame.H2OFrame.round(), h2o.frame.H2OFrame.sign(),
    h2o.frame.H2OFrame.signif(), h2o.frame.H2OFrame.sin(), h2o.frame.H2OFrame.sinh(), h2o.frame.H2OFrame.sinpi(),
    h2o.frame.H2OFrame.sqrt(), h2o.frame.H2OFrame.tan(), h2o.frame.H2OFrame.tanh(), h2o.frame.H2OFrame.tanpi()
    """
    h2oframe = genData(-1,1,3,4)    # verify H2OFrame.abs()
    compareEvalOps(h2oframe, h2oframe.abs(), H2OFrame, "abs")   # verify H2OFrame.abs()
    compareEvalOps(h2oframe, h2oframe.acos(), H2OFrame, "acos") # verify H2OFrame.acos()
    compareEvalOps(h2oframe, h2oframe.asin(), H2OFrame, "asin") # verify H2OFrame.asin()
    compareEvalOps(h2oframe, h2oframe.asinh(), H2OFrame, "asinh") # verify H2OFrame.asinh()
    compareEvalOps(h2oframe, h2oframe.atan(), H2OFrame, "atan") # verify H2OFrame.atan()
    compareEvalOps(h2oframe, h2oframe.cos(), H2OFrame, "cos") # verify H2OFrame.cos()
    compareEvalOps(h2oframe, h2oframe.cosh(), H2OFrame, "cosh") # verify H2OFrame.cosh()
    compareEvalOps(h2oframe, h2oframe.cospi(), H2OFrame, "cospi") # verify H2OFrame.cospi()
    compareEvalOps(h2oframe, h2oframe.exp(), H2OFrame, "exp") # verify H2OFrame.exp()
    compareEvalOps(h2oframe, h2oframe.expm1(), H2OFrame, "expm1") # verify H2OFrame.expm1()
    compareEvalOps(h2oframe, h2oframe.sin(), H2OFrame, "sin") # verify H2OFrame.sin()
    compareEvalOps(h2oframe, h2oframe.sinh(), H2OFrame, "sinh") # verify H2OFrame.sinh()
    compareEvalOps(h2oframe, h2oframe.sinpi(), H2OFrame, "sinpi") # verify H2OFrame.sinpi()
    compareEvalOps(h2oframe, h2oframe.tan(), H2OFrame, "tan") # verify H2OFrame.tan()
    compareEvalOps(h2oframe, h2oframe.tanh(), H2OFrame, "tanh") # verify H2OFrame.tanh()
    compareEvalOps(h2oframe, h2oframe.tanpi(), H2OFrame, "tanpi") # verify H2OFrame.tanpi()
    h2o.remove(h2oframe)
    h2oframe = genData(1,3,3,4)
    compareEvalOps(h2oframe, h2oframe.acosh(), H2OFrame, "acosh")   # verify H2OFrame.acos()
    compareEvalOps(h2oframe, h2oframe.digamma(), H2OFrame, "digamma")   # verify H2OFrame.digamma()
    h2o.remove(h2oframe)
    h2oframe = genData(-0.9,0.9,3,4)
    compareEvalOps(h2oframe, h2oframe.atanh(), H2OFrame, "atanh")   # verify H2OFrame.atanh()
    h2o.remove(h2oframe)
    h2oframe = genData(-10,10,3,4)
    compareEvalOps(h2oframe, h2oframe.ceil(), H2OFrame, "ceil")   # verify H2OFrame.ceil()
    compareEvalOps(h2oframe, h2oframe.floor(), H2OFrame, "floor")   # verify H2OFrame.floor()
    compareEvalOps(h2oframe, h2oframe.round(digits=0), H2OFrame, "round")   # verify H2OFrame.round()
    compareEvalOps(h2oframe, h2oframe.sign(), H2OFrame, "sign")   # verify H2OFrame.sign()
    compareEvalOps(h2oframe, h2oframe.signif(digits=7), H2OFrame, "signif")   # verify H2OFrame.signif()
    h2o.remove(h2oframe)
    h2oframe = genData(1,10,5,5)
    compareEvalOps(h2oframe, h2oframe.log(), H2OFrame, "log") # verify H2OFrame.log()
    compareEvalOps(h2oframe, h2oframe.log10(), H2OFrame, "log10") # verify H2OFrame.log10()
    compareEvalOps(h2oframe, h2oframe.log1p(), H2OFrame, "log1p") # verify H2OFrame.log1p()
    compareEvalOps(h2oframe, h2oframe.log2(), H2OFrame, "log2") # verify H2OFrame.log2()
    compareEvalOps(h2oframe, h2oframe.lgamma(), H2OFrame, "lgamma") # verify H2OFrame.lgamma()
    compareEvalOps(h2oframe, h2oframe.sqrt(), H2OFrame, "sqrt") # verify H2OFrame.sqrt()
    compareEvalOps(h2oframe, h2oframe.trigamma(), H2OFrame, "trigamma") # verify H2OFrame.trigamma()
    compareEvalOps(h2oframe, h2oframe.gamma(), H2OFrame, "gamma")   # verify H2OFrame.gamma()



def genData(lowBound, upBound, rowN, colN):
    python_lists = np.random.uniform(lowBound, upBound, (rowN, colN))
    return h2o.H2OFrame(python_obj=python_lists)

def compareEvalOps(origFrame, newFrame, objType, operString):
    assert_is_type(newFrame, objType)
    pyunit_utils.assert_correct_frame_operation(origFrame, newFrame, operString)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrameAsin())
else:
    h2o_H2OFrameAsin()

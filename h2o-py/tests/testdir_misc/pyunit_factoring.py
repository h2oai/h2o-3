from __future__ import print_function
from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o import H2OFrame
from h2o.exceptions import H2OTypeError, H2OValueError

def compare_frames(expected, actual):
    assert actual.shape == expected.shape
    assert actual.columns == expected.columns, "Columns differ: %r vs %r" % (actual.columns, colnames)
    for i in range(len(actual.columns)):
        colname = actual.columns[i]
        t1 = expected.types[colname]
        t2 = actual.types[colname]
        assert t1 == t2, ("Bad types %s: expected %s, got %s" %(colname, t1, t2))
        col1 = expected[colname]
        s1 = str(h2o.as_list(col1))
        col2 = actual[colname] 
        s2 = str(h2o.as_list(col2))
        assert s1 == s2, ("bad values: expected[%d] = %r, actual[%d] = %r"
                          % (i, s1, i, s2))

def test1():
    badFrame = H2OFrame({"one": [4, 6, 1], "two": ["a", "b", "cde"], "three": [0, 5.2, 14]})
    badClone = H2OFrame({"one": [4, 6, 1], "two": ["a", "b", "cde"], "three": [0, 5.2, 14]})
    compare_frames(badFrame, badClone)
    
    try:
      badFrame.asfactor()
      assert False, "The frame contaied a real number, an error should be thrown"
    except H2OValueError: # as designed
      pass
        
    compare_frames(badFrame, badClone)

    originalAfterOp = H2OFrame.get_frame(badFrame.frame_id)
    compare_frames(badFrame, originalAfterOp)

    goodFrame = H2OFrame({"one": [4, 6, 1], "two": ["a", "b", "cde"]})
    goodClone = H2OFrame({"one": [4, 6, 1], "two": ["a", "b", "cde"]})
    compare_frames(goodFrame, goodClone)

    factoredFrame = goodFrame.asfactor()

    originalAfterOp = H2OFrame.get_frame(goodFrame.frame_id)
    compare_frames(goodFrame, originalAfterOp)

    expectedFactoredFrame = H2OFrame({"one": [4, 6, 1], "two": ["a", "b", "cde"]}, column_types={"one":"categorical", "two": "enum"})

    compare_frames(expectedFactoredFrame, factoredFrame)

    refactoredFrame = expectedFactoredFrame.asfactor()
    factoredAfterOp = H2OFrame.get_frame(refactoredFrame.frame_id)
    compare_frames(expectedFactoredFrame, factoredAfterOp)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test1)
else:
    test1()


from __future__ import print_function
import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from pandas.testing import assert_frame_equal 
from h2o.frame import H2OFrame


def test_relevel_by_freq_weights():
    fr = H2OFrame([
        [1, 'a'],
        [2, 'b'],
        [3, 'c']
    ])
    fr["C2"] = fr["C2"].asfactor()
    assert fr.levels() == [[], ['a', 'b', 'c']]

    fr_releveled = fr.relevel_by_frequency(weights_column="C1")

    assert_frame_equal(fr_releveled.as_data_frame(), fr.as_data_frame())
    assert fr_releveled.levels() == [[], ['c', 'b', 'a']]


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_relevel_by_freq_weights)
else:
    test_relevel_by_freq_weights()

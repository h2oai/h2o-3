import sys
import pandas as pd
from pandas.util.testing import assert_frame_equal
sys.path.insert(1,"../../")
from h2o.frame import H2OFrame
from tests import pyunit_utils

def pubdev_6360():
    source = [
        [1, 'Peter', 'blah'],
        [2, 'Carl', ''],
        [3, 'Maria', 'whatever'],
        [4, 'Cindy', None]
    ]
    expected = [
        [1, 'Peter', 1],
        [2, 'Carl', 0],
        [3, 'Maria', 1],
        [4, 'Cindy', 0]
    ]
    columns = ['ID', 'Name', 'testcolumn']
    sourcePandasFrame = pd.DataFrame(source, columns=columns)
    expectedPandasFrame = pd.DataFrame(expected, columns=columns)

    h2oFrame = H2OFrame(sourcePandasFrame)
    h2oFrame[h2oFrame['testcolumn'] != '', 'testcolumn'] = '1'
    try:
        h2oFrame[h2oFrame['testcolumn'] == '', 'testcolumn'] = '0'
        assert False, "H2O Frame operation should fail on an enum column"
    except Exception as e:
        assert 'Cannot assign value 1 into a vector of type Enum.' == e.args[
            0].msg, "H2O Frame operation failed on an unexpected error"

    h2oFrame = H2OFrame(sourcePandasFrame)
    h2oFrame['testcolumn'] = h2oFrame['testcolumn'].ascharacter()
    h2oFrame[h2oFrame['testcolumn'] != '', 'testcolumn'] = '1'
    h2oFrame[h2oFrame['testcolumn'] == '', 'testcolumn'] = '0'
    h2oFrame['testcolumn'] = h2oFrame['testcolumn'].asfactor()

    assert_frame_equal(h2oFrame.as_data_frame(use_pandas=True), expectedPandasFrame)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6360)
else:
    pubdev_6360()

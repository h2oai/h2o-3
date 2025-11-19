import sys
sys.path.insert(1,"../../")
import h2o
import pandas as pd
from tests import pyunit_utils


def test_relevel_issue():
    df = pd.DataFrame({
        "state": ["AL", "GA", "NY", "PA"],
        "bin_levels": ["$10'0", "$2\"00", "$500", "other"]
    })
    hdf = h2o.H2OFrame(df, column_types=["enum", "enum"])
    pyunit_utils.assert_equals(hdf["bin_levels"].relevel("$500").levels()[0][0], "$500")
    pyunit_utils.assert_equals(hdf["bin_levels"].relevel("$10'0").levels()[0][0], "$10'0")
    pyunit_utils.assert_equals(hdf["bin_levels"].relevel("$2\"00").levels()[0][0], "$2\"00")


pyunit_utils.standalone_test(test_relevel_issue)

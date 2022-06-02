from __future__ import print_function

import sys
import os

sys.path.insert(1, os.path.join("..", "..", ".."))
import matplotlib
matplotlib.use("Agg")  # remove warning from python2 (missing TKinter)
import pandas as pd
from tests import pyunit_utils
from h2o.explanation._explain import *



def repro_invent_values():
    df = pd.DataFrame({"a": ["\"Test\"" for _ in range(10000000)]})
    print( pd.__version__ )
    print(df.value_counts())
    hf = h2o.H2OFrame(df, column_types=["enum"])

    val_counts = hf["a"].as_data_frame(True).value_counts()
    print(val_counts)

    # should be (('"Test"',), 10000000),
    # not (('"Test"',), 9999982) (('""Test""',), 18)
    assert len(val_counts) == 1
    assert val_counts['"Test"'] == 10000000





pyunit_utils.run_tests([
    repro_invent_values
])


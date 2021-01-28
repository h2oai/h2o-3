from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import string
import random
import pandas as pd
import numpy as np
import os
'''
The below function will test if the exporting of a file in H2O is NOT asynchronous. Meaning the h2o.export_file()
waits for the entire process to finish before exiting.
'''


def test_export_not_asynchronous():
    orig_path = pyunit_utils.locate("bigdata/laptop/citibike-nyc/2013-07.csv")
    pros_hex = h2o.upload_file(orig_path)

    def id_generator(size=6, chars=string.ascii_uppercase + string.digits):
        return ''.join(random.choice(chars) for _ in range(size))

    export_path = pyunit_utils.locate("results") + "/" + "test_export_not_async_" + id_generator() + ".csv"

    h2o.export_file(pros_hex, export_path)
    export_size1 = os.stat(export_path).st_size

    pandas_df_exported = pd.read_csv(export_path)
    pandas_df_exported = pandas_df_exported.drop(columns=['starttime', 'stoptime']) # H2O exports these as timestamps
    pandas_df_orig = pd.read_csv(orig_path, dtype={'birth year': np.float64}, na_values="\N")
    pandas_df_orig = pandas_df_orig.drop(columns=['starttime', 'stoptime'])

    pd.testing.assert_frame_equal(pandas_df_orig, pandas_df_exported)
    export_size2 = os.stat(export_path).st_size

    # Show that file size didn't change in the mean time => export is not asynchronous
    assert export_size1 == export_size2


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_export_not_asynchronous)
else:
    test_export_not_asynchronous()




#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

import pandas
import h2o
from tests import pyunit_utils
from pandas.util.testing import assert_frame_equal

TEST_DATASET = pyunit_utils.locate('smalldata/logreg/prostate_missing.csv')


def test_4723():
    pandas_frame = pandas.read_csv(TEST_DATASET)
    frame = h2o.import_file(TEST_DATASET)

    # Ensure that the as_data_frame method does not modify the frame
    assert_frame_equal(pandas_frame, frame.as_data_frame())

    # Now insert some missing values
    expected_rows_count = frame['RACE'].shape[0]

    # Check that the shape of the data frames is not modified
    pandas_default_rows_count = frame['RACE'].as_data_frame(use_pandas=True).shape[0]
    assert pandas_default_rows_count == expected_rows_count, "Result's rows count when using pandas with default na_value equal to expected_rows_count. Expected: %s, actual: %s" % (
        expected_rows_count, pandas_default_rows_count)
    no_pandas_default_rows_count = len(frame['RACE'].as_data_frame(use_pandas=False, header=False))
    assert no_pandas_default_rows_count == expected_rows_count, "Result's rows count when NOT using pandas must be equal to expected_rows_count. Expected: %s, actual: %s" % (
        expected_rows_count, no_pandas_default_rows_count)


def test_npe_string_vec():
    f = h2o.create_frame(string_fraction = 1)
    f['C1'].insert_missing_values(1)
    print(f['C1'][0,0])

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_4723)
    pyunit_utils.standalone_test(test_npe_string_vec)
else:
    test_4723()
    test_npe_string_vec()

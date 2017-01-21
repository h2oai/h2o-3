#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
import datetime

import h2o
import pandas as pd
import numpy as np
from tests import pyunit_utils


def test_date_comparisons():
    dfpd = pd.DataFrame({"date": pd.date_range("1/1/2011", periods=10), "value": range(10)})
    dfh2o = h2o.H2OFrame(dfpd)
    z1 = dfpd["date"].min()
    z2 = dfpd["date"].values[2]
    assert isinstance(z1, pd.Timestamp)
    assert isinstance(z2, np.datetime64)
    # Check that the conversion used in H2OFrame.moment() work as expected
    assert z1.to_pydatetime() == datetime.datetime(2011, 1, 1, 0, 0)
    assert z2.astype("M8[ms]").astype("O") == datetime.datetime(2011, 1, 3, 0, 0)

    test1pd = dfpd["date"] > z1
    test1h2o = dfh2o["date"] > z1
    assert test1pd.sum() == 9, "Incorrect Pandas comparison result:\n%r" % test1pd
    assert test1h2o.sum().flatten() == 9, "Incorrect H2O comparison result:\n%r" % test1h2o

    test2pd = dfpd["date"] > z2
    test2h2o = dfh2o["date"] > z2
    assert test2pd.sum() == 7, "Incorrect Pandas comparison result:\n%r" % test2pd
    assert test2h2o.sum().flatten() == 7, "Incorrect H2O comparison result:\n%r" % test2h2o



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_date_comparisons)
else:
    test_date_comparisons()

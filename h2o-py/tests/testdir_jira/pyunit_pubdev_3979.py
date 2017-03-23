#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
import datetime

import h2o
from tests import pyunit_utils


def test_3979():
    df = h2o.H2OFrame(
        [["1990-06-07 07:00:00", "1990-06-07 07:00:00"],
         ["1990-07-07 07:00:00", "1990-07-07 07:00:00"],
         ["1990-08-07 07:00:00", "1990-08-07 07:00:00"],
         ["1990-09-07 07:00:00", "1990-08-07 07:00:00"],
         ["1990-10-01 07:00:00", "1990-08-07 07:00:00"],
         ["1990-10-02 07:00:00", "1990-08-07 07:00:00"],
         ["1990-10-25 07:00:00", "1990-08-07 07:00:00"],
         ["1990-11-13 08:00:00", "1990-08-07 07:00:00"],
         ["1991-06-07 07:00:00", "1990-08-07 07:00:00"],
         ["1991-07-07 07:00:00", "1990-08-07 07:00:00"]],
        column_names=["Datecolumn", "Datecolumn2"],
    )
    assert df.type("Datecolumn") == "time"
    assert df.type("Datecolumn2") == "time"

    dd = h2o.H2OFrame([["1990-06-07 07:00:00"]])
    assert dd.type("C1") == "time"

    assert dd[0, 0] == df[0, 0]
    chk1 = dd['C1'] == datetime.datetime(1990, 6, 7, 7, 0, 0)
    assert isinstance(chk1, h2o.H2OFrame) and chk1.shape == (1, 1)
    assert int(chk1) == 1

    chk2 = df["Datecolumn"] == datetime.datetime(1990, 6, 7, 7, 0, 0)
    assert isinstance(chk2, h2o.H2OFrame) and chk2.shape == (10, 1)
    assert chk2[0, 0] == 1
    assert int(chk2.sum()) == 1


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_3979)
else:
    test_3979()

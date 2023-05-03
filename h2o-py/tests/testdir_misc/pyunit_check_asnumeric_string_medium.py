#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pyunit_asnumeric_string():

    small_test = [pyunit_utils.locate("bigdata/laptop/lending-club/LoanStats3a.csv")]

    print("Import and Parse data")
    types = {"int_rate": "string", "revol_util": "string", "emp_length": "string"}

    data = h2o.import_file(path=small_test, col_types=types)
    assert data['int_rate'].gsub('%', '').trim().asnumeric().isna().sum() == 3


if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_asnumeric_string)
else:
    pyunit_asnumeric_string()

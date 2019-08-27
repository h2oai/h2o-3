#!/usr/bin/env python
# -*- coding: utf-8 -*-
from h2o import H2OFrame
from tests import pyunit_utils
import sys

def pubdev_6534():
    df_data = [["D", "E", "NA", "NA"], ["1", "A", "NA", "NA"]]
    df = H2OFrame.from_python(df_data, column_types=['factor']*4, na_strings=["NA"])

    assert df.type("C1") == "enum"
    assert df.type("C2") == "enum"
    assert df.type("C3") == "int"
    assert df.type("C4") == "int"

    # convert empty col to enum
    df['C3'] = df['C3'].asfactor()
    # convert empty cols to char
    df['C4'] = df['C4'].ascharacter()

    print(df)
    assert df.type("C3") == "enum"
    assert df.type("C4") == "string"


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6534)
else:
    pubdev_6534()

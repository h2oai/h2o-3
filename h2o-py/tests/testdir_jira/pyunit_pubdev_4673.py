#!/usr/bin/env python
from __future__ import absolute_import, division, print_function, unicode_literals
import h2o
from tests import pyunit_utils

def test_4673():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    # If this didn't throw an exception in Python 3.6, then we're good.
    print(fr.mean())
    fr.apply(lambda x: x["class"] + x[0], axis=1).show()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_4673)
else:
    test_4673()

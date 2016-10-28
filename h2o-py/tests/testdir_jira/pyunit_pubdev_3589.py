#!/usr/bin/env python
from __future__ import absolute_import, division, print_function, unicode_literals
import h2o
from tests import pyunit_utils


def test_3589():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/jira/test_pubdev3589.txt"))
    # If this didn't throw an exception, then we're good.
    print(fr)
    fr.show()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_3589)
else:
    test_3589()

#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from collections import defaultdict
import random
import sys
sys.path.insert(1, "../../")
import h2o
from h2o.exceptions import H2OValueError
from h2o.utils.compatibility import viewvalues
from tests import pyunit_utils

def create_frame_test():
    """Test `h2o.create_frame()`."""
    for _ in range(10):
        r = random.randint(1, 1000)
        c = random.randint(1, 1000)

        frame = h2o.create_frame(rows=r, cols=c)
        assert frame.nrow == r and frame.ncol == c, \
            "Expected {0} rows and {1} cols, but got {2} rows and {3} cols.".format(r, c, frame.nrow, frame.ncol)

    def assert_coltypes(frame, freal, fenum, fint, fbin, ftime, fstring):
        # The server does not report columns as binary -- instead they are integer.
        fint += fbin
        fbin = 0
        type_counts = defaultdict(int)
        for ft in viewvalues(frame.types):
            type_counts[ft] += 1
        print("Created table with column counts: {%s}" % ", ".join("%s: %d" % t for t in type_counts.items()))
        for ct in ["real", "enum", "int", "time", "string"]:
            assert abs(type_counts[ct] - locals()["f" + ct] * frame.ncol) < 1, \
                "Wrong column count of type %s: %d" % (ct, type_counts[ct])

    f1 = h2o.create_frame(rows=10, cols=1000, real_fraction=1)
    assert_coltypes(f1, 1, 0, 0, 0, 0, 0)

    f2 = h2o.create_frame(rows=10, cols=1000, binary_fraction=0.5, time_fraction=0.5)
    assert_coltypes(f2, 0, 0, 0, 0.5, 0.5, 0)

    f3 = h2o.create_frame(rows=10, cols=1000, string_fraction=0.2, time_fraction=0.8)
    assert_coltypes(f3, 0, 0, 0, 0, 0.8, 0.2)

    f4 = h2o.create_frame(rows=10, cols=1000, real_fraction=0.9)
    assert_coltypes(f4, 0.9, 0.04, 0.04, 0.02, 0, 0)

    f5 = h2o.create_frame(rows=2, cols=1000, integer_fraction=0.75000000000001, string_fraction=0.25000000000001)
    assert_coltypes(f5, 0, 0, 0.75, 0, 0, 0.25)

    try:
        h2o.create_frame(rows=10, cols=1000, real_fraction=0.1, categorical_fraction=0.1, integer_fraction=0.1,
                         binary_fraction=0.1, time_fraction=0.1, string_fraction=0.1)
        assert False, "The data frame should not have been created!"
    except H2OValueError:
        pass

    try:
        h2o.create_frame(rows=10, cols=1000, real_fraction=0.5, categorical_fraction=0.5, integer_fraction=0.1)
        assert False, "The data frame should not have been created!"
    except H2OValueError:
        pass


if __name__ == "__main__":
    pyunit_utils.standalone_test(create_frame_test)
else:
    create_frame_test()

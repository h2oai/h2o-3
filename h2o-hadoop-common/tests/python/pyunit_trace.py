#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
import os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.exceptions import H2OServerError
from tests import pyunit_utils


def trace_request():
    err = None
    try:
        h2o.api("TRACE /")
    except H2OServerError as e:
        err = e

    assert err is not None
    assert str(err.message).startswith("HTTP 405 Method Not Allowed")


if __name__ == "__main__":
    pyunit_utils.standalone_test(trace_request)
else:
    trace_request()

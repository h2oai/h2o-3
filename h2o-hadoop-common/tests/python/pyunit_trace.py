#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o
from h2o.exceptions import H2OServerError


def trace_request():
    err = None
    try:
        h2o.api("TRACE /3/Cloud")
    except H2OServerError as e:
        err = e

    msg = str(err.args[0])

    assert err is not None
    print("<Error message>")
    print(msg)
    print("</Error Message>")

    # exact message depends on Jetty Version and security settings
    assert msg.startswith("HTTP 500") or msg.startswith("HTTP 405 Method Not Allowed")


if __name__ == "__main__":
    pyunit_utils.standalone_test(trace_request)
else:
    trace_request()

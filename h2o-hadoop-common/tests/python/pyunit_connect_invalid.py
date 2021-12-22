#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
import h2o
from h2o.exceptions import H2OServerError
from tests import pyunit_utils
import copy


def connect_invalid():
    current = h2o.connection()

    print(current._auth)

    if current._auth is None:
        print("Skipping test running in non-authenticated environment")
        return

    invalid = copy.copy(current)

    # Not invalid yet - first do sanity check that original connection can be used to make a request
    invalid.request("GET /3/About")

    auth_user = invalid._auth[0] if isinstance(invalid._auth, tuple) else "jenkins" # fallback for CI testing
    invalid._auth = (auth_user, "invalid-password")

    # Test with invalid basic auth
    err = None
    try:
        invalid._auth = ("invalid-user", "invalid-password")
        invalid.request("GET /3/About")
    except H2OServerError as e:
        err = e

    assert err is not None

    msg = str(err.args[0])
    print("<Error message>")
    print(msg)
    print("</Error Message>")

    assert msg.startswith("HTTP 401")  # Unauthorized
    
    # Test without any auth
    err = None
    try:
        invalid._auth = None
        invalid.request("GET /3/About")
    except H2OServerError as e:
        err = e

    assert err is not None

    msg = str(err.args[0])
    print("<Error message>")
    print(msg)
    print("</Error Message>")

    assert msg.startswith("HTTP 401") # Unauthorized


if __name__ == "__main__":
    pyunit_utils.standalone_test(connect_invalid)
else:
    connect_invalid()

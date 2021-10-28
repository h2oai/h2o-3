#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
import h2o
from tests import pyunit_utils
import requests


def redirect_relative():
    if os.environ.get('JOB_NAME') and os.environ.get('JOB_NAME').startswith("h2o-3-kerberos-smoke-pipeline/"): 
        h2o.log_and_echo("Skipping test 'redirect_relative' on Kerberos pipeline (it is not configured with form_auth)")
        return

    conn = h2o.connection()

    # get default requests arguments
    req_args = conn._request_args()
    headers = req_args["headers"]
    headers["User-Agent"] = "Mozilla/pyunit"

    # invalidate authentication
    req_args["auth"] = None
    req_args["headers"] = headers 

    response_flow = requests.request("GET", conn._base_url + "/flow/index.html", allow_redirects=False, **req_args)
    print(response_flow)
    assert response_flow.status_code in [302, 303]
    assert response_flow.headers["location"].startswith("/login")


if __name__ == "__main__":
    pyunit_utils.standalone_test(redirect_relative)
else:
    redirect_relative()

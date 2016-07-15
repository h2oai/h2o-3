from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o, inspect
from tests import pyunit_utils


def check_strict():
    # We may be either connected to an existing h2o server, or not. If we are, then discover the connection settings
    # so that we don't have to start a new server (starting a new server may be not possible if h2o.jar is located in
    # some unknown to us place in the system).
    hc = h2o.connection()
    url = None
    if hc is not None:
        url = hc.base_url

    out = {"version_check_called": False}
    def tracefunc(frame, event, arg):
        if frame.f_code.co_name == "version_check":
            out["version_check_called"] = True
        return None
    sys.settrace(tracefunc)
    try:
        h2o.init(url=url)
    except h2o.H2OConnectionError:
        pass

    assert out["version_check_called"], \
        "Strict version checking got turned off! TURN IT BACK ON NOW YOU JERK!"


if __name__ == "__main__":
    pyunit_utils.standalone_test(check_strict)
else:
    check_strict()

from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o, inspect
from tests import pyunit_utils


def check_strict():
    # inspection doesn't work with decorated functions.
    out = {"version_check_called": False}
    def tracefunc(frame, event, arg):
        if frame.f_code.co_name == "version_check":
            out["version_check_called"] = True
        return None
    sys.settrace(tracefunc)
    try:
        h2o.init()
    except h2o.H2OConnectionError:
        pass

    assert out["version_check_called"], \
        "Strict version checking got turned off! TURN IT BACK ON NOW YOU JERK!"


if __name__ == "__main__":
    pyunit_utils.standalone_test(check_strict)
else:
    check_strict()

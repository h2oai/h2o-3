from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2odemo():
    """
    Python API test: h2o.demo(funcname, interactive=True, echo=True, test=False)[source]

    Copied from pyunit_glm_demo.py
    """

    try:
        h2o.demo(funcname="glm", interactive=False, echo=True, test=True)
    except Exception as e:
        assert False, "h2o.demo() command not is working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2odemo)
else:
    h2odemo()

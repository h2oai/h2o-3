import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import inspect

def h2oremove_all():
    """
    Python API test: h2o.remove_all()

    Cannot test this one on Jenkins.  It will crash other tests.  So, Pasha found a way around this
    by just checking the argument list which should be empty.
    """
    # call with no arguments
    signature = inspect.getfullargspec(h2o.remove_all)
    assert len(signature.args) == 1, "h2o.remove_all() should have one optional argument!"
    assert signature.args[0] == "retained"

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oremove_all)
else:
    h2oremove_all()

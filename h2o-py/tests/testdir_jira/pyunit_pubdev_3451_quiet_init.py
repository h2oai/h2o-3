import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import io
import sys

def pubdev_3451():

    # We call h2o.init, and see if there is any output.
    # In a normal test, h2o.init will have been called before, but the output 
    # the first and the second time this function gets called is comparable
    
    # Setup a trap
    stdout_backup = sys.stdout
    text_trap = io.StringIO()
    sys.stdout = text_trap
    
    # Run function, expecting no output
    #h2o.init(quiet = True)
    init(quiet = True, strict_version_check = False)
    
    # Restore stdout
    sys.stdout = stdout_backup

    assert text_trap.getvalue() == ""


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_3451)
else:
    pubdev_3451()

import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import io
import sys

def pubdev_3451():

    # We don't reinitialize h2o, but instead call h2o.init again, and see if there is any output.
    
	# Setup a trap
	stdout_backup = sys.stdout
	text_trap = io.StringIO()
	sys.stdout = text_trap
    
	# Run function, expecting no output
	h2o.init(quiet = True)
	
	# Restore stdout
	sys.stdout = stdout_backup

    assert text_trap.getvalue() == ""


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_3451)
else:
    pubdev_3451()

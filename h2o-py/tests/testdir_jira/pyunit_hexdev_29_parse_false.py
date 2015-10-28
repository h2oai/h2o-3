import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
################################################################################
##
## Verifying that Python can support importing without parsing.
##
################################################################################

def parse_false():

    fraw = h2o.import_file(pyunit_utils.locate("smalldata/jira/hexdev_29.csv"), parse=False)
    assert isinstance(fraw, list)

    fhex = h2o.parse_raw(h2o.parse_setup(fraw))
    fhex.summary()
    assert fhex.__class__.__name__ == "H2OFrame"



if __name__ == "__main__":
    pyunit_utils.standalone_test(parse_false)
else:
    parse_false()

import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def space_headers():
    
    

    f = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/citibike_head.csv"))

    print f.names

    f["starttime"].show()

    h2o_median = f["start station id"].median()

    assert h2o_median == 444, "Expected median for \"start station id\" to be 444, but got {0}".format(h2o_median)



if __name__ == "__main__":
    pyunit_utils.standalone_test(space_headers)
else:
    space_headers()
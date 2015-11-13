import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def rep_len_check():
    # Connect to a pre-existing cluster
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    # data is single column (vec)
    vec = iris[0].rep_len(length_out=301)
    assert vec.nrow == 301, "Expected an H2OVec with 301 rows, but got {0} rows".format(vec.nrow)
    for r in range(len(vec)): assert (vec[r,:] == vec[r % 150,:]).all(), "Expected {0}, but got {1}".format(vec[r % 150,:], vec[r,:])

    # data is frame
    fr = iris.rep_len(length_out=7)
    assert fr.nrow == 150 and fr.ncol == 7, "Expected an H2OFrame with 150 rows and 7 columns, but got {0} rows and {1} cols".format(fr.nrow, fr.ncol)



if __name__ == "__main__":
    pyunit_utils.standalone_test(rep_len_check)
else:
    rep_len_check()

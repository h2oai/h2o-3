import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def strsplit_check():
    # Connect to a pre-existing cluster
    

    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    # single column (frame)
    result = frame["C5"].strsplit("-")
    assert result.nrow == 150 and result.ncol == 2
    assert result[0,0] == "Iris" and result[0,1] == "setosa", "Expected 'Iris' and 'setosa', but got {0} and " \
                                                              "{1}".format(result[0,0], result[0,1])

    # single column (vec)
    vec = frame["C5"]
    result = vec.strsplit("s")
    assert result.nrow == 150 and result.ncol == 4
    assert result[0,0] == "Iri" and result[0,1] == "-" and result[0,2] == "eto" and \
           result[0,3] == "a", "Expected 'Iri', '-', 'eto', and 'a', but got {0}, {1}, {2}, and " \
                               "{3}".format(result[0,0], result[0,1], result[0,2], result[0,3])



if __name__ == "__main__":
    pyunit_utils.standalone_test(strsplit_check)
else:
    strsplit_check()

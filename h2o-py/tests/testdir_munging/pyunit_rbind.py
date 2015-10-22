import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def rbind_check():
    # Connect to a pre-existing cluster
    

    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars.csv"))
    row_orig = frame.nrow

    frame_2 = frame.rbind(frame)
    row_2 = frame_2.nrow
    assert 2*row_orig == row_2, "Expected 2*{0} rows, but got {1}".format(2*row_orig, row_2)

    frame_3 = frame_2.rbind(frame_2)
    row_3 = frame_3.nrow
    assert 4*row_orig == row_3, "Expected 4*{0} rows, but got {1}".format(4*row_orig, row_3)

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    try:
        frame_fail = frame.rbind(iris)
        frame_fail.show()
        assert False, "Expected the rbind of cars and iris to fail, but it didn't"
    except EnvironmentError:
        pass



if __name__ == "__main__":
    pyunit_utils.standalone_test(rbind_check)
else:
    rbind_check()

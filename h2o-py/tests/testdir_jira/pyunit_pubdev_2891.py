import h2o
from tests import pyunit_utils

def pubdev_2891():

    #check header is respected
    names = ["a", "b", "c", "d"]
    python_obj = [names, [1, 1, 1, 1]]
    the_frame_1 = h2o.H2OFrame.from_python(python_obj, header=1)
    print(the_frame_1)
    assert the_frame_1.col_names == names

    #check the dimension of the frame
    python_obj = ["a", "b", "c", "asdfasdf"]
    the_frame_1 = h2o.H2OFrame(python_obj)
    print(the_frame_1)
    pyunit_utils.check_dims_values(python_obj, the_frame_1, rows=4, cols=1)

    the_frame_1 = h2o.H2OFrame.from_python(python_obj, header=1)
    print(the_frame_1)
    assert the_frame_1.names == ["a"]
    assert the_frame_1.nrows == 3


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_2891)
else:
    pubdev_2891()

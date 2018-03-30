
import h2o

from tests import pyunit_utils


def pubdev_5441():

    frame = h2o.import_file(pyunit_utils.locate("smalldata/jira/pubdev_5441.csv"))
    assert frame is not None
    assert frame.shape == (4,4)

    frame_head = h2o.import_file(path = pyunit_utils.locate("smalldata/jira/pubdev_5441.csv"), header = 1)
    # 3 rows, 4 columns, first column is header
    assert frame_head.shape == (3,4)

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5441)
else:
    pubdev_5441()

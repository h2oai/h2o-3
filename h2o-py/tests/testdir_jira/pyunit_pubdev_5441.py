
import h2o
from tests import pyunit_utils


def pubdev_5441():

    frame = h2o.import_file(pyunit_utils.locate("smalldata/jira/pubdev_5441.csv"))
    assert frame is not None
    assert frame.shape == (4,4)

    frame_head = h2o.import_file(path = pyunit_utils.locate("smalldata/jira/pubdev_5441.csv"), header = 1)
    # 3 rows, 4 columns, first column is header
    assert frame_head.shape == (3,4)

    newline_before_start = """
Beautiful Luxury Building on the Upper East Side. Large studio on high floor with wide open views so you can see miles and miles. Large living area, hardwood floors, high ceilings, updated kitchen with granite counter tops, stainless steel appliances and marble bathroom. Building includes doorman, elevator, laundry room gym and roof deck. Photos of other unit in building. Call this will not last long. NO FEE!!"""
    newline_data = {'number': [1,2], 'text': [newline_before_start, 'No special characters here' ] }
    frame = h2o.H2OFrame(newline_data)
    assert frame.ncols == 2
    assert frame.nrow == 2
    # The parsed string must exactly match the String with a newline inside
    assert frame['text'][0,0] == newline_before_start

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5441)
else:
    pubdev_5441()

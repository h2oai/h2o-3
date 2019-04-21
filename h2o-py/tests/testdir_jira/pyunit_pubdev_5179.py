import h2o
from h2o.frame import H2OFrame
from tests import pyunit_utils
import numpy

def pubdev_5179():

    data = [numpy.arange(0, 20).tolist() for x in numpy.arange(0, 20).tolist()]
    fr = h2o.H2OFrame(data)
    light = H2OFrame.get_frame(fr.frame_id, full_cols=10) # only first 10 columns will be returned with data

    # verify that light frame have all columns
    assert len(light.columns) == 20
    assert len(light.types) == 20
    assert len(light._ex._cache._data) == 10 # But only data for 10 columns is available

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5179)
else:
    pubdev_5179()

import h2o
from h2o.frame import H2OFrame
from tests import pyunit_utils
import numpy

def pubdev_5179():

    data = [numpy.arange(0, 999).tolist() for x in numpy.arange(0, 99).tolist()]
    fr = h2o.H2OFrame(data)
    light = H2OFrame.get_frame(fr.frame_id, light=True)

    # verify that light frame have all columns
    assert len(light.columns) == 999
    assert len(light.types) == 999


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5179)
else:
    pubdev_5179()

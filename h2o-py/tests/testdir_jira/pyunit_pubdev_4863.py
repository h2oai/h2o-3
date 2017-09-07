import h2o
import pytest

from h2o.exceptions import H2OResponseError
from tests import pyunit_utils


def pubdev_4863():

    with pytest.raises(H2OResponseError) as exception_info:
        h2o.rapids("(tmp= digi_temp (cols_py 123STARTSWITHDIGITS 'a'))")
    
    assert 'Error: Name lookup of \'123STARTSWITHDIGITS\' failed' in str(exception_info.value)

def create_frame_digits():
    column_Headers = ["a" ]
    python_obj = [column_Headers, [1]]
    h2o.H2OFrame.from_python(python_obj, header=1, destination_frame='123_ABCD')
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_4863)
else:
    pubdev_4863()

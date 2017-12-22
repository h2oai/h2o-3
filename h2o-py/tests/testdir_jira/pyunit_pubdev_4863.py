import h2o

from h2o.exceptions import H2OResponseError
from tests import pyunit_utils


def pubdev_4863():

    try:
        h2o.rapids("(tmp= digi_temp (cols_py 123STARTSWITHDIGITS 'a'))")
        assert False
    except H2OResponseError as error:
        print(error)
        assert 'Error: Name lookup of \'123STARTSWITHDIGITS\' failed' in str(error)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_4863)
else:
    pubdev_4863()

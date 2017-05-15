import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from numpy import testing as tst

def distance_check():

    x = h2o.H2OFrame.from_python(['Martha', 'Dwayne', 'Dixon'], column_types=['factor'])
    y = h2o.H2OFrame.from_python(['Marhta', 'Duane', 'Dicksonx'], column_types=['string'])
    dist = x.strdistance(y, measure="jw")
    dist_list = h2o.as_list(dist, use_pandas=False, header=False)

    tst.assert_allclose([float(c[0]) for c in dist_list], [0.961111, 0.84, 0.813333], atol=0.001)


if __name__ == "__main__":
    pyunit_utils.standalone_test(distance_check)
else:
    distance_check()

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


def distance_check_with_empty_strings():
    x = h2o.H2OFrame.from_python(['Martha', 'Dwayne', 'Dixon'], column_types=['factor'])
    y = h2o.H2OFrame.from_python(['Marhta', 'Duane', ''], column_types=['string'])
    dist = x.strdistance(y, measure="jw")
    dist_list = h2o.as_list(dist, use_pandas=False, header=False)
    tst.assert_allclose([float(c[0]) for c in dist_list], [0.961111, 0.84, 0.0], atol=0.001)

def distance_check_without_empty_strings():
    x = h2o.H2OFrame.from_python(['Martha', 'Dwayne', 'Dixon'], column_types=['factor'])
    y = h2o.H2OFrame.from_python(['Marhta', 'Duane', ''], column_types=['string'])
    dist = x.strdistance(y, measure="jw", compare_empty=False)
    dist_list = h2o.as_list(dist, use_pandas=False, header=False)
    # compare without last value as it is empty list
    tst.assert_allclose([float(c[0]) for c in dist_list[0:2]], [0.961111, 0.84], atol=0.001)
    # compare that last value os NA
    dist_na_list = h2o.as_list(dist.isna(), use_pandas=False, header=False)
    assert dist_na_list == [['0'], ['0'], ['1']]

__TESTS__ = [distance_check,
             distance_check_with_empty_strings,
             distance_check_without_empty_strings]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()

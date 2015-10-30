import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def levels_nlevels_setlevel_setLevels_test():

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    # frame (default)
    levels = iris.levels()
    nlevels = iris.nlevels()

    # frame (w/ index)
    levels = iris[4].levels()[0]
    nlevels = iris[4].nlevels()[0]
    assert {'Iris-setosa', 'Iris-versicolor', 'Iris-virginica'} == set(levels), \
        "Expected levels to be {0}, but got {1}".format(
          {'Iris-setosa', 'Iris-versicolor', 'Iris-virginica'},levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    # vec
    iris[4] = iris[4].set_level(level='Iris-setosa')
    levels = iris[4].levels()[0]
    nlevels = iris[4].nlevels()[0]
    assert {'Iris-setosa', 'Iris-versicolor', 'Iris-virginica'} == set(levels), \
        "Expected levels to be {0}, but got {1}".format(
          {'Iris-setosa', 'Iris-versicolor', 'Iris-virginica'},levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'Iris-setosa'

    levels = iris[4].levels()[0]
    nlevels = iris[4].nlevels()[0]
    assert {'Iris-setosa', 'Iris-versicolor', 'Iris-virginica'} == set(levels), \
        "Expected levels to be {0}, but got {1}".format(
          {'Iris-setosa', 'Iris-versicolor', 'Iris-virginica'},levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    iris[4] = iris[4].set_level(level='Iris-versicolor')
    levels = iris[4].levels()[0]
    nlevels = iris.nlevels()[0]
    assert {'Iris-setosa', 'Iris-versicolor', 'Iris-virginica'} == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'Iris-versicolor'

    levels = iris[1].levels()[0]
    nlevels = iris[1].nlevels()[0]
    assert levels == [], "Expected levels to be [], but got {0}".format(levels)
    assert nlevels == 0, "Expected nlevels to be 0, but got {0}".format(nlevels)

    ################### reimport, set new domains, rerun tests ###################################
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    iris[4] = iris[4].set_levels(levels=["a", "b", "c"])

    # frame (default)
    levels = iris.levels()
    nlevels = iris.nlevels()

    # frame (w/ index)
    levels = iris[4].levels()[0]
    nlevels = iris.nlevels()[0]
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    # vec
    iris[4] = iris[4].set_level(level='a')
    levels = iris[4].levels()[0]
    nlevels = iris[4].nlevels()[0]
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'a'

    levels = iris[4].levels()[0]
    nlevels = iris[4].nlevels()[0]
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    iris[4] = iris[4].set_level(level='b')
    levels = iris[4].levels()[0]
    nlevels = iris[4].nlevels()[0]
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'b'

    levels = iris[1].levels()[0]
    nlevels = iris[1].nlevels()[0]
    assert levels == [], "Expected levels to be [], but got {0}".format(levels)
    assert nlevels == 0, "Expected nlevels to be 0, but got {0}".format(nlevels)

    one_column_frame = iris[4]
    one_column_frame = one_column_frame.set_level(level='c')
    assert one_column_frame[0,0] == 'c'



if __name__ == "__main__":
    pyunit_utils.standalone_test(levels_nlevels_setlevel_setLevels_test)
else:
    levels_nlevels_setlevel_setLevels_test()

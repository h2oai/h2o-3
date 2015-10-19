




def levels_nlevels_setlevel_setLevels_test():

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    # frame (default)
    levels = iris.levels()
    nlevels = iris.nlevels()

    # frame (w/ index)
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)
    assert set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    # vec
    iris[4] = iris[4].set_level(level='Iris-setosa')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)
    assert set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'Iris-setosa'

    levels = iris[4].levels()
    nlevels = iris[4].nlevels()
    assert set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    iris[4] = iris[4].set_level(level='Iris-versicolor')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)
    assert set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'Iris-versicolor'

    levels = iris[1].levels()
    nlevels = iris[1].nlevels()
    assert levels == None, "Expected levels to be None, but got {0}".format(levels)
    assert nlevels == 0, "Expected nlevels to be 0, but got {0}".format(nlevels)

    ################### reimport, set new domains, rerun tests ###################################
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    iris[4] = iris[4].set_levels(levels=["a", "b", "c"])

    # frame (default)
    levels = iris.levels()
    nlevels = iris.nlevels()

    # frame (w/ index)
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    # vec
    iris[4] = iris[4].set_level(level='a')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'a'

    levels = iris[4].levels()
    nlevels = iris[4].nlevels()
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)

    iris[4] = iris[4].set_level(level='b')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)
    assert set(['a', 'b', 'c']) == set(levels), \
        "Expected levels to be {0}, but got {1}".format(set(['a', 'b', 'c']),levels)
    assert nlevels == 3, "Expected nlevels to be 3, but got {0}".format(nlevels)
    assert iris[0,4] == 'b'

    levels = iris[1].levels()
    nlevels = iris[1].nlevels()
    assert levels == None, "Expected levels to be None, but got {0}".format(levels)
    assert nlevels == 0, "Expected nlevels to be 0, but got {0}".format(nlevels)

    one_column_frame = iris[4]
    one_column_frame = one_column_frame.set_level(level='c')
    assert one_column_frame[0,0] == 'c'


levels_nlevels_setlevel_setLevels_test()

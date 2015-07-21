import sys
sys.path.insert(1, "../../")
import h2o


def levels_nlevels_setlevel_setLevels_test(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))

    # frame (default)
    levels = iris.levels()
    nlevels = iris.nlevels()

    # frame (w/ index)
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)

    # vec
    iris[4] = iris[4].setLevel(level='Iris-setosa')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)

    levels = iris[4].levels()
    nlevels = iris[4].nlevels()

    iris[4] = iris[4].setLevel(level='Iris-versicolor')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)

    levels = iris[1].levels()
    nlevels = iris[1].nlevels()

    ################### reimport, set new domains, rerun tests ###################################
    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))
    iris[4] = iris[4].setLevels(levels=["a", "b", "c"])

    # frame (default)
    levels = iris.levels()
    nlevels = iris.nlevels()

    # frame (w/ index)
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)

    # vec
    iris[4] = iris[4].setLevel(level='a')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)

    levels = iris[4].levels()
    nlevels = iris[4].nlevels()

    iris[4] = iris[4].setLevel(level='b')
    levels = iris.levels(col=4)
    nlevels = iris.nlevels(col=4)

    levels = iris[1].levels()
    nlevels = iris[1].nlevels()

    one_column_frame = iris[4]
    one_column_frame = one_column_frame.setLevel(level='c')

if __name__ == "__main__":
    h2o.run_test(sys.argv, levels_nlevels_setlevel_setLevels_test)

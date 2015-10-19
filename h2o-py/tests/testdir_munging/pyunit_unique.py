



def pyunit_unique():

    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    uniques = iris[4].unique()
    rows, cols = uniques.dim
    assert rows == 3 and cols == 1, "Expected 3 rows and 1 column, but got {0} rows and {1} column".format(rows,cols)
    assert "Iris-setosa" in uniques[0], "Expected Iris-setosa to be in the set of unique species, but it wasn't"
    assert "Iris-virginica" in uniques[0], "Expected Iris-virginica to be in the set of unique species, but it wasn't"
    assert "Iris-versicolor" in uniques[0], "Expected Iris-versicolor to be in the set of unique species, but it wasn't"


pyunit_unique()





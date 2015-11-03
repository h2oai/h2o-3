import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def interaction_check():
    # Connect to a pre-existing cluster
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    # add a couple of factor columns to iris
    iris = iris.cbind(iris[4] == "Iris-setosa")
    iris[5] = iris[5].asfactor()
    iris.set_name(5,"C6")

    iris = iris.cbind(iris[4] == "Iris-virginica")
    iris[6] = iris[6].asfactor()
    iris.set_name(6, name="C7")

    # create a frame of the two-way interactions
    two_way_interactions = h2o.interaction(iris, factors=[4,5,6], pairwise=True, max_factors=10000, min_occurrence=1)
    assert two_way_interactions.nrow == 150 and two_way_interactions.ncol == 3, \
        "Expected 150 rows and 3 columns, but got {0} rows and {1} " \
        "columns".format(two_way_interactions.nrow, two_way_interactions.ncol)
    levels1 = two_way_interactions.levels()[0]
    levels2 = two_way_interactions.levels()[1]
    levels3 = two_way_interactions.levels()[2]

    assert levels1 == ["Iris-setosa_1", "Iris-versicolor_0", "Iris-virginica_0"], \
        "Expected the following levels {0}, but got {1}".format(["Iris-setosa_1", "Iris-versicolor_0", "Iris-virginica_0"],
                                                                levels1)
    assert levels2 == ["Iris-setosa_0", "Iris-versicolor_0", "Iris-virginica_1"], \
        "Expected the following levels {0}, but got {1}".format(["Iris-setosa_0", "Iris-versicolor_0", "Iris-virginica_1"],
                                                                levels2)
    assert levels3 == ["0_0", "1_0", "0_1"], "Expected the following levels {0}, but got {1}".format(["0_0", "1_0", "0_1"],
                                                                                                     levels3)


    # do the same thing, but set 'factors' arg to list of column names
    two_way_interactions = h2o.interaction(iris, factors=["C5","C6","C7"], pairwise=True, max_factors=10000, min_occurrence=1)
    assert two_way_interactions.nrow == 150 and two_way_interactions.ncol == 3, \
        "Expected 150 rows and 3 columns, but got {0} rows and {1} " \
        "columns".format(two_way_interactions.nrow, two_way_interactions.ncol)
    levels1 = two_way_interactions.levels()[0]
    levels2 = two_way_interactions.levels()[1]
    levels3 = two_way_interactions.levels()[2]

    assert levels1 == ["Iris-setosa_1", "Iris-versicolor_0", "Iris-virginica_0"], \
        "Expected the following levels {0}, but got {1}".format(["Iris-setosa_1", "Iris-versicolor_0", "Iris-virginica_0"],
                                                                levels1)
    assert levels2 == ["Iris-setosa_0", "Iris-versicolor_0", "Iris-virginica_1"], \
        "Expected the following levels {0}, but got {1}".format(["Iris-setosa_0", "Iris-versicolor_0", "Iris-virginica_1"],
                                                                levels2)
    assert levels3 == ["0_0", "1_0", "0_1"], "Expected the following levels {0}, but got {1}".format(["0_0", "1_0", "0_1"],
                                                                                                     levels3)

    #TODO: allow factors to be list of lists



if __name__ == "__main__":
    pyunit_utils.standalone_test(interaction_check)
else:
    interaction_check()

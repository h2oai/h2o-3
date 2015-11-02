import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils





def upload_file():


    a = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    print a.describe()

    from h2o import H2OFrame


    # using lists []
    py_list_to_h2o = H2OFrame(zip(*[[0, 1, 2, 3, 4]]))

    print py_list_to_h2o.describe()

    py_list_to_h2o_2 = H2OFrame(zip(*[[0, 1, 2, 3], [5, 6, "hi", "dog"]]))

    print py_list_to_h2o_2.describe()


    # using tuples ()
    py_tuple_to_h2o = H2OFrame(zip(*[(0, 1, 2, 3, 4)]))

    print py_tuple_to_h2o.describe()

    py_tuple_to_h2o_2 = H2OFrame(zip(*((0, 1, 2, 3), (5, 6, "hi", "dog"))))

    print py_tuple_to_h2o_2.describe()


    # using dicts {}
    py_dict_to_h2o = H2OFrame({"column1": [5, 4, 3, 2, 1],
                                          "column2": (1, 2, 3, 4, 5)})

    py_dict_to_h2o.describe()

    py_dict_to_h2o_2 = H2OFrame({"colA": ["bilbo", "baggins"], "colB": ["meow"]})

    print py_dict_to_h2o_2.describe()


    # using collections.OrderedDict

    import collections
    d = {"colA": ["bilbo", "baggins"], "colB": ["meow"]}  # still unordered!
    py_ordered_dict_to_h2o = H2OFrame(collections.OrderedDict(d))

    py_ordered_dict_to_h2o.describe()


    # make an ordered dictionary!
    d2 = collections.OrderedDict()
    d2["colA"] = ["bilbo", "baggins"]
    d2["colB"] = ["meow"]


    py_ordered_dict_to_h2o_2 = H2OFrame(collections.OrderedDict(d2))
    py_ordered_dict_to_h2o_2.describe()


    # numpy.array

    # import numpy as np
    #
    # py_numpy_ary_to_h2o = H2OFrame(np.ones((50, 100), dtype=int))
    #
    # py_numpy_ary_to_h2o.describe()



if __name__ == "__main__":
    pyunit_utils.standalone_test(upload_file)
else:
    upload_file()

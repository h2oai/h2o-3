import sys
sys.path.insert(1, "../../")
import h2o, tests


def upload_file():
    

    a = h2o.upload_file(tests.locate("smalldata/logreg/prostate.csv"))
    print a.describe()

    from h2o import H2OFrame


    # using lists []
    py_list_to_h2o = H2OFrame(python_obj=[0, 1, 2, 3, 4])

    print py_list_to_h2o.describe()

    py_list_to_h2o_2 = H2OFrame(python_obj=[[0, 1, 2, 3], [5, 6, "hi", "dog"]])

    print py_list_to_h2o_2.describe()


    # using tuples ()
    py_tuple_to_h2o = H2OFrame(python_obj=(0, 1, 2, 3, 4))

    print py_tuple_to_h2o.describe()

    py_tuple_to_h2o_2 = H2OFrame(python_obj=((0, 1, 2, 3), (5, 6, "hi", "dog")))

    print py_tuple_to_h2o_2.describe()


    # using dicts {}
    py_dict_to_h2o = H2OFrame(python_obj={"column1": [5, 4, 3, 2, 1],
                                          "column2": (1, 2, 3, 4, 5)})

    py_dict_to_h2o.describe()

    py_dict_to_h2o_2 = H2OFrame(python_obj={"colA": ["bilbo", "baggins"], "colB": ["meow"]})

    print py_dict_to_h2o_2.describe()


    # using collections.OrderedDict

    import collections
    d = {"colA": ["bilbo", "baggins"], "colB": ["meow"]}  # still unordered!
    py_ordered_dict_to_h2o = H2OFrame(python_obj=collections.OrderedDict(d))

    py_ordered_dict_to_h2o.describe()


    # make an ordered dictionary!
    d2 = collections.OrderedDict()
    d2["colA"] = ["bilbo", "baggins"]
    d2["colB"] = ["meow"]


    py_ordered_dict_to_h2o_2 = H2OFrame(python_obj=collections.OrderedDict(d2))
    py_ordered_dict_to_h2o_2.describe()


    # numpy.array

    # import numpy as np
    #
    # py_numpy_ary_to_h2o = H2OFrame(python_obj=np.ones((50, 100), dtype=int))
    #
    # py_numpy_ary_to_h2o.describe()

if __name__ == "__main__":
  tests.run_test(sys.argv, upload_file)

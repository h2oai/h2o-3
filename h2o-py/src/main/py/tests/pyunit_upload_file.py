import sys
sys.path.insert(1, "..")  # inserts before index "1"

import h2o

h2o.init()

a = h2o.upload_file("../../../../../smalldata/logreg/prostate.csv")
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
py_dict_to_h2o = H2OFrame(python_obj={"column1": [5,4,3,2,1], "column2": [1,2,3,4,5]})

py_dict_to_h2o.describe()

py_dict_to_h2o_2 = H2OFrame(python_obj={"colA": ["bilbo", "baggins"], "colB": ["meow"]})

print py_doct_to_h2o_2.describe()
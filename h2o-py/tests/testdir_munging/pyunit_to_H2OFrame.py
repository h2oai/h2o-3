import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



import numpy as np
import pandas as pd

def to_H2OFrame():
    # Connect to a pre-existing cluster
    

    # TODO: negative testing

    ## 1. list
    #   a. single col
    python_obj = [1, "a", 2.5, "bcd", 0]
    the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=1)

    #   b. 5 cols, 1 row
    python_obj = [[1], [2], [3.7], [8], [9]]
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=5)

    #   c. 3 cols, 5 rows
    python_obj = [[6,7,8,9,10], [1,2,3,4,5], [3,2,2,2,2]]
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=3)

    python_obj = [["a", "b"], ["c", "d"]]
    the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=2, cols=2)

    #   d. jagged
    python_obj = [[6,7,8,9,10], [1,2,3,4], [3,2,2]]
    the_frame = h2o.H2OFrame(python_obj)
    #pyunit_utils.check_dims_values(python_obj, the_frame, rows=3, cols=5) TODO


    ## 2. tuple
    #   a. single row
    python_obj = (1, "a", 2.5, "bcd", 0)
    the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=1)

    #   b. single column
    python_obj = ((1,), (2,), (3.7,), (8,), (9,))
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=5)

    #   c. multiple rows, columns
    python_obj = ((6,7,8,9,10), (1,2,3,4,5), (3,2,2,2,2))
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=3)

    #   d. jagged
    python_obj = ((6,7,8,9,10), (1,2,3,4), (3,2,2))
    the_frame = h2o.H2OFrame(python_obj)
    #pyunit_utils.check_dims_values(python_obj, the_frame, rows=3, cols=5) TODO

    ## 3. list-tuple mixed
    #   a. single column
    python_obj = ((1,), [2], (3.7,), [8], (9,))
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=5)

    #   b. single column
    python_obj = [(1,), [2], (3.7,), [8], (9,)]
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=5)

    #   c. multiple rows, columns
    python_obj = ([6,7,8,9,10], (1,2,3,4,5), [3,2,2,2,2])
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=3)

    #   d. multiple rows, columns
    python_obj = [(6,7,8,9,10), [1,2,3,4,5], (3,2,2,2,2)]
    the_frame = h2o.H2OFrame(python_obj)
    pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=3)

    #   e. jagged
    python_obj = [(6,7,8,9,10), [1,2,3,4], (3,2,2)]
    the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=3, cols=5) TODO

    #   f. jagged
    python_obj = ((6,7,8,9,10), [1,2,3,4], (3,2,2))
    the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=3, cols=5) TODO

    ## 4. dictionary
    #   a. single row
    # python_obj = {"a":1, "b":"a", "c":2.5, "d":"bcd", "e":0}
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=5)
    # assert set(the_frame.names) == set(python_obj.keys()), "H2OFrame header is hosed. Got {0}, but should have got " \
    #                                                "{1}".format(the_frame.names, python_obj.keys())
    #
    # python_obj = {"a":[1], "b":["a"], "c":[2.5], "d":["bcd"], "e":[0]}
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=5)
    # assert set(the_frame.names) == set(python_obj.keys()), "H2OFrame header is hosed. Got {0}, but should have got " \
    #                                                "{1}".format(the_frame.names, python_obj.keys())
    #
    # #   b. single column
    # python_obj = {"foo":(1,2,3.7,8,9)}
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=1)
    # assert set(the_frame.names) == set(python_obj.keys()), "H2OFrame header is hosed. Got {0}, but should have got " \
    #                                                "{1}".format(the_frame.names, python_obj.keys())
    #
    # #   c. multiple rows, columns
    # python_obj = {"foo":[6,7,8,9,10], "bar":(1,2,3,4,5), "baz":(3,2,2,2,2)}
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=3)
    # assert set(the_frame.names) == set(python_obj.keys()), "H2OFrame header is hosed. Got {0}, but should have got " \
    #                                                "{1}".format(the_frame.names, python_obj.keys())
    #
    # #   d. jagged
    # python_obj = {"foo":(6,7), "bar":(1,2,3,4), "baz":(3,2,2)}
    # the_frame = h2o.H2OFrame(python_obj)
    # # check_dims_values_jagged() TODO
    # assert set(the_frame.names) == set(python_obj.keys()), "H2OFrame header is hosed. Got {0}, but should have got " \
    #                                                "{1}".format(the_frame.names, python_obj.keys())

    ## 5. numpy.ndarray
    #   a. single row
    # python_obj = np.array([1, "a", 2.5, "bcd", 0])
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=5)
    #
    # #   b. single column
    # python_obj = np.array([[1], [2], [3.7], [8], [9]])
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=1)
    #
    # #   c. multiple rows, columns
    # python_obj = np.array([[6,7,8,9,10], [1,2,3,4,5], [3,2,2,2,2]])
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=3, cols=5)
    #
    # #   d. jagged
    # python_obj = np.array([[6,7,8,9,10], [1,2,3,4], [3,2,2]])
    # the_frame = h2o.H2OFrame(python_obj)
    # # check_dims_values_jagged() TODO
    #
    # ## 6. pandas.DataFrame
    # #   a. single row
    # python_obj = pd.DataFrame({'foo' : pd.Series([1]), 'bar' : pd.Series([6]), 'baz' : pd.Series(["a"]) })
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=1, cols=3)
    #
    # #   b. single column
    # python_obj = pd.DataFrame({'foo' : pd.Series([1, 2, 3, 7.8, 9])})
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=1)
    #
    # #   c. multiple rows, columns
    # python_obj = pd.DataFrame({'foo' : pd.Series([6,7,8,9,10]), 'bar' : pd.Series([1,2,3,4,5]),
    #                            'baz' : pd.Series([3,2,2,2,2])})
    # the_frame = h2o.H2OFrame(python_obj)
    # pyunit_utils.check_dims_values(python_obj, the_frame, rows=5, cols=3)
    #
    # #   d. jagged
    # python_obj = pd.DataFrame({'foo' : pd.Series([6,7,8]), 'bar' : pd.Series([1,2,3,4,5]), 'baz' : pd.Series([3,2,2,2])})
    # the_frame = h2o.H2OFrame(python_obj)
    # # check_dims_values_jagged() TODO



if __name__ == "__main__":
    pyunit_utils.standalone_test(to_H2OFrame)
else:
    to_H2OFrame()

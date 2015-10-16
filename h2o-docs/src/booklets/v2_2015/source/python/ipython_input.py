# Customarily, we import and start H2O as follows:
import h2o
h2o.init()  # Will set up H2O cluster using all available cores
# To create an H2OFrame object from a python tuple:
df = h2o.H2OFrame(((1, 2, 3),
                   ('a', 'b', 'c'),
                   (0.1, 0.2, 0.3)))
df
# To create an H2OFrame object from a python list:
df = h2o.H2OFrame([[1, 2, 3],
                   ['a', 'b', 'c'],
                   [0.1, 0.2, 0.3]])
df
# To create an H2OFrame object from a python dict (or collections.OrderedDict):
df = h2o.H2OFrame({'A': [1, 2, 3],
                   'B': ['a', 'b', 'c'],
                   'C': [0.1, 0.2, 0.3]})
df
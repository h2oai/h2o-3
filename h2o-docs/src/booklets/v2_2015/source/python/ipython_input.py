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

# To create an H2OFrame object from a dict with specified column types:
df2 = h2o.H2OFrame({'A': [1, 2, 3],
                    'B': ['a', 'a', 'b'],
                    'C': ['hello', 'all', 'world'],
                    'D': ['12/1/2015 00:21:25', '12/2/2015 01:21:25', '12/3/2015 02:21:25']},
                   column_types=['numeric', 'enum', 'string', 'time'])

df2

df2.types

import numpy as np
df = h2o.H2OFrame(np.random.randn(100,4).tolist(), columns=list('ABCD'))
df.head()
df.tail(5)

df.columns

df.describe()
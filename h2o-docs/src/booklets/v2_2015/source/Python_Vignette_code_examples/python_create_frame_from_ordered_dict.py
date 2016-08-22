In [7]: df = h2o.H2OFrame({'A': [1, 2, 3],
   ...:                    'B': ['a', 'b', 'c'],
   ...:                    'C': [0.1, 0.2, 0.3]})

Parse Progress: [###############################] 100%
Uploaded py2714e8a2-67c7-45a3-9d47-247120c5d931 into cluster with 3 rows and 3 cols

In [8]: df
Out[8]: H2OFrame with 3 rows and 3 columns:
  A    C  B
---  ---  ---
  1  0.1  a
  2  0.2  b
  3  0.3  c
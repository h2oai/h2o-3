In [5]: df = h2o.H2OFrame(zip(*[[1, 2, 3],
   ...:                    ['a', 'b', 'c'],
   ...:                    [0.1, 0.2, 0.3]]))

Parse Progress: [###############################] 100%
Uploaded py2c9ccb17-a86e-47d7-be1a-a7950b338870 into cluster with 3 rows and 3 cols

In [6]: df
Out[6]: H2OFrame with 3 rows and 3 columns:
  C1  C2      C3
----  ----  ----
   1  a      0.1
   2  b      0.2
   3  c      0.3
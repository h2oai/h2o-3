In [3]: df = h2o.H2OFrame(zip(*((1, 2, 3),
   ...:                    ('a', 'b', 'c'),
   ...:                    (0.1, 0.2, 0.3))))

Parse Progress: [###############################] 100%
Uploaded py9bccf8ce-c01e-40c8-bc73-b8e7e0b17c6a into cluster with 3 rows and 3 cols

In [4]: df
Out[4]: H2OFrame with 3 rows and 3 columns:
  C1  C2      C3
----  ----  ----
   1  a      0.1
   2  b      0.2
   3  c      0.3
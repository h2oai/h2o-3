In [14]: df2 = h2o.H2OFrame.from_python({'A': [1, 2, 3],
   ....:                                 'B': ['a', 'a', 'b'],
   ....:                                 'C': ['hello', 'all', 'world'],
   ....:                                 'D': ['12MAR2015:11:00:00', '13MAR2015:12:00:00', '14MAR2015:13:00:00']},
   ....:                                 column_types=['numeric', 'enum', 'string', 'time'])

Parse Progress: [###############################] 100%
Uploaded py17ea1f6d-ae83-451d-ad33-89e770061601 into cluster with 3 rows and 4 cols

In [10]: df2
Out[10]: H2OFrame with 3 rows and 4 columns:
  A      C  B                   D
--- ------ -- -------------------
  1  hello  a 2015-03-12 11:00:00
  2    all  a 2015-03-13 12:00:00
  3  world  b 2015-03-14 13:00:00
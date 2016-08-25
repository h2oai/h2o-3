In [14]: df2 = h2o.H2OFrame.from_python({'A': [1, 2, 3],
   ....:                                 'B': ['a', 'a', 'b'],
   ....:                                 'C': ['hello', 'all', 'world'],
   ....:                                 'D': ['12MAR2015:11:00:00', '13MAR2015:12:00:00', '14MAR2015:13:00:00']},
   ....:                                 column_types=['numeric', 'enum', 'string', 'time'])

Parse Progress: [###############################] 100%

In [10]: df2
Out[10]: 
   A  C      B              D
---  -----  ---  -----------
  1  hello  a    1.42618e+12
  2  all    a    1.42627e+12
  3  world  b    1.42636e+12

[3 rows x 4 columns]
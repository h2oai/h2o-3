In [46]: df3 = h2o.H2OFrame.from_python(
    {'A': [1, 2, 3,None,''],                          
     'B': ['a', 'a', 'b', 'NA', 'NA'],
     'C': ['hello', 'all', 'world', None, None],
     'D': ['12MAR2015:11:00:00',None,
           '13MAR2015:12:00:00',None,
           '14MAR2015:13:00:00']},   
    column_types=['numeric', 'enum', 'string', 'time'])

In [47]: df3
Out[47]: 
    A      C    B             D
0   1  hello    a  1.426183e+12
1   2    all    a  nan
2   3  world    b  1.426273e+12
3 nan          NA  nan
4 nan          NA  1.426363e+12

[5 rows x 4 columns]
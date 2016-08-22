In [108]: df10 = h2o.H2OFrame.from_python( { 
            'A': ['Hello', 'World', 
                  'Welcome', 'To', 
                  'H2O', 'World'],
            'n': [0,1,2,3,4,5]} )

Parse Progress: [###############################] 100%
Uploaded py57e84cb6-ce29-4d13-afe4-4333b2186c72 into cluster with 6 rows and 2 cols

In [109]: df11 = h2o.H2OFrame.from_python(np.random.randint(0, 10, size=100).tolist9), column_names=['n'])

Parse Progress: [###############################] 100%
Uploaded py090fa929-b434-43c0-81bd-b9c61b553a31 into cluster with 100 rows and 1 cols

In [112]: df11.merge(df10)
Out[112]: H2OFrame with 100 rows and 2 columns:
   n      A
0  7    NaN
1  3     To
2  0  Hello
3  9    NaN
4  9    NaN
5  3     To
6  4    H2O
7  4    H2O
8  5  World
9  4    H2O
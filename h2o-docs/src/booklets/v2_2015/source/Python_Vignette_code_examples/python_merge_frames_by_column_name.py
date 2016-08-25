In [108]: df10 = h2o.H2OFrame.from_python( { 
            'A': ['Hello', 'World', 
                  'Welcome', 'To', 
                  'H2O', 'World'],
            'n': [0,1,2,3,4,5]} )

Parse Progress: [###############################] 100%

# Create a single-column, 100-row frame 
# Include random integers from 0-5
In [109]: df11 = h2o.H2OFrame.from_python(np.random.randint(0,6,(100,1)), column_names=list('n'))

Parse Progress: [###############################] 100%

# Combine column "n" from both datasets
In [112]: df11.merge(df10)
Out[112]: 
     n  A
   ---  -------
0    2  Welcome
1    5  World
2    4  H2O
3    2  Welcome
4    3  To
5    3  To
6    1  World
7    1  World
8    3  To
9    1  World

[100 rows x 2 columns]
df10 = h2o.H2OFrame.from_python( { 
        'A': ['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'],
        'n': [0,1,2,3,4,5]} )

# Create a single-column, 100-row frame 
# Include random integers from 0-5
df11 = h2o.H2OFrame.from_python(np.random.randint(0,6,(100,1)), column_names=list('n'))

# Combine column "n" from both datasets
df11.merge(df10)

#    n  A
#  ---  -------
#    2  Welcome
#    5  World
#    4  H2O
#    2  Welcome
#    3  To
#    3  To
#    1  World
#    1  World
#    3  To
#    1  World
# 
# [100 rows x 2 columns]
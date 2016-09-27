df7 = h2o.H2OFrame.from_python(['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'])

# View the H2OFrame
df7

#  C1     C2     C3       C4    C5    C6
#  -----  -----  -------  ----  ----  -----
#  Hello  World  Welcome  To    H2O   World
# 
# [1 row x 6 columns]

# Find how many times "l" appears in each string
df7.countmatches('l')
 
#   C1    C2    C3    C4    C5    C6
# ----  ----  ----  ----  ----  ----
#    2     1     1     0     0     1
# 
# [1 row x 6 columns]
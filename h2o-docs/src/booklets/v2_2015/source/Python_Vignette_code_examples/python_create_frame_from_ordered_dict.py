df = h2o.H2OFrame({'A': [1, 2, 3],'B': ['a', 'b', 'c'],'C': [0.1, 0.2, 0.3]})

# View the H2OFrame
df

#   A    C  B
# ---  ---  ---
#   1  0.1  a
#   2  0.2  b
#   3  0.3  c
#
# [3 rows x 3 columns]
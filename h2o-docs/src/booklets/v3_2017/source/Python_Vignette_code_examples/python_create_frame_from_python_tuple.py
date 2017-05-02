df = h2o.H2OFrame(zip(*((1, 2, 3), ('a', 'b', 'c'), (0.1, 0.2, 0.3))))

# View the H2OFrame
df

#   C1  C2      C3
# ----  ----  ----
#    1  a      0.1
#    2  b      0.2
#    3  c      0.3
#
# [3 rows x 3 columns]
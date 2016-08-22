In [127]: df13 = df12.group_by(['A','B']).sum().frame

In [128]: df13
Out[128]: H2OFrame with 6 rows and 4 columns:
     A      B     sum_C     sum_D
0  bar    one  1.055763  1.733467
1  bar    two  0.758202  0.521504
2  foo  three  0.412450 -0.050374
3  foo    one  1.682168 -1.833366
4  foo    two -1.223957  1.048333
5  bar  three -1.066722 -0.311055
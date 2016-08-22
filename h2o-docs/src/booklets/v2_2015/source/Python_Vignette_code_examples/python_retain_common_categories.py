In [168]: bb_df = df12.interaction(['B','B'], pairwise=False, max_factors=2, min_occurrence=1)

Interactions Progress: [########################] 100%

In [169]: bb_df
Out[169]: H2OFrame with 8 rows and 1 columns:
     B_B
0    one
1    one
2    two
3  other
4    two
5    two
6    one
7  other
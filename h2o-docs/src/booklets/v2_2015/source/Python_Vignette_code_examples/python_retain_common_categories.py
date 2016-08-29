bb_df = df12.interaction(['B','B'], pairwise=False, max_factors=2, min_occurrence=1)

# View H2OFrame
bb_df

#  B_B
#  -----
#  one
#  one
#  two
#  other
#  two
#  two
#  one
#  other
#
# [8 rows x 1 column]
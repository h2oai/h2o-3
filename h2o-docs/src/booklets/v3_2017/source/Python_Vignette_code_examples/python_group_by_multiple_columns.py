df13 = df12.group_by(['A','B']).sum().frame

# View the H2OFrame
df13
 
#  A    B           sum_C        sum_D
#  ---  -----  ----------  -----------
#  bar  one    -0.165891   -0.433233
#  bar  three   2.25083     0.512449
#  bar  two     0.0817828   0.00830419
#  foo  one    -0.0752683   1.51216
#  foo  three   0.879319    1.48051
#  foo  two    -2.13829     2.47479
# 
# [6 rows x 4 columns]
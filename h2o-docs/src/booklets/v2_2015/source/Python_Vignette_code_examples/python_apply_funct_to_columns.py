df5 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))

df5.apply(lambda x: x.mean(na_rm=True))

# H2OFrame:
#         A          B           C          D
# ---------  ---------  ----------  ---------
# 0.0304506  0.0334168  -0.0374976  0.0520486
#
# [1 row x 4 columns]
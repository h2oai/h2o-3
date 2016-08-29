# Create a frame of random numbers w/ 100 rows
df8 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))

# Create a second frame of random numbers w/ 100 rows
df9 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))

# Combine the two frames, adding the rows from df9 to df8
df8.rbind(df9)

#           A          B           C          D
# -----------  ---------  ----------  ---------
#  1.11442      1.31272    0.250418    1.73182
# -1.61876      0.428622  -1.16684    -0.032936
#  0.637249    -0.48904    1.55848     0.669266
#  0.00355574  -0.40736   -0.979222   -0.395017
#  0.218243    -0.154004  -0.219537   -0.750664
# -0.047789     0.306318   0.557441   -0.319108
# -1.45013     -0.614564   0.472257   -0.456181
# -0.594333    -0.435832  -0.0257311   0.548708
#  0.571215    -1.22759   -2.01855    -0.491638
# -0.697252    -0.864301  -0.542508   -0.152953
# 
# [200 rows x 4 columns]
import sys
sys.path.insert(1, "../../../")
import h2o

h2o.init()

covtype = h2o.upload_file(h2o.locate("smalldata/covtype/covtype.20k.data"))
covtype[54] = covtype[54].asfactor()
#dlmodel = h2o.deeplearning(x=covtype[0:54], y=covtype[54], hidden=[17,191], epochs=1, training_frame=covtype,
#                           balance_classes=False, reproducible=True, seed=1234, export_weights_and_biases=True)


train =h2o.import_frame(h2o.locate("bigdata/laptop/mnist/train.csv.gz"))
predictors = range(100)
ae_model = h2o.deeplearning(x=train[predictors], training_frame=train, activation="Tanh", autoencoder=True,
                            hidden=[50], l1=1e-5, ignore_const_cols=False, epochs=1)

foo = ae_model.anomaly(covtype)

print foo
# pros = h2o.upload_file(h2o.locate("smalldata/prostate/prostate.csv.zip"))
# pros[1] = pros[1].asfactor()
# r = pros[0].runif() # a column of length pros.nrow() with values between 0 and 1
# # ~80/20 train/validation split
# pros_train = pros[r > .2]
# pros_valid = pros[r <= .2]

# standardize: False
# family: binomial
# solver: L_BFGS
# prior: 0.661566011292
# beta_constraints:
# Displaying 6 row(s):
# Row ID  names         lower_bounds           upper_bounds
# --------  ------------  ---------------------  ----------------------
# 1  [u'PSA']      [0.08442861247981925]  [0.12694071519057182]
# 2  [u'RACE']     [-0.5780290588548885]  [-0.13810131302030548]
# 3  [u'DPROS']    [0.76852832394281]     [1.3496105569956252]
# 4  [u'DCAPS']    [-0.9016507404584659]  [-0.32401365663844667]
# 5  [u'AGE']      [0.5855928861219879]   [1.1951827824495105]
# 6  [u'GLEASON']  [0.5811227135549313]   [0.6066065068362181]
#
# lambda_search: True
# max_iterations: 29

# bc = []
# bc.append([u'PSA',0.08442861247981925,0.12694071519057182])
# bc.append([u'RACE',-0.5780290588548885,-0.13810131302030548])
# bc.append([u'DPROS',0.76852832394281,1.3496105569956252])
# bc.append([u'DCAPS',-0.9016507404584659,-0.32401365663844667])
# bc.append([u'AGE',0.5855928861219879,1.1951827824495105])
# bc.append([u'GLEASON',0.5811227135549313,0.6066065068362181])
# beta_constraints = h2o.H2OFrame(python_obj=bc)
# beta_constraints.setNames(['names', 'lower_bounds', 'upper_bounds'])
# glm = h2o.glm(x=pros_train[[6, 3, 4, 5, 2, 8]], y=pros_train[1], validation_x=pros_valid[[6, 3, 4, 5, 2, 8]],
#         validation_y=pros_valid[1], standardize=False, family="binomial")
#
# glm.coef()
# glm.show()

#iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader_65_rows.csv"))
#iris = h2o.H2OFrame(vecs=[v for v in iris._vecs[0:4]])
#air = h2o.import_frame(path=h2o.locate("smalldata/airlines/allyears2k_headers.zip"))

#foo = h2o.H2OFrame()
#air.describe()
#air.show()
#foo = air[air["Origin"] == "ZZZQQ"]
#foo.show()

#foo = h2o.H2OFrame(python_obj=[['a',2,3],['b',5,6],['c',8,9]])
#foo.show()
#foo.setNames(['names', 'lower_bounds', 'upper_bounds'])

#res = [v.mean() for v in iris]
#res = iris.sum()
#res = iris[4].isfactor()

#foo = iris[0:5,2]
#bar = h2o.as_list(foo)
#res = iris + 2
#res = 2 - iris
#res2 = h2o.as_list(res[0])

#res.head(rows=30, cols=2)
#res.tail()
#res.describe()
#res.show() # 10 only

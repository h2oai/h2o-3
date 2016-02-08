iris_h2o = as.h2o(iris)
m = h2o.glm(training_frame=iris_h2o,y="Species",x=1:4,family="multinomial")
prit(m)
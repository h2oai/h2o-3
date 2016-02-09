library(h2o)
h2o.init()
iris_h2o = as.h2o(iris)
h2o.fit = h2o.glm(training_frame=iris_h2o,y="Species",x=1:4,family="multinomial")
h2o.fit
air.grid <- h2o.gbm(y = "IsDepDelayed", x = myX, distribution="multinomial", data = air_train.hex, n.trees=c(5,10,15), interaction.depth=c(2,3,4), shrinkage=c(0.1,0.2))

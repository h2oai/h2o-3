library(h2o)
localH2O <- h2o.init()
air = h2o.importFile(localH2O, "allyears_tiny.csv")

s = h2o.runif(air)
air.train = air[s <= 0.8,]
air.valid = air[(s > 0.8) & (s <= 0.9),]
air.test  = air[s > 0.9,]

myX = c("Origin", "Dest", "Distance", "UniqueCarrier", "Month", "DayofMonth", "DayOfWeek")
myY = "IsDepDelayed"

air.gbm = h2o.gbm(x = myX, y = myY, data = air.train, validation = air.valid,
                  distribution = "multinomial",
                  n.trees = 10, interaction.depth = 3, shrinkage = 0.01,
                  importance = T, cv=list(nfold=4, seed=42))
print(air.gbm@model)

pred = h2o.predict(air.gbm, air.test)
head(pred)

# Unit Test Development
require(gbm)

flatDF <- data.frame(loc = c(1, 2, 3, 4, 7, 8, 9, 11, 12, 13), 
           weight=rep(1, 10),
           class=c(1,1,1,1,2,2,2,3,3,3))

flatModel <- gbm(class~loc, data=flatDF, n.trees=2, n.minobsinnode=1, interaction.depth=2)


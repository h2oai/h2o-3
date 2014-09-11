setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.gbmMultiVBern.golden <- function(H2Oserver) {
	
#Import data: 
Log.info("Making data...") 
set.seed(3719)
n <- 1000
X <- matrix(rnorm(10*n), n, 10)
y <- rep(-1, n)
y[apply(X*X, 1, sum) > qchisq(.5, 10)] <- 1

dimnames(X)[[2]] <- c("x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9", "x10")


train.data <- as.data.frame(X)
train.data$y <- y

#  Now repeat for 10000 test data
n <- 10000
X <- matrix(rnorm(10*n), n, 10)
y <- rep(-1, n)
y[apply(X*X, 1, sum) > qchisq(.5, 10)] <- 1
dimnames(X)[[2]] <- c("x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9", "x10")
test.data <- as.data.frame(X)
test.data$y <- y

#  Need to put training and test data together for gbm below and convert 
#  to 0-1 data

train.data2 <- train.data
train.data2$y[train.data2$y < 0] <- 0
test.data2 <- test.data
test.data2$y[test.data2$y < 0] <- 0
all.data2 <- rbind(train.data2, test.data2)

fy<- as.factor(all.data2$y)
f.all.data2<- all.data2[,-11]
f.all.data2$y<- fy

expect_equal(nrow(f.all.data2), nrow(all.data2))

#########Give Data to H2O####

f.all.data.H<- as.h2o(H2Oserver, f.all.data2)
all.data.H<- as.h2o(H2Oserver, all.data2)
f.all.data.H[,11]<- as.factor(f.all.data.H[,11])



###Fit GBM models in R: 


fit.gbm1 <- gbm(y ~ x1 + x2 + x3, dist="multinomial", data=f.all.data2, n.trees = 1, interaction.depth = 1, shrinkage = .7, bag.fraction = 1, verbose=T)

fit.gbm2 <- gbm(y ~ x1 + x2 + x3, dist="bernoulli", data=all.data2, n.trees = 1, interaction.depth = 1, shrinkage = .7, bag.fraction = 1, verbose=T)


###Fit GBM models in H2O+R

h2o.fit1<- h2o.gbm(x=c("x1", "x2", "x3"), y="y", distribution="multinomial", data=f.all.data.H, n.trees=1, interaction.depth = 1, shrinkage = .7, n.bins = 1000)

h2o.fit2<- h2o.gbm(x=c("x1", "x2", "x3"), y="y", distribution="bernoulli", data=f.all.data.H, n.trees=1, interaction.depth = 1, shrinkage = .7, n.bins = 1000)

######### Get predictions: ###################

pred.gbm1<-as.data.frame(predict.gbm(fit.gbm1, newdata=f.all.data2, n.trees=1, type="response"))
pred.gbm2<- as.data.frame(predict.gbm(fit.gbm2, newdata=all.data2, n.trees=1, type="response"))
pred.h2o.fit1<- as.data.frame(h2o.predict(h2o.fit1, f.all.data.H))
pred.h2o.fit2<- as.data.frame(h2o.predict(h2o.fit2, f.all.data.H))

#We expect that the two families within the same tool produce different predictions because they are optimizing a different loss function. This is true for the models produced in R. We also expect that the models built between the two tools agree to some extent, and thus significant positive correlation between the two vectors of predictors. 

Is.R.Different<- cor.test(pred.gbm1[,2], pred.gbm2[,1], method="spearman")
Is.H2O.Different<- cor.test(pred.h2o.fit1$X1, pred.h2o.fit2$X1, method="spearman")
Does.Family.Mult.Agree<- cor.test(pred.h2o.fit1$X1, pred.gbm1[,2], method="spearman")
Does.Family.Bern.Agree<- cor.test(pred.h2o.fit2$X1, pred.gbm2[,1], method="spearman")

##############Print out and Compare P-values for Spearman statistics for each of the comparrisons###############
Log.info("Print model statistics for R and H2O... \n")
Log.info(paste("Is R Different  : ", Is.R.Different$p.value))
Log.info(paste("Is H2O Different  : ", Is.H2O.Different$p.value))
Log.info(paste("Multinomial Agreement  : ", Does.Family.Mult.Agree$p.value))
Log.info(paste("Bernoulli Agreement  : ", Does.Family.Bern.Agree$p.value))

Log.info("Compare Spearman test statistics in R to model statistics in H2O")
expect_less_than(Does.Family.Bern.Agree$p.value, .05)
expect_less_than(Does.Family.Mult.Agree$p.value, .05)
#expect_less_than(Is.R.Different$p.value, .05)
#expect_less_than(Is.H2O.Different$p.value, .05)

testEnd()
}

doTest("GBM: Is it Bernoulli Yet?", test.gbmMultiVBern.golden)


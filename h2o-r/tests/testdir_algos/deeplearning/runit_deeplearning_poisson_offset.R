####### This tests offset in deeplearing for poisson by comparing results with expected behavior ######

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function() {
	ca = read.csv(file =locate("smalldata/glm_test/cancar_logIn.csv"),header = T)
	cc = as.h2o(ca,destination_frame = "cc")
	cc$Merit = as.factor(cc$Merit)
	cc$Class = as.factor(cc$Class)
	myX =c("Merit","Class")
	myY = "Claims"
	
	#without offset
	#gg = gbm(formula = Claims~factor(Class)+factor(Merit) , distribution = "poisson",data = ca,
     #    n.trees = 9,interaction.depth = 1,n.minobsinnode = 1,shrinkage = 1,bag.fraction = 1,
      #   train.fraction = 1,verbose=T)
	#gg$train.error # -407652.4
	#pr = predict(gg, ca, type="response")
	#summary(pr)  #mean = 20200.0; min = 727.6 ; max = 202400.0
	hh = h2o.deeplearning(x = myX,y = "Claims",distribution ="poisson",hidden = c(6),epochs = 995,train_samples_per_iteration = -1,
                      reproducible = T,activation = "Tanh",balance_classes = F,force_load_balance = F,
                      seed = 5313,score_training_samples = 0,score_validation_samples = 0,
                      training_frame = cc) 
	hh@model$training_metrics@metrics$mean_residual_deviance #-407674.1
	mean_deviance = hh@model$training_metrics@metrics$mean_residual_deviance
	expect_equal(mean_deviance,-407656.252653641)
	ph = as.data.frame(h2o.predict(hh,newdata = cc)) 
	expect_equal(19949.4997158079, mean(ph[,1]) )
	expect_equal(330.83082167763, min(ph[,1]) )
	expect_equal(200851.947039858, max(ph[,1]) )
	
	#with offset
	#gg = gbm(formula = Claims~factor(Class)+factor(Merit)+offset(log(Insured))  , distribution = "poisson",data = ca,
     #    n.trees = 9,interaction.depth = 2,n.minobsinnode = 1,shrinkage = 1,bag.fraction = 1,
      #   train.fraction = 1,verbose=T)
	#gg$train.error  # -408153.4
	#link = predict(gg, ca, type="link")
	#link.offset = link + ca$logInsured
	#pred = exp(link.offset)
	#summary(pred)  #mean = 20200.0; min = 530.1; max = 215900.0
	hh = h2o.deeplearning(x = myX,y = "Claims",distribution ="poisson",hidden = c(6),epochs = 995,train_samples_per_iteration = -1,
                      reproducible = T,activation = "Tanh",balance_classes = F,force_load_balance = F,
                      seed = 5313,score_training_samples = 0,score_validation_samples = 0,
                      offset_column = "logInsured",training_frame = cc) 
	#hh@model$training_metrics@metrics$mean_residual_deviance 
	mean_deviance = hh@model$training_metrics@metrics$mean_residual_deviance
	expect_equal(mean_deviance,-408154.877874604)
	ph = as.data.frame(h2o.predict(hh,newdata = cc)) 
	summary(ph)
	expect_equal(20218.7019284291, mean(ph[,1]) )
	expect_equal(563.535820765981, min(ph[,1]) )
	expect_equal(217887.618075763, max(ph[,1]) )

	testEnd()
}
doTest("Deeplearning offset Test: deeplearning w/ offset for poisson distribution", test)



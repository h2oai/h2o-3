####### This tests rowsubsetting in H2O ######



###################

test <- function(h) {
	ir = as.h2o(iris,destination_frame = "iris")
	
	frm_h2o = as.data.frame(ir[-1,])
	frm_R =  iris[-1,]
	expect_that(frm_h2o,equals(frm_R))
	
	
}
doTest("Subset dataframe test: row subset in h2o and compare results with R", test)

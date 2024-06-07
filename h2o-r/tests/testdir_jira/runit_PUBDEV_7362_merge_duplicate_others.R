setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# problem with merge.
test <- function() {
    # code from Kuba
    left <- as.h2o(data.frame(topic=c("A","B","C","D"), value=c(12,13,14,15), stringsAsFactors = TRUE)) # [A, 12][B, 13][C, 14][D, 15]
    right <- as.h2o(data.frame(topic=c("Y","B","X","D"), bigValue=c(10000, 20000, 30000, 40000), stringsAsFactors = TRUE)) #[Y, 10000][B, 20000][X, 30000][D, 40000]

    merged <- h2o.merge(right, left, all.x = TRUE, method="radix")
    resultF <- as.h2o(data.frame(topic=c("B","D","X","Y"), bigvalue=c(20000, 40000, 30000, 10000), value = c(13, 15, NA, NA), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"topic"), h2o.arrange(resultF,"topic"))
    
    merged <- h2o.merge(left, right, all.y = TRUE, method="radix")
    resultF <- as.h2o(data.frame(topic=c("B","D","X","Y"), value = c(13, 15, NA, NA), bigvalue=c(20000, 40000, 30000, 10000), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"topic"), h2o.arrange(resultF,"topic"))
 
    merged <- h2o.merge(left, right, all.x = FALSE, all.y = FALSE, method="radix")
    resultF <- as.h2o(data.frame(topic=c("B","D"), value = c(13, 15), bigvalue=c(20000, 40000), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"topic"), h2o.arrange(resultF,"topic"))
    
    merged <- h2o.merge(right, left, all.x = FALSE, all.y = FALSE, method="radix")
    resultF <- as.h2o(data.frame(topic=c("B","D"), bigvalue=c(20000, 40000), value = c(13, 15), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"topic"), h2o.arrange(resultF,"topic"))
    
    # customer code
    left_hf <- as.h2o(data.frame(fruit = c(-177000000, -4000000, 100000000000, 200000000000, 1000000000000),
                                 color = c('red', 'orange', 'yellow', 'red', 'blue'), stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(-177000000), citrus = c(FALSE)))
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    resultF <- as.h2o(data.frame(fruit = c(100000000000,200000000000,1000000000000,-177000000,-4000000), 
                                 color=c('yellow','red','blue','red','orange'), citrus=c(NA, NA, NA, FALSE, NA),
                                 stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    # left frame starts lower
    left_hf <- as.h2o(data.frame(fruit = c(2,3,0,257,256), color = c('red', 'orange', 'yellow', 'red', 'blue'),
     stringsAsFactors = TRUE))
    right_hf <- as.h2o( data.frame(fruit = c(258,518,517,1030,1028,1028,1030,2049),
                                   citrus = c(TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE,TRUE)))
    merged2 <- h2o.merge(left_hf, right_hf, all.y = TRUE) # H2O give wrong answer
    print(merged2)
    resultF <- as.h2o(data.frame(fruit=c(258,517,518,1028,1028,1030,1030,2049),
                                 color=c(NA,NA,NA,NA,NA,NA,NA,NA),
                                 citrus=c(TRUE,FALSE,TRUE,TRUE,FALSE,FALSE,TRUE,TRUE)))
    assertMergeCorrect(h2o.arrange(merged2, "fruit"), h2o.arrange(resultF,"fruit"))
    
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    print(merged)
    resultF <- as.h2o(data.frame(fruit=c(0,2,3,256,257), color=c('yellow','red','orange','blue','red'),
    citrus=c(NA,NA,NA,NA,NA), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    # both frame more or less overlapped
    left_hf <- as.h2o(data.frame(fruit = c(2,3,3,3,0,4,7,9,257,256,518,518,1028), 
                                 color = c('red', 'orange', 'yellow', 'red', 'blue', 'purple', 
                                           'cyan','red', 'orange', 'yellow', 'red', 'blue','negra'),
                                 stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(3,3,3,3,6,8,12,14,258,518,518,517,1030,1028,1028,1030,2049), 
                                  citrus = c(TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE,TRUE, TRUE, FALSE, 
                                             FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE)))
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    resultF <- as.h2o(data.frame(fruit=c(0,2,3,3,3,3,3,3,3,3,3,3,3,3,4,7,9,256,257,518,518,518,518,1028,1028), 
                                 color=c('blue','red','orange','orange','orange','orange','yellow','yellow','yellow',
                                         'yellow','red','red','red','red','purple','cyan','red','yellow','orange',
                                         'red','red','blue','blue','negra','negra'),
                                 citrus=c(NA,NA,TRUE,TRUE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,
                                          NA,NA,NA,NA,NA,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE),
                                 stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    merged <- h2o.merge(left_hf, right_hf, all.x=FALSE, all.y=FALSE)
    resultF <- as.h2o(data.frame(fruit=c(3,3,3,3,3,3,3,3,3,3,3,3,518,518,518,518,1028,1028), 
                                 color=c('orange','orange','orange','orange','yellow','yellow','yellow','yellow',
                                         'red','red','red','red','red','red','blue','blue','negra','negra'),
                                 citrus=c(TRUE,TRUE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,
                                          FALSE,FALSE,FALSE,FALSE,FALSE,FALSE), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged, "fruit"), h2o.arrange(resultF,"fruit"))
    
    # both frame with duplicate keys
    # left frame starts higher and with overlap
    left_hf <- as.h2o(data.frame(fruit = c(2,3,0,257,256,518,1028), color = c('red', 'orange', 'yellow', 'red', 'blue', 'purple', 'cyan'), stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(258,518,517,1030,1028,1030,1035), citrus = c(TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE), stringsAsFactors = TRUE))
    merged <- h2o.merge(left_hf, right_hf, all.x = FALSE, all.y=FALSE)
    resultF <- as.h2o(data.frame(fruit=c(518, 1028), color=c('purple', 'cyan'), citrus=c(TRUE, TRUE), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    # left frame starts higher and no overlap
    left_hf <- as.h2o(data.frame(fruit = c(2,3,0,14,15,16,17), 
                                 color = c('red', 'orange', 'yellow', 'red', 'blue', 'purple', 'cyan'),
                                 stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(258,518,517,1030,1028,1030,1035), 
                                  citrus = c(TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE)))
    merged <- h2o.merge(left_hf, right_hf, all.x = FALSE, all.y=FALSE)
    print(merged)
    expect_true((nrow(merged) == 0 && ncol(merged) == 3), info="Merged frame and expected result are different in size.")
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    resultF <-as.h2o(data.frame(fruit=c(0,2,3,14,15,16,17),
                                color=c('yellow','red','orange','red', 'blue', 'purple', 'cyan'),
                                citrus=c(NA,NA,NA,NA,NA,NA,NA), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    # code from Kuba
    left <- as.h2o(data.frame(topic=c("A","B","C","D"), value=c(12,13,14,15), stringsAsFactors = TRUE)) # [A, 12][B, 13][C, 14][D, 15]
    right <- as.h2o(data.frame(topic=c("Y","B","X","D"), bigValue=c(10000, 20000, 30000, 40000), stringsAsFactors = TRUE)) #[Y, 10000][B, 20000][X, 30000][D, 40000]
    merged <- h2o.merge(left, right, all.y = TRUE, method="radix")
    resultF <- as.h2o(data.frame(topic=c("B","D","X","Y"), value = c(13, 15, NA, NA), bigvalue=c(20000, 40000, 30000, 10000), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"topic"), h2o.arrange(resultF,"topic"))

   
    # example from Neema
    left_hf <- as.h2o(data.frame(fruit = c(-177000000, -4000000, 100000000000, 200000000000, 1000000000000),
                                 color = c('red', 'orange', 'yellow', 'red', 'blue'), stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(-177000000, -177000000),
                                  citrus = c(FALSE)))
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    resultF <- as.h2o(data.frame(fruit = c(100000000000,200000000000,1000000000000,-177000000,-177000000,-4000000), 
                                 color=c('yellow','red','blue','red','red','orange'), citrus=c(NA, NA, NA, FALSE, FALSE, NA), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))

    merged <- h2o.merge(left_hf, right_hf, all.y = TRUE)
    resultF <- as.h2o(data.frame(fruit = c(-177000000,-177000000), 
                                 color=c('red','red'), citrus=c(FALSE, FALSE), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    # more or less overlapped
    left_hf <- as.h2o(data.frame(fruit = c(2,3,0,257,256,518,1028), 
                                 color = c('red', 'orange', 'yellow', 'red', 'blue', 'purple', 'cyan'), stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(2,1,3,258,518,517,1030,1028,1030,1035,0), 
                                  citrus = c(FALSE, TRUE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE, TRUE)))
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    resultF <- as.h2o(data.frame(fruit=c(0,2,3,256,257,518,1028),
                                 color=c('yellow','red','orange','blue','red','purple','cyan'),
                                 citrus=c(TRUE, FALSE, FALSE, NA, NA, TRUE, TRUE), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    # left frame with duplicate keys
    left_hf <- as.h2o(data.frame(fruit = c(2,3,3,3,3,0,257,256,518,1028, 1028, 1028), 
                                 color = c('red', 'orange', 'yellow', 'red', 'blue', 'purple', 'cyan',
                                            'black','red','violet','magenta','cyan'), stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(258,517,1030,1028,1030,2049), 
                                  citrus = c(TRUE, TRUE, FALSE, FALSE, TRUE, FALSE)))
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    resultF <- as.h2o(data.frame(fruit=c(0,2,3,3,3,3,256,257,518,1028,1028,1028),
                                 color=c('purple','red', 'orange', 'yellow', 'red', 'blue','black','cyan','red','violet','magenta','cyan'),
                                 citrus=c(NA,NA,NA,NA,NA,NA,NA,NA,NA,FALSE,FALSE,FALSE), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
    
    # rite frame with duplicate keys
    left_hf <- as.h2o(data.frame(fruit = c(2,3,0,257,256,518,1028), 
                                 color = c('red', 'orange', 'yellow', 'red', 'blue', 'purple', 'cyan'), stringsAsFactors = TRUE))
    right_hf <- as.h2o(data.frame(fruit = c(3,3,3,3,258,518,517,1030,1028,1028,1030,2049), 
                                  citrus = c(TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE,TRUE, TRUE, FALSE, FALSE, TRUE)))
    merged <- h2o.merge(left_hf, right_hf, all.x = TRUE)
    resultF <- as.h2o(data.frame(fruit=c(0,2,3,3,3,3,256,257,518,1028,1028), 
                                 color=c('yellow','red','orange','orange','orange','orange','blue','red','purple','cyan','cyan'),
                                 citrus=c(NA, NA, TRUE, TRUE, FALSE, FALSE, NA, NA, FALSE, TRUE, FALSE), stringsAsFactors = TRUE))
    assertMergeCorrect(h2o.arrange(merged,"fruit"), h2o.arrange(resultF,"fruit"))
}

assertMergeCorrect <- function(mergedFrame, resultF) {
    colnames(mergedFrame) <- colnames(resultF)
    print("Merged Frame")
    print(mergedFrame,n=nrow(mergedFrame))
    print("Expected Frame")
    print(resultF,n=nrow(resultF))
    compareFrames(mergedFrame, resultF, prob=1, enum2String=TRUE)
}

doTest("PUBDEV-7362: check merge", test)

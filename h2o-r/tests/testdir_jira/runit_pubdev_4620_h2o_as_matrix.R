setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
if (!("dplyr" %in% rownames(installed.packages())))
  install.packages("https://cran.rstudio.com/bin/macosx/el-capitan/contrib/3.4/dplyr_0.7.3.tgz", repos=NULL, type="source")
library(dplyr)
if (!("text2vec" %in% rownames(installed.packages())))
  install.packages('https://cran.rstudio.com/bin/macosx/el-capitan/contrib/3.4/text2vec_0.5.0.tgz', repos=NULL, type="source")
library(text2vec)
if ("slam" %in% rownames(installed.packages()))
library(slam)
if ("data.table" %in% rownames(installed.packages()))
library(data.table)

# In PUBDEV-4620, it is stated that our code does not work properly if the response column is not the first column.
# I am going to generate 4 different data frames with the response column at column index 0, 1, 2 or 3.
# Next, I will build a GBM model with the same seed and compare the model.  They should yield similar results if
# not exactly the same.

check.as.h2o<- function() {
  h2o.removeAll()
  # the only thing different between the data frames are the column number of output.  It should work with all positions
  dataframeOut1 <- data_frame(output = c('win','lose','win','lose','win','win','win','win','win','lose',
                                         'lose','lose','lose','win','win','win','lose','win','lose','lose'),
                              id = c(1:20),
                              text = c("this is a this", 
                                       "this is another",
                                       'hello',
                                       'what???',
                                       "Wendy Wong", 
                                       "is great", 
                                       "intelligence", 
                                       "strong and healthy",
                                       "and positive", 
                                       "and crash the world",
                                       "for thinking otherwise",
                                       "now that I have", 
                                       "to think ", 
                                       "of another", 
                                       "six sentences",
                                       "what a chore!", 
                                       "when would I be rich",
                                       "next year?", 
                                       "no more ", 
                                       "than three years"),
                              value = c(200,400,120,300,320,110,430,903,703,390,123, 300, 129, 213, 432, 135, 675, 290, 182, 300))
  
  
  dataframeOut4 <- data_frame(id = c(1:20),
                              text = c("this is a this", 
                                       "this is another",
                                       'hello',
                                       'what???',
                                       "Wendy Wong", 
                                       "is great", 
                                       "intelligence", 
                                       "strong and healthy",
                                       "and positive", 
                                       "and crash the world",
                                       "for thinking otherwise",
                                       "now that I have", 
                                       "to think ", 
                                       "of another", 
                                       "six sentences",
                                       "what a chore!", 
                                       "when would I be rich",
                                       "next year?", 
                                       "no more ", 
                                       "than three years"),
  value = c(200,400,120,300,320,110,430,903,703,390,123, 300, 129, 213, 432, 135, 675, 290, 182, 300),
  output = c('win','lose','win','lose','win','win','win','win','win','lose',
             'lose','lose','lose','win','win','win','lose','win','lose','lose'))
  
  dataframeOut2 <- data_frame(id = c(1:20),
                           output = c('win','lose','win','lose','win','win','win','win','win','lose',
                                      'lose','lose','lose','win','win','win','lose','win','lose','lose'),
                           text = c("this is a this", 
                                    "this is another",
                                    'hello',
                                    'what???',
                                    "Wendy Wong", 
                                    "is great", 
                                    "intelligence", 
                                    "strong and healthy",
                                    "and positive", 
                                    "and crash the world",
                                    "for thinking otherwise",
                                    "now that I have", 
                                    "to think ", 
                                    "of another", 
                                    "six sentences",
                                    "what a chore!", 
                                    "when would I be rich",
                                    "next year?", 
                                    "no more ", 
                                    "than three years"),
                          value = c(200,400,120,300,320,110,430,903,703,390,123, 300, 129, 213, 432, 135, 675, 290, 182, 300))
  dataframeOut3 <- data_frame(id = c(1:20),
                              text = c("this is a this", 
                                       "this is another",
                                       'hello',
                                       'what???',
                                       "Wendy Wong", 
                                       "is great", 
                                       "intelligence", 
                                       "strong and healthy",
                                       "and positive", 
                                       "and crash the world",
                                       "for thinking otherwise",
                                       "now that I have", 
                                       "to think ", 
                                       "of another", 
                                       "six sentences",
                                       "what a chore!", 
                                       "when would I be rich",
                                       "next year?", 
                                       "no more ", 
                                       "than three years"),
                              output = c('win','lose','win','lose','win','win','win','win','win','lose',
                                         'lose','lose','lose','win','win','win','lose','win','lose','lose'),
                              value = c(200,400,120,300,320,110,430,903,703,390,123, 300, 129, 213, 432, 135, 675, 290, 182, 300))
  
  train1 <- prepareFrame(dataframeOut1)
  train2 <- prepareFrame(dataframeOut2)
  train3 <- prepareFrame(dataframeOut3)
  train4 <- prepareFrame(dataframeOut4)


  # Train any H2O model (e.g GBM)
  mymodel1 <- runModel(train1)
  pred1 <- predict(mymodel1, train1)
  summary(mymodel1)
  
  mymodel2 <- runModel(train2)
  pred2 <- predict(mymodel2, train2)
  summary(mymodel2)
  
  mymodel3 <- runModel(train3)
  pred3 <- predict(mymodel3, train3)
  summary(mymodel3)
  
  mymodel4 <- runModel(train4)
  pred4 <- predict(mymodel4, train4)
  summary(mymodel4)
  
  # compare the prediction frames, they should be very close if not equal
  compareFrames(pred1[1:nrow(pred1),2:3], pred2[1:nrow(pred2),2:3], prob=1, tolerance=1e-10)
  compareFrames(pred1[1:nrow(pred1),2:3], pred3[1:nrow(pred3),2:3], prob=1, tolerance=1e-10)
  compareFrames(pred1[1:nrow(pred1),2:3], pred4[1:nrow(pred4),2:3], prob=1, tolerance=1e-10)
}

runModel<-function(trainFrame) {
  return(h2o.gbm(y="y", training_frame=trainFrame, distribution='bernoulli', seed=1))
}
prepareFrame<-function(dataframe) {
  prep_fun = tolower
  tok_fun = word_tokenizer
  
  #create the tokens
  train_tokens = dataframe$text %>% prep_fun %>% tok_fun
  
  it_train = itoken(train_tokens)
  vocab = create_vocabulary(it_train)
  vectorizer = vocab_vectorizer(vocab)
  dtm_train = create_dtm(it_train, vectorizer)
  
  train <- as.h2o(dtm_train)
  train$y <- as.h2o(dataframe$output)
  
  return(train)
}

doTest("PUBDEV-4620: check as.h2o.matrix() with response in different columns", check.as.h2o)
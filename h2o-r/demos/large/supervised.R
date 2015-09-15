#The following function runs all the four algos on datasets with categorical/continuous response and reports performance measures
#
#Note progress bar not working for glm shows, 0 throughout and then 100 when job completes 
# related jiras- PUBDEV-782,  PUBDEV-783,  PUBDEV-784
# 

if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }
install.packages("h2o", type="source", repos=(c("file:///users/arno/h2o-dev/h2o-r/R")))



library(h2o)
h <- h2o.init(ip="mr-0xd1", port=53322)

runAll = function(data_path,split_ratio,response,predictors,flag,test_file_path){ 
  #remove all keys from h2o prior to running the function
  h2o.rm(keys=as.character(grep(pattern = "", x = h2o.ls(h)$key,value=T),conn = h)); 
  
  print(paste("Parse : ",data_path,sep=''));
  time = system.time(data  <- h2o.importFile(data_path,conn=h,key="data.hex"));
  print(paste("time to parse the file:",time[3]))
  
  if(flag%in% c("B","M") ){
    data[,response] <- as.factor(data[,response]);
  }
  if(is.null(test_file_path) ){
    rnd <- h2o.runif(data, 1234)
    train <- h2o.assign(data[rnd < split_ratio,], key="train.hex")
    test <- h2o.assign(data[rnd >= split_ratio,], key="test.hex")
  }else{
    train = h2o.assign(data,key="train.hex")  #ssign does not work on kdd'98 so parsing the file again
    #train <- h2o.importFile(data_path,conn=h,key="train.hex");
    test <- h2o.importFile(test_file_path,conn=h,key="test.hex");
    if(flag%in% c("B","M") ){
      train[,response] <- as.factor(train[,response])
      test[,response] <- as.factor(test[,response])
    }
  }
  print(paste("Dimension of train set: ",dim(train),sep=''));
  print(paste("Dimension of test set: ",dim(test),sep=''));
  print("Summary of response column: ")
  print(summary(train[,response]));
  h2o.rm(keys="data.hex",conn = h)
  print(h2o.ls(h))
  
  myY = response
  myX = predictors
  
  #function for performance measure
  perf = function(my_model,newdata,flag){
    perf = h2o.performance(model=my_model,data=newdata)
    print(perf)
    #     if( flag%in% c("B","M")){
    #       if(flag == "B"){
    #         print(paste("AUC : ",h2o.auc(perf),sep =''))
    #       }  
    #       print(paste("LogLoss : ",h2o.logloss(perf),sep ='')) 
    #       print("Confusion Matrix :")
    #       print(h2o.confusionMatrix(perf))
    #     }else{
    #       print(paste("MeanSquaredError : ",h2o.mse(perf),sep =''))
    #     }
  }
  
  #algo = c("h2o.gbm","h2o.glm","h2o.randomForest","h2o.deeplearning")
  #eval(expr) does not work so will have to write a model object for each algo
  
  print(paste("Run GLM:"))
  if(flag =="B"){
    #glm does not chk the type of the response column, will have to specifically specify the family as binomial
    time = system.time(my_model <- h2o.glm(x=myX,y=myY,training_frame=train,validation_frame=test,family="binomial"))
    print(paste("Time taken to run GLM:",time[3]))
    perf(my_model,test,flag)
  }else{
    time = system.time(my_model <- h2o.glm(x=myX,y=myY,training_frame=train,validation_frame=test))
    print(paste("Time taken to run GLM:",time[3]))
    perf(my_model,test,flag ="G") # bec no multinomial in GLM instead runs gaussian
  }
  
  print(paste("Run GLM with lambda search:"))
  if(flag =="B"){
    #glm does not chk the type of the response column, will have to specifically specify the family as binomial
    time = system.time(my_model <- h2o.glm(x=myX,y=myY,training_frame=train,validation_frame=test,family="binomial",lambda_search=T))
    print(paste("Time taken to run GLM with lambda search:",time[3]))
    perf(my_model,test,flag)
  }else{
    time = system.time(my_model <- h2o.glm(x=myX,y=myY,training_frame=train,lambda_search=T))
    print(paste("Time taken to run GLM with lambda search:",time[3]))
    perf(my_model,test,flag ="G") # bec no multinomial in GLM instead runs gaussian
  }
  
  print(paste("Run DRF:"))
  time = system.time(my_model <- h2o.randomForest(x=myX,y=myY,training_frame=train,validation_frame=test))
  print(paste("Time taken to run DRF:",time[3]))
  perf(my_model,test,flag)
  
  print(paste("Run GBM:"))
  time = system.time(my_model <- h2o.gbm(x=myX,y=myY,training_frame=train,validation_frame=test))
  print(paste("Time taken to run GBM:",time[3]))
  perf(my_model,test,flag)
  
  print(paste("Run DL:"))
  time = system.time(my_model <- h2o.deeplearning(x=myX,y=myY,training_frame=train,validation_frame=test))
  print(paste("Time taken to run DL:",time[3]))
  perf(my_model,test,flag)
}


smokeTest = T

if (smokeTest) {
  path <- "/home/arno/h2o-dev/"
    
  runAll(  
    data_path= paste0(path,"smalldata/logreg/prostate.csv"),
    split_ratio = .8,
    response = 2,
    predictors = 3:8,
    flag = "B",
    test_file_path =NULL)
  
  runAll(  
    data_path= paste0(path,"smalldata/logreg/prostate.csv"),
    split_ratio = .8,
    response = 4,
    predictors = c(2:3,5:8),
    flag = "M",
    test_file_path =NULL)
  
  runAll(  
    data_path= paste0(path,"smalldata/logreg/prostate.csv"),
    split_ratio = .8,
    response = 3,
    predictors = c(1:2,4:8),
    flag = "G",
    test_file_path =NULL)
  
} 

if (!smokeTest) {
  
  #-------------------------------------Binary Response
  
  
  runAll(  
    data_path= "hdfs://mr-0xd6.0xdata.loc/datasets/HIGGS.csv",
    split_ratio = .8,
    response = 1,
    predictors = 2:22,
    flag = "B",
    test_file_path =NULL)
  
  runAll(  
    data_path= "hdfs://mr-0xd6.0xdata.loc/datasets/chicagoCrimes.csv",
    split_ratio = .8,
    response = 9,
    predictors = c(1,2,3,4,5,6,7,8,10,11,12,13,14,15,16,17,18,19,20,21,22),
    flag = "B",
    test_file_path =NULL)
  
  runAll(  
    data_path=  "hdfs://mr-0xd6.0xdata.loc/datasets/airlines/airlines_all.csv",
    split_ratio = .8,
    response = 31,
    predictors = c(1,2,3,4,6,8,9,10,13,17,18,19,22,24),
    flag = "B",
    test_file_path =NULL)
  
  #---------------------------------------Multinomial Response (glm will assume continuous)
  
  runAll(  
    data_path= "hdfs://mr-0xd6.0xdata.loc/datasets/mnist/train.csv.gz",
    split_ratio = .8,
    response = 785,
    predictors = 1:784,
    flag = "M",
    test_file_path= "hdfs://mr-0xd6.0xdata.loc/datasets/mnist/test.csv.gz")
  
  runAll(  
    data_path= "hdfs://mr-0xd6.0xdata.loc/datasets/covtype.data",
    split_ratio = .8,
    response = 55,
    predictors = 1:54,
    flag = "M",
    test_file_path = NULL)
  
  #-----------------------------------------Continuous Response
  
  runAll(  
    data_path= "hdfs://mr-0xd6.0xdata.loc/datasets/cup98LRN_z.csv",
    split_ratio = .8,
    response = 473,
    predictors = c(7,9,14,16,18,41,42,65,70,72,90,159,188,239,251,269,288,354,363,371,372,390,410,427,451,458,465,466),
    flag = "G",
    test_file_path= "hdfs://mr-0xd6.0xdata.loc/datasets/cup98VAL_z.csv")
  
  runAll(  
    data_path= "hdfs://mr-0xd6.0xdata.loc/datasets/citibike-nyc/2014-07.csv",
    split_ratio = .8,
    response = 1,
    predictors = 2:15,
    flag = "G",
    test_file_path ="/hdfs://mr-0xd6.0xdata.loc/datasets/citibike-nyc/2014-08.csv")
  
  #-----------------------------------------
  
}

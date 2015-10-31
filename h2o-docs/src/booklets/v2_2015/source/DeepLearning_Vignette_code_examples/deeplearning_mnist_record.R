#World Record run used epochs=8000

model <- h2o.deeplearning(x=x, y=y,
 	training_frame=train_hex, validation_frame=test_hex, 
 	activation="RectifierWithDropout", 
 	hidden=c(1024,1024,2048), epochs=10, 
 	input_dropout_ratio=0.2, l1=1e-5, max_w2=10,
 	train_samples_per_iteration=-1, 
 	classification_stop=-1, stopping_rounds=0)

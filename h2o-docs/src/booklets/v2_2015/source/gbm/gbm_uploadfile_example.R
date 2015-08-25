weather.hex <- h2o.uploadFile(h2o_server, path  = "weather.csv", header  = TRUE, sep = ",", destination_frame = "weather.hex")

# To see a brief summary of the data, run the following command.
summary(weather.hex)
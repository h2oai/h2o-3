library(h2o)
h2o.init()
weather.hex <- h2o.uploadFile(path  = h2o:::.h2o.locate("smalldata/junit/weather.csv"), header  = TRUE, sep = ",", destination_frame = "weather.hex")

# To see a brief summary of the data, run the following command.
summary(weather.hex)

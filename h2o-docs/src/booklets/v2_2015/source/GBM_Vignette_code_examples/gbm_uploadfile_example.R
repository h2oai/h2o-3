library(h2o)
h2o.init()
weather.hex <- h2o.uploadFile(path  = h2o:::.h2o.locate("smalldata/junit/weather.csv"), header  = TRUE, sep = ",", destination_frame = "weather.hex")

# Get a summary of the data
summary(weather.hex)

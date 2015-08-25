weather_hex = h2o.uploadFile(path = "weather.csv", header = TRUE, sep = ",", key = "weather.hex")

# To see a brief summary of the data, run the following command.
weather_hex.describe()

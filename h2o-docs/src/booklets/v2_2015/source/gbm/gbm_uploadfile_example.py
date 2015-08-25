weather_hex = h2o.import_file(path = "weather.csv")

# To see a brief summary of the data, run the following command.
weather_hex.describe()

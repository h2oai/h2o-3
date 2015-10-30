import h2o

h2o.init()
weather_hex = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/junit/weather.csv")

# To see a brief summary of the data, run the following command.
weather_hex.describe()

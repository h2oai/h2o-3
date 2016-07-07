from h2o.utils.shared_utils import _locate # private function. used to find files within h2o git project directory.
weather_path = _locate("smalldata/chicago/chicagoAllWeather.csv")
census_path = _locate("smalldata/chicago/chicagoCensus.csv")
crimes_path = _locate("smalldata/chicago/chicagoCrimes10k.csv.zip")

print("Import and Parse weather data")
weather = h2o.import_file(path=weather_path, col_types = ["time"] + ["numeric"]*6)
weather.drop("date")
weather.describe()

print("Import and Parse census data")
census = h2o.import_file(path=census_path, col_types = ["numeric", "enum"] + ["numeric"]*7)
census.describe()

print("Import and Parse crimes data")
crimes = h2o.import_file(path=crimes_path)
crimes.describe()

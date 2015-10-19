import h2o
from h2o.h2o import _locate # private function. used to find files within h2o git project directory.

h2o.init()
weather_hex = h2o.import_file(path = _locate("smalldata/junit/weather.csv"))

# To see a brief summary of the data, run the following command.
weather_hex.describe()

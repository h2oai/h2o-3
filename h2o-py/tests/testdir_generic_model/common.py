try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO
import sys


# Captures the output of an action
# Required to work both in Py 2 & 3
class Capturing(list):
    def __enter__(self):
        self._stdout = sys.stdout
        sys.stdout = self._stringio = StringIO()
        return self

    def __exit__(self, *args):
        self.extend(self._stringio.getvalue().splitlines())
        del self._stringio
        sys.stdout = self._stdout


# Output checks common for multiple algorithms

def compare_multinomial_output(original, generic):
    original = original[original.find("'Model Summary: '"):].replace('ModelMetricsMultinomial: gbm', '').strip()
    generic = generic[generic.find("'Model Summary: '"):].replace('ModelMetricsMultinomialGeneric: generic', '').strip()
    assert generic == original


def compare_binomial_output(original, generic):
    original = original[original.find("'Model Summary: '"):].replace('ModelMetricsBinomial: gbm', '').strip()
    generic = generic[generic.find("'Model Summary: '"):].replace('ModelMetricsBinomialGeneric: generic', '').strip()
    assert generic == original


def compare_regression_output(original, generic):
    original = original[original.find("'Model Summary: '"):].replace('ModelMetricsRegression: gbm', '').strip()
    generic = generic[generic.find("'Model Summary: '"):].replace('ModelMetricsRegression: generic', '').strip()
    assert generic == original

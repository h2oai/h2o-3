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

def compare_output(original, generic, strip_part, algo_name, generic_algo_name):
    original = original[original.find(strip_part):].replace(algo_name, '').strip()
    generic = generic[generic.find(strip_part):].replace(generic_algo_name, '').strip()
    assert generic == original


def drop_model_parameters_from_printout(printout_of_the_model):
    return printout_of_the_model.split(', \'Model parameters', 1)[0]+']'

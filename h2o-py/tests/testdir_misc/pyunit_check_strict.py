from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o, inspect
from tests import pyunit_utils


def check_strict():
  args, varargs, keywords, defaults = inspect.getargspec(h2o.init)
  assert dict(list(zip(args[-len(defaults):], defaults)))['strict_version_check'], "strict version checking got turned off! TURN IT BACK ON NOW YOU JERK!"


if __name__ == "__main__":
  pyunit_utils.standalone_test(check_strict)
else:
  check_strict()
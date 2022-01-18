def _get_numpy(function_name):
    try:
        import numpy as np
        return np
    except ImportError:
        print("`numpy` library is required for function {0}!".format(function_name))
        return None


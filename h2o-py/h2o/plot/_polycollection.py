def get_polycollection(server, raise_if_not_available=False):
    try:
        from matplotlib.collections import PolyCollection as polycoll
        return polycoll
    except ImportError as e:
        if raise_if_not_available:
            raise e
        print("`matplotlib` library is required for this function!")
        return None
import h2o
from h2o.h2o import tokenize_custom_jvm_args



def pubdev_5976():

    # Expect empty result after the only token is skipped (tokens without '-'are skipped)
    tokens = tokenize_custom_jvm_args(["ea"])
    assert len(tokens) == 0

    # Only one token has '-' at the beginning, expect length 1
    tokens = tokenize_custom_jvm_args(["-ea", "ea"])
    assert len(tokens) == 1

    # Both tokens verified correctly
    tokens = tokenize_custom_jvm_args(["-ea", "-abcd"])
    assert len(tokens) == 2

    # Both tokens verified correctly
    tokens = tokenize_custom_jvm_args([])
    assert len(tokens) == 0

    # None results in empty list of custom args
    tokens = tokenize_custom_jvm_args(None)
    assert len(tokens) == 0

if __name__ == "__main__":
    pubdev_5976()
else:
    pubdev_5976()
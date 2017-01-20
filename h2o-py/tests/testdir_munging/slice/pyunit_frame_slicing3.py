#!/usr/env/bin python
# -*- encoding: utf-8 -*-
from __future__ import division, print_function

import h2o


def test_slice3():
    fr = h2o.H2OFrame.from_python([
        [1,  -1,  0],
        [2,  -2, 10],
        [4,  -3, 50],
        [8,  -4, -7],
        [16, -5, 12],
        [32, -6, 99],
        [64, -7,  1],
    ], column_names=["a", "b", "c"])

    check_frame(fr[::2, :], [[1, -1, 0], [4, -3, 50], [16, -5, 12], [64, -7, 1]])
    check_frame(fr[1:3, :2], [[2, -2], [4, -3]])
    check_frame(fr[:100, 1], [[-1], [-2], [-3], [-4], [-5], [-6], [-7]])
    check_frame(fr[:, 1], [[-1], [-2], [-3], [-4], [-5], [-6], [-7]])
    check_frame(fr[-2:, :], [[32, -6, 99], [64, -7, 1]])
    check_frame(fr[-3:-1, -1:], [[12], [99]])
    check_frame(fr[-10:-6, :], [[1, -1, 0]])
    # check_frame(fr[-3::2, ::2], [[16, 12], [64, 1]])

    fr[:, 1] = 0
    check_frame(fr[:, 1], [[0]] * 7)


def check_frame(actual, expected):
    exp_shape = (len(expected), len(expected[0]))
    assert actual.shape == exp_shape, "Incorrect frame size: actual = %r vs expected = %r" % (actual.shape, exp_shape)
    data = [[int(e) for e in row]
            for row in actual.as_data_frame(False, False)]
    assert actual.shape == exp_shape, "Incorrect frame size: actual = %r vs expected = %r" % (actual.shape, exp_shape)
    assert data == expected, "Frames do not coincide:\nActual: %r\nExpected: %r\n" % (data, expected)


if __name__ == "__main__":
    h2o.init()

test_slice3()

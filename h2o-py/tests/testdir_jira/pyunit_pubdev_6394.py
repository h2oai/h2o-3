# -*- coding: utf-8 -*-
from h2o import H2OFrame
from tests import pyunit_utils


def pubdev_6394():
    # JUnit tests are to be found in RapidsTest class
    
    data = [['location'],
             ['Ｘ県 Ａ市'],
             ['Ｘ県 Ｂ市'],
             ['Ｘ県 Ｂ市'],
             ['Ｙ県 Ｃ市'],
             ['Ｙ県 Ｃ市']]

    original_frame = H2OFrame(data, header=True, column_types=['enum'])
    
    assert original_frame.type('location') == 'enum'
    assert original_frame.categories() == [u'Ｘ県 Ａ市', u'Ｘ県 Ｂ市', u'Ｙ県 Ｃ市']
    
    # Reduce cardinality of 'location' column to 2 by reducing existing categorical values to ['Ｘ県','Y県']
    expected_categories = [u'Ｘ県', u'Ｙ県']
    transformed_frame = original_frame['location'].gsub(' .*', '')
    print(transformed_frame)
    
    assert transformed_frame.ncols == 1
    assert transformed_frame.nrows == original_frame.nrows
    assert transformed_frame.type('C1') == 'enum'
    assert transformed_frame['C1'].categories() == expected_categories
    
    # Test gsub without changing the cardinality

    data = [['location'],
            ['ab'],
            ['ac'],
            ['ad'],
            ['ae'],
            ['af']]

    original_frame = H2OFrame(data, header=True, column_types=['enum'])
    assert original_frame.type('location') == 'enum'
    assert original_frame.categories() == ['ab', 'ac', 'ad', 'ae', 'af']

    expected_categories = ['b', 'c', 'd', 'e', 'f']
    transformed_frame = original_frame['location'].gsub('a', '')
    print(transformed_frame)
    
    assert transformed_frame.ncols == 1
    assert transformed_frame.nrows == original_frame.nrows
    assert transformed_frame.type('C1') == 'enum'
    assert transformed_frame['C1'].categories() == expected_categories


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6394)
else:
    pubdev_6394()

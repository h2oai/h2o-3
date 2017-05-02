def python_vignette():
    from tests import pyunit_utils
    story1 = [pyunit_utils.locate("Python_Vignette_code_examples/python_add_common_column_to_orig_dataframe.py")]
    story2 = [pyunit_utils.locate("Python_Vignette_code_examples/python_apply_funct_to_columns.py")]
    story3 = [pyunit_utils.locate("Python_Vignette_code_examples/python_apply_funct_to_rows.py")]
    story4 = [pyunit_utils.locate("Python_Vignette_code_examples/python_change_missing_values_to_new_value.py")]
    story5 = [pyunit_utils.locate("Python_Vignette_code_examples/python_column_is_anyfactor.py")]
    story6 = [pyunit_utils.locate("Python_Vignette_code_examples/python_combine_frames_append_one_as_columns.py")]
    story7 = [pyunit_utils.locate("Python_Vignette_code_examples/python_combine_frames_append_one_as_rows.py")]
    story8 = [pyunit_utils.locate("Python_Vignette_code_examples/python_create_categorical_interaction_features.py")]
    story9 = [pyunit_utils.locate("Python_Vignette_code_examples/python_create_frame_from_ordered_dict.py")]
    story10 = [pyunit_utils.locate("Python_Vignette_code_examples/python_create_frame_from_python_dict.py")]
    story11 = [pyunit_utils.locate("Python_Vignette_code_examples/python_create_frame_from_python_list.py")]
    story12 = [pyunit_utils.locate("Python_Vignette_code_examples/python_create_frame_from_python_tuple.py")]
    story13 = [pyunit_utils.locate("Python_Vignette_code_examples/python_create_frame_with_missing_elements.py")]
    story14 = [pyunit_utils.locate("Python_Vignette_code_examples/python_descriptive_stats_entire_frame.py")]
    story15 = [pyunit_utils.locate("Python_Vignette_code_examples/python_descriptive_stats_single_column.py")]
    story16 = [pyunit_utils.locate("Python_Vignette_code_examples/python_determine_string_count_in_element.py")]
    story17 = [pyunit_utils.locate("Python_Vignette_code_examples/python_display_column_names.py")]
    story18 = [pyunit_utils.locate("Python_Vignette_code_examples/python_display_column_types.py")]
    story19 = [pyunit_utils.locate("Python_Vignette_code_examples/python_display_compressiondistributionsummary.py")]
    story20 = [pyunit_utils.locate("Python_Vignette_code_examples/python_display_day_of_month.py")]
    story21 = [pyunit_utils.locate("Python_Vignette_code_examples/python_display_day_of_week.py")]
    story22 = [pyunit_utils.locate("Python_Vignette_code_examples/python_find_missing_data_in_frame.py")]
    story23 = [pyunit_utils.locate("Python_Vignette_code_examples/python_find_rows_missing_data_for_a_column.py")]
    story24 = [pyunit_utils.locate("Python_Vignette_code_examples/python_group_and_apply_function.py")]
    story25 = [pyunit_utils.locate("Python_Vignette_code_examples/python_group_by_multiple_columns.py")]
    story26 = [pyunit_utils.locate("Python_Vignette_code_examples/python_histogramming_data.py")]
    story27 = [pyunit_utils.locate("Python_Vignette_code_examples/python_initializeh2o_example.py")]
    story28 = [pyunit_utils.locate("Python_Vignette_code_examples/python_injest_time_natively.py")]
    story29 = [pyunit_utils.locate("Python_Vignette_code_examples/python_join_results.py")]
    story30 = [pyunit_utils.locate("Python_Vignette_code_examples/python_merge_frames_by_column_name.py")]
    story31 = [pyunit_utils.locate("Python_Vignette_code_examples/python_replace_first_l_with_x.py")]
    story32 = [pyunit_utils.locate("Python_Vignette_code_examples/python_retain_common_categories.py")]
    story33 = [pyunit_utils.locate("Python_Vignette_code_examples/python_select_column_index.py")]
    story34 = [pyunit_utils.locate("Python_Vignette_code_examples/python_select_column_name.py")]
    story35 = [pyunit_utils.locate("Python_Vignette_code_examples/python_select_multiple_column_names.py")]
    story36 = [pyunit_utils.locate("Python_Vignette_code_examples/python_select_multiple_columns_by_index.py")]
    story37 = [pyunit_utils.locate("Python_Vignette_code_examples/python_select_rows_boolean.py")]
    story38 = [pyunit_utils.locate("Python_Vignette_code_examples/python_select_rows_by_slicing.py")]
    story39 = [pyunit_utils.locate("Python_Vignette_code_examples/python_show_column_types.py")]
    story40 = [pyunit_utils.locate("Python_Vignette_code_examples/python_split_using_regex.py")]
    story41 = [pyunit_utils.locate("Python_Vignette_code_examples/python_view_categorical_levels_in_column.py")]
    story42 = [pyunit_utils.locate("Python_Vignette_code_examples/python_view_top_and_bottom_of_frame.py")]

    approved_py_code_examples = story1+story2+story3+story4+story5+story6+story7+story8+story9+story10+story11+story12+story13+story14+story15+story16+story17+story18+story19+story20+story21+story22+story23+story24+story25+story26+story27+story28+story29+story30+story31+story32+story33+story34+story35+story36+story37+story38+story39+story40+story41+story42

    pybooklet_utils.check_code_examples_in_dir(approved_py_code_examples,
                                               pyunit_utils.locate("Python_Vignette_code_examples"))

    pybooklet_utils.check_story("story1",story1)
    pybooklet_utils.check_story("story2",story2)
    pybooklet_utils.check_story("story3",story3)
    pybooklet_utils.check_story("story4",story4)
    pybooklet_utils.check_story("story5",story5)
    pybooklet_utils.check_story("story6",story6)
    pybooklet_utils.check_story("story7",story7)
    pybooklet_utils.check_story("story8",story8)
    pybooklet_utils.check_story("story9",story9)
    pybooklet_utils.check_story("story10",story10)
    pybooklet_utils.check_story("story11",story11)
    pybooklet_utils.check_story("story12",story12)
    pybooklet_utils.check_story("story13",story13)
    pybooklet_utils.check_story("story14",story14)
    pybooklet_utils.check_story("story15",story15)
    pybooklet_utils.check_story("story16",story16)
    pybooklet_utils.check_story("story17",story17)
    pybooklet_utils.check_story("story18",story18)
    pybooklet_utils.check_story("story19",story19)
    pybooklet_utils.check_story("story20",story20)
    pybooklet_utils.check_story("story21",story21)
    pybooklet_utils.check_story("story22",story22)
    pybooklet_utils.check_story("story23",story23)
    pybooklet_utils.check_story("story24",story24)
    pybooklet_utils.check_story("story25",story25)
    pybooklet_utils.check_story("story26",story26)
    pybooklet_utils.check_story("story27",story27)
    pybooklet_utils.check_story("story28",story28)
    pybooklet_utils.check_story("story29",story29)
    pybooklet_utils.check_story("story30",story30)
    pybooklet_utils.check_story("story31",story31)
    pybooklet_utils.check_story("story32",story32)
    pybooklet_utils.check_story("story33",story33)
    pybooklet_utils.check_story("story34",story34)
    pybooklet_utils.check_story("story35",story35)
    pybooklet_utils.check_story("story36",story36)
    pybooklet_utils.check_story("story37",story37)
    pybooklet_utils.check_story("story38",story38)
    pybooklet_utils.check_story("story39",story39)
    pybooklet_utils.check_story("story40",story40)
    pybooklet_utils.check_story("story41",story41)
    pybooklet_utils.check_story("story42",story42)

python_vignette()
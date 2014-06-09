Steam.FrameInspectionView = (_, _frame) ->
  title: _frame.key
  key: _frame.key
  columns: _frame.column_names
  columnCount: "(#{_frame.column_names.length})"
  isRawFrame: _frame.is_raw_frame
  template: 'frame-inspection-view'



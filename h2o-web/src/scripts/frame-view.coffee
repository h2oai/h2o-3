Steam.FrameView = (_, _frame) ->
  createRow = (attribute, columns) ->
    header: attribute
    cells: map columns, (column) ->
      switch column.type
        when 'enum'
          switch attribute
            when 'min', 'max', 'mean', 'sigma'
              '-'
            when 'cardinality'
              column.domain.length
            else
              column[attribute]
        else
          switch attribute
            when 'cardinality'
              '-'
            else
              column[attribute]

  createDataRow = (offset, index, columns) ->
    header: "Row #{offset + index}"
    cells: map columns, (column) ->
      switch column.type
        when 'uuid'
          'TODO'
        when 'enum'
          column.domain[column.data[index]]
        else
          value = column.data[index]
          if value is 'NaN' then '-' else value #TODO handle precision

  createSummaryRows = (columns) ->
    rows = []
    for attribute in words 'type min max mean sigma missing cardinality'
      rows.push createRow attribute, columns
    rows
  
  createDataRows = (offset, rowCount, columns) ->
    rows = []
    for index in [0 ... rowCount]
      rows.push createDataRow offset, index, columns
    rows

  createFrameTable = (offset, rowCount, columns) ->
    header: createRow 'label', columns
    summaryRows: createSummaryRows columns
    dataRows: createDataRows offset, rowCount, columns

  data: _frame
  key: _frame.key.name
  timestamp: _frame.creation_epoch_time_millis
  title: _frame.key.name
  columns: _frame.column_names
  table: createFrameTable _frame.off, _frame.len, _frame.columns
  isRawFrame: _frame.is_raw_frame
  parseUrl: "/2/Parse2.query?source_key=#{encodeURIComponent _frame.key}"
  dispose: ->
  template: 'frame-view'


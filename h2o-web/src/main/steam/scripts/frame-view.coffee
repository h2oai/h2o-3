significantDigitsBeforeDecimal = (value) -> 1 + Math.floor Math.log(Math.abs value) / Math.LN10

formatToSignificantDigits = (digits, value) ->
  if value is 0
    0
  else
    sd = significantDigitsBeforeDecimal value
    if sd >= digits
      value.toFixed 0
    else
      magnitude = Math.pow 10, digits - sd
      Math.round(value * magnitude) / magnitude

formatTime = d3.time.format '%Y-%m-%d %H:%M:%S'
formatDateTime = (time) -> if time then formatTime new Date time else '-'

formatReal = do ->
  __formatFunctions = {}
  getFormatFunction = (precision) ->
    if precision is -1
      identity
    else
      __formatFunctions[precision] or __formatFunctions[precision] = d3.format ".#{precision}f"

  (precision, value) ->
    (getFormatFunction precision) value

Steam.FrameView = (_, _frame) ->
  createMinMaxInspection = (column, attribute) ->
    [ div, h1, table, tbody, tr, td ] = geyser.generate words 'div h1 table.y-monospace.table.table-condensed tbody tr td'
    div [
      h1 "#{column.label} - #{attribute}"
      table tbody map column[attribute], (value, i) -> tr td formatMinMaxValue column, attribute, i
    ]

  createMinMaxCell = (column, attribute, value) ->
    value: value
    showMore: ->
      _.inspect
        content: createMinMaxInspection column, attribute
        template: 'geyser'

  formatMinMaxValue = (column, attribute, index) ->
    switch column.type
      when 'time'
        formatDateTime column[attribute][index]
      when 'real'
        formatReal column.precision, column[attribute][index]
      when 'int'
        formatToSignificantDigits 6, column[attribute][index]

  createMinMaxRow = (attribute, columns) ->
    map columns, (column) ->
      switch column.type
        when 'time', 'real', 'int'
          createMinMaxCell column, attribute, formatMinMaxValue column, attribute, 0
        else
          null

  createMeanRow = (columns) ->
    map columns, (column) ->
      switch column.type
        when 'time'
          formatDateTime column.mean
        when 'real'
          formatReal column.precision, column.mean
        when 'int'
          formatToSignificantDigits 6, column.mean
        else
          '-'

  createSigmaRow = (columns) ->
    map columns, (column) ->
      switch column.type
        when 'time', 'real', 'int'
          formatToSignificantDigits 6, column.sigma
        else
          '-'

  createCardinalityRow = (columns) ->
    map columns, (column) ->
      switch column.type
        when 'enum'
          column.domain.length
        else
          '-'

  createPlainRow = (attribute, columns) ->
    map columns, (column) -> column[attribute]

  createMissingsRow = (columns) ->
    map columns, (column) -> 
      if column.missing is 0 then '-' else column.missing

  createInfRow = (attribute, columns) ->
    map columns, (column) ->
      switch column.type
        when 'real', 'int'
          if column[attribute] is 0 then '-' else column[attribute]
        else
          '-'

  createSummaryRow = (columns) ->
    map columns, (column) ->
      displaySummary: ->
        alert 'Not implemented'

  createDataRow = (offset, index, columns) ->
    header: "Row #{offset + index}"
    cells: map columns, (column) ->
      switch column.type
        when 'uuid'
          column.str_data[index] or '-'
        when 'enum'
          column.domain[column.data[index]]
        when 'time'
          formatDateTime column.data[index]
        else
          value = column.data[index]
          if value is 'NaN'
            '-'
          else
            if column.type is 'real'
              formatReal column.precision, value
            else
              value

  createDataRows = (offset, rowCount, columns) ->
    rows = []
    for index in [0 ... rowCount]
      rows.push createDataRow offset, index, columns
    rows

  createFrameTable = (offset, rowCount, columns) ->
    hasMissings = hasZeros = hasPinfs = hasNinfs = hasEnums = no
    for column in columns
      hasMissings = yes if not hasMissings and column.missing > 0
      hasZeros = yes if not hasZeros and column.zeros > 0
      hasPinfs = yes if not hasPinfs and column.pinfs > 0
      hasNinfs = yes if not hasNinfs and column.ninfs > 0
      hasEnums = yes if not hasEnums and column.type is 'enum'

    header: createPlainRow 'label', columns
    typeRow: createPlainRow 'type', columns
    minRow: createMinMaxRow 'mins', columns
    maxRow: createMinMaxRow 'maxs', columns
    meanRow: createMeanRow columns
    sigmaRow: createSigmaRow columns
    cardinalityRow: if hasEnums then createCardinalityRow columns else null
    missingsRow: if hasMissings then createMissingsRow columns else null
    zerosRow: if hasZeros then createInfRow 'zeros', columns else null
    pinfsRow: if hasPinfs then createInfRow 'pinfs', columns else null
    ninfsRow: if hasNinfs then createInfRow 'ninfs', columns else null
    summaryRow: createSummaryRow columns
    #summaryRows: createSummaryRows columns
    hasMissings: hasMissings
    hasZeros: hasZeros
    hasPinfs: hasPinfs
    hasNinfs: hasNinfs
    hasEnums: hasEnums
    dataRows: createDataRows offset, rowCount, columns

  data: _frame
  key: _frame.key.name
  timestamp: _frame.creation_epoch_time_millis
  title: _frame.key.name
  columns: _frame.column_names
  table: createFrameTable _frame.off, _frame.len, _frame.columns
  dispose: ->
  template: 'frame-view'


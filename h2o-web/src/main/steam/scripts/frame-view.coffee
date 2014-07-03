localConfig =
  chartWidth: 275

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

computeTopCounts = (column, rowCount, top) ->
  { bins, domain } = column
  levels = map bins, (count, index) -> count: count, index: index
  sortedLevels = sortBy levels, (level) -> -level.count

  cardinality: column.domain.length
  levels: map (head sortedLevels, top), (level) ->
    label: domain[level.index]
    percent: "#{formatReal 1, level.count / rowCount * 100}%"
    count: level.count

renderTopCounts = (topCounts, bounds) ->
  width = bounds.width - bounds.margin.left - bounds.margin.right
  height = bounds.height - bounds.margin.top - bounds.margin.bottom

  scaleY = d3.scale.linear()
    .domain [ 0, histogram.maxCount ]
    .range [ height, 0 ]

renderHistogram = (_, histogram, bounds) ->
  width = bounds.width - bounds.margin.left - bounds.margin.right
  height = bounds.height - bounds.margin.top - bounds.margin.bottom

  scaleX = d3.scale.linear()
    .domain histogram.domain
    .range [ 0, width ]

  scaleY = d3.scale.linear()
    .domain [ 0, histogram.maxCount ]
    .range [ height, 0 ]

  ###
  axisX = d3.svg.axis()
    .scale scaleX
    .orient 'bottom'

  axisY = d3.svg.axis()
    .scale scaleY
    .orient 'left'
    .ticks 5
  ###

  el = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'
  svg = d3.select el
    .attr 'width', bounds.width
    .attr 'height', bounds.height
    .append 'g'
    .attr 'transform', "translate(#{bounds.margin.left},#{bounds.margin.top})"

  svg.append 'line'
    .attr 'x1', 0
    .attr 'x2', width
    .attr 'y1', height
    .attr 'y2', height
    .style 'fill', 'none'
    .style 'stroke', 'lightskyblue'

  bar = svg.selectAll '.bar'
    .data histogram.bins
    .enter()
    .append 'g'
    .attr 'class', 'bar'
    .attr 'transform', (bin) -> "translate(#{scaleX bin.start},#{scaleY bin.count})"

  intervalWidth = scaleX(histogram.domain[0] + histogram.stride) - scaleX(histogram.domain[0])

  bar.append 'rect'
    .attr 'x', 1
    .attr 'width', intervalWidth - 1
    .attr 'height', (bin) -> height - scaleY bin.count
    .on 'mousemove', (bin) ->
      column = histogram.column
      callout = if column.type is 'real'
        Start: formatReal column.precision, bin.start
        End: formatReal column.precision, bin.end
        Count: bin.count
      else
        Start: bin.start
        End: bin.end
        Count: bin.count
      _.callout callout, d3.event.pageX, d3.event.pageY
    .on 'mouseout', -> _.callout null

  ###
  bar.append 'title'
    .text (bin) ->
      column = histogram.column
      if column.type is 'real'
        "#{bin.count} (#{formatReal column.precision, bin.start} - #{formatReal column.precision, bin.end})"
      else
        "#{bin.count} (#{bin.start} - #{bin.end})"

  svg.append 'g'
    .attr 'class', 'x axis'
    .attr 'transform', "translate(0,#{height})"
    .call axisX

  svg.append 'g'
    .attr 'class', 'y axis'
    .call axisY
  ###


  el

createHistogram = (column, stride, interval, bins) ->
  start = (head bins).start
  end = (last bins).end

  column: column
  bins: bins
  stride: stride * interval
  domain: [ start, end ]
  start: if column.type is 'real' then formatReal column.precision, start else start
  end: if column.type is 'real' then formatReal column.precision, end else end
  maxCount: d3.max bins, (bin) -> bin.count

computeCharacteristics = (column, rowCount) ->
  { missing, zeros, pinfs, ninfs } = column
  other = rowCount - missing - zeros - pinfs - ninfs
  chunks = [
    [ 'Missing', missing, 'tomato' ]
    [ '-Inf', ninfs, 'lightseagreen' ]
    [ 'Zero', zeros, 'lightslategray' ]
    [ '+Inf', pinfs, 'lightskyblue' ]
    [ 'Other', other, '#aaa' ]
  ]
  offset = 0
  parts = map chunks, (chunk) ->
    [ label, count, color ] = chunk
    percent = "#{formatReal 1, count / rowCount * 100}%"
    part =
      label: "#{label}"
      percent: percent
      offset: offset
      count: count
      color: color
    offset += count
    part

  total: rowCount
  parts: parts

renderCharacteristics = (characteristics, bounds) ->
  width = bounds.width - bounds.margin.left - bounds.margin.right
  height = bounds.height - bounds.margin.top - bounds.margin.bottom

  scaleX = d3.scale.linear()
    .domain [ 0, characteristics.total ]
    .range [ 0, width ]

  el = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'
  svg = d3.select el
    .attr 'width', bounds.width
    .attr 'height', bounds.height
    .append 'g'
    .attr 'transform', "translate(#{bounds.margin.left},#{bounds.margin.top})"

  bar = svg.selectAll '.bar'
    .data characteristics.parts
    .enter()
    .append 'g'
    .attr 'class', 'bar'
    .attr 'transform', (part) -> "translate(#{scaleX part.offset},0)"

  bar.append 'rect'
    .attr 'x', 1
    .attr 'width', (part) -> scaleX part.count
    .attr 'height', bounds.height
    .style 'fill', (part) -> part.color

  bar.append 'title'
    .text (part) -> "#{part.label} (#{part.percent})"

  el

computeBoxplot = (percentiles, column) ->

  q1: column.pctiles[percentiles.indexOf 0.25]
  q2: column.pctiles[percentiles.indexOf 0.5]
  q3: column.pctiles[percentiles.indexOf 0.75]
  mean: column.mean
  min: head column.mins
  max: head column.maxs
  mins: column.mins
  maxs: column.maxs

renderBoxplot = (boxplot, bounds) ->
  width = bounds.width - bounds.margin.left - bounds.margin.right
  height = bounds.height - bounds.margin.top - bounds.margin.bottom

  scaleX = d3.scale.linear()
    .domain [ boxplot.min, boxplot.max ]
    .range [ 0, width ]

  svg = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'
  g = d3.select svg
    .attr 'width', bounds.width
    .attr 'height', bounds.height
    .append 'g'
    .attr 'transform', "translate(#{bounds.margin.left},#{bounds.margin.top})"

  h25 = height / 4
  h50 = height / 2
  h75 = height * 0.75
  q1 = scaleX boxplot.q1
  q2 = scaleX boxplot.q2
  q3 = scaleX boxplot.q3
  mean = scaleX boxplot.mean
  min = scaleX boxplot.min
  max = scaleX boxplot.max
  mins = map boxplot.mins, scaleX
  maxs = map boxplot.maxs, scaleX

  drawRule = (x1, y1, x2, y2) ->
    g.append 'line'
      .attr 'class', 'rule'
      .attr 'x1', x1
      .attr 'y1', y1
      .attr 'x2', x2
      .attr 'y2', y2

  drawCircle = (x) ->
    g.append 'circle'
      .attr 'class', 'rule'
      .attr 'cx', x
      .attr 'cy', h50
      .attr 'r', 3

  # Box from Q1 to Q2
  g.append 'rect'
    .attr 'class', 'rule'
    .attr 'x', q1
    .attr 'y', 0
    .attr 'width', q3 - q1
    .attr 'height', height

  # Lower whisker
  drawRule 0, h50, q1, h50

  # Upper whisker
  drawRule q3, h50, width, h50

  # Lower fence
  drawRule 0, h25, 0, h75

  # Upper fence 
  drawRule width, h25, width, h75

  # Median (Q2)
  drawRule q2, 0, q2, height
    .style 'stroke-dasharray', '3,3'

  # Mean ('+')
  drawRule mean - 5, h50, mean + 5, h50
  drawRule mean, h50 - 5, mean, h50 + 5

  # Circles for mins/maxs higher/lower than min/max
  forEach mins, (value) -> drawCircle value if value isnt min
  forEach maxs, (value) -> drawCircle value if value isnt max

  svg

computeHistogram = (column, minIntervalCount) ->
  { base, stride, bins } = column
  interval = Math.floor bins.length / minIntervalCount
  if interval > 0
    intervalCount = minIntervalCount + if bins.length % interval > 0 then 1 else 0
    createHistogram column, stride, interval, times intervalCount, (intervalIndex) ->
      m = intervalIndex * interval
      n = m + interval
      count = 0
      for binIndex in [m ... n] when n < bins.length
        count += bins[binIndex]
      start: base + intervalIndex * stride * interval
      end: base + (intervalIndex + 1) * stride * interval
      count: count

  else
    createHistogram column, stride, 1, map bins, (count, intervalIndex) ->
      start: base + intervalIndex * stride
      end: base + (intervalIndex + 1) * stride
      count: count

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

  renderTopCountsTable = (topCounts) ->
    [ div, table, tbody, tr, td ] = geyser.generate words 'div table.table.table-condensed.y-monospace tbody tr td'
    [ datacell ] = geyser.generate [ "td.y-chart data-value='$value'" ]

    maxCount = d3.max topCounts.levels, (level) -> level.count

    div table tbody map topCounts.levels, (level) ->
      tr [
        td level.label
        td level.count
        datacell '', $value: level.count / maxCount
        td level.percent
      ]


  createSummaryInspection = (frame) ->
    column = head frame.columns

    [ div ] = geyser.generate [ 'div' ]
    switch column.type
      when 'int', 'real'
        #TODO include jquery.pep for sliders to customize bins
        histogram = computeHistogram column, 32
        appendHistogram = ($element) ->
          $element.empty().append renderHistogram _, histogram,
            width: localConfig.chartWidth
            height: 100
            margin:
              top: 0
              right: 0
              bottom: 0
              left: 0
        histogramInspection =
          data: histogram
          graphic:
            markup: div()
            behavior: appendHistogram

        boxplot = computeBoxplot frame.default_pctiles, column
        appendBoxplot = ($element) ->
          $element.empty().append renderBoxplot boxplot,
            width: localConfig.chartWidth
            height: 70
            margin:
              top: 5
              right: 5
              bottom: 5
              left: 5
        boxplotInspection =
          graphic:
            markup: div()
            behavior: appendBoxplot
      else
        topCounts = computeTopCounts column, frame.rows, 15
        topCountsTable = renderTopCountsTable topCounts
        maxBarSize = 100
        updateTopCountsTable = ($element) ->
          [ container, bar ] = geyser.generate [
            "div.y-box"
            "div.y-bar style='width:$width'"
          ]
          $('td.y-chart', $element).each ->
            $el = $ @
            value = parseFloat $el.attr 'data-value'
            $el.append geyser.render container bar '', $width: "#{Math.round value * 100}%"

        topCountsInspection =
          caption: if topCounts.cardinality is topCounts.levels.length then 'Levels' else "Top #{describeCount topCounts.levels.length, 'Level'}"
          graphic:
            markup: topCountsTable
            behavior: updateTopCountsTable


    characteristics = computeCharacteristics column, frame.rows

    appendCharacteristics = ($element) ->
      $element.empty().append renderCharacteristics characteristics,
        width: localConfig.chartWidth
        height: 10
        margin:
          top: 0
          right: 0
          bottom: 0
          left: 0

    characteristicsInspection =
      parts: characteristics.parts
      graphic:
        markup: div()
        behavior: appendCharacteristics

    _.inspect
      columnLabel: column.label
      histogram: histogramInspection
      boxplot: boxplotInspection
      topCounts: topCountsInspection
      characteristics: characteristicsInspection
      template: 'column-summary-view'

  createSummaryRow = (frameKey, columns) ->
    map columns, (column) ->
      displaySummary: ->
        _.requestColumnSummary frameKey, column.label, (error, frames) ->
          if error
            _.error 'Error requesting column summary', column, error
          else
            createSummaryInspection head frames

  createDataRow = (offset, index, columns) ->
    header: "Row #{offset + index + 1}"
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
    summaryRow: createSummaryRow _frame.key.name, columns
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


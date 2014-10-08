Flow = if exports? then exports else @Flow = {}

marked.setOptions
  smartypants: yes
  highlight: (code, lang) ->
    if window.hljs
      (window.hljs.highlightAuto code, [ lang ]).value
    else
      code

ko.bindingHandlers.cursorPosition =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    if arg = ko.unwrap valueAccessor()
      # Bit of a hack. Attaches a method to the bound object that returns the cursor position. Uses dwieeb/jquery-textrange.
      arg.read = -> $(element).textrange 'get', 'position'
    return

Flow.Application = (_) ->
  _view = Flow.Repl _
  Flow.DialogManager _
  
  context: _
  view: _view

Flow.ApplicationContext = (_) ->
  context$
    ready: do edges$

Flow.DialogManager = (_) ->

Flow.HtmlTag = (_, level) ->
  isCode: no
  render: (input, go) ->
    go null,
      text: input.trim() or '(Untitled)'
      template: "flow-#{level}"

Flow.Raw = (_) ->
  isCode: no
  render: (input, go) ->
    go null,
      text: input
      template: 'flow-raw'

Flow.Markdown = (_) ->
  isCode: no
  render: (input, go) ->
    try
      html = marked input.trim() or '(No content)'
      go null,
        html: html
        template: 'flow-md'
    catch error
      go error

Flow.Coffeescript = (_) ->
  render: (input, go) ->

Flow.Repl = (_) ->
  _cells = nodes$ []
  _selectedCell = null
  _selectedCellIndex = -1
  _clipboardCell = null
  _lastDeletedCell = null

  countLines = (text) ->
    newlineCount = 1
    for character in text when character is '\n'
      newlineCount++
    newlineCount

  checkConsistency = ->
    for cell, i in _cells()
      unless cell
        error "index #{i} is empty"
    return

  selectCell = (target) ->
    return if _selectedCell is target
    _selectedCell.isSelected no if _selectedCell
    _selectedCell = target
    _selectedCell.isSelected yes
    _selectedCellIndex = _cells.indexOf _selectedCell
    checkConsistency()
    return

  createCell = (type='cs', input='') ->
    _type = node$ type
    _renderer = lift$ _type, (type) ->
      switch type
        when 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'
          Flow.HtmlTag _, type
        when 'md'
          Flow.Markdown _
        when 'cs'
          Flow.Coffeescript _
        else
          Flow.Raw _

    _isSelected = node$ no
    _isActive = node$ no
    _hasError = node$ no
    _isBusy = node$ no
    _isReady = lift$ _isBusy, (isBusy) -> not isBusy
    _hasInput = node$ yes
    _input = node$ input
    _output = node$ null
    _lineCount = lift$ _input, countLines

    # This is a shim.
    # The ko 'cursorPosition' custom binding attaches a read() method to this.
    _cursorPosition = {}

    # select and display input when activated
    apply$ _isActive, (isActive) ->
      selectCell self if isActive
      _hasInput yes if isActive

    # deactivate when deselected
    apply$ _isSelected, (isSelected) ->
      _isActive no unless isSelected

    select = -> selectCell self

    execute = (go) ->
      renderer = _renderer()
      _isBusy yes
      renderer.render _input(), (error, result) ->
        if error
          _hasError yes
          #XXX display error
          debug error
        else
          _hasError no
          _output result
          _hasInput renderer.isCode is yes

        _isBusy no

      _isActive no
      go() if go

    self =
      type: _type
      isSelected: _isSelected
      isActive: _isActive
      hasError: _hasError
      isBusy: _isBusy
      isReady: _isReady
      hasInput: _hasInput
      input: _input
      output: _output
      lineCount: _lineCount
      select: select
      execute: execute
      _cursorPosition: _cursorPosition
      cursorPosition: -> _cursorPosition.read()
      template: 'flow-cell'

  cloneCell = (cell) ->
    createCell cell.type(), cell.input()

  switchToCommandMode = ->
    _selectedCell.isActive no

  switchToEditMode = ->
    _selectedCell.isActive yes
    no

  convertCellToCode = -> _selectedCell.type 'cs'

  convertCellToHeading = (level) -> -> _selectedCell.type "h#{level}"

  convertCellToMarkdown = -> _selectedCell.type 'md'

  convertCellToRaw = -> _selectedCell.type 'raw'

  copyCell = ->
    _clipboardCell = cloneCell _selectedCell

  cutCell = ->
    _clipboardCell = _selectedCell
    removeCell()

  deleteCell = ->
    _lastDeletedCell = _selectedCell
    removeCell()

  removeCell = ->
    cells = _cells()
    if cells.length > 1
      if _selectedCellIndex is cells.length - 1
        #TODO call dispose() on this cell
        splice _cells, _selectedCellIndex, 1
        selectCell cells[_selectedCellIndex - 1]
      else
        #TODO call dispose() on this cell
        splice _cells, _selectedCellIndex, 1
        selectCell cells[_selectedCellIndex]
    return
    
  insertCell = (index, cell) ->
    splice _cells, index, 0, cell
    selectCell cell
    cell

  insertCellAbove = ->
    insertCell _selectedCellIndex, createCell 'cs'

  insertCellBelow = ->
    insertCell _selectedCellIndex + 1, createCell 'cs'

  moveCellDown = ->
    cells = _cells()
    unless _selectedCellIndex is cells.length - 1
      splice _cells, _selectedCellIndex, 1
      _selectedCellIndex++
      splice _cells, _selectedCellIndex, 0, _selectedCell
    return

  moveCellUp = ->
    unless _selectedCellIndex is 0
      cells = _cells()
      splice _cells, _selectedCellIndex, 1
      _selectedCellIndex--
      splice _cells, _selectedCellIndex, 0, _selectedCell
    return

  mergeCellBelow = ->
    cells = _cells()
    unless _selectedCellIndex is cells.length - 1
      nextCell = cells[_selectedCellIndex + 1]
      if _selectedCell.type() is nextCell.type()
        nextCell.input _selectedCell.input() + '\n' + nextCell.input()
        removeCell()
    return

  splitCell = ->
    if _selectedCell.isActive()
      input = _selectedCell.input()
      if input.length > 1
        cursorPosition = _selectedCell.cursorPosition()
        if 0 < cursorPosition < input.length - 1
          left = substr input, 0, cursorPosition
          right = substr input, cursorPosition
          _selectedCell.input left
          insertCell _selectedCellIndex + 1, createCell 'cs', right
          _selectedCell.isActive yes
    return

  pasteCellAbove = ->
    insertCell _selectedCellIndex, _clipboardCell if _clipboardCell

  pasteCellBelow = ->
    insertCell _selectedCellIndex + 1, _clipboardCell if _clipboardCell

  undoLastDelete = ->
    insertCell _selectedCellIndex + 1, _lastDeletedCell if _lastDeletedCell
    _lastDeletedCell = null

  runCell = ->
    _selectedCell.execute()
    no

  runCellAndInsertBelow = ->
    _selectedCell.execute -> insertCellBelow()
    no

  #TODO ipython has inconsistent behavior here. seems to be doing runCellAndInsertBelow if executed on the lowermost cell.
  runCellAndSelectBelow = ->
    _selectedCell.execute -> selectNextCell()
    no

  saveFlow = ->
    debug 'saveFlow'
    no

  selectNextCell = ->
    cells = _cells()
    unless _selectedCellIndex is cells.length - 1
      selectCell cells[_selectedCellIndex + 1]
    return

  selectPreviousCell = ->
    unless _selectedCellIndex is 0
      cells = _cells()
      selectCell cells[_selectedCellIndex - 1]
    return

  displayHelp = -> debug 'displayHelp'

  # (From IPython Notebook keyboard shortcuts dialog)
  # The IPython Notebook has two different keyboard input modes. Edit mode allows you to type code/text into a cell and is indicated by a green cell border. Command mode binds the keyboard to notebook level actions and is indicated by a grey cell border.
  # 
  # Command Mode (press Esc to enable)
  # 
  normalModeKeyboardShortcuts = [
    [ 'enter', 'edit mode', switchToEditMode ]
    #[ 'shift+enter', 'run cell, select below', runCellAndSelectBelow ]
    #[ 'ctrl+enter', 'run cell', runCell ]
    #[ 'alt+enter', 'run cell, insert below', runCellAndInsertBelow ]
    [ 'y', 'to code', convertCellToCode ]
    [ 'm', 'to markdown', convertCellToMarkdown ]
    [ 'r', 'to raw', convertCellToRaw ]
    [ '1', 'to heading 1', convertCellToHeading 1 ]
    [ '2', 'to heading 2', convertCellToHeading 2 ]
    [ '3', 'to heading 3', convertCellToHeading 3 ]
    [ '4', 'to heading 4', convertCellToHeading 4 ]
    [ '5', 'to heading 5', convertCellToHeading 5 ]
    [ '6', 'to heading 6', convertCellToHeading 6 ]
    [ 'up', 'select previous cell', selectPreviousCell ]
    [ 'down', 'select next cell', selectNextCell ]
    [ 'k', 'select previous cell', selectPreviousCell ]
    [ 'j', 'select next cell', selectNextCell ]
    [ 'ctrl+k', 'move cell up', moveCellUp ]
    [ 'ctrl+j', 'move cell down', moveCellDown ]
    [ 'a', 'insert cell above', insertCellAbove ]
    [ 'b', 'insert cell below', insertCellBelow ]
    [ 'x', 'cut cell', cutCell ]
    [ 'c', 'copy cell', copyCell ]
    [ 'shift+v', 'paste cell above', pasteCellAbove ]
    [ 'v', 'paste cell below', pasteCellBelow ]
    [ 'z', 'undo last delete', undoLastDelete ]
    [ 'd d', 'delete cell (press twice)', deleteCell ]
    [ 'shift+m', 'merge cell below', mergeCellBelow ]
    [ 's', 'save notebook', saveFlow ]
    #[ 'mod+s', 'save notebook', saveFlow ]
    # [ 'l', 'toggle line numbers' ]
    # [ 'o', 'toggle output' ]
    # [ 'shift+o', 'toggle output scrolling' ]
    # [ 'q', 'close pager' ]
    [ 'h', 'keyboard shortcuts', displayHelp ]
    # [ 'i', 'interrupt kernel (press twice)' ]
    # [ '0', 'restart kernel (press twice)' ]
  ] 

  # 
  # Edit Mode (press Enter to enable) 
  # 
  editModeKeyboardShortcuts = [
    # Tab : code completion or indent
    # Shift-Tab : tooltip
    # Cmd-] : indent
    # Cmd-[ : dedent
    # Cmd-a : select all
    # Cmd-z : undo
    # Cmd-Shift-z : redo
    # Cmd-y : redo
    # Cmd-Up : go to cell start
    # Cmd-Down : go to cell end
    # Opt-Left : go one word left
    # Opt-Right : go one word right
    # Opt-Backspace : del word before
    # Opt-Delete : del word after
    [ 'esc', 'command mode', switchToCommandMode ]
    [ 'ctrl+m', 'command mode', switchToCommandMode ]
    [ 'shift+enter', 'run cell, select below', runCellAndSelectBelow ]
    [ 'ctrl+enter', 'run cell', runCell ]
    [ 'alt+enter', 'run cell, insert below', runCellAndInsertBelow ]
    [ 'ctrl+shift+-', 'split cell', splitCell ]
    [ 'mod+s', 'save notebook', saveFlow ]
  ]

  setupKeyboardHandling = (mode) ->
    for [ shortcut, caption, f ] in normalModeKeyboardShortcuts
      Mousetrap.bind shortcut, f

    for [ shortcut, caption, f ] in editModeKeyboardShortcuts
      Mousetrap.bindGlobal shortcut, f
    return

  initialize = ->
    setupKeyboardHandling 'normal'
    cell = createCell 'cs'
    push _cells, cell
    selectCell cell

  initialize()

  cells: _cells
  template: (view) -> view.template

$ ->
  window.flow = flow = Flow.Application do Flow.ApplicationContext
  ko.applyBindings flow
  flow.context.ready()
  

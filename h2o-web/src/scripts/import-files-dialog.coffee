
Steam.ImportFilesDialog = (_, _go) ->
  _specifiedPath = node$ ''
  _errorMessage = node$ ''
  _hasErrorMessage = lift$ _errorMessage, isTruthy

  _importedPath = node$ ''
  _importedFiles = nodes$ []
  _hasImportedFiles = lift$ _importedFiles, (files) -> files.length > 0
  _selectedFileCount = node$ 0
  _hasSelectedFiles = lift$ _selectedFileCount, (count) -> count > 0
  _fileSelectionCaption = lift$ _selectedFileCount, (count) -> "#{describeCount count, 'file'} selected."

  _selectAllFiles = node$ yes
  _blockCheckboxes = no

  importFiles = (files) ->
    sourceKeys = map files, (file) -> file.key
    _.requestParseSetup sourceKeys, (error, result) ->
      console.log error, result
    return

  importSelectedFiles = -> importFiles filter _importedFiles(), (file) -> file.isSelected()
  
  createFileItems = (result) ->
    map result.keys, (key, index) ->
      self =
        key: key
        path: result.files[index]
        isSelected: node$ yes
        importFile: -> importFiles [ self ]

  processImportResult = (result) -> 
    files = createFileItems result
    _blockCheckboxes = yes
    forEach files, (file) ->
      apply$ file.isSelected, (isSelected) ->
        unless _blockCheckboxes
          count = 0
          for file in files when file.isSelected()
            count++
          _selectedFileCount count
    _blockCheckboxes = no

    apply$ _selectAllFiles, (checked) ->
      _blockCheckboxes = yes
      for file in files
        file.isSelected checked
      _blockCheckboxes = no
      _selectedFileCount if checked then files.length else 0

    _importedPath result.path
    _importedFiles files
    _selectedFileCount files.length

  tryImportFiles = ->
    specifiedPath = _specifiedPath()
    _.requestImportFiles specifiedPath, (error, result) ->
      console.log error, result
      if error
        _errorMessage error.data.errmsg
      else
        _errorMessage ''
        #_go 'confirm', result
        processImportResult result

  cancel = -> _go 'cancel'

  specifiedPath: _specifiedPath
  tryImportFiles: tryImportFiles
  hasErrorMessage: _hasErrorMessage
  errorMessage: _errorMessage

  hasImportedFiles: _hasImportedFiles
  importedPath: _importedPath
  importedFiles: _importedFiles
  selectAllFiles: _selectAllFiles
  hasSelectedFiles: _hasSelectedFiles
  fileSelectionCaption: _fileSelectionCaption
  importSelectedFiles: importSelectedFiles

  cancel: cancel
  template: 'import-files-dialog'

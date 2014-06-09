Steam.DialogManager = (_) ->

  initialize = ->
    linkDialog _.alert, Steam.AlertDialog
    linkDialog _.fail, Steam.ErrorDialog
    linkDialog _.confirm, Steam.ConfirmDialog
    linkDialog _.promptForFrame, Steam.SelectFrameDialog
    linkDialog _.configureStripPlot, Steam.ConfigureStripPlotDialog
    linkDialog _.promptImportFiles, Steam.ImportFilesDialog
    linkDialog _.promptParseFiles, Steam.ParseFilesDialog

    # --- Add additional dialogs here ---

  linkDialog = (edge, createDialog) ->
    link$ edge, (requestArgs..., go) ->
      throw new Error 'No continuation provided.' unless isFunction go
      requestArgs.unshift _
      requestArgs.push (responseArgs...) ->
        _.unloadDialog dialog
        go.apply null, responseArgs
      _.loadDialog dialog = createDialog.apply null, requestArgs

  link$ _.positionDialog, (element) ->
    return unless element.nodeType is 1
    $dialogContainer = $ element
    $dialog = $ $dialogContainer.children()[0]
    [ $dialogHeader, $dialogBody ] = map $dialog.children(), (child) -> $ child
    dialogWidth = parseInt $dialog.attr 'data-dialog-width'
    dialogHeight = parseInt $dialog.attr 'data-dialog-height'

    parentWidth = $dialog.parent().parent().width()

    unless dialogWidth > 0
      dialogWidth = parentWidth * 0.75

    # Set the dialog width so that the .height() measurement returns an accurate height.
    $dialog.width dialogWidth

    unless dialogHeight > 0
      dialogHeight = $dialog.height()

    dialogLeft = -dialogWidth / 2
    dialogTop = -dialogHeight / 2
    dialogHeaderHeight = $dialogHeader.height()
    dialogBodyHeight = dialogHeight - dialogHeaderHeight

    $dialog.attr 'style', "position:absolute;width:#{dialogWidth}px;height:#{dialogHeight}px;left:#{dialogLeft}px;top:#{dialogTop}px"
    $dialogHeader.attr 'style', 'position:absolute;top:0;left:0;right:0'
    $dialogBody.attr 'style', "position:absolute;left:0;right:0;bottom:0;top:#{dialogHeaderHeight}px"

  initialize()

  return




Steam.Xhr = (_) ->
  createResponse = (status, data, xhr) ->
    status: status, data: data, xhr: xhr

  link$ _.invokeH2O, (method, path, go) ->
    $.getJSON path
      .done (data, status, xhr) ->
        go null, createResponse status, data, xhr
      .fail (xhr, status, error) ->
        go createResponse status, xhr.responseJSON, xhr


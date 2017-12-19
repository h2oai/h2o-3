
$(document).ready(function() {

  // turn functions section into ref-table
  $('#functions').find('table').attr('class', 'ref-index');
  
  // are we in examples?
  var examples = window.location.href.match("/articles/examples/") !== null;
  if (examples) {
    $('.template-vignette').addClass('examples');
   
    // remove right column
    $(".col-md-9").removeClass("col-md-9").addClass('col-md-10');
    $(".col-md-3").remove();
    
  }
});


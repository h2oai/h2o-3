/**
 * jMatrix
 * http://code.google.com/p/jquery-matrix/
 *
 * Copyright (c) 2009 Manuel Strehl
 * Licensed under the MIT license
 *
 * $Id$
 */
(function ($) {

/**
 * Return a random integer
 */
var rand = function (max) {
  return max? Math.round (Math.random () * max) : 0;
};

/**
 * Extends jQuery objects with the matrix method
 */
$.fn.matrix = function (options) {

  /**
   * Settings get defaulted with values from $.fn.matrix.settings
   */
  var settings = $.extend ({}, $.fn.matrix.settings, options);
  
  /**
   * Add string data to a single matrix string
   */
  var addData = function ($element, initelement, n, data, randset, matrix_height) {
    if ($element.length) { // could be, that the element is deleted meanwhile
      if (n % settings.charstep == 1) { // every charstep'th iteration adds a new character
        var add = document.createElement ("span");
        add.appendChild (document.createTextNode (data.substr (n%data.length, 1)));
        $(add).animate ({'opacity':'0'}, randset);
        initelement.parentNode.insertBefore (add, initelement);
      }
      initelement.firstChild.nodeValue = data.substr (n%data.length, 1);
      if ($element.position ().top < matrix_height + 10) {
        window.setTimeout (function () { addData ($element, initelement, n+1, data, randset, matrix_height); }, 50);
      }
    }
  };
  
  /**
   * Add a new matrix string (one vertically floating text stripe)
   */
  var addString = function ($matrix, matrix_width, matrix_height, string_count) {
    var size = rand (3); // the size of the string (0: large, foreground; 3: small, background)
    var seed = 20-3*size;
    var pos = seed*rand (matrix_width/seed); // the position from the left border (foreground is wider apart)
    var speed = settings.speedSeed + rand (settings.speedSeed)
                + (3-size) * rand (settings.speedSeed); // the downwards speed (foreground is faster)
    
    // The string to append:
    var span = document.createElement ("span");
    var init = document.createElement ("span");
    span.setAttribute ("class", 'ui-matrix-string ui-matrix-string-size'+size);
    span.style.left = pos+'px';
    span.style.top = "-"+(50+rand (matrix_height))+'px';
    init.setAttribute ("class", "ui-matrix-string-first");
    init.appendChild (document.createTextNode ("0"));
    span.appendChild (init);
    var $mstring = $(span);
    
    $matrix.append ($mstring);
    // move it:
    $mstring.animate ({"top": matrix_height + 10}, speed, function () {
      this.parentNode.removeChild (this);
    });
    
    addData ($mstring, init, 0, settings.data[rand(settings.data.length-1)], settings.fadingSeed + rand (settings.fadingSeed), matrix_height);
    
    if (string_count < settings.number) {
      // add the next string here. Gives a nicer, cascading result than a while loop in the main body
      string_count++;
      window.setTimeout (function () { addString ($matrix, matrix_width, matrix_height, string_count); }, settings.distanceRoot + rand (settings.distanceSeed));
    }
  };
  
  // don't break the chain
  return this.each (function () {
    var $matrix = $(this);
    $matrix.addClass ("ui-matrix");
    $matrix.height (settings.height);
    var matrix_height = settings.height;
    $matrix.width (settings.width);
    var matrix_width  = settings.width;
    
    addString ($matrix, matrix_width, matrix_height, 0);
  });

};

/**
 * Publicly available settings for jMatrix
 *
 * data: array of strings that are used for the matrix effect
 * number: number of strings generated per matrix (more will lead to a 
 *   significant performance impact)
 */
$.fn.matrix.settings = {
  'data': [
    "H2O: the data is the system",
    "H2O: make hadoop do math",
    "案ずるより産むが易し",
    "虎穴に入らずんば虎子を得ず",
    "猿も木から落ちる",
    "井の中の蛙大海を知らず",
    "二兎を追う者は一兎をも得ず",
    "門前の小僧習わぬ経を読む",
    "脳ある鷹は爪を隠す"
  ],
  'number': 100,
  'charstep': 7,
  'speedSeed': 10000,
  'fadingSeed': 8000,
  'distanceRoot': 100,
  'distanceSeed': 500,
  'width': 500,
  'height': 500
};

})(jQuery);

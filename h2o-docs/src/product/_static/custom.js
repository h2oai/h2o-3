 function addCopyButtonToCode(){
 // get all code elements
 var allCodeBlocksElements = $( "div.highlight pre" );

 // For each element, do the following steps
 allCodeBlocksElements.each(function(ii) {
 // define a unique id for this element and add it
 var currentId = "codeblock" + (ii + 1);
 $(this).attr('id', currentId);

 // create a button that's configured for clipboard.js
 // point it to the text that's in this code block
 // add the button just after the text in the code block w/ jquery
 var clipButton = '<button class="btn copybtn" data-clipboard-target="#' + currentId + '"><img src="https://clipboardjs.com/assets/images/clippy.svg" width="13" alt="Copy to clipboard"></button>';
    $(this).after(clipButton);
 });

 // tell clipboard.js to look for clicks that match this query
 new Clipboard('.btn');
 }

 $(document).ready(function () {
 // Once the DOM is loaded for the page, attach clipboard buttons
 addCopyButtonToCode();
 });

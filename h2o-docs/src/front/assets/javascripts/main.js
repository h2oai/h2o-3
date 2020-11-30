// Force external links to open in new tab / window
(function ($) {
  $(function () {
    var siteName = window.location.host.replace('www.', '');
    $('a[href^="htt"]:not([target]):not([href*="www.' + siteName + '"]):not([href*="//' + siteName + '"])').attr('target', '_blank');
  });
})(jQuery);

// Use $ instead of jQuery without replacing global $
(function ($) {
    $.fn.highlighttext = function(){
        return this.each(function () {
            if($(this).find("span").length > 0){
                var spanleft = $(this).find("span").offset().left,
                    $thisleft = $(this).offset().left;
                if(spanleft === $thisleft){
                    $(this).addClass("highlight-text");
                }
            }
        });
    };
    
    $.fn.imageResponsiveIe= function(){
            return this.each(function () {
                var imgUrl = $(this).find("img").prop("src");
                if (imgUrl) {
                    $(this).find("img").attr("style", "display: none !important");
                    $(this).css("backgroundImage", 'url(' + imgUrl + ')').css({'background-repeat':'no-repeat','background-position':'center','background-size':'cover'});
                }
            });
    }

    // Fix image squishing on IE
    $.fn.imageResponsive = function () {
        return this.each(function () {
            var img = $(this).find("img"),
                defaultWidth = img.prop('naturalWidth'),
                defaultHeight = img.prop('naturalHeight'),
                parentHeight = $(this).outerHeight(true),
                parentWidth = $(this).width(),
                aspectRatio = defaultWidth / defaultHeight;
            img.css({
                "height": "auto",
                "width": "100%",
                "margin-left": "0px",
                "max-width": "inherit"
            });
            var imgHeight = parentWidth / aspectRatio;
            var imgTop = (imgHeight - parentHeight) / 2;
            img.css({
                "margin-top": "-" + imgTop + "px"
            });
            if (img.height() < parentHeight) {
                img.css({
                    "height": "100%",
                    "width": "auto"
                });
                var right_margin = (img.width() - parentWidth) / 2;
                img.css({
                    "margin-left": "-" + right_margin + "px",
                    "margin-top": "0"
                });
            }
            else if (img.width() < parentWidth) {
                img.css({
                    "height": "auto",
                    "width": "100%",
                    "margin-left": "0"
                });
                img.css({
                    "margin-top": "-" + imgTop + "px"
                });
            }
        });
    };
  // On DOM ready
  $(function () {

    // Initialize WOW.js plugin
    new WOW().init();

    // Add class for IE and Edge on body
    if ($('html').hasClass('ua-ie')) {
      $('body').addClass('ua-ie');
    }
    
    if ($('html').hasClass('ua-edge')) {
      $('body').addClass('ua-edge');
    }
    if($('.banner-fourth-level').length > 0 || $('.banner-third-level').length > 0 || $('.banner-with-content').length > 0 || $('.search-banner').length > 0 || $('.error-404').length > 0){
      $('body').addClass('small-banner');
    }
    $(".banner-hero .slider").each(function () {
      if($(this).find("h1").length > 0){
          var spanleft = $(this).find("h1").offset().left,
              $thisleft = $(this).find("h1 span").offset().left;
          if(spanleft === $thisleft){
              $(this).addClass("highlight-text");
          }
      }
    });
      $("h1").highlighttext();
      $("h2").highlighttext();
      $("h3").highlighttext();
      $("h4").highlighttext();
      $("h5").highlighttext();
      $("h6").highlighttext();


//    var page_url = window.location.href;
//        page_url = page_url.split('/');
//    if(page_url[3]==="sample-blog" || page_url[3]==="products-detail" || page_url[3]==="contact-new"){
//      $('.breadcrumb').css('display','block');
//    }


    // Detect small height devices and add class on body
    function detectHeight() {
      var windowHeight = $(window).height();
      (windowHeight <= 750) ? $('body.h2o').addClass('we-small-height') : $('body.h2o').removeClass('we-small-height');
    }

    detectHeight();
    $('.generic-view-grid .col-two').matchHeight();
    $(window).on("resize", function () {
      detectHeight();
      $('.generic-view-grid .col-two').matchHeight();
    }).resize();
  });

  // Add class to body for custom animation starting point
  $(window).on('load', function () {
    $('body').addClass('animate-in');
  });
})(jQuery);

/* Placeholders.js v4.0.1 */
/*!
 * The MIT License
 *
 * Copyright (c) 2012 James Allardice
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
!function(a){"use strict";function b(){}function c(){try{return document.activeElement}catch(a){}}function d(a,b){for(var c=0,d=a.length;d>c;c++)if(a[c]===b)return!0;return!1}function e(a,b,c){return a.addEventListener?a.addEventListener(b,c,!1):a.attachEvent?a.attachEvent("on"+b,c):void 0}function f(a,b){var c;a.createTextRange?(c=a.createTextRange(),c.move("character",b),c.select()):a.selectionStart&&(a.focus(),a.setSelectionRange(b,b))}function g(a,b){try{return a.type=b,!0}catch(c){return!1}}function h(a,b){if(a&&a.getAttribute(B))b(a);else for(var c,d=a?a.getElementsByTagName("input"):N,e=a?a.getElementsByTagName("textarea"):O,f=d?d.length:0,g=e?e.length:0,h=f+g,i=0;h>i;i++)c=f>i?d[i]:e[i-f],b(c)}function i(a){h(a,k)}function j(a){h(a,l)}function k(a,b){var c=!!b&&a.value!==b,d=a.value===a.getAttribute(B);if((c||d)&&"true"===a.getAttribute(C)){a.removeAttribute(C),a.value=a.value.replace(a.getAttribute(B),""),a.className=a.className.replace(A,"");var e=a.getAttribute(I);parseInt(e,10)>=0&&(a.setAttribute("maxLength",e),a.removeAttribute(I));var f=a.getAttribute(D);return f&&(a.type=f),!0}return!1}function l(a){var b=a.getAttribute(B);if(""===a.value&&b){a.setAttribute(C,"true"),a.value=b,a.className+=" "+z;var c=a.getAttribute(I);c||(a.setAttribute(I,a.maxLength),a.removeAttribute("maxLength"));var d=a.getAttribute(D);return d?a.type="text":"password"===a.type&&g(a,"text")&&a.setAttribute(D,"password"),!0}return!1}function m(a){return function(){P&&a.value===a.getAttribute(B)&&"true"===a.getAttribute(C)?f(a,0):k(a)}}function n(a){return function(){l(a)}}function o(a){return function(){i(a)}}function p(a){return function(b){return v=a.value,"true"===a.getAttribute(C)&&v===a.getAttribute(B)&&d(x,b.keyCode)?(b.preventDefault&&b.preventDefault(),!1):void 0}}function q(a){return function(){k(a,v),""===a.value&&(a.blur(),f(a,0))}}function r(a){return function(){a===c()&&a.value===a.getAttribute(B)&&"true"===a.getAttribute(C)&&f(a,0)}}function s(a){var b=a.form;b&&"string"==typeof b&&(b=document.getElementById(b),b.getAttribute(E)||(e(b,"submit",o(b)),b.setAttribute(E,"true"))),e(a,"focus",m(a)),e(a,"blur",n(a)),P&&(e(a,"keydown",p(a)),e(a,"keyup",q(a)),e(a,"click",r(a))),a.setAttribute(F,"true"),a.setAttribute(B,T),(P||a!==c())&&l(a)}var t=document.createElement("input"),u=void 0!==t.placeholder;if(a.Placeholders={nativeSupport:u,disable:u?b:i,enable:u?b:j},!u){var v,w=["text","search","url","tel","email","password","number","textarea"],x=[27,33,34,35,36,37,38,39,40,8,46],y="#ccc",z="placeholdersjs",A=new RegExp("(?:^|\\s)"+z+"(?!\\S)"),B="data-placeholder-value",C="data-placeholder-active",D="data-placeholder-type",E="data-placeholder-submit",F="data-placeholder-bound",G="data-placeholder-focus",H="data-placeholder-live",I="data-placeholder-maxlength",J=100,K=document.getElementsByTagName("head")[0],L=document.documentElement,M=a.Placeholders,N=document.getElementsByTagName("input"),O=document.getElementsByTagName("textarea"),P="false"===L.getAttribute(G),Q="false"!==L.getAttribute(H),R=document.createElement("style");R.type="text/css";var S=document.createTextNode("."+z+" {color:"+y+";}");R.styleSheet?R.styleSheet.cssText=S.nodeValue:R.appendChild(S),K.insertBefore(R,K.firstChild);for(var T,U,V=0,W=N.length+O.length;W>V;V++)U=V<N.length?N[V]:O[V-N.length],T=U.attributes.placeholder,T&&(T=T.nodeValue,T&&d(w,U.type)&&s(U));var X=setInterval(function(){for(var a=0,b=N.length+O.length;b>a;a++)U=a<N.length?N[a]:O[a-N.length],T=U.attributes.placeholder,T?(T=T.nodeValue,T&&d(w,U.type)&&(U.getAttribute(F)||s(U),(T!==U.getAttribute(B)||"password"===U.type&&!U.getAttribute(D))&&("password"===U.type&&!U.getAttribute(D)&&g(U,"text")&&U.setAttribute(D,"password"),U.value===U.getAttribute(B)&&(U.value=T),U.setAttribute(B,T)))):U.getAttribute(C)&&(k(U),U.removeAttribute(B));Q||clearInterval(X)},J);e(a,"beforeunload",function(){M.disable()})}}(this);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // Variable to clear interval
  var removeInterval;


  // Declare all functions asap here
  function autoSlide() {
    removeInterval = setInterval(function () {
      var autoIndex = $('.h2o .banner-hero .slider.active').index();
      if (autoIndex >= 2) {
        autoIndex = 0;
      }
      else {
        autoIndex++;
      }
      $('.h2o .banner-hero .slider:eq(' + autoIndex + ')').trigger('click');
    }, 7000);
  }

  function classChange($i, $th, length) {
    $th.addClass('active').removeClass('n-active p-active');
    if ($i === length) {
      $th.parent().find('.slider:eq(' + (0) + ')').addClass('n-active').removeClass('next-active p-active active');
    } else {
      $th.parent().find('.slider:eq(' + ($i + 1) + ')').addClass('n-active').removeClass('next-active p-active active');
    }

    if ($i === 0) {
      $th.parent().find('.slider:eq(' + (length) + ')').addClass('p-active').removeClass('next-active n-active active');
    } else {
      $th.parent().find('.slider:eq(' + ($i - 1) + ')').addClass('p-active').removeClass('next-active n-active active');
      
      
      
    }
  }

  // Function for clone the Active div
  function cloneDiv() {
    var clone = $('.h2o .banner-hero .slider.active').clone().removeClass('active').addClass('p-clone-active'),
            clone2 = $('.h2o .banner-hero .slider.p-active').clone().removeClass('p-clone-active p-active').addClass('n-clone-active');
    $('.h2o .banner-hero .content-wrapper').last().append(clone);
    $('.h2o .banner-hero .content-wrapper').last().append(clone2);
  }

  // Function for remove clone the div
  function removeCloneDiv() {
    $('.h2o .banner-hero .slider.p-clone-active').remove();
    $('.h2o .banner-hero .slider.n-clone-active').remove();
    $('.h2o .banner-hero .slider.next-active').remove();
  }

  function setTime($i, $th, length) {
    setTimeout(function () {
      removeCloneDiv();
      cloneDiv();
      $('.h2o .banner-hero .slider.p-clone-active').css({'left': '', 'z-index': ''});
      $('.h2o .banner-hero .slider.n-clone-active').css({'left': '', 'z-index': ''});
    }, 510);
  }

  // On DOM ready
  $(function () {
    cloneDiv();

    $('.h2o .banner-hero .slider').click(function (e) {
      e.stopPropagation();
      if (!$(this).hasClass('active')) {
        clearInterval(removeInterval);
        var prevIndex = $('.h2o .banner-hero .slider.active'),
                index = $(this).index(),
                $this = $(this),
                length = $('.h2o .banner-hero .slider').length - 3,
                nextLeft = parseInt($('.h2o .banner-hero .slider.n-active').css('left'));
        if ($this.hasClass('p-active')) {
          $('.h2o .banner-hero .slider.n-clone-active').remove();
          var clone2 = $('.h2o .banner-hero .slider.active').clone().removeClass('active p-clone-active p-active').addClass('n-clone-active').css({'left': '0', 'z-index': '2'});
          $('.h2o .banner-hero .content-wrapper').last().append(clone2);
          setTimeout(function () {
            $('.h2o .banner-hero .slider.n-clone-active').css({'left': nextLeft + 'px', 'z-index': '2'});
          }, 100);
        } else {
          $('.h2o .banner-hero .slider.n-clone-active').css({'left': nextLeft + 'px', 'z-index': '2'});
        }

        $('.h2o .banner-hero .slider.p-clone-active').css({'left': '100%', 'z-index': '4'});

        classChange(index, $this, length);
        setTime(index, $this, length);
        autoSlide();
      }
    });

    // Simple parallax code
    function initParallax(scrollTop) {
      if (scrollTop <= $(window).height())
        $('.h2o .banner-hero .content-wrapper').css({
          'top': (scrollTop / 4)
        });
    }

    $(window).on('scroll', function () {
      var scrollTop = $(window).scrollTop();
      initParallax(scrollTop);
    });
  });

  // On window load
  $(window).on('load', function () {
    autoSlide();
  });

})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    if (!$('body').hasClass('ua-ie'))
      $('.banner-with-content .col-two .media-body .bg-img img').css('object-fit', 'cover');

    $('.banner-with-content .col-two').matchHeight();
  });

  // On Window load
  $(window).on('load', function () {
    if (!$('body').hasClass('ua-ie'))
      $('.banner-with-content .col-two .media-body .bg-img img').css('object-fit', 'cover');
  });
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On Window load
  $(window).on('load', function () {
    $('body.h2o.ua-ie .banner-second-level > .bg-img').imageResponsiveIe();
    $('body.h2o.ua-ie .banner-with-subhead > .bg-img').imageResponsiveIe();
    $('body.h2o.ua-ie .banner-fourth-level > .bg-img').imageResponsiveIe();
    $('body.h2o.ua-ie .banner-third-level > .bg-img').imageResponsiveIe();
    $('body.h2o.ua-ie .banner-with-content > .bg-img').imageResponsiveIe();
  });

})(jQuery);
// Use $ instead of $ without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    if (!$('body').hasClass('ua-ie'))
      $('body.h2o .blog-detail .container .bg-img img').css('object-fit', 'cover');

    $(document).on('click', '.blog-detail .blog-info .blog-value .share-option', function (e) {
      e.stopPropagation();
      e.preventDefault();
      var height = $('.addthis_inline_share_toolbox').height(),
              pos = $(this).position();
      if ($(this).hasClass('active')) {
        $('.addthis_inline_share_toolbox').stop(true, true).slideUp(400);
        $(this).removeClass('active').css({'margin-bottom': '0', 'transition': 'all 0.5s ease'});
      }
      else {
        $(this).addClass('active').css({'margin-bottom': height + 'px', 'transition': 'all 0.5s ease'});
        $('.addthis_inline_share_toolbox').css({'top': (pos.top + $(this).height() + 10) + 'px', 'left': pos.left / 2 + 'px'});
        $('.addthis_inline_share_toolbox').stop(true, true).slideDown(400);
      }
    });
    $(document).click(function () {
      $('.addthis_inline_share_toolbox').stop(true, true).slideUp(400);
      $('.blog-detail .blog-info .blog-value .share-option').removeClass('active').css({'margin-bottom': '0', 'transition': 'all 0.5s ease'});
    });
  });

  // On Window Load
  $(window).on('load', function () {
    $('body.h2o .blog-detail .container .bg-img img').imageResponsiveIe();
    if (!$('body').hasClass('ua-ie'))
      $('body.h2o .blog-detail .container .bg-img img').css('object-fit', 'cover');
  });

  // On Window Resize
  $(window).on("resize", function () {
    var height = $('.addthis_inline_share_toolbox').height(),
            pos = $('.blog-detail .blog-info .blog-value .share-option').position(),
            activeHeight = $('.addthis_inline_share_toolbox').innerHeight();
    if ($('.blog-detail .blog-info .blog-value .share-option').hasClass('active')) {
      $('.blog-detail .blog-info .blog-value .share-option').css({'margin-bottom': height + 'px', 'transition': 'all 0.5s ease'});
      $('.addthis_inline_share_toolbox').css({'top': (pos.top + activeHeight - 17) + 'px', 'left': pos.left / 2 + 'px'});
    }
  });
})(jQuery);

(function ($) {
// On DOM ready
  $(function () {
    $('body.h2o .blog-grid .grid-container').masonry({
      itemSelector: '.block-container',
      columnWidth: '.grid-sizer',
      gutter: '.gutter-sizer',
      percentPosition: true
    });
  });
  
  // On Window Load
  $(window).on("load", function () {
    // Get url and set selectbox value
    var url = window.location.search
            , hash = url.substring(url.indexOf('?') + 1)
            , params = hash.split('&');
    $(params).each(function (i, elem) {
      if (elem !== "") {
        elem = elem.split("=");
        $("select#" + elem[0]).val(decodeURIComponent(elem[1])).trigger("change");
      }
    });
  });
  
  // On Window resize
  $(window).on("resize", function () {
    $('body.h2o .blog-grid .grid-container .block-container.inner-content-block').each(function () {
      $(this).find(".bg-img").imageResponsive();
    });
  });
})(jQuery);


//(function ($) {
//  var $itemsPerRow = 4,
//          divs;
//
//  // Add and Remove class on click element
//  function addRemoveClass($t) {
//    var parentDiv = $t.parents('.col-four-wrap');
//    $('.col-four-team-grid .col-four-wrap').not(parentDiv).css({'margin-bottom': '0', 'transition': 'all 0.2s ease'});
//    if ($t.parent('.col-four').hasClass('active')) {
//      $t.parent('.col-four').removeClass('active');
//      removeMargin($t);
//      $('.col-four-team-grid .col-four-wrap').css({'margin-bottom': '0', 'transition': 'all 0.5s ease'});
//    } else {
//      $('.col-four-team-grid .col-four').removeClass('active');
//      $t.parent('.col-four').addClass('active');
//      setTimeout(function () {
//        displayContent($t, parentDiv);
//      }, 210);
//    }
//  }
//
//  // Remove Margin Bottom from click element parent
//  function removeMargin($t) {
//    if (parseInt($t.parents('.col-four-wrap').css('margin-bottom')) === 0) {
//      $t.parents('.col-four-wrap').css({'margin-bottom': '0', 'transition': 'all 0.2s ease'});
//    }
//  }
//
//  // slide Down the main div
//  function removeContent($t) {
//    $('.col-four-team-grid .col-four-team-info-div').stop(true, true).slideUp(200);
//  }
//
//  // Slide Down the main div
//  function displayContent($t, parentDiv) {
//    var pos = $t.parents('.col-four-wrap').position(),
//            height = $t.parent('.col-four').height() + parseInt($('.col-four-team-grid').css('padding-top'));
//    if (parseInt($t.parents('.col-four-wrap').css('margin-bottom')) === 0) {
//      $t.parents('.col-four-wrap').css({'margin-bottom': 350 + 'px', 'transition': 'all 0.5s ease'});
//      $('.col-four-team-grid .col-four-team-info-div').slideUp(200);
//    } else {
//      $('.col-four-team-grid .col-four-team-info-div').css('display', 'none');
//    }
//    $('.col-four-team-grid .col-four-team-info-div').css({'top': pos.top + height + 'px'}).stop(true, true).slideDown(function () {
//      var openHeight = $('.col-four-team-grid .col-four-team-info-div').outerHeight(true) + 10;
//      $t.parents('.col-four-wrap').css({'margin-bottom': openHeight + 'px', 'transition': 'all 0.5s ease'});
//    });
//  }
//
//  // Match the div which element content to show
//  function displayInnerContent($t) {
//    var id = $t.parents('.col-four').attr('id');
//    $('.col-four-team-grid .col-four-team-info-div .item').css('display', 'none');
//    innerLoop(id, $t);
//  }
//
//  // Match the div which element content to show
//  function innerLoop(id, $t) {
//    $('.col-four-team-grid .col-four-team-info-div .item').each(function () {
//      var dataId = $(this).attr('data-id');
//      if (dataId === id) {
//        $(this).delay(200).fadeIn();
//      } else {
//        $(this).css('display', 'none');
//      }
//    });
//  }
//
//  // adjust open div on resize
//  function reiszeOpen($t) {
//    var pos = $t.parents('.col-four-wrap').position(),
//            height = $t.height() + parseInt($('.col-four-team-grid').css('padding-top'));
//    $('.col-four-team-grid .col-four-team-info-div').css({'top': pos.top + height + 'px'});
//    var openHeight = $('.col-four-team-grid .col-four-team-info-div').outerHeight(true) + 10;
//    $t.parents('.col-four-wrap').css({'margin-bottom': openHeight + 'px', 'transition': 'all 0.5s ease'});
//  }
//
//  // Get Items per row
//  function getItemsPerRow() {
//    if (window.innerWidth > 767 && $itemsPerRow != 4) {
//      $itemsPerRow = 4;
//      wrapDiv();
//      $('.col-four-team-grid .col-four-team-info-div').css({'display': 'none'});
//    } else if (window.innerWidth > 480 && window.innerWidth < 768 && $itemsPerRow != 2) {
//      $itemsPerRow = 2;
//      wrapDiv();
//      $('.col-four-team-grid .col-four-team-info-div').css({'display': 'none'});
//    } else if (window.innerWidth < 481 && $itemsPerRow != 1) {
//      $itemsPerRow = 1;
//      wrapDiv();
//      $('.col-four-team-grid .col-four-team-info-div').css({'display': 'none'});
//    }
////    return $itemsPerRow;
//  }
//
//  // On DOM ready
//  $(function () {
//    divs = $(".col-four-team-grid .col-four");
//    $(document).on('click', '.col-four-team-grid .col-four .content', function (e) {
//      e.stopPropagation();
//      var $this = $(this);
//      removeContent($this);
//      displayInnerContent($this);
//      addRemoveClass($this);
//    });
//    $(document).on('click', function (e) {
//      $('.col-four-team-grid .col-four').removeClass('active');
//      $('.col-four-team-grid .col-four-wrap').css({'margin-bottom': '0'});
//      $('.col-four-team-grid .col-four-team-info-div').css({'display': 'none'});
//    });
//
//    $('.col-four-team-grid .col-four').matchHeight();
//    $('.col-four-team-grid .col-four .content').matchHeight();
//
//    wrapDiv();
//  });
//
//  function wrapDiv() {
//    for (var i = 0; i < divs.length; i += $itemsPerRow) {
//      divs.slice(i, i + $itemsPerRow).wrapAll("<div class='col-four-wrap'></div>");
//    }
//  }
//// On Window Load
//  $(window).on("load", function () {
//    $("body.h2o.ua-ie .col-four-team-grid .image").each(function () {
//      var imgUrl = $(this).children("img").prop("src");
//      if (imgUrl) {
//        $(this).css("backgroundImage", 'url(' + imgUrl + ')').css({'background-repeat': 'no-repeat', 'background-position': 'center', 'background-size': 'cover'});
//      }
//    });
//  });
//
//  // On Window resize
//  $(window).on("resize", function () {
//    $('.col-four-team-grid .col-four').each(function () {
//      var $this = $(this);
//      if ($(this).hasClass('active')) {
//        setTimeout(function () {
//          reiszeOpen($this);
//        }, 10);
//      }
//    });
//    $('.col-four-team-grid .col-four').matchHeight();
//    $('.col-four-team-grid .col-four .content').matchHeight();
////    wrapDiv();
//    getItemsPerRow();
//  });
//})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
// Accordion functionality
  $(".col-two-accordion .accordion-toggle").on("click", function () {
    if ($(this).hasClass('rotate-arrow')) {
      $(this).removeClass('rotate-arrow');
      $(this).find(".content").stop(true, true).slideUp(500);
    }
    else {
      // Hide the other panels
      $('.col-two-accordion .accordion-toggle').removeClass('rotate-arrow');
      $(this).addClass('rotate-arrow');
      $(".col-two-accordion .accordion-toggle .content").stop(true, true).slideUp(500);
      $(this).find(".content").stop(true, true).slideDown(500);
    }
  });

})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  $(window).on('load', function () {
    $('body.h2o.ua-ie .col-two-grid .column-wrapper .col-two .bg-img').imageResponsiveIe();
  });
})(jQuery);



(function ($) {
  // On DOM ready
  $(function () {
    $('.contact-form .column-wrapper > .col-two').matchHeight();
  });

  // On Window resize
  $(window).on("resize", function () {
    $('.contact-form .column-wrapper > .col-two').matchHeight();
  }).resize();
})(jQuery);
(function ($) {

  // Add and Remove class on click element
  function addRemoveClass($t) {
    var parentDiv = $t.parents('.col-three-wrap');
    $('.customer-grid .col-three-wrap').not(parentDiv).css({'margin-bottom': '0', 'transition': 'all 0.2s ease'});
    if ($t.parent('.col-three').hasClass('active')) {
      $t.parent('.col-three').removeClass('active');
      removeMargin($t);
      $('.customer-grid .col-three-wrap').css({'margin-bottom': '0', 'transition': 'all 0.5s ease'});
    } else {
      $('.customer-grid .col-three').removeClass('active');
      $t.parent('.col-three').addClass('active');
      setTimeout(function () {
        displayContent($t, parentDiv);
      }, 210);
    }
  }

  // Remove Margin Bottom from click element parent
  function removeMargin($t) {
    if (parseInt($t.parents('.col-three-wrap').css('margin-bottom')) === 0) {
      $t.parents('.col-three-wrap').css({'margin-bottom': '0', 'transition': 'all 0.2s ease'});
    }
  }

  // slide Down the main div
  function removeContent($t) {
    $('.customer-grid .customer-info-div').stop(true, true).slideUp(200);
  }

  // Slide Down the main div
  function displayContent($t, parentDiv) {
    var pos = $t.parents('.col-three-wrap').position(),
            height = $t.parent('.col-three').height() + parseInt($('.customer-grid').css('padding-top'));
    if (parseInt($t.parents('.col-three-wrap').css('margin-bottom')) === 0) {
      $t.parents('.col-three-wrap').css({'margin-bottom': 350 + 'px', 'transition': 'all 0.5s ease'});
      $('.customer-grid .customer-info-div').slideUp(200);
    } else {
      $('.customer-grid .customer-info-div').css('display', 'none');
    }
    $('.customer-grid .customer-info-div').css({'top': pos.top + height + 'px'}).stop(true, true).slideDown(function () {
      var openHeight = $('.customer-grid .customer-info-div').outerHeight(true) + 10;
      $t.parents('.col-three-wrap').css({'margin-bottom': openHeight + 'px', 'transition': 'all 0.5s ease'});
    });
  }

  // Match the div which element content to show
  function displayInnerContent($t) {
    var id = $t.parents('.col-three').attr('id');
    $('.customer-grid .customer-info-div .item').css('display', 'none');
    innerLoop(id, $t);
  }

  // Match the div which element content to show
  function innerLoop(id, $t) {
    $('.customer-grid .customer-info-div .item').each(function () {
      var dataId = $(this).attr('data-id');
      if (dataId === id) {
        $(this).delay(200).fadeIn();
      } else {
        $(this).css('display', 'none');
      }
    });
  }
  
  // adjust open div on resize
  
   function reiszeOpen($t){
     var pos = $t.parents('.col-three-wrap').position(),
                height = $t.height() + parseInt($('.customer-grid').css('padding-top'));
        $('.customer-grid .customer-info-div').css({'top': pos.top + height + 'px'});
        var openHeight = $('.customer-grid .customer-info-div').outerHeight(true) + 10;
        $t.parents('.col-three-wrap').css({'margin-bottom': openHeight + 'px', 'transition': 'all 0.5s ease'});
   }
  // On DOM ready
  $(function () {
    var windowWidth = $(window).width();
    $(document).on('click', '.customer-grid .col-three .content', function (e) {
      e.stopPropagation();
      var $this = $(this);
      removeContent($this);
      displayInnerContent($this);
      addRemoveClass($this);
    });
    $(document).on('click','.customer-info-div .close-icon', function (e) {
      $('.customer-grid .col-three').removeClass('active');
      $('.customer-grid .col-three-wrap').css({'margin-bottom': '0'});
      $('.customer-grid .customer-info-div').slideUp();
    });
    $(document).on('click', function (e) {
      $('.customer-grid .col-three').removeClass('active');
      $('.customer-grid .col-three-wrap').css({'margin-bottom': '0'});
      $('.customer-grid .customer-info-div').slideUp();
    });
  });
// On Window Load
  $(window).on("load", function () {
    $("body.h2o.ua-ie .customer-grid .image").each(function () {
      var imgUrl = $(this).children("img").prop("src");
      if (imgUrl) {
        $(this).css("backgroundImage", 'url(' + imgUrl + ')').css({'background-repeat': 'no-repeat', 'background-position': 'center', 'background-size': 'cover'});
      }
    });
    // Get url and set selectbox value
    var url = window.location.search
            , hash = url.substring(url.indexOf('?') + 1)
            , params = hash.split('&');
    $(params).each(function (i, elem) {
      if (elem !== "") {
        elem = elem.split("=");
        $("input#" + elem[1]).val(decodeURIComponent(elem[1])).trigger("click");
      }
    });
  });
  // On Window resize
  $(window).on("resize", function () {
    $('.customer-grid .col-three').each(function () {
      var $this = $(this);
      if ($(this).hasClass('active')) {
        setTimeout(function(){
          reiszeOpen($this);
        },10);
      }
    });
  });
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
// Accordion functionality
  $(".faq-accordion .accordion-toggle").on("click", function () {
    if ($(this).hasClass('rotate-arrow')) {
      $(this).removeClass('rotate-arrow');
      $(this).find(".content").stop(true, true).slideUp(500);
    }
    else {
      // Hide the other panels
      $('.faq-accordion .accordion-toggle').removeClass('rotate-arrow');
      $(this).addClass('rotate-arrow');
      $(".faq-accordion .accordion-toggle .content").stop(true, true).slideUp(500);
      $(this).find(".content").stop(true, true).slideDown(500); 
    }
  });

})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    if (!$('body').hasClass('ua-ie'))
      $('body.h2o .featured-views-slider .item .bg-img img').css('object-fit', 'cover');

    if ($('.featured-views-slider .slider .item').length > 1) {
      $('.featured-views-slider .slider').slick({
        infinite: true,
        autoplay: true,
        fade: true,
        speed: 800,
        slidesToShow: 1,
        slidesToScroll: 1,
        dots: true,
        arrows: false
      });
    } else {
      return false;
    }
  });
  
  
  $(window).on('load', function () {
    $('body.h2o.ua-ie .featured-views-slider .item .bg-img').imageResponsiveIe();
    if (!$('body').hasClass('ua-ie'))
      $('body.h2o .featured-views-slider .item .bg-img img').css('object-fit', 'cover');
  });
  
})(jQuery);



// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    $('.filter-tabs').on('click', '.tabs-toggle', function () {
      var windowWidth = $(window).width();
      if (windowWidth < 992) {
        $(this).children('.caret').toggleClass('rotate');
        $(this).parent().children('ul').stop(true, true).slideToggle();
      }
    });
    $('.filter-tabs').on('click', '.tabs > li', function () {
       var value = $(this).text();
       var windowWidth = $(window).width();
       $(this).parent().children().removeClass('active');
       $(this).addClass('active');
       $('.filter-tabs .tabs-toggle .text').html(value);
       if (windowWidth < 992) {
        $(this).parent().slideUp();
        $('.filter-tabs .tabs-toggle .caret').removeClass('rotate');
       }
    });
    $('.filter-tabs .filter-btn').click(function(){
      if($(this).hasClass('active')){
        $(this).removeClass('active');
        $('.clear-box').css('display','block');
        $('.filter-info').stop(true,true).slideUp(function(){
        });
      }
      else{
        $(this).addClass('active');
        $('.filter-info').stop(true,true).slideDown();
        $('.clear-box').css('display','none');
      }
    });
    
    $('.filter-info .close-icon').click(function(){
      $('.filter-tabs .filter-btn').removeClass('active');
      $('.filter-info').stop(true,true).slideUp();
      $('.clear-box,.clear-element').fadeIn();
    });
  });
    /*
       * Set Left tabs and navigation as per container dynamically for desktop
       */
    $(window).on('resize', function () {
        var windowWidth = $(window).width();
        if (windowWidth > 991) {
            $('.filter-tabs .caret').removeClass('rotate');
            $('.filter-tabs ul.tabs').removeAttr('style');
        }
    }).resize();
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    // Set Cookie
    function setCookie(cname, cvalue) {
      document.cookie = cname + "=" + cvalue + ";" + ";path=/";
    }

    // Get Cookie
    function getCookie(cname) {
      var name = cname + "=";
      var decodedCookie = decodeURIComponent(document.cookie);
      var ca = decodedCookie.split(';');
      for (var i = 0; i < ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) == ' ') {
          c = c.substring(1);
        }
        if (c.indexOf(name) == 0) {
          return c.substring(name.length, c.length);
        }
      }
      return "";
    }
    
    // Check if Cookiee exists & set it if it doesn't exist
    function checkCookie() {
      var popup_val = getCookie("popup");
      var popupclose_val = getCookie("popupclose");
      if (popup_val === "") {
        setCookie("popup", 'true');
      }
      if (popupclose_val) {
        $('.gbpr-popup').css({"visibility": "hidden", "opacity": "0"});
      } else {
        $('.gbpr-popup').css({"visibility": "visible", "opacity": "1"});
      }
    }

    checkCookie();
    
    $(".gbpr-popup .close-popup").click(function () {
      $(".gbpr-popup").css({"visibility": "hidden", "opacity": "0"});
      setCookie("popupclose", 'true');
    });
  });
})(jQuery);
(function ($) {
  var sel = $('body.h2o .generic-col-two-grid .column-wrapper .col-two');
  // On DOM ready
  $(function () {
    (window.matchMedia("(min-width: 596px)").matches) ? sel.matchHeight() : sel.css('height', 'auto');
  });

  // On Window resize
  $(window).on("resize", function () {
    (window.matchMedia("(min-width: 596px)").matches) ? sel.matchHeight() : sel.css('height', 'auto');
  }).resize();
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On Window load
  $(window).on('load', function () {
    $('body.h2o.ua-ie .intro-with-cta > .bg-img').imageResponsiveIe();
  });

})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  function initialize($sel, $lat, $long) {
    if ($('#' + $sel).length <= 0)
      return !1;
    var mapStyles = [
      {
        "featureType": "administrative",
        "elementType": "labels.text.fill",
        "stylers": [
          {"color": "#F4F4F3"},
          {"hue": "#F4F4F3"},
          {"saturation": "0"},
          {"gamma": "0"},
          {"lightness": "0"}
        ]
      },
      {
        "featureType": "administrative.province",
        "elementType": "geometry.stroke",
        "stylers": [
          {"visibility": "off"}
        ]
      },
      {
        "featureType": "landscape",
        "elementType": "geometry",
        "stylers": [
          {"lightness": "0"},
          {"saturation": "0"},
          {"color": "#F4F4F3"},
          {"gamma": "1"}
        ]
      },
      {
        "featureType": "landscape.man_made",
        "elementType": "all",
        "stylers": [
          {"lightness": "-3"},
          {"gamma": "1.00"}
        ]
      },
      {
        "featureType": "landscape.natural.terrain",
        "elementType": "all",
        "stylers": [
          {"visibility": "off"}
        ]
      },
      {
        "featureType": "poi",
        "elementType": "all",
        "stylers": [
          {"visibility": "off"}
        ]
      },
      {
        "featureType": "poi.park",
        "elementType": "geometry.fill",
        "stylers": [
          {"color": "#333333"},
          {"visibility": "simplified"}
        ]
      },
      {
        "featureType": "road",
        "elementType": "all",
        "stylers": [
          {"saturation": -100},
          {"lightness": 45},
          {"visibility": "simplified"}
        ]
      },
      {
        "featureType": "road.highway",
        "elementType": "all",
        "stylers": [
          {"visibility": "simplified"}
        ]
      },
      {
        "featureType": "road.highway",
        "elementType": "geometry.fill",
        "stylers": [
          {"color": "#FFE530"},
          {"visibility": "simplified"}
        ]
      },
      {
        "featureType": "road.highway",
        "elementType": "labels.text",
        "stylers": [
          {"visibility": "simplified"},
          {"color": "#FFE530"}
        ]
      },
      {
        "featureType": "road.arterial",
        "elementType": "labels.text.fill",
        "stylers": [
          {"visibility": "simplified"},
          {"color": "#FFE530"}
        ]
      },
      {
        "featureType": "road.arterial",
        "elementType": "labels.icon",
        "stylers": [
          {"visibility": "simplified"}
        ]
      },
      {
        "featureType": "transit",
        "elementType": "all",
        "stylers": [
          {"visibility": "simplified"},
          {"color": "#2b2b2b"}
        ]
      },
      {
        "featureType": "transit.station.airport",
        "elementType": "labels.icon",
        "stylers": [
          {"hue": "#2b2b2b"},
          {"saturation": "-77"},
          {"gamma": "0.57"},
          {"lightness": "0"}
        ]
      },
      {
        "featureType": "transit.station.rail",
        "elementType": "labels.text.fill",
        "stylers": [
          {"visibility": "off"},
          {"color": "#2b2b2b"}
        ]
      },
      {
        "featureType": "transit.station.rail",
        "elementType": "labels.icon",
        "stylers": [
          {"hue": "#2b2b2b"},
          {"lightness": "4"},
          {"gamma": "0.75"},
          {"saturation": "-68"}
        ]
      },
      {
        "featureType": "water",
        "elementType": "all",
        "stylers": [
          {"color": "#C9C9C9"},
          {"visibility": "on"}
        ]
      },
      {
        "featureType": "water",
        "elementType": "geometry.fill",
        "stylers": [
          {"color": "#C9C9C9"}
        ]
      },
      {
        "featureType": "water",
        "elementType": "labels.text.fill",
        "stylers": [
          {"lightness": "-49"},
          {"saturation": "-53"},
          {"gamma": "0.79"}
        ]
      }
    ]
            , mapProperties = {
              scrollwheel: !1,
              disableDefaultUI: !0,
              panControl: !0,
              zoomControl: false,
              mapTypeControl: !1,
              scaleControl: !0,
              streetViewControl: !1,
              overviewMapControl: !1,
              zoom: 8,
              center: new google.maps.LatLng($lat, $long),
              styles: mapStyles
            }
    , map = new google.maps.Map(document.getElementById($sel), mapProperties);
  }

  // Initialize all maps with generic map object
  function initMap() {
    initialize("google_map1", 40.742994, -73.983900);
    initialize("google_map2", 43.649714, -79.388878);
    initialize("google_map3", 50.092409, 14.452772);
  }

  $(window).on("load", function () {
    initMap();
  });
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    $('.logo-slider .logo-wrapper').slick({
      infinite: true,
      speed: 800,
      arrows: false,
      dots: true,
      autoplay: true,
      autoplaySpeed: 3000,
      slidesToShow: 6,
      slidesToScroll: 3,
      responsive: [
        {
          breakpoint: 1023,
          settings: {
            slidesToShow: 5,
            slidesToScroll: 3
          }
        },
        {
          breakpoint: 767,
          settings: {
            slidesToShow: 4,
            slidesToScroll: 2
          }
        },
        {
          breakpoint: 595,
          settings: {
            slidesToShow: 3,
            slidesToScroll: 2
          }
        },
        {
          breakpoint: 480,
          settings: {
            slidesToShow: 2,
            slidesToScroll: 2
          }
        }
      ]
    });
  });
})(jQuery);
(function () {
  // Config Area - replace with your instance values
  var formIds = [1349],
          podId = '//app-ab12.marketo.com',
          munchkinId = '644-PKX-778';

  // No need to touch anything below this line
  var MKTOFORM_ID_PREFIX = 'mktoForm_', MKTOFORM_ID_ATTRNAME = 'data-formId';

  formIds.forEach(function (formId) {
    var loadForm = MktoForms2.loadForm.bind(
            MktoForms2,
            podId,
            munchkinId,
            formId
            ),
            formEls = [].slice.call(
            document.querySelectorAll('[' + MKTOFORM_ID_ATTRNAME + '="' + formId + '"]')
            );

    (function loadFormCb(formEls) {
      var formEl = formEls.shift();
      formEl.id = MKTOFORM_ID_PREFIX + formId;
      loadForm(function (form) {
        formEl.id = '';
        formEls.length && loadFormCb(formEls);
        form.getFormElem().find('button.mktoButton').html('Submit');
      });
    })(formEls);
  });
})();

// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    $('.open-source-community .logo-wrapper').slick({
      infinite: true,
      speed: 800,
      arrows: false,
      dots: true,
      autoplay: true,
      autoplaySpeed: 3000,
      slidesToShow: 6,
      slidesToScroll: 3,
      responsive: [
        {
          breakpoint: 1023,
          settings: {
            slidesToShow: 5,
            slidesToScroll: 3
          }
        },
        {
          breakpoint: 767,
          settings: {
            slidesToShow: 4,
            slidesToScroll: 2
          }
        },
        {
          breakpoint: 595,
          settings: {
            slidesToShow: 3,
            slidesToScroll: 2
          }
        },
        {
          breakpoint: 480,
          settings: {
            slidesToShow: 2,
            slidesToScroll: 2
          }
        }
      ]
    });
  });
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
    // On DOM ready
    $(function () {
        $('.our-contribution .slider').slick({
            infinite: true,
            speed: 800,
            arrows: false,
            dots: true,
            autoplay: true, 
            autoplaySpeed: 3000,
            slidesToShow: 1,
            slidesToScroll: 1
        });
    });
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // Active slide index
    var activeSlideIndex = 0,
            // Store slick slider object
            slick;

  // Return video id from any youtube link
  function getVideoId(url) {
    var regExp = /^.*(youtu.be\/|v\/|u\/\w\/|embed\/|watch\?v=|\&v=)([^#\&\?]*).*/;
    var match = url.match(regExp);
    if (match && match[2].length == 11) {
      return match[2];
    } else {
      return 'error';
    }
  }

  // Set consition based on window width for slides
  function fixLessSlides(slideCount, slides, index) {
    // If window width is less than 992
    if (window.matchMedia("(max-width: 991px)").matches) {
      $('.portfolio-infographic .infographic-slider .tab-content .col').off('click');

      // If window width is less than 481px
      if (window.matchMedia("(max-width: 480px)").matches) {
        return;
      }

      // If window width is greater than 481px & less than 992px
      else {

        // If there are 2 slides
        if (slideCount === 2) {
          $('.portfolio-infographic .infographic-slider .tab-content .col').clone().insertAfter('.portfolio-infographic .infographic-slider .tab-content .col:last-of-type');
          slick.slick('reinit');
          slick.slick('slickGoTo', 0);
        } else {
          return;
        }
      }
    }

    // If window width is greater than 992px
    else {

      // If there are less than 4 slides or greater than 1 slide
      if (slideCount > 1 && slideCount < 4) {

        // If there are 3 slides
        if (slideCount === 3) {
          $('.portfolio-infographic .infographic-slider .tab-content .col').clone().insertAfter('.portfolio-infographic .infographic-slider .tab-content .col:last-of-type');
          slick.slick('reinit');
          slick.slick('slickGoTo', 0);
        }

        // If there are 2 slides
        else if (slideCount === 2) {
          $(document).on('click', '.portfolio-infographic .infographic-slider .tab-content .' + index, function (e) {
            if (!$(this).hasClass('slick-current')) {
              e.preventDefault();
              $(this).parent().find('.col').removeClass('slick-current');
              $(this).addClass('slick-current');
              $('body').removeClass('overlay-active');
              $('.video-overlay').find('iframe').attr('src', '');
            }
          });
        }
      }

      // If there are greater than 4 slides or 1 slide
      else {
        return;
      }
    }
  }

  // Make images responsive
  function imageResponsiveIe() {
    if ($('body.h2o').hasClass('ua-ie')) {
      $(".h2o .portfolio-infographic .infographic-slider .tab-content .col").each(function () {
        var $container = $(this).find(".background-content .bg-img"),
                imgUrl = $container.find("img").prop("src");
        if (imgUrl) {
          $container.css("backgroundImage", 'url(' + imgUrl + ')').addClass("custom-object-fit");
        }
      });
    }
  }

  // Function to adjust position of elements in container
  function adjustPosition() {
    var leftOffset = $('.container').offset().left,
            containerWidth = $('.container').width(),
            slideNavWidth = $('.slide-nav').width(),
            slideHeight = $('.portfolio-infographic .infographic-slider .col .v-middle-wrapper').height();
    $('.portfolio-infographic .infographic-slider').css({'left': leftOffset + 20 + 'px', 'width': 'calc(100% - (' + (leftOffset + 20 + 'px') + '))'});
    $('.portfolio-infographic .infographic-slider .slide-nav').css({'left': containerWidth - slideNavWidth - 14 + 'px'});
  }

  // On DOM ready
  $(function () {
    // Initialize slick slider
    slick = $('.portfolio-infographic .infographic-slider .tab-content').find('.slider').slick({
//      infinite: false,
      speed: 800,
      slidesToShow: 3,
      slidesToScroll: 1,
      prevArrow: $('.portfolio-infographic .infographic-slider .slide-nav .left'),
      nextArrow: $('.portfolio-infographic .infographic-slider .slide-nav .right'),
      responsive: [
        {
          breakpoint: 992,
          settings: {
            slidesToShow: 2
          }
        },
        {
          breakpoint: 481,
          settings: {
            slidesToShow: 1
          }
        }
      ]
    });
    slick.slick('slickUnfilter');
    slick.slick('slickFilter', '.finance');

    imageResponsiveIe();

    // Change slides on click of tabs
    $('.portfolio-infographic .infographic-slider .tabs').on('click', 'ul > li', function () {
//      updateSlide($(this).index());
      if (!$(this).hasClass('active')) {
        var index = $(this).attr('id'),
                $text = $(this).children('span').text(),
                windowWidth = $(window).width();
        $(this).parent().children('li').removeClass('active');
        $(this).addClass('active');
        slick.slick('slickUnfilter');
        slick.slick('slickFilter', '.' + index);
        slick.slick('slickGoTo', 0);


        var slides = slick[0].slick.$slides,
                slideCount = slick[0].slick.slideCount;
        fixLessSlides(slideCount, slides, index);
      }
      $(this).parents('.tabs').find('button .text').html($text);
      if (windowWidth < 596) {
        $('.portfolio-infographic .infographic-slider .tabs button .caret').removeClass('rotate');
        $(this).parent().stop(true, true).slideUp();
      }
      imageResponsiveIe();
    });

    $('.portfolio-infographic .infographic-slider .tabs').on('click', 'button', function () {
      var windowWidth = $(window).width();
      if (windowWidth < 596) {
        $(this).children('.caret').toggleClass('rotate');
        $(this).parent().children('ul').stop(true, true).slideToggle();
      }
    });
  });

  // Code to run on Orientation change
  $(window).on('orientationchange', function () {
    setTimeout(function () {
//        (window.matchMedia("(max-width: 1023px)").matches) ? true : false;
      slick.slick('reinit');
      slick.slick('slickUnfilter');
      slick.slick('slickFilter', '.finance');
      $('.portfolio-infographic .infographic-slider .tabs > ul > li').removeClass('active');
      $('.portfolio-infographic .infographic-slider .tabs > ul > li:first-of-type').addClass('active');
      slick.slick('slickGoTo', 0);
    }, 5);
    
    // Set Left tabs and navigation as per container dynamically for mobile devices
    setTimeout(function () {
      adjustPosition();
//        $('.ua-ie.h2o .portfolio-infographic .tab-content .slider .col.slick-current .background-content .bg-img').imageResponsive();
    }, 10);
  });

  /*
   * Set Left tabs and navigation as per container dynamically for desktop
   */
  $(window).on('resize', function () {
//      $('.ua-ie.h2o .portfolio-infographic .tab-content .slider .col.slick-current .background-content .bg-img').imageResponsive();
    var windowWidth = $(window).width();
    if (windowWidth > 595) {
      $('.portfolio-infographic .infographic-slider .tabs button .caret').removeClass('rotate');
      $('.portfolio-infographic .infographic-slider .tabs > ul').removeAttr('style');
    }
    adjustPosition();
  }).resize();
})(jQuery);
// Use $ instead of $ without replacing global $
(function ($) {
  var sel = $('.products-overview .column-wrapper .two-col-outer .col-two'),
          sel2 = $('.products-overview .column-wrapper .two-col-outer .col-two a:first-child');
          
  // On DOM ready
  $(function () {
    (window.matchMedia("(min-width: 481px)").matches) ? sel.matchHeight() : sel.css('height', 'auto');
    (window.matchMedia("(min-width: 481px)").matches) ? sel2.matchHeight() : sel2.css('height', 'auto');
  });

  // On Window resize
  $(window).on("resize", function () {
    (window.matchMedia("(min-width: 481px)").matches) ? sel.matchHeight() : sel.css('height', 'auto');
    (window.matchMedia("(min-width: 481px)").matches) ? sel2.matchHeight() : sel2.css('height', 'auto');
  }).resize();
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  var sel = $('.reference-customers .column-wrapper .col-two');

  // On DOM ready
  $(function () {

    (window.matchMedia("(min-width: 596px)").matches) ? sel.matchHeight() : sel.css('height', 'auto');
      $('body.h2o.ua-ie .reference-customers .column-wrapper .col-two.img-block .bg-img').imageResponsiveIe();
  });

  // On Window resize
  $(window).on("resize", function () {

    (window.matchMedia("(min-width: 596px)").matches) ? sel.matchHeight() : sel.css('height', 'auto');
      $('body.h2o.ua-ie .reference-customers .column-wrapper .col-two.img-block .bg-img').imageResponsiveIe();
  }).resize();
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    if (!$('body').hasClass('ua-ie'))
      $('.related-resources-slider  .col-three > .bg-img img').css('object-fit', 'cover');

    $('.related-resources-slider .slider-1').slick({
      infinite: true,
      speed: 800,
      autoplay: false,
      autoplaySpeed: 3000,
      slidesToShow: 3,
      slidesToScroll: 1,
      prevArrow: $('.related-resources-slider .slide-nav .prev-slide'),
      nextArrow: $('.related-resources-slider .slide-nav .next-slide'),
      responsive: [
        {
          breakpoint: 991,
          settings: {
            slidesToShow: 2
          }
        },
        {
          breakpoint: 595,
          settings: {
            slidesToShow: 1
          }
        }
      ]
    });
  });

  // On Window load
  $(window).on('load', function () {
    $('.ua-ie .related-resources-slider .col-three > .bg-img').imageResponsiveIe();
    if (!$('body').hasClass('ua-ie'))
      $('.related-resources-slider  .col-three > .bg-img img').css('object-fit', 'cover');
  });
})(jQuery);

// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    if (!$('body').hasClass('ua-ie'))
      $('.related-post-slider .item > .bg-img img').css('object-fit', 'cover');

    $('.related-post-slider .post-slider').slick({
      infinite: true,
      speed: 800,
      arrows: true,
      autoplay: true,
      autoplaySpeed: 3000,
      slidesToShow: 2,
      slidesToScroll: 1,
      prevArrow: $('.related-post-slider .prev-slide'),
      nextArrow: $('.related-post-slider .next-slide'),
      responsive: [
        {
          breakpoint: 595,
          settings: {
            slidesToShow: 1
          }
        }
      ]
    });
  });

  // On Window load
  $(window).on('load', function () {
    $('.ua-ie .related-post-slider .item .bg-img').imageResponsiveIe();
    if (!$('body').hasClass('ua-ie'))
      $('.related-post-slider .item > .bg-img img').css('object-fit', 'cover');
  });
})(jQuery);

(function ($) {
  function searchValue() {
    var searchString = window.location.search;
    var searchKeyword = searchString.substring(3);
    if (searchKeyword.length > 0) {

      $("body.h2o .search-banner form .clear-field").show();
    }
  }

  function searchform() {
    $("body.h2o .search-banner form").addClass("search-banner-form");
    $("body.h2o .search-banner form .search-field").attr("placeholder", "Start your serch here...");
    $("body.h2o .search-banner form .search-submit").addClass("btn-black");
    if ($("body.h2o .search-banner form .clear-field").length < 1) {
      $("body.h2o .search-banner form").append('<span class="clear-field"></span>');
    }
  }

  // On DOM ready
  $(function () {
    $(".search-banner .bg-img").imageResponsive();
    $("body.h2o .search-banner form input[type=text]").on("keyup", function () {
      var valLength = $(this).val();
      if (valLength.length > 0) {
        $("body.h2o .search-banner form .clear-field").show();
      } else {
        $("body.h2o .search-banner form .clear-field").hide();
      }
    });
    $(document).on("click", "body.h2o .search-banner form .clear-field", function () {
      $("body.h2o .search-banner form input[type=text]").val("");
      $("body.h2o .search-banner form .clear-field").hide();
    });
    searchValue();
    //searchform();
  });

  // On Window load
  $(window).on("load", function () {
    $(".search-banner .bg-img").imageResponsive();
    searchValue();
    //searchform();
  });

  // On Window resize
  $(window).on("resize", function () {
    $(".search-banner .bg-img").imageResponsive();
  });
})(jQuery);

(function ($) {
    // On DOM ready
    $(function () {
        $('.solutions-grid .column-wrapper > .col-two').matchHeight();
    });

    // On Window resize
    $(window).on("resize", function () {
        $('.solutions-grid .column-wrapper > .col-two').matchHeight();
    }).resize(); 
})(jQuery);
// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
  $(function () {
    $('.speakers-slider .content-wrapper').slick({
      infinite: false,
      speed: 800,
      arrows: false,
      dots: true,
      autoplay: true,
      autoplaySpeed: 3000,
      slidesToShow: 3,
      slidesToScroll: 1,
      responsive: [
        {
          breakpoint: 767,
          settings: {
            slidesToShow: 2 
          }
        },
        {
          breakpoint: 595,
          settings: {
            slidesToShow: 2
          }
        },
        {
          breakpoint: 480,
          settings: {
            slidesToShow: 1
          }
        }
      ]
    });
  });

  $(window).on("load", function () {
    $('.speakers-slider').css({"opacity": "1"});
  });

})(jQuery);
// Use $ instead of $ without replacing global $
(function ($) {
  // Define all variables
  var flag = true,
          targetScroll = 0,
          sectionposition = 0,
          scrollId = 0;

  // To limit the rate of function call
  function debounce(func, wait, immediate) {
    var timeout;
    return function () {
      var context = this, args = arguments;
      var later = function () {
        timeout = null;
        if (!immediate)
          func.apply(context, args);
      };
      var callNow = immediate && !timeout;
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
      if (callNow)
        func.apply(context, args);
    };
  }


  function navResize() {
    /* Nav for responsive */
    if ($(".sticky-nav.fixed-nav").length > 0) {
      var navHeight = $(".sticky-nav .container").outerHeight(),
              logoLeft = $("body.h2o header .logo").offset().left;
      $(".sticky-nav.fixed-nav").css({"height": navHeight + "px"});
      $(".sticky-nav.fixed-nav .container").css({"padding-left": logoLeft + "px"});
    }
  }

  function scrollQuery($target) {
    var $navOffest = $("body.h2o header").outerHeight(),
            $tocContentOffset = $(".sticky-nav").outerHeight(),
            scrolled = $(document).scrollTop(),
            targetPosition = ($target.offset().top) - scrolled;
    if ($(".sticky-nav.fixed-nav").length > 0 && (targetPosition < 0)) {
      targetScroll = $tocContentOffset + $navOffest;
    } else {
      targetScroll = $tocContentOffset;
    }
  }

  function smoothScroll() {
    // Active nav on click
    $('.h2o .sticky-nav ul li a').on('click', function (a) {
      a.preventDefault();
      $(".h2o .sticky-nav ul li").removeClass("active");
      $(this).parent().addClass("active");
    });
    // Scroll to section on click
    $('.h2o .sticky-nav ul li a[href^="#"]').on('click', function (e) {
      e.preventDefault();
      flag = false;
      var $this = this,
              targetID = $this.hash,
              $target = $(targetID);
      scrollQuery($target);
      $('html, body').stop().animate({
        'scrollTop': ($target.offset().top - targetScroll)
      }, 800, 'swing');
      setTimeout(function () {
        flag = true;
      }, 900);
    });
  }

  function activeonscrollUp() {
    $("body.h2o .sticky-nav ul li").each(function () {
      if (flag === true) {
        scrollId = $(this).find("a").attr("href");
        if ($(scrollId).length > 0) {
          var scrollTop = $(document).scrollTop(),
                  sectionOffset = $(scrollId).offset().top,
                  $navOffest = $("body.h2o header").outerHeight(),
                  $tocContentOffset = $(".sticky-nav").outerHeight();
          sectionposition = sectionOffset - scrollTop;
          if ($(".sticky-nav.fixed-nav").length > 0 && (sectionposition < 0)) {
            targetScroll = $tocContentOffset + $navOffest;
          } else {
            targetScroll = $tocContentOffset;
          }
          if (sectionposition <= targetScroll) {
            $(".h2o .sticky-nav ul li").removeClass("active");
            $(this).addClass("active");
          }
        }
      }
    });
  }

  function activeonscrollDown() {
    $("body.h2o .sticky-nav ul li").each(function () {
      if (flag === true) {
        scrollId = $(this).find("a").attr("href");
        if ($(scrollId).length > 0) {
          var scrollTop = $(document).scrollTop(),
                  sectionOffset = $(scrollId).offset().top,
                  $navOffest = $("body.h2o header").outerHeight(),
                  $tocContentOffset = $(".sticky-nav").outerHeight(),
                  windowHalf = ($(window).height()) / 2;
          sectionposition = sectionOffset - scrollTop;
          if ($(".sticky-nav.fixed-nav").length > 0 && $("body.h2o header").hasClass("off-canvas")) {
            targetScroll = $tocContentOffset + $navOffest;
          } else {
            targetScroll = $tocContentOffset;
          }
          if (sectionposition >= targetScroll && windowHalf >= sectionposition) {
            $(".h2o .sticky-nav ul li").removeClass("active");
            $(this).addClass("active");
          }
        }

      }
    });
  }

  // On DOM ready
  $(function () {
    $("body.h2o .sticky-nav ul li").each(function () {
      scrollId = $(this).find("a").attr("href");
      if ($(scrollId).length <= 0) {
        $(this).hide();
      }
    });
  });

  // On Window scroll & load
  $(window).on('scroll load', function () {
    if ($(".sticky-nav").length > 0) {
      /* Fix nav on scroll */
      var scrolled = $(document).scrollTop(),
              scollTop = $(".sticky-nav").offset().top,
              logoLeft = $("body.h2o header .logo").offset().left,
              navPosition = scollTop - scrolled,
              navHeight = $(".sticky-nav .container").height();
      if ((scrolled > scroll)) {
        if (navPosition <= 0) {
          $(".sticky-nav").addClass("fixed-nav").css({"height": navHeight + "px"});
          $(".sticky-nav .container").css({"padding-left": logoLeft + "px", "top": "0"});
        }
        activeonscrollUp();
      } else if ((scrolled < scroll)) {
        if ($("body.h2o header").hasClass("off-canvas")) {
          var headerHeight = $("body.h2o header").outerHeight();
          if (navPosition > headerHeight) {
            $(".sticky-nav").removeClass("fixed-nav").removeAttr("style");
            $(".sticky-nav .container").removeAttr("style");
          } else {
            $(".sticky-nav").addClass("fixed-nav").css({"height": navHeight + "px"});
            if ($("body.h2o header").hasClass("fixed")) {
              var headerHeight = $("body.h2o header").outerHeight();
              $(".sticky-nav .container").css({"top": headerHeight + "px", "padding-left": logoLeft + "px"});
            }
          }
        } else {
          if (navPosition > 0) {
            $(".sticky-nav").removeClass("fixed-nav").removeAttr("style");
            $(".sticky-nav .container").removeAttr("style");
          } else {
            if ($("body.h2o header").hasClass("fixed")) {
              $(".sticky-nav").removeClass("fixed-nav");
              var headerHeight = $("body.h2o header").outerHeight();
              $(".sticky-nav .container").css("top", headerHeight + "px");
            }
          }
        }
        activeonscrollDown();
      }
      scroll = $(document).scrollTop();
    }
  });

  // Bind logic to debounce
  var initOnResize = debounce(function () {
    navResize();
  }, 140);
  $(window).on('load', function () {
    smoothScroll();
  });
  window.addEventListener('resize', initOnResize);

  // On orientation change
  $(window).on('orientationchange', function () {
    setTimeout(function () {
      if($(".sticky-nav").length > 0){
      var scrolled = $(document).scrollTop(),
            scollTop = $(".sticky-nav").offset().top,
              navPosition = scollTop - scrolled;
      if (navPosition > 0) {
        $(".sticky-nav").removeClass("fixed-nav").removeAttr("style");
        $(".sticky-nav .container").removeAttr("style");
      }
    }
    }, 100);
  });
})(jQuery);
(function ($) {
    // Add and Remove class on click element
    function addRemoveClass($t) {
        var parentDiv = $t.parents('.col-four');
        $('.team-profile .col-four-wrap').not(parentDiv).css({'margin-bottom': '0', 'transition': 'all 0.2s ease'});
        if ($t.parent('.col-four').hasClass('active')) {
            $t.parent('.col-four').removeClass('active');
            removeMargin($t);
            $('.team-profile .col-four-wrap').css({'margin-bottom': '0', 'transition': 'all 0.5s ease'});
        }
        else {
            $('.team-profile .col-four').removeClass('active');
            $t.parent('.col-four').addClass('active');
            setTimeout(function () {
                displayContent($t, parentDiv);
            }, 210);
        }
    }

    // Remove Margin Bottom from click element parent
    function removeMargin($t) {
        if (parseInt($t.parents('.col-four-wrap').css('margin-bottom')) === 0) {
            $t.parents('.col-four-wrap').css({'margin-bottom': '0', 'transition': 'all 0.2s ease'});
        }
    }

    // slide Down the main div
    function removeContent($t) {
        $('.team-profile .team-info-div').stop(true, true).slideUp(200);
    }

    // Slide Down the main div
    function displayContent($t, parentDiv) {
        var pos = $t.parents('.col-four-wrap').position(),
            height = $t.parent('.col-four').height() + parseInt($('.team-profile').css('padding-top'));
        if (parseInt($t.parents('.col-four-wrap').css('margin-bottom')) === 0) {
            $t.parents('.col-four-wrap').css({'margin-bottom': 250 + 'px', 'transition': 'all 0.5s ease'});
            $('.team-profile .team-info-div').slideUp(200);
        } else {
            $('.team-profile .team-info-div').css('display', 'none');
        }
        $('.team-profile .team-info-div').css({'top': pos.top + height + 'px'}).stop(true, true).slideDown(function () {
            var openHeight = $('.team-profile .team-info-div').outerHeight(true) + 10;
            $t.parents('.col-four-wrap').css({'margin-bottom': openHeight + 'px', 'transition': 'all 0.5s ease'});
        });
    }

    // Match the div which element content to show 
    function displayInnerContent($t) {
        var id = $t.parents('.col-four').attr('id');
        $('.team-profile .team-info-div .info-div').css('display', 'none');
        innerLoop(id, $t);
    }

    // Match the div which element content to show
    function innerLoop(id, $t) {
        $('.team-profile .team-info-div .info-div').each(function () {
            var dataId = $(this).attr('data-id');
            if (dataId === id) {
                $(this).delay(200).fadeIn();
            } else {
                $(this).css('display', 'none');
            }
        });
    }

    // adjust open div on resize

    function reiszeOpen($t) {
        var pos = $t.parents('.col-four-wrap').position(),
            height = $t.height() + parseInt($('.team-profile').css('padding-top'));
        $('.team-profile .team-info-div').css({'top': pos.top + height + 'px'});
        var openHeight = $('.team-profile .team-info-div').outerHeight(true) + 10;
        $t.parents('.col-four-wrap').css({'margin-bottom': openHeight + 'px', 'transition': 'all 0.5s ease'});
    }

    // On DOM ready
    $(function () {
        var windowWidth = $(window).width();
        $(document).on('click', '.team-profile .col-four .content', function (e) {
            e.stopPropagation();
            var $this = $(this);
            removeContent($this);
            displayInnerContent($this);
            addRemoveClass($this);
        });
        $('.team-profile .team-info-div .info-div .close-btn').on('click', function (e) {
            $('.team-profile .col-four-wrap .col-four').removeClass('active');
            $('.team-profile .col-four-wrap').css({'margin-bottom': '0'});
            $('.team-profile .team-info-div').css({'display': 'none'});

        });
    });
// On Window Load
    $(window).on("load", function () {
        $("body.h2o.ua-ie .team-profile .image").each(function () {
            var imgUrl = $(this).children("img").prop("src");
            if (imgUrl) {
                $(this).css("backgroundImage", 'url(' + imgUrl + ')').css({
                    'background-repeat': 'no-repeat',
                    'background-position': 'center',
                    'background-size': 'cover'
                });
            }
        });
    });

    // On Window resize
    $(window).on("resize", function () {
        $('.team-profile .col-four').each(function () {
            var $this = $(this);
            if ($(this).hasClass('active')) {
                setTimeout(function () {
                    reiszeOpen($this);
                }, 10);
            }
        });
    });
})(jQuery);
// Use $ instead of $ without replacing global $
(function ($) {
  // Return video id from any youtube link
  function getVideoId(url) {
    var regExp = /^.*(youtu.be\/|v\/|u\/\w\/|embed\/|watch\?v=|\&v=)([^#\&\?]*).*/;
    var match = url.match(regExp);
    if (match && match[2].length == 11) {
      return match[2];
    } else {
      return 'error';
    }
  }
  
  // On Orientation change
  $(window).on('orientationchange', function () {
    setTimeout(function () {
     calcVideoRatio();
    },50);
  });
})(jQuery);

$(document).ready(function(){
	$('.navbar-burger').click(function(){
		$('.navbar-menu').toggle();
	});
})
$(window).scroll(function() {
		if ($(this).scrollTop() > 50 ) {
				$('.scrolltop:hidden').stop(true, true).fadeIn();
		} else {
				$('.scrolltop').stop(true, true).fadeOut();
		}
});
$(function(){$(".scrollitem").click(function(){$("html,body").animate({scrollTop:$("body").offset().top},"1000");return false})})
$(window).scroll(function() {    
		var scroll = $(window).scrollTop();
		if (scroll >= 250) {
			$(".navbar").addClass("has-shadow");
		} else {
			$(".navbar").removeClass("has-shadow");
		}
}); //missing );

$(function(){
	$('table').addClass('table table-condensed table-responsive'); 
	// jQuery('[data-toggle="popover"]').popover();
});
function setupAutocomplete(site){
	$('input.navquery').devbridgeAutocomplete({
  		lookup: jQuery.map(site,function (page) {
  			return {value: page.alltitles, key: page.title, data:page};
  		}),
  		onSelect: function(suggestion){
  			document.location=window._pageRelpath+suggestion.data.outpath;
  		}
	});
}
/*!
 * jQuery Expandable Input Plugin v1.0
 * https://github.com/armmer1/expandable-input
 *
 * Copyright 2014, Nattawat Nonsung
 */


/**
* Use immediately Invoked Function Expression to
* - Prevent conflicting with other libary on alias $
* - Scope varaible to be private
*/
(function( $ ) {
  $.fn.expandable = function(options) {

		// Define default setting
		var settings = $.extend({
			width: 150,
			duration: 300
    }, options );

		var width = this.outerWidth();

		this.on("focus", function(){
			// $(this).css({width: settings.width});
			$(this).animate({
	      width: settings.width
	    }, settings.duration, function(){
	      // callback function
	    });
		});

		this.on("blur", function(){
			$(this).animate({
	      width: width
	    }, settings.duration, function(){
	      // callback function
	    });
		});

		if(settings.action && typeof(settings.action) == "function"){
			this.on('keypress', function(e) {
				if (e.which === 13) { // press enter
					settings.action($(this).val());
				}
			});
		}
		// Return jQuery so that it's chainable 
		return this;		
  };
 
}( jQuery ));


jQuery(function(){
	jQuery.getJSON(window._pageRelpath+'site.json',function(data){
		var site=data;
		setupAutocomplete(site);		
	});
	jQuery("#searchQuery").expandable({
	    		width: 300,
	    		duration: 300
	    	});
	
  $('[data-toggle="tooltip"]').tooltip();
  $('[data-has-toggle="tooltip"]').tooltip();
 
});

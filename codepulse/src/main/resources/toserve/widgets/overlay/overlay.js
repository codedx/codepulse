/**
 * overlay.js
 * By DylanH
 * 
 * Requires jQuery, spin.js and overlay.css to be included on the same page
 */

$(document).ready(function(){

function Overlay(container, _opts){
	
	var opts = _opts || { radius: 4, length: 7, width: 4, lines: 10, spinnerOnly: false }
	
	var spinner = new Spinner(opts)
	var $overlay = $("<div class='overlay'>")
	var spinnerOnly = opts['spinnerOnly']
	var overlay = $overlay[0]
	
	$(container).addClass("overlay-container")
	
	var activated = false
	
	this.activate = function(){
		if(!activated){
			$(container).addClass("overlay-waiting")
			activated = true
			if(!spinnerOnly) $overlay.appendTo(container)
			spinner.spin(overlay)
		}
	}
	
	this.deactivate = function(){
		if(activated){
			$(container).removeClass("overlay-waiting")
			activated = false
			spinner.stop()
			if(!spinnerOnly) $overlay.detach()
		}
	}
	
}

$.fn.overlay = function(cmd, opts) {
	/* allow for usage of $(..).overlay(opts) */
	if(typeof cmd === 'object'){
		opts = cmd
		cmd = undefined
	} else if(typeof cmd !== 'string'){
		console.log('not doing anything... args=', cmd, opts)
		return;
	}
	
	this.each(function(){
	
		var $this = $(this),
			data = $this.data()
		
		if(cmd === 'remove' && data.overlay){
			data.overlay.deactivate()
			delete data.overlay
		}
			
		// want to reset the options, so deactivate the old, and create a new
		if(data.overlay && typeof opts === 'object'){
			data.overlay.deactivate();
			data.overlay = new Overlay(this, opts)
		} 
		// otherwise, just make sure the overlay exists
		else if(!data.overlay) {
			data.overlay = new Overlay(this, opts)
		}
		
		//handle commands
		if(cmd === 'wait' || cmd === 'show' || cmd === 'activate'){
			data.overlay.activate()
		}
		if(cmd === 'ready' || cmd === 'hide' || cmd === 'deactivate'){
			data.overlay.deactivate()
		}
	
	})
	
}

}) // end document.ready
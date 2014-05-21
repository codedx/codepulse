/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Bacon.js Busses and Properties are lazy, so if nothing is listening
// to them, they won't do work. This leads to bugs where a property isn't
// updated when its underlying Bus is updated. This function ensures that
// a listener is added to the Bus or Property, to avoid these bugs.
Bacon.Observable.prototype.noLazy = function(){
	this.onValue(function(){})
	return this
}

Bacon.Model = function(initial){
	var value = initial
	var changes = new Bacon.Bus()
	var prop = changes.toProperty(value).noLazy()

	this.get = function(){ return value }
	this.set = function(v){
		value = v
		changes.push(value)
	}

	this.__defineGetter__('changes', function(){ return changes })
	this.__defineGetter__('property', function(){ return prop })
}

// Mimics `Bacon.later` but doesn't actually start the `delay`
// until an animation frame is available.
Bacon.laterWithRender = function(delay, value){
	return Bacon.fromCallback(function(fire){
		requestAnimationFrame(function(){
			setTimeout(function(){
				fire(value)
			}, delay)
		})
	})
}

// provides `$.externalHref` to force links to open in the default browser
// in embedded mode
$.fn.extend({
	openInBrowser: function() {
		var $this = $(this)
		if (CodePulse.isEmbedded) {
			// open with native browser
			$this.click(function(e) {
				e.preventDefault()
				require('nw.gui').Shell.openExternal($this.attr('href'))
			})
		} else {
			// open in a new tab
			$this.attr('target', '_blank')
		}
	}
})
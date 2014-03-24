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

// jQuery "betterAffix" plugin, by Dylan Halperin @ Applied Visions
//
// This plugin provides the "betterAffix" function for jQuery elements.
// The "betterAffix" function sets up scroll and resize listeners that
// will adjust the target element's "top" and "bottom" CSS values (under
// the assumption that the target element is 'position: fixed') so that
// it will not breach the vertical bounds of a "scope" element.
//
// Example use case: a sidebar should normally take up the entire height
// of the window, except for when the header or footer is visible, where
// it should cede the space to the header and footer.
//
// E.g. `$('#sidebar').betterAffix('#main-content')
//

;(function($, extensionName){

	// The current adjustmentListener is stored in the jQuery data
	// mechanism under this key:
	var dataKey = '__' + extensionName

	/*
	Given two listener functions, unregister the 'oldOne' and
	register the 'newOne' to react to scroll and resize events.
	The 'newOne' should also be run immediately.
	*/
	function swapListeners(oldOne, newOne){
		var $w = $(window)
		if(oldOne){
			$w .off('scroll', oldOne)
			$w .off('resize', oldOne)
		}
		if(newOne){
			$w .on('scroll', newOne)
			$w .on('resize', newOne)
			newOne()
		}
	}

	/* Pretend interface Scope:
	 * function computeTop(){ return Int }
	 * function computeBottom(){ return Int }
	 */

	function WithinItemScope($elem){
		this.computeTop = function(){
			return $elem.position().top
		}
		this.computeBottom = function(){
			return $elem.position().top + $elem.outerHeight()
		}
	}

	function BetweenItemsScope($top, $bottom){
		this.computeTop = function(){
			// the bottom of the 'top' element
			return $top.position().top + $top.outerHeight()
		}
		this.computeBottom = function(){
			// the top of the 'bottom' element
			return $bottom.position().top
		}
	}

	/*
	Returns a function that, when called, will update the 'top' and 'bottom'
	CSS properties of the $target jQuery element so that it stays within the
	vertical bounds of the $scope jQuery element, assuming that its position
	is fixed.
	*/
	function adjustmentListener($target, scope){
		return function(){
			var scrollY = window.scrollY,
			scopeTop = scope.computeTop(),
			scopeBottom = scope.computeBottom(),
			windowHeight = window.innerHeight

			var topInView = (scrollY < scopeTop),
				bottomInView = (scrollY > scopeBottom - windowHeight)

			var topOffset = topInView ? (scopeTop - scrollY) : 0,
				bottomOffset = bottomInView ? (scrollY - (scopeBottom - windowHeight)) : 0

			$target.css('top', topOffset + 'px').css('bottom', bottomOffset + 'px')
		}
	}

	/*
	The "betterAffix" jQuery plugin:

		$(..).betterAffix('targetSelector')   - initialize using the first element selected by $('targetSelector') 
		                                        as the vertical bounds
		$(..).betterAffix($scope)             - initialize with the jQuery `$scope` selection as the vertical bounds
		$(..).betterAffix(scopeElem)          - initialize with the `scopeElem` as the vertical bounds
		$(..).betterAffix()                   - just update the positioning
	*/
	$.fn[extensionName] = function(){

		// If an argument was given, it's an initializer
		if(arguments.length){
			var arg = arguments[0],
				scope = undefined

			if($.isPlainObject(arg) && arg.top && arg.bottom){
				scope = new BetweenItemsScope($(arg.top), $(arg.bottom))
			} 
			else if((typeof arg == 'string') || (arg.constructor === $) || (arg.tagName)) {
				scope = new WithinItemScope($(arg))
			}
			else {
				throw 'illegal argument'
			}

			var currentListener = this.data(dataKey),
				newListener = adjustmentListener(this, scope)
			swapListeners(currentListener, newListener)
			this.data(dataKey, newListener)
		} else {
			var listener = this.data(dataKey)
			if(typeof listener == 'function') listener()
		}

		return this
	}

})(jQuery, 'betterAffix');

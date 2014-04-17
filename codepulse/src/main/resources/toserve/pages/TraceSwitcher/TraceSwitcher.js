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

/*
TraceSwitcher.js

This script provides the `TraceSwitcher` object in global scope.

It also will automatically wire up a button with the "data-toggle"
attribute set to "TraceSwitcher"; the button will cause the
TraceSwitcher's UI to toggle between shown and hidden states (by
calling TraceSwitcher.open() and TraceSwitcher.close() accordingly).

This script will automatically be included with the "TraceSwitcher"
template html. 
*/
;(function(exports){

	// predefinition of jQuery vars that will be assigned when the document is ready
	var slideDownSidebar

	// the main exported variable, defined later
	var TraceSwitcher

	$(document).ready(function(){
		// get references to the moving parts
		slideDownSidebar = $('.traceSwitcher .slideDownSidebar')

		// auto-setup a master button control to toggle the TraceSwitcher view
		$('[data-toggle=TraceSwitcher]').each(function(){
			wireUpController($(this))
		})
	})

	// showSidebar('toggle'|true|false) to show/hide the slideDownSidebar
	function showSidebar(arg){
		if(!slideDownSidebar) return

		var isCollapsed = slideDownSidebar.hasClass('collapsed'),
			shouldBeCollapsed = (arg == 'toggle') ? !isCollapsed : !arg

		slideDownSidebar.toggleClass('collapsed', shouldBeCollapsed)

		return !shouldBeCollapsed
	}

	function wireUpController($button){
		var switcherOpen = false
		$button.click(function(){
			switcherOpen = !switcherOpen
			TraceSwitcher[switcherOpen ? 'open': 'close']()
			$button.toggleClass('active', switcherOpen)
		})
	}

	TraceSwitcher = exports['TraceSwitcher'] = {
		'open': function(){ showSidebar(true) },
		'close': function(){ showSidebar(false) }
	}

})(this);
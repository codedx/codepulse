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
ProjectSwitcher.js

This script provides the `ProjectSwitcher` object in global scope.

It also will automatically wire up a button with the "data-toggle"
attribute set to "ProjectSwitcher"; the button will cause the
ProjectSwitcher's UI to toggle between shown and hidden states (by
calling ProjectSwitcher.open() and ProjectSwitcher.close() accordingly).

This script will automatically be included with the "ProjectSwitcher"
template html. 
*/
;(function(exports){

	// predefinition of jQuery vars that will be assigned when the document is ready
	var slideDownSidebar

	// the main exported variable, defined later
	var ProjectSwitcher

	$(document).ready(function(){
		// get references to the moving parts
		slideDownSidebar = $('.projectSwitcher .slideDownSidebar')

		// auto-setup a master button control to toggle the ProjectSwitcher view
		$('[data-toggle=ProjectSwitcher]').each(function(){
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
			ProjectSwitcher[switcherOpen ? 'open': 'close']()
			$button.toggleClass('active', switcherOpen)
		})
	}

	ProjectSwitcher = exports['ProjectSwitcher'] = {
		'open': function(){ showSidebar(true) },
		'close': function(){ showSidebar(false) }
	}

})(this);
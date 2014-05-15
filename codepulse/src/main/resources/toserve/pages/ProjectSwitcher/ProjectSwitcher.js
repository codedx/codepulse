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

	// flag for if the sidebar is shown
	var isSidebarOpen = false

	// save any trigger buttons
	var triggers = $()

	$(document).ready(function(){
		// get references to the moving parts
		slideDownSidebar = $('.projectSwitcher .slideDownSidebar')

		// update the flag for if the sidebar is open
		isSidebarOpen = !slideDownSidebar.hasClass('collapsed')

		// auto-setup a master button control to toggle the ProjectSwitcher view
		$('[data-toggle=ProjectSwitcher]').each(function(){
			wireUpController($(this))
		})
	})

	// showSidebar('toggle'|true|false) to show/hide the slideDownSidebar
	function showSidebar(arg){
		if(!slideDownSidebar) return

		// if it is already shown, shake it to draw attention
		if(arg == true && isSidebarOpen){
			shakeSidebar()
			return true
		}

		// collapse or expand depending on the argument
		isSidebarOpen = (arg == 'toggle') ? !isSidebarOpen : arg
		slideDownSidebar.toggleClass('collapsed', !isSidebarOpen)

		// update the 'active' state on all of the trigger buttons
		triggers.toggleClass('active', isSidebarOpen)

		// return the new open state
		return isSidebarOpen
	}

	function shakeSidebar(){
		if(!slideDownSidebar) return

		// The shake css class (in ProjectSwitcher.css) starts an animation.
		// When that animation ends, remove the shake class so we can add it again later.
		slideDownSidebar.addClass('shake')
		slideDownSidebar.one('webkitAnimationEnd mozAnimationEnd MSAnimationEnd oanimationend animationend',
			function(){ slideDownSidebar.removeClass('shake') })
	}

	function wireUpController($button){
		triggers = triggers.add($button)
		$button.click(function(){ showSidebar('toggle') })
	}

	ProjectSwitcher = exports['ProjectSwitcher'] = {
		'open': function(){ showSidebar(true) },
		'close': function(){ showSidebar(false) }
	}

})(this);
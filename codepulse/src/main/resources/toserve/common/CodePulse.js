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

 ;(function(CodePulse){

	var homeRegex = /^(.*)\/(index(\.html)?)?$/,
		projectRegex = /^(.*)\/projects\/(\d+)$/,
		currentPath = document.location.pathname,
		homeMatch = homeRegex.exec(currentPath),
		projectMatch = projectRegex.exec(currentPath)

	// The userAgent string expected from the node-webkit client
	CodePulse.nativeUserAgent = 'CodePulse (node-webkit)'

	// The userAgent string provided by the current client
	CodePulse.currentUserAgent = window.navigator.userAgent

	// True if the client is node-webkit; False if the client is a browser
	CodePulse.isEmbedded = (CodePulse.nativeUserAgent == CodePulse.currentUserAgent)

	// Detect which page the user is looking at
	CodePulse.isOnHomePage = homeMatch != null
	CodePulse.isOnProjectPage = projectMatch != null
	CodePulse.projectPageId = projectMatch && parseInt(projectMatch[2])

	// in case CodePulse is running as a non-root .war in some javaEE web container,
	// the path prefix might not be empty.
	CodePulse.pathPrefix = (homeMatch && homeMatch[1]) || (projectMatch && projectMatch[1]) || ''

	// Convenience functions to generate URL paths to the home and project pages.
	CodePulse.homePath = function(){
		return CodePulse.pathPrefix + '/'
	}
	CodePulse.projectPath = function(projectId){
		return CodePulse.pathPrefix + '/projects/' + projectId
	}
	CodePulse.apiPath = function(endpoint){
		return CodePulse.pathPrefix + '/api/' + endpoint
	}

	// Handle external links via the "external-href" attribute
	$(document).ready(function(){

		$('[external-href]').each(function(){
			var $this = $(this),
				href = $this.attr('external-href')
			$this
				.removeAttr('external-href')
				.attr('href', href)

			if(CodePulse.isEmbedded){
				// open with native browser
				$this.click(function(e){
					e.preventDefault()
					require('nw.gui').Shell.openExternal(href)
				})
			} else {
				// open in a new tab
				$this.attr('target', '_blank')
			}
		})
	})

})(this.CodePulse || (this.CodePulse = {}));
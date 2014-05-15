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

/**
 DependencyCheck.js
  - requires API, jQuery, D3, Bacon.js
*/

;(function(exports){

	exports.DependencyCheckController = DependencyCheckController

	function DependencyCheckController($container) {
		var self = this,
			$reportContainer = $('.report-container', $container)

		var reportShownBus = new Bacon.Bus
		this.reportShown = reportShownBus.toProperty(false)

		;(function() {
			var $closeButton = $('.report-header .close-button', $container)
			$closeButton.click(function() {
				self.closeReport()
			})
		})()

		this.showReport = function(node) {
			//!! fetch and render report to $reportContainer
			$reportContainer.text(node.label)
			console.log(node)

			reportShownBus.push(true)
		}

		this.closeReport = function() {
			reportShownBus.push(false)
		}

		// status tracking
		var statusBus = new Bacon.Bus, vulnerableNodesBus = new Bacon.Bus
		this.status = statusBus.toProperty().noLazy()
		this.vulnerableNodes = vulnerableNodesBus.toProperty().noLazy()

		API.getDependencyCheckStatus(function(status) {
			statusBus.push(status)
		})

		API.getVulnerableNodes(function(nodes) {
			vulnerableNodesBus.push(nodes)
		})

		var updateStream = $(document).asEventStream('dependencycheck-update', function(event, args) { return args })

		updateStream.onValue(function(update) {
			statusBus.push(update.summary)
			vulnerableNodesBus.push(update.vulnerableNodes)
		})
	}

})(this)
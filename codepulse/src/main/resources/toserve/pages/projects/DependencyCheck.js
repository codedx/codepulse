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

		function generateReport(report, isFullReport, container) {
			var jars = d3.select(container).select('#vulns').selectAll('li.jar')
				.data(report.vulns, function(d) { return d.jar })

			jars.exit().remove()
			var jarNodes = jars.enter().append('li').classed('jar', true)

			var jarNodeInfo = jarNodes.append('div').classed('jar-info', true)
			jarNodeInfo.append('span').classed('jar-name', true).text(function(d) { return d.jar })

			var cves = jarNodes.append('ul').classed('cve-list', true).selectAll('li.cve').data(function(d) { return d.cves })

			cves.exit().remove()
			var cveNodes = cves.enter().append('li').classed('cve', true)
			var cveHeaders = cveNodes.append('div').classed('cve-header', true)

			cveHeaders.append('a')
				.classed('cve-name', true)
				.attr('external-href', function(d) { return d.url })
				.text(function(d) { return d.name })
			cveHeaders.append('a')
				.classed('cwe-name', true)
				.attr('title', function(d) { return d.cwe.name })
				.attr('external-href', function(d) { return d.cwe.url })
				.text(function(d) { return d.cwe.name })
			cveNodes.append('p').classed('cve-description', true)
				.text(function(d) { return d.description })

			$('#full-report-link', container).attr('external-href', report.report)
			CodePulse.handleExternalHrefs($(container))
		}

		this.showReport = function(node, isFullReport) {
			var reportNodes = []

			;(function findVulnerableNodes(node) {
				if (vulnerableNodeSet.has(node.id)) reportNodes.push(node.id)
				node.children.forEach(findVulnerableNodes)
			})(node)

			API.getDependencyCheckReport(reportNodes, function(report) {
				generateReport(report, isFullReport, $reportContainer[0])
				reportShownBus.push(true)
			})
		}

		this.closeReport = function() {
			reportShownBus.push(false)
		}

		// status tracking
		var statusBus = new Bacon.Bus, vulnerableNodesBus = new Bacon.Bus
		this.status = statusBus.toProperty().noLazy()
		this.vulnerableNodes = vulnerableNodesBus.toProperty().noLazy()

		var vulnerableNodeSet = d3.set()
		this.vulnerableNodes.onValue(function(vulnerableNodes) {
			vulnerableNodeSet = d3.set(vulnerableNodes)
		})

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
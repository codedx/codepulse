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
 PackageWidget.js
  - requires jQuery, D3, Bacon.js
*/

;(function(exports){

	/*
	Returns a "short string" representation of `n`.
	Uses suffixes 'K' (kilo = 1000), 'M' (million), 'B' (billion), or 'T' (trillion) to shorten long numbers.
	*/
	function numToShortString(n){
		n = parseInt(n)
		var s = n.toString()
		var suffixes = ['', 'K', 'M', 'B', 'T']
		var i = 0
		while(s.length > 4 && i <= 4){
			var div = Math.pow(1000, i)
			s = parseInt(n / div) + suffixes[i]
			i++
		}
		return s
	}

	/*
	Makes a user-friendly percentage string based on the given `ratio` (number from 0 to 1)
	For numbers between 
	*/
	function percentageString(ratio){
		var perc = ratio * 100
		if(perc < 1 && perc > 0) return "<1%"
		if(perc > 99 && perc < 100) return ">99%"
		return parseInt(perc) + '%'
	}

	/* The package labels get 200px of width by default.
	 * 20 of those pixels are taken up by the expand/collapse icon.
	 * Indentation will take away from that. 
	 */
	var packageLabelDefaultWidth = 180

	/*
	 * Creates a function($e, obj) that takes a jQuery element `$e`,
	 * locates the common UI parts within $e (assuming it is a clone
	 * of the PackageWidget's template html), and assigns them to
	 * fields in the `obj`.
	 */
	var assignUiParts = (function(){
		var widgetPartsToFind = [
			'childrenContainer',
			'labelContainer',
			'countContainer',
			'barContainer',
			'collapser',
			'collapserIcon',
			'indent',
			'contentContainer',
			'labelText',
			'instrumentationSelectedToggle',
			'countNumber',
			'barFill',
			'barLabel'
		]

		/*
		 * Each UI part is located in the template HTML with
		 * the id "WidgetPart-partName", where `partName`
		 * corresponds to the string in `widgetPartsToFind`.
		 */
		var infos = widgetPartsToFind.map(function(name){
			var selector = '#WidgetPart-' + name
			return {name:name, selector:selector}
		})
		return function($e, obj){
			infos.forEach(function(info){
				var $f = $e.find(info.selector)
				obj[info.name] = $f
			})
		}
	})()

	function PackageWidget(){

		// ============================================================================
		// Private/Local State
		// ============================================================================
		var e = PackageWidget.template.clone()

		var self = this,
			_fullLabel = '',
			_abbreviatedLabel = '',
			_indentation = 0,
			_methodCount = 0,
			_totalCoverage = 0,
			_displayCompact = false,

			_selected = 0,
			_selectable = true,
			_selectedBus = new Bacon.Bus(),
			_enabled = true,

			_instrumentationSelected = 0,
			_instrumentationSelectable = true,
			_instrumentationSelectedBus = new Bacon.Bus(),

			_collapseChildren = false,
			_childWidgets = [],

			_isSurfaceOn = false

		// ============================================================================
		// UI Selected Elements
		// ============================================================================

		this.uiParts = {
			'main': e,

			'highlightsD3': d3.select(e[0]).selectAll('.highlight')
		}

		// find the remaining UI parts via the helper function `assignUiParts`
		assignUiParts(e, this.uiParts)

		// ============================================================================
		// Public Functions
		// ============================================================================

		this.fullLabel = function(newLabel){
			if(!arguments.length) return _fullLabel

			_fullLabel = newLabel
			this.uiParts.labelContainer.attr('title', _fullLabel)
			return self
		}

		this.abbreviatedLabel = function(newLabel){
			if(!arguments.length) return _abbreviatedLabel

			_abbreviatedLabel = newLabel
			this.uiParts.labelText.text(_abbreviatedLabel)
			return self
		}

		this.methodCount = function(newMethodCount){
			if(!arguments.length) return _methodCount

			_methodCount = newMethodCount
			updateMethodCounterUI()
			return self
		}

		this.totalCoverage = function(newCount){
			if(!arguments.length) return _totalCoverage

			_totalCoverage = newCount
			updateMethodCounterUI()
			return self
		}

		this.indent = function(newIndent){
			if(!arguments.length) return _indentation

			_indentation = newIndent
			this.uiParts.contentContainer.css('width', (packageLabelDefaultWidth - _indentation) + 'px')
			this.uiParts.indent.css('width', _indentation + 'px')
			_childWidgets.forEach(function(child){
				child.indent(_indentation + 8)
			})
		}

		this.selectable = function(newSel){
			if(!arguments.length) return _selectable

			_selectable = newSel
			return self
		}

		this.enabled = function(newEnabled){
			if(!arguments.length) return _enabled

			_enabled = newEnabled
			return self
		}

		this.isSurfaceOn = function(newIsSurfaceOn){
			if(!arguments.length) return _isSurfaceOn

			_isSurfaceOn = newIsSurfaceOn
			return self
		}

		this.instrumentationSelectable = function(newSel){
			if(!arguments.length) return _instrumentationSelectable

			_instrumentationSelectable = newSel

			self.uiParts.instrumentationSelectedToggle
				.toggleClass('disabled', !_instrumentationSelectable)

			return self
		}

		this.displayCompact = function(compactFlag){
			if(!arguments.length) return _displayCompact

			_displayCompact = compactFlag
			this.uiParts.main.toggleClass('compact', _displayCompact)
			return self
		}

		this.flashHighlight = function(){
			self.uiParts.highlightsD3
				.interrupt()
				.style('opacity', .8)
			.transition().duration(5000)
				.style('opacity', 0)

			return self
		}

		this.addDependencyCheckBadge = function(status) {
			if (status.state == 'none') return;

			if (self.uiParts.vulnerableBadge) {
				self.uiParts.vulnerableBadge.remove();
				delete self.uiParts.vulnerableBadge;
			}

			self.badgeClickEnabled = false

			var badge = self.uiParts.depCheckBadge
			if (!badge) {
				badge = PackageWidget.depCheckBadge.clone()
				badge.click(function(e) {
					if (self.badgeClickEnabled) self.vulnerableBadgeClicks.push(e)
					e.stopPropagation()
				})
				self.uiParts.depCheckBadge = badge
				self.uiParts.labelText.before(badge)
			}

			var $badge = $(badge),
				$status = $('#dependencycheck-status', badge),
				$summary = $('#dependencycheck-summary', badge)

			function setStatus(status, title) {
				$badge.addClass('pending')
				$summary.hide()
				$status.show()
				$status.attr('title', title);
				$status.text(status)
				self.badgeClickEnabled = false
			}

			function setSummary(numVuln) {
				$status.hide()
				$badge.removeClass('pending')
				if (numVuln >= 0) {
					$summary.show()
					$('#dependencycheck-numvuln', $summary).text(numVuln)
					$badge.toggleClass('clean', numVuln == 0)
					self.badgeClickEnabled = numVuln > 0
				} else $badge.hide()
			}

			switch (status.state) {
				case 'queued':
					setStatus('[queued]', 'This project has been queued for a Dependency Check scan.')
					break

				case 'running':
					setStatus('[scanning]', 'Dependency Check is currently scanning dependencies for known vulnerabilities.')
					break

				case 'failed':
					setStatus('[failed]', 'Dependency Check has failed; please consult the logs for more information.')
					break

				case 'unknown':
					setStatus('[unknown]', 'Dependency Check is in an unknown state; please consult the logs for more information.')
					break

				case 'finished':
					setSummary(status.numFlaggedDeps)
					break
			}
		}

		this.addVulnerableBadge = function(isSelf) {
			if (!self.uiParts.depCheckBadge) {
				var badge = self.uiParts.vulnerableBadge
				if (!badge) {
					badge = PackageWidget.vulnBadge.clone()
					badge.click(function(e) {
						self.vulnerableBadgeClicks.push(e)
						e.stopPropagation()
					})
					badge.addClass('bubbled')
					self.uiParts.vulnerableBadge = badge
					self.uiParts.labelText.before(badge)
				}
				if (isSelf) badge.removeClass('bubbled')
			}
		}

		/*
		Get or set the selected state:
			`selected()` returns the state
			`selected(<boolean>)` sets the state
			`selected('toggle')` flips the state
		The setters will trigger a 'selected' event and return a self-reference
		*/
		this.selected = function(newSelect){
			if(!arguments.length) return _selected
			_selected = newSelect

			self.uiParts.main
				.toggleClass('selected', _selected == 1 || _selected == undefined)
				.toggleClass('partial-select', _selected == undefined)

			_selectedBus.push(_selected)

			return self
		}

		this.instrumentationSelected = function(newSelect){
			if(!arguments.length) return _instrumentationSelected
			_instrumentationSelected = newSelect

			self.uiParts.instrumentationSelectedToggle
				.toggleClass('full-select', _instrumentationSelected == 1)
				.toggleClass('partial-select', _instrumentationSelected == undefined)

			_instrumentationSelectedBus.push(_instrumentationSelected)

			return self
		}

		this.collapseChildren = function(arg, animate){
			if(!arguments.length) return _collapseChildren

			if(arg == 'toggle') _collapseChildren = !_collapseChildren
			else _collapseChildren = arg

			updatedCollapserState()

			if(animate){
				self.uiParts.childrenContainer[_collapseChildren ? 'slideUp' : 'slideDown']()
			} else {
				self.uiParts.childrenContainer[_collapseChildren ? 'hide' : 'show']()
			}
			return self
		}

		this.children = {
			'add': function(childWidget){
				_childWidgets.push(childWidget)
				childWidget.indent(_indentation + 8)
				self.uiParts.childrenContainer.append(childWidget.uiParts.main)
				updatedCollapserState()
				return this
			},

			'forEach': function(callback){
				_childWidgets.forEach(callback)
			}
		}

		this.children.__defineGetter__('length', function(){ return _childWidgets.length })

		/*
		Exposes the current `selected` state as a Rx Property
		*/
		this.selectedProp = _selectedBus.toProperty(_selected).skipDuplicates().noLazy()

		this.selectionClicks = new Bacon.Bus()

		this.instrumentationSelectedProp = _instrumentationSelectedBus.toProperty(_instrumentationSelected).noLazy().skipDuplicates()

		this.instrumentationSelectedClicks = new Bacon.Bus()

		this.vulnerableBadgeClicks = new Bacon.Bus()

		// ============================================================================
		// Helper Methods
		// ============================================================================

		function updateMethodCounterUI(){
			var coverageRatio = _methodCount ? (_totalCoverage / _methodCount) : 0,
				fillWidth = parseInt(coverageRatio * 100) + '%',
				coverageText = percentageString(coverageRatio),
				countText = numToShortString(_methodCount)

			self.uiParts.countNumber.text(countText)
			self.uiParts.barFill.css('width', fillWidth)
			self.uiParts.barLabel.text(coverageText)
			self.uiParts.barFill.toggleClass('covered', coverageRatio > 0)

			updateBarLabelPlacement()
		}

		function updateBarLabelPlacement(){
			var barFill = self.uiParts.barFill,
				barPxWidth = barFill.width(),
				barLabelWidth = self.uiParts.barLabel.width()

			barFill.toggleClass('small', barPxWidth - barLabelWidth < 20)
		}

		function updatedCollapserState(){
			self.uiParts.collapserIcon.toggleClass('icon-plus', _collapseChildren)
			self.uiParts.collapserIcon.toggleClass('icon-minus', !_collapseChildren)
			self.uiParts.main.toggleClass('has-children', !!_childWidgets.length)
		}

		// ============================================================================
		// Setup and Initialization
		// ============================================================================

		$(window).resize(updateBarLabelPlacement)
		this.uiParts.contentContainer.click(function(){
			// as long as the widget is 'selectable', toggle it when clicked
			if(_enabled && _selectable) {
				// self.selected('toggle') 
				self.selectionClicks.push(1)
			}
		})
		this.uiParts.instrumentationSelectedToggle.click(function(){
			if(_instrumentationSelectable){
				self.instrumentationSelectedClicks.push(1)
			}
		})
	}

	$(document).ready(function(){
		PackageWidget.template = $('#package-widget-template').attr('id', null).remove()
		PackageWidget.vulnBadge = $('#package-widget-template-has-vuln').attr('id', null).remove()
		PackageWidget.depCheckBadge = $('#package-widget-template-dependency-check').attr('id', null).remove()
	})

	exports.PackageWidget = PackageWidget

})(this)
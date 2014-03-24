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

;(function(exports){

	var nextRecordingId = 0

	function Recording(){

		// local variables

		var self = this,
			id = nextRecordingId++,

			_color = 'black',
			_colorChange = new Bacon.Bus(),

			_label = null,
			_labelChange = new Bacon.Bus(),

			_coverage = [0,0], // [x,y] means x out of y methods covered
			_coverageChange = new Bacon.Bus(),

			_menu = [], // expect items with {icon, text, onSelect}
			_menuChange = new Bacon.Bus(),

			_dataKey = null,
			_dataKeyChange = new Bacon.Bus(),

			_selected = false,
			_selectedChange = new Bacon.Bus(),

			_running = true,
			_runningChange = new Bacon.Bus()

		this.logMe = function(){
			console.log("Recording{ key: '" + _dataKey + "', label: '" + _label + "', coverage: '" + _coverage + "', color: '" + _color + "'}")
		}

		// getters

		this.getColor = function(){ return _color }
		this.getLabel = function(){ return _label }
		this.getCoverage = function(){ return _coverage }
		this.getMenu = function(){ return _menu }
		this.getDataKey = function(){ return _dataKey }
		this.isSelected = function(){ return _selected }
		this.isRunning = function(){ return _running }

		// setters

		this.setColor = function(newColor){
			_color = newColor
			_colorChange.push(_color)
			return self
		}

		this.setLabel = function(newLabel){
			_label = newLabel
			_labelChange.push(_label)
			return self
		}

		this.setCoverage = function(numCovered, outOfNum){
			if(isNaN(numCovered) || isNaN(outOfNum)) throw 'illegal argument: required 2 numbers'
			if(numCovered > outOfNum) throw 'illegal argument: numCovered must be <= outOfNum'
			_coverage[0] = numCovered
			_coverage[1] = outOfNum
			_coverageChange.push(_coverage)
			return self
		}
		this.setMenu = function(newMenu){
			// TODO: validate menu contents
			_menu = newMenu
			_menuChange.push(_menu)
			return self
		}
		this.setDataKey = function(newKey){
			_dataKey = newKey
			_dataKeyChange.push(_dataKey)
			return self
		}
		this.setSelected = function(newSel){
			_selected = newSel
			_selectedChange.push(_selected)
			return self
		}
		this.setRunning = function(newRunning){
			_running = newRunning
			_runningChange.push(_running)
			return self
		}

		// properties (Bacon.js)

		this.color = _colorChange.toProperty(_color)
		this.label = _labelChange.toProperty(_label)
		this.coverage = _coverageChange.toProperty(_coverage)
		this.menu = _menuChange.toProperty(_menu)
		this.dataKey = _dataKeyChange.toProperty(_dataKey)
		this.selected = _selectedChange.toProperty(_selected)
		this.running = _runningChange.toProperty(_running)

		// add dummy subscribers to the properties or else bacon will ignore any initialization events
		var noop = function(){}

		this.color.onValue(noop)
		this.label.onValue(noop)
		this.coverage.onValue(noop)
		this.menu.onValue(noop)
		this.dataKey.onValue(noop)
		this.selected.onValue(noop)
		this.running.onValue(noop)
	}

	exports.Recording = Recording

})(this);
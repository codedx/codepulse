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
	
	// define variables to represent
	var recordingTemplate,
		colorPickerTemplate
	
	// once the DOM has loaded, initialize the template/container variables
	$(document).ready(function(){
		var templatesContainer = $('#recording-controls [data-role=templates-container]')
		recordingTemplate = templatesContainer.find('[data-role=recording-template]')
		colorPickerTemplate = templatesContainer.find('[data-role=colorpicker-template]')
	})

	function RecordingWidget(){
		var self = this,
			template = recordingTemplate.clone(),
			label = template.find('.recording-label'),
			labelText = label.find('.text'),
			labelTextEdit = label.find('.text-edit'),
			labelTextSave = label.find('.text-edit-save'),
			percBadge = template.find('.menu-badge abbr'),
			colorSwatch = template.find('.swatch'),
			swatchContainer = template.find('.swatch-container'),
			menuRoot = template.find('.dropdown-menu'),

			labelEdits = this.labelEdits = new Bacon.Bus(),
			colorEdits = this.colorEdits = new Bacon.Bus()

		this.$ui = template

		var _label = null,
			_color = null

		this.setLabel = function(newLabel){
			_label = newLabel
			labelText.text(newLabel || 'Untitled')
			labelTextEdit.attr('placeholder', newLabel || 'Untitled Recording')
			labelTextEdit.val(newLabel)
		}

		this.openLabelEditor = ( function(){


			// flag that gets when the save button is clicked,
			// and checked by the closeEditor function to make
			// it skip the normal close actions.
			var saveClicked = false

			// switch into our out of editing mode
			function setEditing(isEditing) {
				label.toggleClass('editing', isEditing)
				if(isEditing) labelTextEdit.focus().select()
			}
			
			// use the text in the editor as the new title
			function saveEditor() {
				var txt = labelTextEdit.val()
				//setTitle(txt)
				labelEdits.push(txt)
				setEditing(false)
			}
			
			// leave editing mode without saving
			function closeEditor() {
				if(saveClicked){
					saveClicked = false
				} else {
					labelTextEdit.val(_label)
					setEditing(false)
				}
			}

			// Keypresses that leave the editor
			labelTextEdit.keydown(function(e){
				// Escape Key Pressed:
				if(e.which == 27) closeEditor()
				
				// Enter Key Pressed:
				if(e.which == 13) saveEditor()
			})

			labelTextSave.click(function(){
				saveClicked = true
				saveEditor()
			})
			
			// Un-focusing the editor causes it to close
			labelTextEdit.focusout(function(){
				setTimeout(closeEditor, 100)
			})

			return function(){ setEditing(true) }
		})();
		
		this.setInProgress = function(inProgress){
			template.toggleClass('in-progress', inProgress)
		}

		this.setColor = function(newColor){
			_color = newColor
			colorSwatch.css('background-color', newColor)
		}

		this.setDisabled = function(disabled){
			template.toggleClass('disabled', disabled)
		}

		this.setMenu = function(menuItems){

			function mkMenuLi(m){
				if(m.divider){
					return $('<li class="divider">')[0]
				} else {
					var icon = $('<i>'),
						span = $('<span>'),
						link = $('<a>'),
						li = $('<li>')
					link.appendTo(li)
					icon.appendTo(link)
					span.appendTo(link)

					m.icon && icon.addClass('icon-' + m.icon)
					span.text(m.label)
					link.click(m.onSelect)
					if(m.selected) li.addClass('active')
					return li[0]
				}
			}

			var uiMenuItems = menuItems.map(mkMenuLi)

			menuRoot.children().remove()
			menuRoot.append(uiMenuItems)

		}

		this.selectionClicks = template.find('.recording-label .text, .menu-badge').asEventStream('click')

		this.setSelected = function(selected){ template.toggleClass('selected', selected) }

		this.setCoverage = function(numCovered, outOfNum){
			var percentage = outOfNum ? (numCovered / outOfNum) * 100 : 0

			var percString
			if(percentage <= 0) percString = '0%'
			else if(percentage < 1) percString = '<1%'
			else if(percentage >= 100) percString = '100%'
			else if(percentage > 99) percString = '>99%'
			else percString = parseInt(percentage) + '%'

			var msg
			if(!numCovered || !outOfNum) msg = 'Nothing was traced'
			else msg = numCovered + ' out of the ' + outOfNum +
				' methods in this codebase were called during this recording'

			percBadge.attr('title', msg).text(percString)
		}

		this.enableColorEditor = function(){
			setupColorpicker(swatchContainer, _color || 'black', {
				'progressCallback': function(current){ self.setColor(current) },
				'finishedCallback': function(choice){ choice && colorEdits.push(choice) },
				'onOpen': function(){ swatchContainer.addClass('editor-open') },
				'onClose': function(){ swatchContainer.removeClass('editor-open') }
			})
		}
	}

	/*
	 * Uses the 'Flexi' ColorPicker widget in conjunction with qTip2
	 * to install a color chooser dialog to the left of the given jquery
	 * selected `$elem`.
	 *
	 * @param $elem - the trigger element. Clicking it will activate the tooltip/chooser
	 * @param initialColorRaw - a css color string (it will be sanitized via d3.rgb)
	 * @param callbacksObject - an object that contains callback functions:
	 *   callbacksObject.progressCallback - called when the color chooser's cursors are moved
	 *   callbacksObject.finishedCallback - called when ok/cancel is pressed, or the tooltip
	 *                                      is hidden for other reasons. If OK, the argument is
	 *                                      the color that the user picked
	 *   callbackObjects.onOpen - called when the tooltip opens
	 *   callbackObjects.onClose - called when the tooltip closes
	 */
	function setupColorpicker($elem, initialColorRaw, callbacksObject){
		var template = colorPickerTemplate.clone(),
			slider = template.find('.slider')[0],
			picker = template.find('.picker')[0],
			pickerCursor = template.find('.picker-indicator')[0],
			sliderCursor = template.find('.slider-indicator')[0],
			okButton = template.find('[name=ok]'),
			cancelButton = template.find('[name=cancel]'),
			initialColor = d3.rgb(initialColorRaw).toString(),
			currentColor = initialColor,
			qtipApi = undefined,
			qtipHide = undefined,
			qtipOpen = false,
			okClicked = false

		callbacksObject = callbacksObject || {}
		var noop = function(){},
			progressCallback = callbacksObject['progressCallback'] || noop,
			finishedCallback = callbacksObject['finishedCallback'] || noop,
			onOpen = callbacksObject['onOpen'] || noop,
			onClose = callbacksObject['onClose'] || noop

		// puts "pointerEvents: none;" on the cursors' styles
		ColorPicker.fixIndicators(pickerCursor, sliderCursor)

		function pickerCallback(hex, hsv, rgb, pickerCoord, sliderCoord){
			ColorPicker.positionIndicators(
				sliderCursor, pickerCursor,
				sliderCoord, pickerCoord)

			progressCallback(hex)
			currentColor = hex
		}

		function onOk(e){
			okClicked = true
			$elem.qtip('hide')
		}

		function onCancel(e){
			okClicked = false
			$elem.qtip('hide')
		}

		okButton.click(onOk)
		cancelButton.click(onCancel)

		var cp = new ColorPicker(slider, picker, pickerCallback)
		cp.setHex(currentColor)

		$elem.qtip({
			content: template,
			style: 'colorpicker-tooltip',
			position: {
				my: 'right center',
				at: 'left center',
				viewport: $(window)
			},
			show: {
				event: 'click',
				effect: function(){
					cp.setHex(initialColor)
					qtipOpen = true
					okClicked = false
					$(this).show()
					onOpen()
				},
				delay: 0
			},
			hide: {
				fixed: true,
				event: 'unfocus',
				effect: function(){
					if(okClicked){
						initialColor = currentColor
						finishedCallback(currentColor)
					} else {
						cp.setHex(initialColor)
						finishedCallback()
					}

					qtipOpen = false
					onClose()
				},
				delay: 0
			}
		})

	}

	exports.RecordingWidget = RecordingWidget
})(this);
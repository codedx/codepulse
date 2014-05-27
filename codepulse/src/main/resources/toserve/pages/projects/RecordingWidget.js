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
	var recordingTemplate
	
	// once the DOM has loaded, initialize the template/container variables
	$(document).ready(function(){
		var templatesContainer = $('#recording-controls [data-role=templates-container]')
		recordingTemplate = templatesContainer.find('[data-role=recording-template]')
	})

	function RecordingWidget(){
		var self = this,
			template = recordingTemplate.clone(),
			label = template.find('.recording-label'),
			labelText = label.find('.text'),
			labelTextEdit = label.find('.text-edit'),
			labelTextSave = label.find('.text-edit-save'),
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

			// switch into our out of editing mode
			function setEditing(isEditing) {
				label.toggleClass('editing', isEditing)
				if(isEditing) labelTextEdit.focus().select()
			}
			
			// use the text in the editor as the new title
			function saveEditor() {
				var txt = labelTextEdit.val()
				labelEdits.push(txt)
				setEditing(false)
			}
			
			// leave editing mode without saving
			function closeEditor() {
				labelTextEdit.val(_label)
				setEditing(false)
			}

			// Keypresses that leave the editor
			labelTextEdit.keydown(function(e){
				// Escape Key Pressed:
				if(e.which == 27) closeEditor()
				
				// Enter Key Pressed:
				if(e.which == 13) saveEditor()
			})

			// save when the 'save' button is clicked or the user leaves the input box
			labelTextSave.click(saveEditor)
			labelTextEdit.focusout(saveEditor)

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

		this.enableColorEditor = function(){
			colorpickerTooltip(swatchContainer, _color || 'black', {
				'progressCallback': function(current){ self.setColor(current) },
				'finishedCallback': function(choice){ choice && colorEdits.push(choice) },
				'onOpen': function(){ swatchContainer.addClass('editor-open') },
				'onClose': function(){ swatchContainer.removeClass('editor-open') }
			})
		}
	}

	exports.RecordingWidget = RecordingWidget
})(this);
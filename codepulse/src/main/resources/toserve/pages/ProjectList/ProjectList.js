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

 $(document).ready(function(){

	var $listContainer = $('.projectList'),
		$emptyPlaceholder = $('.emptyListPlaceholder'),
		$placeholderParent = $emptyPlaceholder.parent(),
		listContainer = d3.select($listContainer[0]),
		$template = $listContainer.find('[template]'),
		$listItemTemplate = $template.find('.projectListItem'),
		listItemTemplateHtml = $listItemTemplate.html()

	$template.remove()

	function updateList(items){

		// update the D3 data selection
		var itemsSelection = listContainer
			.selectAll('.projectListItem')
			.data(items, function(d){ return d.id })

		// for new items, create and initialize them
		var itemsAdded = itemsSelection.enter().append('div')
			.attr('class', 'projectListItem')
			.html(listItemTemplateHtml)
			.each(setupNewItem)

		// for deleted items, transition them out and remove them
		var itemsRemoved = itemsSelection.exit()
			.each(function(){ $(this).slideUp(500) })
		.transition().delay(500)
			.remove()

		// update any other information on all items
		itemsSelection.each(updateItem)

		// check if there are *any* non-deleted items
		var nonDeletedItems = items.filter(function(item){
			return item.state != 'delete-pending'
		})

		//bump the empty placeholder to the end of the list
		$emptyPlaceholder.detach().appendTo($placeholderParent)
		// show the placeholder when there are no items
		$emptyPlaceholder[nonDeletedItems.length? 'hide': 'show']()
	}

	function checkRunningState(data){
		switch(data.state){
			case 'running':
			case 'ending':
				return true
			default:
				return false
		}
	}

	// Set up the export and delete links for the data/item.
	// This assumes that the hrefs won't change
	function setupNewItem(data){
		var $item = $(this),
			$exportLink = $item.find('a[name=export]'),
			$deleteLink = $item.find('a[name=delete]')

		// set up the export link
		$exportLink
			.attr('data-downloader', data.exportHref)
			.attr('data-filename', data.name + '.pulse')
			.projectDownloader('create')

		// if deletion is pending, hide it
		if(data.state == 'delete-pending') $item.slideUp(0)
	}

	// Update the name and dates for the data/item
	function updateItem(data){
		var $item = $(this),
			$link = $item.find('a.projectLink'),
			$nameDiv = $item.find('.projectName'),
			$created = $item.find('.date-created span[name=date]'),
			$importedDiv = $item.find('.date-imported'),
			$imported = $importedDiv.find('span[name=date]'),
			isTraceRunning = checkRunningState(data)

		// show or hide the item with a vertical slide animation
		// depending on whether the project is scheduled for deletion
		$item[(data.state == 'delete-pending') ? 'slideUp' : 'slideDown']()

		$link
			.attr('href', data.href)
			.text(data.name)

		$created.text(data.created)

		var imported = data.imported
		$imported.text(imported)
		$importedDiv.css('display', imported ? null : 'none')

		$item.toggleClass('stripedBackground animated', isTraceRunning)

		function handleDeleteClick(){
			if(isTraceRunning){
				var msg = "You can't delete this project because it is currently running."
				alert(msg)
			} else {
				if(confirm("Are you sure you want to delete this project?")) {
					$.ajax(data.deleteHref, {
						type: 'DELETE',
						error: function(xhr, status){
							alert('Deleting failed.')
						}
					})
				}
            }
		}

		// update the delete link: if the project is running, you can't click it
		d3.select($item.find('a[name=delete]')[0])
			.on('click', handleDeleteClick)
			.attr('disabled', isTraceRunning ? 'disabled' : null)
	}

	// Initiate an AJAX request to load the project data list
	function requestProjects(){
		$.ajax('/api/projects', {
			type: 'GET',
			error: function(xhr, status){
				var msg = 'Failed to updated the Projects List. Server says: ' +
					(xhr.responseText || '(no response)')
				alert(msg)
			},
			success: function(datas){
				updateList(datas)
			}
		})
	}

	// load the project list now, and any time a `projectListUpdated` event is triggered
	requestProjects()
	$(document).on('projectListUpdated', requestProjects)
});

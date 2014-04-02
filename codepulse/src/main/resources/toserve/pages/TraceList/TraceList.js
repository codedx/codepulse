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

	var $listContainer = $('.traceList'),
		listContainer = d3.select($listContainer[0]),
		$template = $listContainer.find('[template]'),
		$listItemTemplate = $template.find('.traceListItem'),
		listItemTemplateHtml = $listItemTemplate.html()

	$template.remove()

	function updateList(items){

		// update the D3 data selection
		var itemsSelection = listContainer
			.selectAll('.traceListItem')
			.data(items, function(d){ return d.id })

		// for new items, create and initialize them
		var itemsAdded = itemsSelection.enter().append('div')
			.attr('class', 'traceListItem')
			.html(listItemTemplateHtml)
			.each(setupNewItem)

		// for deleted items, transition them out and remove them
		var itemsRemoved = itemsSelection.exit()
			.each(function(){ $(this).slideUp(500) })
		.transition().delay(500)
			.remove()

		// update any other information on all items
		itemsSelection.each(updateItem)
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
			.traceDownloader('create')

		// set up the delete link
		$deleteLink.click(function(){
			$.ajax(data.deleteHref, {
				type: 'DELETE',
				error: function(xhr, status){
					alert('Deleting failed.')
				}
			})
		})
	}

	// Update the name and dates for the data/item
	function updateItem(data){
		var $item = $(this),
			$link = $item.find('a.traceLink'),
			$nameDiv = $item.find('.traceName'),
			$created = $item.find('.date-created span[name=date]'),
			$importedDiv = $item.find('.date-imported'),
			$imported = $importedDiv.find('span[name=date]')

		$link
			.attr('href', data.href)
			.text(data.name)

		$created.text(data.created)

		var imported = data.imported
		$imported.text(imported)
		$importedDiv.css('display', imported ? null : 'none')

		switch(data.state){
			case 'idle':
			case 'connecting':
				$item.removeClass('stripedBackground animated')
				break;
			case 'running':
			case 'ending':
				$item.addClass('stripedBackground animated')
				break;
		}
	}

	// Initiate an AJAX request to load the trace data list
	function requestTraces(){
		var url = '/trace-api/traces'
		$.ajax('/trace-api/traces', {
			type: 'GET',
			error: function(xhr, status){
				var msg = 'Failed to updated the Traces List. Server says: ' +
					(xhr.responseText || '(no response)')
				alert(msg)
			},
			success: function(datas){
				updateList(datas)
			}
		})
	}

	// load the trace list now, and any time a `traceListUpdated` event is triggered
	requestTraces()
	$(document).on('traceListUpdated', requestTraces)
});

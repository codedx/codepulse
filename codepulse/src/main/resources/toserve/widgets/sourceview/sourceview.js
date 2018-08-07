/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */

var nextWidgetId = 0

Bacon.simpleProperty = function(initial){
    var bus = new Bacon.Bus()
    var prop = arguments.length ? bus.toProperty(initial): bus.toProperty()
    prop.become = function(value){ bus.push(value) }
    prop.onValue(function(){})
    return prop
}

function SourceView(editorParent) {
    this.editorParent = editorParent

    var editor = this.editor = CodeMirror(editorParent, {
        'lineNumbers': true,
        'lineWrapping': true,
        'viewportMargin': 50,
        'readOnly': true,
        'cursorHeight': 0,
        'styleActiveLine': true,
        'styleSelectedText': true,
        'scrollbarStyle': 'overlay',
        'highlightSelectionMatches': { showToken: /\w/ }
    })

    this.$errorView = $('<pre>').appendTo(editorParent).hide()
    this.showingSourceProp = Bacon.simpleProperty(false)

    $(window).on('resize.source-view-' + this._widgetId, function(e){
        // allow for custom widgets (like FlexResizer) to manually trigger
        // a 'resize' event, and react to that event by triggering an editor refresh.
        // Such events will have a target other than 'window'.
        if(e.target && e.target != window){
            if($.contains(e.target, editorParent)){
                editor.refresh()
            }
        }
    })
}

SourceView.prototype.setSourceView = function(mime, source){
    var editor = this.editor
    var _this = this

    editor.mark_count = 0
    editor.line_count = 0

    this.$errorView.hide()
    $(editor.display.wrapper).show()
    mime && editor.setOption('mode', mime)
    editor.setValue(source)
    this.showingSourceProp.become(true)
}

SourceView.prototype.setSourceLocations = function(locations, mark) {

    let hasHighlights = this.editor.mark_count > 0 || this.editor.line_count > 0
    if (!mark && !hasHighlights) {
      return
    }

    locations.forEach(loc => {
        let start = {
            line: loc.startLine - 1,
            ch: loc.startCharacter - 1
        }

        let end = {
            line: loc.endLine - 1,
            ch: loc.endCharacter - 1
        }

        if (isNaN(start.line) || isNaN(end.line)) {
          console.log("Invalid start/end line detected")
          return
        }

        let lineLevelCoverageClassName = "line-level-coverage"
        for (let line = start.line; line <= end.line; line++) {
            let hasLineMark = this.editor.getLineHandle(line).bgClass === lineLevelCoverageClassName
            if (mark && !hasLineMark) {
                this.editor.addLineClass(line, "background", lineLevelCoverageClassName)
                this.editor.line_count += 1
            } else if (!mark && hasLineMark) {
                this.editor.removeLineClass(line, "background", lineLevelCoverageClassName)
                this.editor.line_count -= 1
            }
        }

        if (isNaN(start.ch) || isNaN(end.ch)) {
          return // text marker requires valid start/end character
        }

        let markLabel = "mark_" + start.line + "_" + start.ch + "_" + end.line + "_" + end.ch
        let hasTextMark = this.editor[markLabel] != null

        if (mark && !hasTextMark) {
            this.editor[markLabel] = this.editor.getDoc().markText(start, end, {className: "code-coverage"})
            this.editor.mark_count += 1
        } else if (!mark && hasTextMark) {
            this.editor[markLabel].clear()
            this.editor[markLabel] = null
            this.editor.mark_count -= 1
        }
    })
}

SourceView.prototype.setErrorView = function(message){
    $(this.editor.display.wrapper).hide()
    this.$errorView.show().text(message)
    this.showingSourceProp.become(false)
}

SourceView.prototype.scrollToLine = function(lineNumber, duration, callback){
    if(arguments.length == 1){
        duration = 300
        callback = _.noop
    }
    if(arguments.length == 2){
        if(typeof duration == 'function'){
            callback = duration
            duration = 300
        } else {
            callback = _.noop
        }
    }

    var editor = this.editor
    var lineOffset = editor.getOption('firstLineNumber')
    var lineIndex = lineNumber - lineOffset
    var easing = d3.ease('cubic-in-out')
    var scrollInfo = editor.getScrollInfo()
    var startY = scrollInfo.top
    var lineY = editor.heightAtLine(lineIndex, 'local')
    var endY = editor.heightAtLine(lineIndex, 'local') - scrollInfo.clientHeight / 2
    var dist = endY - startY

    // we're using $.fn.animate to manage the changes-over-time, but it won't
    // do anything unless we give it some properties
    var dummyProperties = { x: 1 }
    $('<div>').animate(dummyProperties, {
        duration: duration,
        progress: function(animation, progress, remainingMs){
            var progressY = startY + easing(progress) * dist
            editor.scrollTo(0, progressY)
        },
        always: function(){
            editor.scrollTo(0, endY)
            editor.setCursor(lineIndex)
            callback()
        }
    })
}

/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in the
 * Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

;(function(exports){

    exports.SurfaceDetectorController = SurfaceDetectorController

    function SurfaceDetectorController() {
        let $surfaceButton = $('#surface-button')
        let showSurfaceBus = new Bacon.Bus
        let showSurfaceStream = $surfaceButton.asEventStream('click')
        let surfaceDetectorUpdateBus = new Bacon.Bus
        let surfaceDetectorUpdateStream = $(document).asEventStream('surfacedetector-update', function(event, args) { return args })

        const runningStateName = "running"
        const failedStateName = "failed"
        const finishedStateName = "finished"

        this.showSurface = showSurfaceBus.toProperty().noLazy()
        this.surfaceDetectorUpdates = surfaceDetectorUpdateBus.toProperty().noLazy()

        this.cancelShowSurface = function() {
            if (isShowSurfaceOn()) {
                toggleShowSurface()
            }
        }

        function isRunning(status) {
            return status.state === runningStateName
        }

        function isFailed(status) {
            return status.state === failedStateName
        }

        function hasResults(status) {
            return status.state === finishedStateName && status.surfaceMethodCount > 0
        }

        function getTitle(status) {
            let title = "No surface methods found"
            if (isRunning(status)) {
                title = "Detecting surface methods..."
            }
            else if (hasResults(status)) {
                title = "Toggle view of " + status.surfaceMethodCount + " surface method"
                if (status.surfaceMethodCount > 1) {
                    title += "s"
                }
            } else if (isFailed(status)) {
                title = "Surface detection error - see log for details"
            }
            return title
        }

        function isShowSurfaceOn() {
            return $surfaceButton.hasClass('im-surface-on')
        }

        function toggleShowSurface() {
            if (!$surfaceButton.hasClass('hasResults') && !isShowSurfaceOn()) {
                return
            }

            let isOn = isShowSurfaceOn()
            $surfaceButton.toggleClass('im-surface-off', isOn)
            $surfaceButton.toggleClass('im-surface-on', !isOn)

            showSurfaceBus.push(!isOn)
        }

        surfaceDetectorUpdateStream.onValue(function(update) {
            if (update.project != CodePulse.projectPageId) {
                return
            }
            surfaceDetectorUpdateBus.push(update.surfacedetector_update)
        })

        surfaceDetectorUpdateBus.onValue(function(status) {
            $surfaceButton.toggleClass('failed', isFailed(status))
            $surfaceButton.toggleClass('hasResults', hasResults(status))
            $surfaceButton.attr('title', getTitle(status))
        })

        showSurfaceStream.onValue(function() {
            toggleShowSurface()
        })

        API.getSurfaceDetectionStatus(function(status) {
            surfaceDetectorUpdateBus.push(status)
        })
    }
})(this);
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

package com.secdec.codepulse.components.includes.snippet

import net.liftweb.http._
import scala.xml.NodeSeq
import scala.xml.Elem
import scala.xml.Attribute
import scala.xml.Text
import scala.xml.Null
import language.implicitConversions

trait IncludesRegistry {

	trait Dependency { def asXml: NodeSeq }
	case class Registration(key: String, dep: Dependency) extends Dependency { def asXml = dep.asXml }

	/** Remembers the Registrations made by calling `register`. Can be used
	  * to dispatch XML from the registration keys.
	  */
	protected val registry = collection.mutable.Map.empty[String, Registration]

	/** Register several dependencies under the given `key`. The arguments `dep0`, and
	  * `depN` are to allow the use of varargs syntax but prevent a 0-argument usage.
	  */
	protected def register(key: String, dep0: Dependency, depN: Dependency*): Registration = {
		val reg = Registration(key, dep0 +: depN)
		registry.put(key, reg)
		reg
	}

	/** A Javascript Dependency, rendered as a `<script>` tag */
	case class JS(src: String) extends Dependency {
		lazy val asXml = <script class="lift:with-resource-id lift:head" type="text/javascript" src={ "/" + LiftRules.resourceServerPath + "/" + src }/>
	}

	/** A CSS Dependency, rendered as a stylesheet `<link>` */
	case class CSS(src: String) extends Dependency {
		lazy val asXml = <link class="lift:with-resource-id lift:head" href={ "/" + LiftRules.resourceServerPath + "/" + src } rel="stylesheet" type="text/css"></link>
	}

	/** Allows a sequence of dependencies to be treated as a single dependency */
	implicit class DependencySeq(seq: Seq[Dependency]) extends Dependency {
		lazy val asXml: NodeSeq = seq flatMap { _.asXml }
	}

}

object Includes extends DispatchSnippet with IncludesRegistry {

	/** Causes the resource server to allow the directories that this
	  * object intends to provide access to. This method only needs to
	  * be called once, but any further times will do nothing.
	  */
	lazy val init = {
		ResourceServer.allow({
			case "common" :: _ => true
			case "widgets" :: _ => true
			case "pages" :: _ => true
			case "thirdparty" :: _ => true
		})
		true
	}

	/** Snippet dispatch looks in the `registry` for each `key`, returning
	  * the registration's XML representation.
	  */
	object dispatch extends PartialFunction[String, NodeSeq => NodeSeq] {
		def isDefinedAt(key: String) = registry contains key
		def apply(key: String) = { _ => registry(key).asXml }
	}

	/*
	 * Third party dependencies:
	 */

	val bacon = register("baconjs", JS("thirdparty/bacon/Bacon-0.7.2-min.js"))
	val bootstrap = register("bootstrap", JS("thirdparty/bootstrap/js/bootstrap.min.js"), CSS("thirdparty/bootstrap/css/bootstrap.min.css"))
	val colorpicker = register("colorpicker", JS("thirdparty/colorpicker/colorpicker.min.js"))
	val d3 = register("d3", JS("thirdparty/d3/d3.min.js"))
	val fileupload = register("jqfileupload", JS("thirdparty/fileupload/jquery.ui.widget.js"),
		JS("thirdparty/fileupload/jquery.iframe-transport.js"),
		JS("thirdparty/fileupload/jquery.fileupload.js"))
	val fontAwesome = register("FontAwesome", CSS("thirdparty/fontawesome/css/font-awesome.min.css"))
	val icomoon = register("icomoon", CSS("thirdparty/icomoon/css/style.css"))
	val jquery = register("jquery", JS("thirdparty/jquery/jquery-2.0.2.min.js"))
	val qtip2 = register("qtip2", JS("thirdparty/qtip2/jquery.qtip.min.js"), CSS("thirdparty/qtip2/jquery.qtip.min.css"))
	val spinner = register("spinner", JS("thirdparty/spin/spin.min.js"))
	val timeago = register("timeago", JS("thirdparty/timeago/jquery.timeago.js"))
	val codemirror = register("codemirror",
		JS("thirdparty/codemirror/lib/codemirror.js"),
		CSS("thirdparty/codemirror/lib/codemirror.css"),
		JS("thirdparty/codemirror/addon/scroll/simplescrollbars.js"),
		CSS("thirdparty/codemirror/addon/scroll/simplescrollbars.css"),
		JS("thirdparty/codemirror/addon/selection/active-line.js"),
		JS("thirdparty/codemirror/addon/mode/multiplex.js"),
		JS("thirdparty/codemirror/mode/meta.js"),
		JS("thirdparty/codemirror/mode/clike/clike.js"),
		JS("thirdparty/codemirror/mode/javascript/javascript.js"),
		JS("thirdparty/codemirror/mode/xml/xml.js"),
		JS("thirdparty/codemirror/mode/javascript/javascript.js"),
		JS("thirdparty/codemirror/mode/css/css.js"),
		JS("thirdparty/codemirror/mode/htmlmixed/htmlmixed.js"),
		JS("thirdparty/codemirror/mode/htmlembedded/htmlembedded.js"))
	val underscore = register("underscore", JS("thirdparty/underscore/underscore-min.js"))
	val handlebars = register("handlebars", JS("thirdparty/handlebars/handlebars.js"))

	/*
	 * Hand-crafted-with-love dependencies:
	 */
	val commonJs = register("commonJs", JS("common/common.js"))
	val overlay = register("overlay", spinner, JS("widgets/overlay/overlay.js"), CSS("widgets/overlay/overlay.css"))
	val commonStyle = register("commonStyle", CSS("common/common.css"))
	val desktopStyle = register("desktopStyle", CSS("common/desktop.css"))
	val projectList = register("ProjectList", JS("pages/ProjectList/ProjectList.js"))
	val projectSwitcher = register("ProjectSwitcher", JS("pages/ProjectSwitcher/ProjectSwitcher.js"), CSS("pages/ProjectSwitcher/ProjectSwitcher.css"))
	val codepulseCommon = register("CodePulseCommon", JS("common/CodePulse.js"))
	val downloader = register("Downloader", JS("common/Downloader.js"))
	val api = register("API", JS("pages/projects/API.js"))
	val codeTreemap = register("codetreemap", overlay, qtip2, JS("widgets/codetreemap/treemap.js"), CSS("widgets/codetreemap/treemap.css"))
	val colorpickerTooltip = register("colorpickerTooltip", colorpicker, qtip2, JS("pages/projects/colorpicker-tooltip.js"), CSS("pages/projects/colorpicker-tooltip.css"))
	val notifications = register("notifications", CSS("widgets/Notifications/Notifications.css"), JS("widgets/Notifications/Notifications.js"), JS("widgets/Notifications/PieClock.js"))
	val projectInputForm = register("ProjectInputForm", CSS("pages/ProjectInputForm/ProjectInputForm.css"), JS("pages/ProjectInputForm/ProjectInputForm.js"))
	val brandingStyle = register("brandingStyle", CSS("common/branding.css"))
	val updates = register("updates", JS("common/UpdateController.js"), CSS("widgets/updates/updates.css"), JS("widgets/updates/updates.js"))
	val sourceview = register("sourceview", JS("widgets/sourceview/sourceview.js"), CSS("widgets/sourceview/sourceview.css"), JS("widgets/sourceview/SourceDataProvider.js"))

	val indexPage = register("indexPage", CSS("pages/index/index.css"))

	val projectsPage = register("projectsPage",
		api,
		JS("pages/projects/TraceDataUpdates.js"),
		JS("pages/projects/TraceStatus.js"),
		JS("pages/projects/TreeData.js"),
		JS("pages/projects/TraceTreeData.js"),
		colorpickerTooltip,
		CSS("pages/projects/PackageWidget.css"),
		JS("pages/projects/PackageWidget.js"),
		JS("pages/projects/PackageController.js"),
		CSS("pages/projects/treemap-tooltip.css"),
		JS("pages/projects/Recording.js"),
		JS("pages/projects/RecordingWidget.js"),
		JS("pages/projects/RecordingManager.js"),
		JS("pages/projects/trace-recording-controls.js"),
		CSS("pages/projects/trace-recording-controls.css"),
		JS("pages/projects/editable.js"),
		JS("pages/projects/projects.js"),
		CSS("pages/projects/projects.css"),
		JS("pages/projects/DependencyCheck.js"),
		JS("pages/projects/SurfaceDetector.js"),
		codemirror,
		underscore,
		handlebars)

	val traceConnectionUI = register("TraceConnectorUI",
		overlay,
		CSS("widgets/TraceConnectorUI/ConnectionHelpForm.css"),
		JS("widgets/TraceConnectorUI/ConnectionHelpForm.js"),
		CSS("widgets/TraceConnectorUI/TraceConnectorUI.css"),
		JS("widgets/TraceConnectorUI/TraceConnectorUI.js"))
}
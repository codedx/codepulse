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
		lazy val asXml = <script class="lift:with-resource-id lift:head" type="text/javascript" src={ "/" + LiftRules.resourceServerPath + "/" + src } lift:eagereval="true"/>
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
	val jquery = register("jquery", JS("thirdparty/jquery/jquery-2.0.2.min.js"))
	val qtip2 = register("qtip2", JS("thirdparty/qtip2/jquery.qtip.min.js"), CSS("thirdparty/qtip2/jquery.qtip.min.css"))
	val spinner = register("spinner", JS("thirdparty/spin/spin.min.js"))
	val timeago = register("timeago", JS("thirdparty/timeago/jquery.timeago.js"))

	/*
	 * Hand-crafted-with-love dependencies:
	 */

	val overlay = register("overlay", spinner, JS("widgets/overlay/overlay.js"), CSS("widgets/overlay/overlay.css"))
	val commonStyle = register("commonStyle", CSS("common/common.css"))
	val desktopStyle = register("desktopStyle", CSS("common/desktop.css"))
	val traceList = register("TraceList", JS("pages/TraceList/TraceList.js"))
	val traceSwitcher = register("TraceSwitcher", JS("pages/TraceSwitcher/TraceSwitcher.js"), CSS("pages/TraceSwitcher/TraceSwitcher.css"))
	val codepulseCommon = register("CodePulseCommon", JS("common/CodePulse.js"))
	val downloader = register("Downloader", JS("common/Downloader.js"))
	val traceAPI = register("TraceAPI", JS("pages/traces/TraceAPI.js"))
	val codeTreemap = register("codetreemap", overlay, qtip2, JS("widgets/codetreemap/treemap.js"), CSS("widgets/codetreemap/treemap.css"))
	val colorpickerTooltip = register("colorpickerTooltip", colorpicker, qtip2, JS("pages/traces/colorpicker-tooltip.js"), CSS("pages/traces/colorpicker-tooltip.css"))

	val traceInputForm = register("TraceInputForm", CSS("pages/TraceInputForm/TraceInputForm.css"), JS("pages/TraceInputForm/TraceInputForm.js"))

	val tracesPage = register("tracesPage",
		JS("pages/traces/common.js"),
		traceAPI,
		JS("pages/traces/TraceDataUpdates.js"),
		JS("pages/traces/TraceStatus.js"),
		JS("pages/traces/TreeData.js"),
		JS("pages/traces/TreeProjector.js"),
		JS("pages/traces/TraceTreeData.js"),
		colorpickerTooltip,
		CSS("pages/traces/PackageWidget.css"),
		JS("pages/traces/PackageWidget.js"),
		JS("pages/traces/PackageController.js"),
		CSS("pages/traces/treemap-tooltip.css"),
		JS("pages/traces/Recording.js"),
		JS("pages/traces/RecordingWidget.js"),
		JS("pages/traces/RecordingManager.js"),
		JS("pages/traces/trace-recording-controls.js"),
		CSS("pages/traces/trace-recording-controls.css"),
		JS("pages/traces/editable.js"),
		JS("pages/traces/traces.js"),
		CSS("pages/traces/traces.css"))
}
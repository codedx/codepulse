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

object Includes extends DispatchSnippet {

	/** Causes the resource server to allow the directories that this
	  * object intends to provide access to. This method only needs to
	  * be called once, but any further times will do nothing.
	  */
	lazy val init = {
		ResourceServer.allow({
			case "common" :: _ => true
			case "widgets" :: _ => true
			case "pages" :: _ => true
		})
		true
	}

	def script(src: String) = <script class="lift:with-resource-id lift:head" type="text/javascript" src={ "/" + LiftRules.resourceServerPath + "/" + src } lift:eagereval="true"/>
	def css(src: String) = <link class="lift:with-resource-id lift:head" href={ "/" + LiftRules.resourceServerPath + "/" + src } rel="stylesheet" type="text/css"></link>

	implicit def nsToNsNs(nodes: NodeSeq) = (_: NodeSeq) => nodes

	private def spinnerScript = script("common/spin.min.js")

	private def themeCss(src: String, tag: String) = css(src) % Attribute("data-theme", Text(tag), Null)

	private val baseIncludes: PartialFunction[String, NodeSeq => NodeSeq] = PartialFunction.empty
	private var includes: List[PartialFunction[String, NodeSeq => NodeSeq]] = baseIncludes :: Nil

	lazy val dispatch = includes.foldLeft(baseIncludes) { _ orElse _ }

	def prepend(i: PartialFunction[String, NodeSeq => NodeSeq]) = {
		includes = i :: includes
		this
	}

	def append(i: PartialFunction[String, NodeSeq => NodeSeq]) = {
		includes = includes ::: List(i)
		this
	}

	append {

		case "jquery" => script("common/jquery-2.0.2.min.js")

		case "baconjs" => script("common/bacon/Bacon-0.7.2.js")
		case "baconjs_min" => script("common/bacon/Bacon-0.7.2-min.js")

		case "d3" => script("common/d3/d3.min.js")

		case "bootstrap" => script("common/bootstrap/js/bootstrap.min.js") +:
			css("common/bootstrap/css/bootstrap.min.css")

		case "spinner" => spinnerScript

		case "overlay" => css("widgets/overlay/overlay.css") +:
			script("widgets/overlay/overlay.js") +:
			spinnerScript

		case "common" => css("common/common.css")

		case "qtip2" => script("common/qtip2/jquery.qtip.min.js");
		case "qtip2style" => css("common/qtip2/jquery.qtip.min.css");

		case "jqfileupload" => script("widgets/file_upload/js/vendor/jquery.ui.widget.js") +:
			script("widgets/file_upload/js/jquery.iframe-transport.js") +:
			script("widgets/file_upload/js/jquery.fileupload.js")

		case "CodePulseCommon" => script("common/CodePulse.js")
		case "Downloader" => script("common/Downloader.js")
		case "TraceAPI" => script("pages/traces/TraceAPI.js")
		case "FontAwesome" => css("common/fontawesome/css/font-awesome.min.css")
		case "TraceList" => script("pages/TraceList/TraceList.js")
		case "codetreemap" => script("widgets/codetreemap/treemap.js") +:
			css("widgets/codetreemap/treemap.css") +:
			Includes.dispatch("overlay")(Nil)

		case "tracesPage" =>
			script("pages/traces/Set.js") +:
				script("pages/traces/TraceAPI.js") +:
				script("pages/traces/TreeData.js") +:
				script("pages/traces/TreeProjector.js") +:
				script("pages/traces/betterAffix.js") +:
				css("pages/traces/PackageWidget.css") +:
				script("pages/traces/PackageWidget.js") +:
				script("pages/traces/PackageController.js") +:
				css("pages/traces/treemap-tooltip.css") +:
				script("common/colorpicker.min.js") +:
				css("pages/traces/colorpicker-tooltip.css") +:
				script("common/qtip2/jquery.qtip.min.js") +:
				css("common/qtip2/jquery.qtip.min.css") +:
				script("pages/traces/Recording.js") +:
				script("pages/traces/RecordingWidget.js") +:
				script("pages/traces/RecordingManager.js") +:
				script("pages/traces/trace-recording-controls.js") +:
				css("pages/traces/trace-recording-controls.css") +:
				script("pages/traces/editable.js") +:
				script("pages/traces/traces.js") +:
				css("pages/traces/traces.css") +:
				script("pages/traces/color-legend.js") +:
				css("pages/traces/color-legend.css")

		case "desktopStyle" =>
			css("common/desktop.css")

		case "TraceSwitcher" =>
			css("pages/TraceSwitcher/TraceSwitcher.css") +:
				script("pages/TraceSwitcher/TraceSwitcher.js")
	}
}
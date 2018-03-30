/* bytefrog: a tracing instrumentation toolset for the JVM. For more information, see
 * <https://github.com/codedx/bytefrog>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.codedx.bytefrog.instrumentation;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.io.IOException;

import com.esotericsoftware.minlog.Log;

import fm.ua.ikysil.smap.*;

/** Handles mapping line level details for a Java class. If a SourceDebugExtension containing a
  * JSR-045 SMAP is present in the class file, that can be used to provide additional line level
  * coverage.
  *
  * Given a `BitSet` containing line hits and a starting line number, the mapper will return all
  * applicable source files and their covered lines.
  *
  * @author robertf
  */
public class LineLevelMapper {
	public static class MappedCoverage {
		/** The path information we have for this coverage; either what's provided by debug
		  * information (if `isMapped` is false), or the path provided by the source map (if
		  * `isMapped` is true)
		  */
		public final String path;

		/** The starting line number for the coverage (corresponding with bit 0 in `lines`) */
		public final int startLine;

		/** The actual line level coverage details, one bit per line, beginning at `startLine` */
		public final BitSet lines;

		public MappedCoverage(String path, int startLine, BitSet lines) {
			this.path = path;
			this.startLine = startLine;
			this.lines = lines;
		}
	}

	private static class MappedLocation {
		public final String path;
		public final int line;

		public MappedLocation(String path, int line) {
			this.path = path;
			this.line = line;
		}
	}

	/** Returns an empty mapper with no source mappings */
	public static LineLevelMapper empty(String sourceFile) {
		return new LineLevelMapper(sourceFile, null);
	}

	/** Returns a mapper for `smap` if it can successfully be parsed, otherwise returns an empty
	  * mapper with no source mappings
	  */
	public static LineLevelMapper parse(String sourceFile, String smap) {
		SourceMap[] maps;

		try {
			Parser parser = new Parser();
			maps = parser.parse(smap);
		} catch (SourceMapException | IOException e) {
			Log.debug("line level mapper", String.format("cannot parse source map for %s; skipping", sourceFile), e);
			return empty(sourceFile);
		}

		Map<Integer, List<MappedLocation>> mappings = new HashMap<>();

		for (SourceMap map : maps) {
			if (!map.getOutputFileName().equals(sourceFile)) {
				Log.debug("line level mapper", String.format("skipping source map; output specified as '%s' (looking for '%s')", map.getOutputFileName(), sourceFile));
				continue;
			}

			for (Stratum stratum : map.getStratumList().items()) {
				for (LineInfo lineInfo : stratum.getLineInfoList().items()) {
					FileInfo file = lineInfo.getFileInfo();
					int inputLine = lineInfo.getInputStartLine();
					int outputLine = lineInfo.getOutputStartLine();
					for (int ili = 0; ili < lineInfo.getRepeatCount(); ili++, inputLine++) {
						for (int oli = 0; oli < lineInfo.getOutputLineIncrement(); oli++, outputLine++) {
							List<MappedLocation> locations = mappings.get(outputLine);
							if (locations == null) mappings.put(outputLine, (locations = new LinkedList<>()));
							locations.add(new MappedLocation(file.getInputFilePath(), inputLine));
						}
					}
				}
			}
		}

		return new LineLevelMapper(sourceFile, mappings);
	}

	private final String filename;
	private final Map<Integer, MappedLocation[]> mappings;

	private LineLevelMapper(String filename, Map<Integer, List<MappedLocation>> rawMappings) {
		this.filename = filename;

		if(rawMappings != null)
		{
			mappings = new HashMap<>(rawMappings.size());
			for (Map.Entry<Integer, List<MappedLocation>> mapping : rawMappings.entrySet()) {
				mappings.put(
					mapping.getKey(),
					mapping.getValue().toArray(new MappedLocation[0])
				);
			}
		}
		else
		{
			mappings = new HashMap<>(64);
		}
	}

	public MappedCoverage[] map(int startLine, BitSet lines) {
		if (mappings == null || mappings.isEmpty()) {
			return null;
		} else {
			// just going to use a start line of 1 for mapped files (assuming they won't be very large)
			Map<String, BitSet> resultMap = new HashMap<>();

			for (int l = lines.nextSetBit(0); l >= 0; l = lines.nextSetBit(l + 1)) {
				MappedLocation[] mappingsForLine = mappings.get(startLine + l);
				if (mappingsForLine != null) for (MappedLocation loc : mappingsForLine) {
					BitSet ls = resultMap.get(loc.path);
					if (ls == null) resultMap.put(loc.path, ls = new BitSet());
					ls.set(loc.line - 1);
				}
			}

			if (resultMap.isEmpty()) return null;

			MappedCoverage[] result = new MappedCoverage[resultMap.size()/* + 1*/];
			int ri = 0;

			for (Map.Entry<String, BitSet> mapped : resultMap.entrySet())
				result[ri++] = new MappedCoverage(mapped.getKey(), 1, mapped.getValue());

			return result;
		}
	}
}
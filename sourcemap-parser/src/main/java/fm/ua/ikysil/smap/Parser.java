/*
 *                 Sun Public License Notice
 * 
 * This file is subject to the Sun Public License Version 
 * 1.0 (the "License"); you may not use this file except in compliance with 
 * the License. A copy of the License is available at http://www.sun.com/
 * 
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 * 
 * The Original Code is sourcemap Library.
 * The Initial Developer of the Original Code is Illya Kysil.
 * Portions created by the Initial Developer are Copyright (C) 2004
 * the Initial Developer. All Rights Reserved.
 * 
 * Alternatively, the Library may be used under the terms of either
 * the Mozilla Public License Version 1.1 or later (the "MPL"),
 * the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * (the "Alternative License"), in which case the provisions
 * of the respective Alternative License are applicable instead of those above.
 * If you wish to allow use of your version of this Library only under
 * the terms of an Alternative License, and not to allow others to use your
 * version of this Library under the terms of the License, indicate your decision by
 * deleting the provisions above and replace them with the notice and other
 * provisions required by the Alternative License. If you do not delete
 * the provisions above, a recipient may use your version of this Library under
 * the terms of any one of the SPL, the MPL, the GPL or the LGPL.
 */
/*
 * Parser.java
 *
 * Created on April 29, 2004, 5:47 PM
 */

package fm.ua.ikysil.smap;

import java.io.*;
import java.util.*;

import fm.ua.ikysil.smap.parser.*;

/**
 *
 * @author  Illya Kysil
 */
public class Parser {

    private Map builders = new TreeMap();

    private State state = new State();

    /** Creates a new instance of Parser */
    public Parser() {
        registerBuilders();
    }

    protected void registerBuilders() {
        add(new SourceMapBuilder());
        add(new EndSourceMapBuilder());
        add(new StratumBuilder());
        add(new FileInfoBuilder());
        add(new LineInfoBuilder());
        add(new VendorInfoBuilder());
        add(new OpenEmbeddedStratumBuilder());
        add(new CloseEmbeddedStratumBuilder());
    }

    private void parseInit() throws SourceMapException {
        state.init();
    }

    private SourceMap[] parseDone() throws SourceMapException {
        EmbeddedStratum result = state.done();
        resolveLineFileInfo(result);
        return result.getSourceMapList().items();
    }

    private void resolveLineFileInfo(EmbeddedStratum embeddedStratum) throws SourceMapException {
        for (Iterator iter = embeddedStratum.getSourceMapList().iterator(); iter.hasNext();) {
            SourceMap sourceMap = (SourceMap) iter.next();
            resolveLineFileInfo(sourceMap);
        }
    }

    private void resolveLineFileInfo(SourceMap sourceMap) throws SourceMapException {
        for (Iterator iter = sourceMap.getStratumList().iterator(); iter.hasNext();) {
            Stratum stratum = (Stratum) iter.next();
            resolveLineFileInfo(stratum);
        }
        for (Iterator iter = sourceMap.getEmbeddedStratumList().iterator(); iter.hasNext();) {
            EmbeddedStratum stratum = (EmbeddedStratum) iter.next();
            resolveLineFileInfo(stratum);
        }
    }

    private void resolveLineFileInfo(Stratum stratum) throws SourceMapException {
        for (Iterator iter = stratum.getLineInfoList().iterator(); iter.hasNext();) {
            LineInfo lineInfo = (LineInfo) iter.next();
            FileInfo fileInfo = stratum.getFileInfoList().get(lineInfo.getFileId());
            if (fileInfo == null) {
                throw new ParserException("Invalid file id: " + lineInfo.getFileId());
            }
            lineInfo.setFileInfo(fileInfo);
        }
    }

    private Builder getBuilder(String[] lines) throws SourceMapException {
        if (lines.length == 0) {
            return null;
        }
        String sectionName = lines[0];
        String[] tokens = lines[0].split(" ", 2);
        if (tokens.length > 1) {
            sectionName = tokens[0].trim();
        }
        if (sectionName.startsWith(Constants.SectionNamePrefix)) {
            sectionName = sectionName.substring(Constants.SectionNamePrefix.length());
        }
        Builder builder = (Builder) builders.get(sectionName);
        if (builder == null) {
            builder = new UnknownInfoBuilder();
        }
        return builder;
    }

    private void parseSection(String[] lines) throws SourceMapException {
        Builder builder = getBuilder(lines);
        if (builder != null) {
            builder.build(state, lines);
        }
    }

    public SourceMap[] parse(String source) throws SourceMapException, IOException {
        return parse(new StringReader(source));
    }

    public SourceMap[] parse(Reader reader) throws SourceMapException, IOException {
        String line = "";
        try {
            parseInit();
            ArrayList lines = new ArrayList();
            BufferedReader br = new BufferedReader(reader);
            boolean sectionLine = true;
            while ((line = br.readLine()) != null) {
                state.lineNumber++;
                if (line.startsWith(Constants.SectionNamePrefix)
                        || (sectionLine && line.equals(Constants.SourceMapHeader))) {
                    parseSection((String[]) lines.toArray(new String[0]));
                    lines.clear();
                }
                sectionLine = line.startsWith(Constants.SectionNamePrefix);
                lines.add(line);
            }
            parseSection((String[]) lines.toArray(new String[0]));
            return parseDone();
        }
        catch (SourceMapException sme) {
            ParserException pe = new ParserException(sme.getMessage() + ":" + state.lineNumber + ":" + line);
            pe.initCause(sme);
            throw pe;
        }
    }

    public void add(Builder builder) {
        builders.put(builder.getSectionName(), builder);
    }

    public void remove(Builder builder) {
        builders.remove(builder.getSectionName());
    }

}

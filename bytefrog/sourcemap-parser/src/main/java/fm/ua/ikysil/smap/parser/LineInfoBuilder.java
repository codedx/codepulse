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
 * LineInfoBuilder.java
 *
 * Created on May 3, 2004, 9:42 AM
 */

package fm.ua.ikysil.smap.parser;

import java.util.regex.*;

import fm.ua.ikysil.smap.*;

/**
 *
 * @author  Illya Kysil
 */
public class LineInfoBuilder implements Builder {

    /** Creates a new instance of LineInfoBuilder */
    public LineInfoBuilder() {
    }

    public String getSectionName() {
        return Constants.LineSectionName;
    }

    private static final String LineInfoPattern = "(\\d++)(#(\\d++))?(,(\\d++))?:(\\d++)(,(\\d++))?($)";

    public void build(State state, String[] lines) throws SourceMapException {
        if (!state.getStratum().getLineInfoList().isEmpty()) {
            throw new SourceMapException("Only one line section allowed");
        }
        Pattern p = Pattern.compile(LineInfoPattern);
        int fileId = 0;
        for (int i = 1; i < lines.length; i++) {
            int inputStartLine = 1;
            int repeatCount = 1;
            int outputStartLine = 1;
            int outputLineIncrement = 1;
            Matcher m = p.matcher(lines[i]);
            if (!m.matches()) {
                throw new SourceMapException("Invalid line info: " + lines[i]);
            }
            try {
                inputStartLine = Integer.parseInt(m.group(1));
                if (m.group(3) != null) {
                    fileId = Integer.parseInt(m.group(3));
                }
                if (m.group(5) != null) {
                    repeatCount = Integer.parseInt(m.group(5));
                }
                outputStartLine = Integer.parseInt(m.group(6));
                if (m.group(8) != null) {
                    outputLineIncrement = Integer.parseInt(m.group(8));
                }
            }
            catch (NumberFormatException nfe) {
                throw new SourceMapException("Invalid line info: " + lines[i]);
            }
            LineInfo lineInfo = new LineInfo(fileId, inputStartLine, repeatCount, outputStartLine, outputLineIncrement);
            state.getStratum().getLineInfoList().add(lineInfo);
        }
    }

}

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
 * State.java
 *
 * Created on May 3, 2004, 10:07 AM
 */

package fm.ua.ikysil.smap.parser;

import java.util.Stack;

import fm.ua.ikysil.smap.*;

/**
 *
 * @author  Illya Kysil
 */
public class State {

    private SourceMap sourceMap;

    private Stratum stratum;

    private EmbeddedStratum parentStratum;

    private Stack stateStack = new Stack();

    public int lineNumber;

    /** Creates a new instance of State */
    public State() {
    }

    public void init() throws SourceMapException {
        lineNumber = 0;
        sourceMap = null;
        stratum = null;
        parentStratum = new EmbeddedStratum();
        stateStack.clear();
    }

    public EmbeddedStratum done() throws SourceMapException {
        if (!stateStack.isEmpty()) {
            throw new ParserException("Unbalanced source map");
        }
        return parentStratum;
    }

    /**
     * Getter for property sourceMap.
     * @return Value of property sourceMap.
     */
    public SourceMap getSourceMap() {
        return sourceMap;
    }

    /**
     * Setter for property sourceMap.
     * @param sourceMap New value of property sourceMap.
     */
    void setSourceMap(SourceMap sourceMap) throws SourceMapException {
        if (this.sourceMap != null) {
            throw new ParserException("End of source map expected");
        }
        this.sourceMap = sourceMap;
        stratum = null;
    }

    void endSourceMap() throws SourceMapException {
        if (sourceMap == null) {
            throw new ParserException("Unexpected end of source map");
        }
        sourceMap = null;
        stratum = null;
    }

    /**
     * Getter for property stratum.
     * @return Value of property stratum.
     */
    public Stratum getStratum() throws SourceMapException {
        if (stratum == null) {
            throw new ParserException("Stratum expected");
        }
        return stratum;
    }

    /**
     * Setter for property stratum.
     * @param stratum New value of property stratum.
     */
    void setStratum(Stratum stratum) throws SourceMapException {
        if (sourceMap == null) {
            throw new ParserException("Source map expected");
        }
        this.stratum = stratum;
    }

    void push(EmbeddedStratum embeddedStratum) throws SourceMapException {
        stateStack.push(new StackItem(sourceMap, parentStratum));
        endSourceMap();
        setParentStratum(embeddedStratum);
    }

    void pop(EmbeddedStratum embeddedStratum) throws SourceMapException {
        if (!parentStratum.getName().equals(embeddedStratum.getName())) {
            throw new ParserException("Invalid closing embedded stratum: " + embeddedStratum.getName());
        }
        StackItem item = (StackItem) stateStack.pop();
        setSourceMap(item.sourceMap);
        setParentStratum(item.parentStratum);
    }

    /**
     * Getter for property parentStratum.
     * @return Value of property parentStratum.
     */
    public EmbeddedStratum getParentStratum() {
        return parentStratum;
    }

    /**
     * Setter for property parentStratum.
     * @param parentStratum New value of property parentStratum.
     */
    private void setParentStratum(EmbeddedStratum parentStratum) {
        this.parentStratum = parentStratum;
    }

    private class StackItem {

        public SourceMap sourceMap;

        public EmbeddedStratum parentStratum;

        public StackItem(SourceMap sourceMap, EmbeddedStratum parentStratum) {
            this.sourceMap = sourceMap;
            this.parentStratum = parentStratum;
        }

    }

}

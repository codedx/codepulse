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
 * SourceMap.java
 *
 * Created on April 29, 2004, 1:04 PM
 */

package fm.ua.ikysil.smap;

import java.util.*;

import fm.ua.ikysil.smap.collections.*;

/**
 *
 * @author  Illya Kysil
 */
public class SourceMap implements Cloneable {

    private String outputFileName;

    private String defaultStratumName;

    private StratumList stratumList = new StratumList();

    private EmbeddedStratumList embeddedStratumList = new EmbeddedStratumList();

    /** Creates a new instance of SourceMap */
    public SourceMap() {
    }

    public SourceMap(String outputFileName, String defaultStratumName) {
        this.outputFileName = outputFileName;
        this.defaultStratumName = defaultStratumName;
    }

    public Object clone() {
        SourceMap sourceMap = new SourceMap(outputFileName, defaultStratumName);
        for (Iterator iter = stratumList.iterator(); iter.hasNext();) {
            sourceMap.getStratumList().add((Stratum) ((Stratum) iter.next()).clone());
        }
        for (Iterator iter = embeddedStratumList.iterator(); iter.hasNext();) {
            sourceMap.getEmbeddedStratumList().add((EmbeddedStratum) ((EmbeddedStratum) iter.next()).clone());
        }
        return sourceMap;
    }

    public boolean isResolved() {
        return embeddedStratumList.isEmpty();
    }

    /**
     * Getter for property outputFileName.
     * @return Value of property outputFileName.
     */
    public String getOutputFileName() {
        return outputFileName;
    }

    /**
     * Setter for property outputFileName.
     * @param outputFileName New value of property outputFileName.
     */
    public void setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
    }

    /**
     * Getter for property defaultStratumName.
     * @return Value of property defaultStratumName.
     */
    public String getDefaultStratumName() {
        return defaultStratumName;
    }

    /**
     * Setter for property defaultStratumName.
     * @param defaultStratumName New value of property defaultStratumName.
     */
    public void setDefaultStratumName(String defaultStratumName) {
        this.defaultStratumName = defaultStratumName;
    }

    /**
     * Getter for property stratumList.
     * @return Value of property stratumList.
     */
    public StratumList getStratumList() {
        return stratumList;
    }

    /**
     * Setter for property stratumList.
     * @param stratumList New value of property stratumList.
     */
    public void setStratumList(StratumList stratumList) {
        this.stratumList.clear();
        if (stratumList != null) {
            this.stratumList.addAll(stratumList);
        }
    }

    /**
     * Getter for property embeddedStratumList.
     * @return Value of property embeddedStratumList.
     */
    public EmbeddedStratumList getEmbeddedStratumList() {
        return embeddedStratumList;
    }

    /**
     * Setter for property embeddedStratumList.
     * @param embeddedStratumList New value of property embeddedStratumList.
     */
    public void setEmbeddedStratumList(EmbeddedStratumList embeddedStratumList) {
        this.embeddedStratumList.clear();
        if (embeddedStratumList != null) {
            this.embeddedStratumList.addAll(embeddedStratumList);
        }
    }

    public Stratum getStratum(String stratumName) {
        for (Iterator iter = stratumList.iterator(); iter.hasNext();) {
            Stratum stratum = (Stratum) iter.next();
            if (stratum.getName().compareTo(stratumName) == 0) {
                return stratum;
            }
        }
        return null;
    }

}

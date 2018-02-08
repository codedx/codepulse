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
 * Stratum.java
 *
 * Created on April 29, 2004, 1:32 PM
 */

package fm.ua.ikysil.smap;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import fm.ua.ikysil.smap.collections.*;

/**
 *
 * @author  Illya Kysil
 */
public class Stratum extends AbstractStratum implements Cloneable {

    private FileInfoList fileInfoList = new FileInfoList();

    private LineInfoList lineInfoList = new LineInfoList();

    private VendorInfoList vendorInfoList = new VendorInfoList();

    private UnknownInfoList unknownInfoList = new UnknownInfoList();

    /** Creates a new instance of Stratum */
    public Stratum() {
        this("");
    }

    public Stratum(String name) {
        super(name);
    }

    public Object clone() {
        Stratum stratum = new Stratum(getName());
        for (Iterator iter = vendorInfoList.iterator(); iter.hasNext();) {
            stratum.getVendorInfoList().add((VendorInfo) ((VendorInfo) iter.next()).clone());
        }
        for (Iterator iter = unknownInfoList.iterator(); iter.hasNext();) {
            stratum.getUnknownInfoList().add((UnknownInfo) ((UnknownInfo) iter.next()).clone());
        }
        Map fileInfoMap = new HashMap();
        for (Iterator iter = fileInfoList.iterator(); iter.hasNext();) {
            FileInfo fileInfoOrig = (FileInfo) iter.next();
            FileInfo fileInfoClone = (FileInfo) fileInfoOrig.clone();
            fileInfoMap.put(fileInfoOrig, fileInfoClone);
            stratum.getFileInfoList().add(fileInfoClone);
        }
        for (Iterator iter = lineInfoList.iterator(); iter.hasNext();) {
            LineInfo lineInfo = (LineInfo) iter.next();
            FileInfo fileInfo = lineInfo.getFileInfo();
            if (fileInfo != null) {
                fileInfo = (FileInfo) fileInfoMap.get(fileInfo);
                lineInfo.setFileInfo(fileInfo);
            }
            stratum.getLineInfoList().add(lineInfo);
        }
        return stratum;
    }

    /**
     * Getter for property fileInfoList.
     * @return Value of property fileInfoList.
     */
    public FileInfoList getFileInfoList() {
        return fileInfoList;
    }

    /**
     * Setter for property fileInfoList.
     * @param fileInfoList New value of property fileInfoList.
     */
    public void setFileInfoList(FileInfoList fileInfoList) {
        this.fileInfoList.clear();
        if (fileInfoList != null) {
            this.fileInfoList.addAll(fileInfoList);
        }
    }

    /**
     * Getter for property lineInfoList.
     * @return Value of property lineInfoList.
     */
    public LineInfoList getLineInfoList() {
        return lineInfoList;
    }

    /**
     * Setter for property lineInfoList.
     * @param lineInfoList New value of property lineInfoList.
     */
    public void setLineInfoList(LineInfoList lineInfoList) {
        this.lineInfoList.clear();
        if (lineInfoList != null) {
            this.lineInfoList.addAll(lineInfoList);
        }
    }

    /**
     * Getter for property vendorInfoList.
     * @return Value of property vendorInfoList.
     */
    public VendorInfoList getVendorInfoList() {
        return vendorInfoList;
    }

    /**
     * Setter for property vendorInfoList.
     * @param vendorInfoList New value of property vendorInfoList.
     */
    public void setVendorInfoList(VendorInfoList vendorInfoList) {
        this.vendorInfoList.clear();
        if (vendorInfoList != null) {
            this.vendorInfoList.addAll(vendorInfoList);
        }
    }

    /**
     * Getter for property unknownInfoList.
     * @return Value of property unknownInfoList.
     */
    public UnknownInfoList getUnknownInfoList() {
        return unknownInfoList;
    }

    /**
     * Setter for property unknownInfoList.
     * @param unknownInfoList New value of property unknownInfoList.
     */
    public void setUnknownInfoList(UnknownInfoList unknownInfoList) {
        this.unknownInfoList.clear();
        if (unknownInfoList != null) {
            this.unknownInfoList.addAll(unknownInfoList);
        }
    }

}

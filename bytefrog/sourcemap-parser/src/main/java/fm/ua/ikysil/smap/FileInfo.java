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
 * FileInfo.java
 *
 * Created on April 29, 2004, 1:07 PM
 */

package fm.ua.ikysil.smap;

/**
 *
 * @author  Illya Kysil
 */
public class FileInfo implements Cloneable {

    private int fileId = -1;

    private String inputFileName;

    private String inputFilePath;

    /** Creates a new instance of FileInfo */
    public FileInfo() {
    }

    public FileInfo(int fileId, String inputFileName, String inputFilePath) {
        this.fileId = fileId;
        this.inputFileName = inputFileName;
        this.inputFilePath = inputFilePath;
    }

    public Object clone() {
        return new FileInfo(fileId, inputFileName, inputFilePath);
    }

    /**
     * Getter for property fileId.
     * @return Value of property fileId.
     */
    public int getFileId() {
        return fileId;
    }

    /**
     * Setter for property fileId.
     * @param fileId New value of property fileId.
     */
    public void setFileId(int fileId) {
        this.fileId = fileId;
    }

    /**
     * Getter for property inputFileName.
     * @return Value of property inputFileName.
     */
    public String getInputFileName() {
        return inputFileName;
    }

    /**
     * Setter for property inputFileName.
     * @param inputFileName New value of property inputFileName.
     */
    public void setInputFileName(String inputFileName) {
        this.inputFileName = inputFileName;
    }

    /**
     * Getter for property inputFilePath.
     * @return Value of property inputFilePath.
     */
    public String getInputFilePath() {
        if (inputFilePath == null) {
            return inputFileName;
        }
        else {
            return inputFilePath;
        }
    }

    /**
     * Setter for property inputFilePath.
     * @param inputFilePath New value of property inputFilePath.
     */
    public void setInputFilePath(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }

}

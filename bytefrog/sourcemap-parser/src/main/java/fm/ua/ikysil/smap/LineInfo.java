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
 * LineInfo.java
 *
 * Created on April 29, 2004, 1:15 PM
 */

package fm.ua.ikysil.smap;

/**
 *
 * @author  Illya Kysil
 */
public class LineInfo implements Cloneable {

    private int fileId = -1;

    private int inputStartLine;

    private int repeatCount;

    private int outputStartLine;

    private int outputLineIncrement;

    private FileInfo fileInfo;

    /** Creates a new instance of LineInfo */
    public LineInfo() {
    }

    public LineInfo(int inputStartLine, int repeatCount, int outputStartLine, int outputLineIncrement) {
        this(-1, inputStartLine, repeatCount, outputStartLine, outputLineIncrement);
    }

    public LineInfo(int fileId, int inputStartLine, int repeatCount,
            int outputStartLine, int outputLineIncrement) {
        this.fileId = fileId;
        this.fileInfo = null;
        this.inputStartLine = inputStartLine;
        this.repeatCount = repeatCount;
        this.outputStartLine = outputStartLine;
        this.outputLineIncrement = outputLineIncrement;
    }

    public LineInfo(FileInfo fileInfo, int inputStartLine, int repeatCount,
            int outputStartLine, int outputLineIncrement) {
        this.fileId = -1;
        this.fileInfo = fileInfo;
        this.inputStartLine = inputStartLine;
        this.repeatCount = repeatCount;
        this.outputStartLine = outputStartLine;
        this.outputLineIncrement = outputLineIncrement;
    }

    public Object clone() {
        LineInfo lineInfo = new LineInfo(fileId, inputStartLine, repeatCount,
                outputStartLine, outputLineIncrement);
        lineInfo.setFileInfo(fileInfo);
        return lineInfo;
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

    public int resolveFileId() {
        if (fileInfo != null) {
            fileId = fileInfo.getFileId();
        }
        return fileId;
    }

    /**
     * Getter for property inputStartLine.
     * @return Value of property inputStartLine.
     */
    public int getInputStartLine() {
        return inputStartLine;
    }

    /**
     * Setter for property inputStartLine.
     * @param inputStartLine New value of property inputStartLine.
     */
    public void setInputStartLine(int inputStartLine) {
        this.inputStartLine = inputStartLine;
    }

    /**
     * Getter for property repeatCount.
     * @return Value of property repeatCount.
     */
    public int getRepeatCount() {
        return repeatCount;
    }

    /**
     * Setter for property repeatCount.
     * @param repeatCount New value of property repeatCount.
     */
    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    /**
     * Getter for property outputStartLine.
     * @return Value of property outputStartLine.
     */
    public int getOutputStartLine() {
        return outputStartLine;
    }

    /**
     * Setter for property outputStartLine.
     * @param outputStartLine New value of property outputStartLine.
     */
    public void setOutputStartLine(int outputStartLine) {
        this.outputStartLine = outputStartLine;
    }

    /**
     * Getter for property outputLineIncrement.
     * @return Value of property outputLineIncrement.
     */
    public int getOutputLineIncrement() {
        return outputLineIncrement;
    }

    /**
     * Setter for property outputLineIncrement.
     * @param outputLineIncrement New value of property outputLineIncrement.
     */
    public void setOutputLineIncrement(int outputLineIncrement) {
        this.outputLineIncrement = outputLineIncrement;
    }

    /**
     * Getter for property fileInfo.
     * @return Value of property fileInfo.
     */
    public FileInfo getFileInfo() {
        return fileInfo;
    }

    /**
     * Setter for property fileInfo.
     * @param fileInfo New value of property fileInfo.
     */
    public void setFileInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

}

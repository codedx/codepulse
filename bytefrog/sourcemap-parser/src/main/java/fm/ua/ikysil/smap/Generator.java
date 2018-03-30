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
 * Generator.java
 *
 * Created on May 3, 2004, 10:34 AM
 */

package fm.ua.ikysil.smap;

import java.io.*;
import java.util.*;
import java.text.MessageFormat;

import fm.ua.ikysil.smap.generator.*;

/**
 *
 * @author  Illya Kysil
 */
public class Generator {

    private Optimizer optimizer;

    /** Creates a new instance of Generator */
    public Generator() {
        this(null);
    }

    public Generator(Optimizer optimizer) {
        setOptimizer(optimizer);
    }

    /**
     * Getter for property optimizer.
     * @return Value of property optimizer.
     */
    public Optimizer getOptimizer() {
        return optimizer;
    }

    /**
     * Setter for property optimizer.
     * @param optimizer New value of property optimizer.
     */
    public void setOptimizer(Optimizer optimizer) {
        this.optimizer = optimizer;
    }

    public void generate(Writer writer, SourceMap sourceMap) throws GeneratorException, IOException {
        BufferedWriter bw = new BufferedWriter(writer);
        try {
            write(bw, sourceMap);
        }
        finally {
            bw.flush();
        }
    }

    private void write(BufferedWriter writer, SourceMap sourceMap) throws GeneratorException, IOException {
        writer.write(Constants.SourceMapHeader);
        writer.newLine();
        writer.write(sourceMap.getOutputFileName());
        writer.newLine();
        String defaultStratumName = sourceMap.getDefaultStratumName();
        if (defaultStratumName.trim().equals("")) {
            defaultStratumName = "Java";
        }
        writer.write(defaultStratumName);
        writer.newLine();
        for (Iterator iter = sourceMap.getEmbeddedStratumList().iterator(); iter.hasNext();) {
            EmbeddedStratum embeddedStratum = (EmbeddedStratum) iter.next();
            write(writer, embeddedStratum);
        }
        for (Iterator iter = sourceMap.getStratumList().iterator(); iter.hasNext();) {
            Stratum stratum = (Stratum) iter.next();
            write(writer, stratum);
        }
        writer.write(Constants.SectionNamePrefix + Constants.EndSectionName);
        writer.newLine();
    }

    private void write(BufferedWriter writer, EmbeddedStratum embeddedStratum) throws GeneratorException, IOException {
        writer.write(Constants.SectionNamePrefix + Constants.OpenEmbeddedStratumSectionName + " " + embeddedStratum.getName());
        writer.newLine();
        for (Iterator iter = embeddedStratum.getSourceMapList().iterator(); iter.hasNext();) {
            SourceMap sourceMap = (SourceMap) iter.next();
            write(writer, sourceMap);
        }
        writer.write(Constants.SectionNamePrefix + Constants.CloseEmbeddedStratumSectionName + " " + embeddedStratum.getName());
        writer.newLine();
    }

    private void write(BufferedWriter writer, Stratum stratum) throws GeneratorException, IOException {
        Stratum opStratum = (optimizer == null ? stratum : optimizer.optimize(stratum));
        generateFileIds(opStratum);
        writer.write(Constants.SectionNamePrefix + Constants.StratumSectionName + " " + opStratum.getName());
        writer.newLine();
        writeFileSection(writer, opStratum);
        writeLineSection(writer, opStratum);
        for (Iterator iter = opStratum.getVendorInfoList().iterator(); iter.hasNext();) {
            VendorInfo vendorInfo = (VendorInfo) iter.next();
            write(writer, vendorInfo);
        }
        for (Iterator iter = opStratum.getUnknownInfoList().iterator(); iter.hasNext();) {
            UnknownInfo unknownInfo = (UnknownInfo) iter.next();
            write(writer, unknownInfo);
        }
    }

    private void generateFileIds(Stratum stratum) throws GeneratorException {
        int fileId = 0;
        for (Iterator iter = stratum.getFileInfoList().iterator(); iter.hasNext();) {
            FileInfo fileInfo = (FileInfo) iter.next();
            fileInfo.setFileId(fileId++);
        }
    }

    private void writeFileSection(BufferedWriter writer, Stratum stratum) throws GeneratorException, IOException {
        if (stratum.getFileInfoList().isEmpty()) {
            return;
        }
        writer.write(Constants.SectionNamePrefix + Constants.FileSectionName);
        writer.newLine();
        for (Iterator iter = stratum.getFileInfoList().iterator(); iter.hasNext();) {
            FileInfo fileInfo = (FileInfo) iter.next();
            if (fileInfo.getInputFileName().equals(fileInfo.getInputFilePath())
                    || "".equals(fileInfo.getInputFilePath())) {
                writer.write(fileInfo.getFileId() + " " + fileInfo.getInputFileName());
                writer.newLine();
            }
            else {
                writer.write("+ " + fileInfo.getFileId() + " " + fileInfo.getInputFileName());
                writer.newLine();
                writer.write(fileInfo.getInputFilePath());
                writer.newLine();
            }
        }
    }

    private void writeLineSection(BufferedWriter writer, Stratum stratum) throws GeneratorException, IOException {
        if (stratum.getLineInfoList().isEmpty()) {
            return;
        }
        writer.write(Constants.SectionNamePrefix + Constants.LineSectionName);
        writer.newLine();
        int lastFileId = 0;
        for (Iterator iter = stratum.getLineInfoList().iterator(); iter.hasNext();) {
            LineInfo lineInfo = (LineInfo) iter.next();
            Object[] values = new Object[] {
                new Integer(lineInfo.getInputStartLine()),
                new Integer(lineInfo.resolveFileId()),
                new Integer(lineInfo.getRepeatCount()),
                new Integer(lineInfo.getOutputStartLine()),
                new Integer(lineInfo.getOutputLineIncrement()),};
            String fmt = "{0}";
            if (lastFileId != lineInfo.getFileId()) {
                lastFileId = lineInfo.getFileId();
                fmt += "#{1}";
            }
            if (lineInfo.getRepeatCount() != 1) {
                fmt += ",{2}";
            }
            fmt += ":{3}";
            if (lineInfo.getOutputLineIncrement() != 1) {
                fmt += ",{4}";
            }
            String line = MessageFormat.format(fmt, values);
            writer.write(line);
            writer.newLine();
        }
    }

    private void write(BufferedWriter writer, VendorInfo vendorInfo) throws GeneratorException, IOException {
        writer.write(Constants.SectionNamePrefix + Constants.VendorSectionName);
        writer.newLine();
        writer.write(vendorInfo.getVendorId());
        writer.newLine();
        String[] data = vendorInfo.getData();
        for (int i = 0; i < data.length; i++) {
            writer.write(data[i]);
            writer.newLine();
        }
    }

    private void write(BufferedWriter writer, UnknownInfo unknownInfo) throws GeneratorException, IOException {
        String[] data = unknownInfo.getData();
        for (int i = 0; i < data.length; i++) {
            writer.write(data[i]);
            writer.newLine();
        }
    }

}

/*
 * Copyright (c) 2007, SQL Power Group Inc.
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of SQL Power Group Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ca.sqlpower.architect.swingui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.apache.log4j.Logger;

import ca.sqlpower.architect.ArchitectException;
import ca.sqlpower.architect.ArchitectUtils;
import ca.sqlpower.architect.SQLCatalog;
import ca.sqlpower.architect.SQLColumn;
import ca.sqlpower.architect.SQLDatabase;
import ca.sqlpower.architect.SQLObject;
import ca.sqlpower.architect.SQLRelationship;
import ca.sqlpower.architect.SQLSchema;
import ca.sqlpower.architect.SQLTable;
import ca.sqlpower.architect.ddl.DDLGenerator;
import ca.sqlpower.architect.diff.ArchitectDiffException;
import ca.sqlpower.architect.diff.DiffChunk;
import ca.sqlpower.architect.diff.DiffType;
import ca.sqlpower.architect.swingui.CompareDMPanel.SourceOrTargetStuff;
import ca.sqlpower.architect.swingui.CompareDMSettings.SourceOrTargetSettings;
import ca.sqlpower.swingui.SPSUtils;

public class CompareDMFormatter {

    private static final Logger logger = Logger.getLogger(CompareDMFormatter.class);
    
    private final ArchitectSwingSession session;
    private CompareDMSettings dmSetting;

    /**
     * The dialog that owns any additional dialogs popped up by this formatter.
     */
    private final Component dialogOwner;

    public CompareDMFormatter(ArchitectSwingSession session, Component dialogOwner, CompareDMSettings compDMSet) {
        super();
        this.session = session;
        this.dialogOwner = dialogOwner;
        dmSetting = compDMSet;
    }

    public void format(List<DiffChunk<SQLObject>> diff, List<DiffChunk<SQLObject>> diff1,
            SQLObject left, SQLObject right) {
        try {
            SourceOrTargetStuff source = dmSetting.getSourceStuff();
            
            DefaultStyledDocument sourceDoc = new DefaultStyledDocument();
            DefaultStyledDocument targetDoc = new DefaultStyledDocument();

            DDLGenerator gen = null;
            if (dmSetting.getOutputFormat().equals(CompareDMSettings.OutputFormat.SQL)) {
                gen = dmSetting.getDdlGenerator().newInstance();
                SQLCatalog cat = (SQLCatalog) dmSetting.getSourceSettings().getCatalogObject();
                SQLSchema sch = (SQLSchema) dmSetting.getSourceSettings().getSchemaObject();
                gen.setTargetCatalog(cat == null ? null : cat.getPhysicalName());
                gen.setTargetSchema(sch == null ? null : sch.getPhysicalName());
            }
            
            final Map<DiffType, AttributeSet> styles = new HashMap<DiffType, AttributeSet>();
            {
                SimpleAttributeSet att = new SimpleAttributeSet();
                StyleConstants.setForeground(att, Color.red);
                styles.put(DiffType.LEFTONLY, att);

                att = new SimpleAttributeSet();
                StyleConstants.setForeground(att, Color.green.darker().darker());
                styles.put(DiffType.RIGHTONLY, att);

                att = new SimpleAttributeSet();
                StyleConstants.setForeground(att, Color.black);
                styles.put(DiffType.SAME, att);

                att = new SimpleAttributeSet();
                StyleConstants.setForeground(att, Color.orange);
                styles.put(DiffType.MODIFIED, att);

                att = new SimpleAttributeSet();
                StyleConstants.setForeground(att, Color.blue);
                styles.put(DiffType.KEY_CHANGED, att);
            }
           if (dmSetting.getOutputFormat().equals(CompareDMSettings.OutputFormat.SQL)) {

                List<DiffChunk<SQLObject>> addRelationships = new ArrayList<DiffChunk<SQLObject>>();
                List<DiffChunk<SQLObject>> dropRelationships = new ArrayList<DiffChunk<SQLObject>>();
                List<DiffChunk<SQLObject>> nonRelationship = new ArrayList<DiffChunk<SQLObject>>    ();
                for (DiffChunk d : diff) {
                    if (logger.isDebugEnabled()) logger.debug(d);
                    if (d.getData() instanceof SQLRelationship) {
                        if (d.getType() == DiffType.LEFTONLY) {
                            dropRelationships.add(d);
                        } else if (d.getType() == DiffType.RIGHTONLY) {
                            addRelationships.add(d);
                        }
                    } else {
                        nonRelationship.add(d);
                    }
                }
                sqlScriptGenerator(styles, dropRelationships, gen);
                sqlScriptGenerator(styles, nonRelationship, gen);
                sqlScriptGenerator(styles, addRelationships, gen);

            } else if (dmSetting.getOutputFormat().equals(CompareDMSettings.OutputFormat.ENGLISH)) {
                generateEnglishDescription(styles, diff, sourceDoc);
                generateEnglishDescription(styles, diff1, targetDoc);
            } else {
                throw new IllegalStateException(
                "Don't know what type of output to make");
            }
           
            // This is a little error-prone because the ancestor could be a frame,
            // So we just hope this is only ever used from the comparedmpanel's dialog
            Dialog owner = (Dialog) SPSUtils.getWindowInHierarchy(dialogOwner);
           
            // get the title string for the compareDMFrame
            if (dmSetting.getOutputFormat().equals(CompareDMSettings.OutputFormat.SQL)) {
                String titleString = "Generated SQL Script to turn "+ toTitleText(true, left)
                + " into " + toTitleText(false, right);

                SQLDatabase db = null;

                if ( dmSetting.getSourceSettings().getDatastoreType().equals(CompareDMSettings.DatastoreType.FILE) )
                    db = null;
                else if (dmSetting.getSourceSettings().getDatastoreType().equals(CompareDMSettings.DatastoreType.PROJECT) )
                    db = session.getTargetDatabase();
                else
                    db = source.getDatabase();
                logger.debug("We got to place #2");

                SQLScriptDialog ssd = new SQLScriptDialog(owner,
                        "Compare DM",
                        titleString,
                        false,
                        gen,
                        db == null?null:db.getDataSource(),
                        false,
                        session);
                ssd.setVisible(true);
                logger.debug("We got to place #3");

            } else {
                String leftTitle = toTitleText(true, left);
                String rightTitle = toTitleText(false, right);

                CompareDMFrame cf =
                    new CompareDMFrame(owner, sourceDoc, targetDoc, leftTitle,rightTitle);

                cf.pack();
                cf.setVisible(true);
            }
        } catch (ArchitectDiffException ex) {
            ASUtils.showExceptionDialog(session, "Could not perform the diff", ex);
            logger.error("Couldn't do diff", ex);
        } catch (ArchitectException exp) {
            ASUtils.showExceptionDialog(session, "StartCompareAction failed", exp);
            logger.error("StartCompareAction failed", exp);
        } catch (BadLocationException ex) {
            ASUtils.showExceptionDialog(session,
                    "Could not create document for results", ex);
            logger.error("Could not create document for results", ex);
        } catch (Exception ex) {
            ASUtils.showExceptionDialog(session, "Unxepected Exception!", ex);
            logger.error("Unxepected Exception!", ex);
        } 

    }

    private void sqlScriptGenerator(Map<DiffType, AttributeSet> styles,
            List<DiffChunk<SQLObject>> diff,
            DDLGenerator gen)
    throws ArchitectDiffException, SQLException,
    ArchitectException, BadLocationException,
    InstantiationException, IllegalAccessException {
        for (DiffChunk<SQLObject> chunk : diff) {
            if (chunk.getType() == DiffType.KEY_CHANGED) {
                if(chunk.getData() instanceof SQLTable)
                {
                    SQLTable t = (SQLTable) chunk.getData();
                    if (hasKey(t)) {
                        gen.addPrimaryKey(t);
                    } else {
                        gen.dropPrimaryKey(t);
                    }
                }

            }else if (chunk.getType() == DiffType.LEFTONLY)
            {
                if (chunk.getData() instanceof SQLTable)
                {
                    SQLTable t = (SQLTable) chunk.getData();
                    gen.dropTable(t);
                }else if (chunk.getData() instanceof SQLColumn){
                    SQLColumn c = (SQLColumn) chunk.getData();
                    gen.dropColumn(c);
                } else if (chunk.getData() instanceof SQLRelationship){
                    SQLRelationship r = (SQLRelationship)chunk.getData();
                    gen.dropRelationship(r);

                } else {
                    throw new IllegalStateException("DiffChunk is an unexpected type.");
                }

            } else if (chunk.getType() == DiffType.RIGHTONLY){
                if (chunk.getData() instanceof SQLTable)
                {
                    SQLTable t = (SQLTable) chunk.getData();
                    if (t == null ) throw new NullPointerException();
                    if(t.getObjectType().equals("TABLE")) {
                        gen.addTable(t);
                    }
                    if (hasKey(t)) {
                        gen.addPrimaryKey(t);
                    }
                }else if (chunk.getData() instanceof SQLColumn){
                    SQLColumn c = (SQLColumn) chunk.getData();
                    gen.addColumn(c);
                }else if (chunk.getData() instanceof SQLRelationship){
                    SQLRelationship r = (SQLRelationship)chunk.getData();
                    gen.addRelationship(r);
                }else {
                    throw new IllegalStateException("DiffChunk is an unexpected type.");
                }
            }
            else if (chunk.getType() == DiffType.MODIFIED)
            {
                if (chunk.getData() instanceof SQLColumn)
                {
                    SQLColumn c = (SQLColumn) chunk.getData();
                    gen.modifyColumn(c);
                } else {
                    throw new IllegalStateException("DiffChunk is an unexpected type.");
                }
            } else {

            }
        }
    }
    
    /**
     * This method generates english descriptions by taking in the diff list
     * and putting the appropiate statements in the document.  It will iterate
     * through the diff list and identify which type of DiffChunk it is and
     * what kind of SQLType it is to produce the proper english description
     * output
     * @throws BadLocationException
     * @throws ArchitectException
     */
    private void generateEnglishDescription(
            Map<DiffType, AttributeSet> styles,
            List<DiffChunk<SQLObject>> diff, DefaultStyledDocument sourceDoc)
            throws BadLocationException, ArchitectException {

        String currentTableName = "";
        
        for (DiffChunk<SQLObject> chunk : diff) {
            SQLObject o = chunk.getData();
            if (dmSetting.getSuppressSimilarities() && chunk.getType().equals(DiffType.SAME)) {
                if (o instanceof SQLTable) {
                    currentTableName = o.getName();
                }
                    
                continue;
            }
            AttributeSet attributes = styles.get(chunk.getType());
            MutableAttributeSet boldAttributes = new SimpleAttributeSet(attributes);
            StyleConstants.setBold(boldAttributes, true);

            if (o == null) {
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        "ERROR: null object in diff list\n",
                        attributes);
            } else if (o instanceof SQLTable) {
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        "Table ",
                        attributes);
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        o.getName() + " ",
                        boldAttributes);
            } else if (o instanceof SQLColumn) {
                if (dmSetting.getSuppressSimilarities() && !currentTableName.equals("")) {
                    attributes = styles.get(DiffType.SAME);
                    boldAttributes = new SimpleAttributeSet(attributes);
                    StyleConstants.setBold(boldAttributes, true);
                    sourceDoc.insertString(
                            sourceDoc.getLength(), 
                            "Table ", 
                            attributes);
                    sourceDoc.insertString(
                            sourceDoc.getLength(), 
                            currentTableName, 
                            boldAttributes);
                    sourceDoc.insertString(
                            sourceDoc.getLength(), 
                            " needs no changes\n", 
                            attributes);
                    currentTableName = "";
                }
                attributes = styles.get(chunk.getType());
                boldAttributes = new SimpleAttributeSet(attributes);
                StyleConstants.setBold(boldAttributes, true);
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        "\tColumn ",
                        attributes);
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        o.getName() + " ",
                        boldAttributes);
            } else if (o instanceof SQLRelationship) {
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        "Foreign Key ",
                        attributes);
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        o.getName() + " ",
                        boldAttributes);
            } else {
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        "Unknown object type ",
                        attributes);
                sourceDoc.insertString(
                        sourceDoc.getLength(),
                        o.getClass().getName() + " ",
                        boldAttributes);
            }


            String diffTypeEnglish;
            switch (chunk.getType()) {
            case LEFTONLY:
                diffTypeEnglish = "should be removed";
                break;

            case MODIFIED:
                diffTypeEnglish = "should be modified";
                break;

            case SAME:
                diffTypeEnglish = "needs no changes";
                break;

            case RIGHTONLY:
                diffTypeEnglish = "should be added";
                break;

            case KEY_CHANGED:
                diffTypeEnglish = "needs a different primary key";
                break;

            default:
                diffTypeEnglish = "!UNKNOWN DIFF TYPE!";
                logger.error("Woops, unknown diff chunk type: "+chunk.getType());
                break;
            }

            sourceDoc.insertString(
                    sourceDoc.getLength(),
                    diffTypeEnglish + "\n",
                    attributes);
        }
    }
    
//  Generates the proper title text for compareDMFrame or SQLScriptDialog                
    private String toTitleText(boolean isSource, SQLObject leftOrRight) {                    
        StringBuffer fileName = new StringBuffer();
        boolean needBrackets = false;
        SourceOrTargetSettings settings;
        
        if (isSource) {
            settings = dmSetting.getSourceSettings();
        } else {
            settings = dmSetting.getTargetSettings();
        }
        
        //Deals with the file name first if avaiable
        if (settings.getDatastoreType().equals(CompareDMSettings.DatastoreType.FILE)) {                                
            File f = new File(settings.getFilePath());                                                                                         
            String tempName = f.getName();
            int lastIndex = tempName.lastIndexOf(".architect");
            if (lastIndex < 0) {
                fileName.append(tempName);
            } else {
                fileName.append(tempName.substring(0, lastIndex));
            }
            needBrackets = true;
        } else if (settings.getDatastoreType().equals(CompareDMSettings.DatastoreType.PROJECT)) {
            SwingUIProject swingUIProject = session.getProject();
            String tempName;
            if (swingUIProject.getFile() != null) {
                tempName = swingUIProject.getFile().getName();
            } else {
                tempName = "New Project";
            }
            int lastIndex = tempName.lastIndexOf(".architect");
            if (lastIndex < 0){
                fileName.append(tempName);
            } else {
                fileName.append(tempName.substring(0,lastIndex));
            }
            needBrackets = true;
        }
        
        //Add in the database name
        if (needBrackets) {
            fileName.append(" (");
        }
        fileName.append(ArchitectUtils.toQualifiedName(leftOrRight));
        if (needBrackets) {
            fileName.append(")");
        }
        return fileName.toString(); 
    }
    
    private boolean hasKey(SQLTable t) throws ArchitectException {
        boolean hasKey = false;
        for (SQLColumn c : t.getColumns()) {
            if (c.isPrimaryKey()) {
                hasKey=true;
                break;
            }
        }
        return hasKey;
    }
}
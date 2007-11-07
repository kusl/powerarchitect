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

import javax.swing.JDialog;

import ca.sqlpower.architect.ArchitectException;
import ca.sqlpower.architect.ArchitectSession;
import ca.sqlpower.architect.CoreUserSettings;
import ca.sqlpower.architect.etl.kettle.KettleJob;
import ca.sqlpower.architect.undo.UndoManager;
import ca.sqlpower.swingui.SwingWorkerRegistry;

/**
 * The ArchitectSwingSession interface provides methods that are applicable
 * to a Swing UI invocation of the Architect.  It extends the ArchitectSession
 * interface, which provides information about a session with the core (non-UI specific)
 * objects.
 */
public interface ArchitectSwingSession extends ArchitectSession, SwingWorkerRegistry {
   
    /**
     * Returns the context that created this session.
     */
    public ArchitectSwingSessionContext getContext();
    
    /**
     * Narrows the return type for the project: Swing Sessions
     * have SwingUI projects, which are a subclass of CoreProject.
     */
    public SwingUIProject getProject();
    
    /**
     * Gets the recent menu list
     * 
     * @return the recent menu
     */
    public RecentMenu getRecentMenu();

    /**
     * Returns the ArchitectFrame created in this session. 
     */
    public ArchitectFrame getArchitectFrame();
    
    /**
     * Gets the value of sourceDatabases
     *
     * @return the value of sourceDatabases
     */
    public DBTree getSourceDatabases();
    
    /**
     * Gets the value of playPen
     *
     * @return the value of playPen
     */
    public PlayPen getPlayPen();
    
    /**
     * Gets the UndoManager keeping track of changes in this session
     */
    public UndoManager getUndoManager();
    
    public CompareDMSettings getCompareDMSettings();
      
    /**
     * Returns the JDialog containing the ProfileManagerView
     */
    public JDialog getProfileDialog();
    
    /**
     * This is a common handler for all actions that must
     * occur when switching projects, e.g., dispose dialogs, 
     * shut down running threads, etc. 
     */
    public void close();
    
    /**
     * See {@link #userSettings}.
     *
     * @return the value of userSettings
     */
    public CoreUserSettings getUserSettings();
    
    /**
     * Saves the project associated with this session, optionally showing a file
     * chooser, and optionally doing the work in a separate worker thread.
     * 
     * @param showChooser If true, a file chooser will always be presented.  If
     * false, a file chooser will only be presented if the project save location
     * has not yet been set.
     * @param separateThread If true, the work will be done in a separate thread
     * and this method will return in a shorter amount of time.
     * @return True if the save operation was not cancelled by the user.
     */
    public boolean saveOrSaveAs(boolean showChooser, boolean separateThread);

    /**
     * See {@link #savingEntireSource}.
     *
     * @return the value of savingEntireSource
     */
    public boolean isSavingEntireSource();

    /**
     * See {@link #savingEntireSource}.
     *
     * @param argSavingEntireSource Value to assign to this.savingEntireSource
     */
    public void setSavingEntireSource(boolean argSavingEntireSource);
    
    public KettleJob getKettleJob();

    public void setKettleJob(KettleJob kettleJob);

    /**
     * Initializes the GUI components for this session. Call this only if you need a GUI.
     * This method must be called on the Swing Event Dispatch Thread.
     * 
     * @throws ArchitectException
     * @throws IllegalStateException if showGUI==true and this method was
     * not called on the Event Dispatch Thread.
     */
    public void initGUI() throws ArchitectException;

    /**
     * Returns true if the session contains a completely new and unmodified project.
     * Otherwise, it returns false.
     * <p>
     * Note: that this property is different from the {@link SwingUIProject#isModified()}
     * property in that the isNew property is persisted when the project is saved, and
     * refers to whether the project was ever modified since it was first created, 
     * whereas, the isModified property refers to whether the project was modified ever
     * since the project was last loaded.  
     */
    public boolean isNew();

    /**
     * Relationship line style: True means direct lines; false means only horizontal
     * and vertical lines.
     */
    public boolean getRelationshipLinesDirect();

    /**
     * Relationship line style: True means direct lines; false means only horizontal
     * and vertical lines.  Updating this preference will cause all of the relationships
     * in this session's play pen to have their line style updated.
     */
    public void setRelationshipLinesDirect(boolean direct);
}

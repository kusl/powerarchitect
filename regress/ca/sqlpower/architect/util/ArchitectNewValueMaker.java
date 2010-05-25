/*
 * Copyright (c) 2010, SQL Power Group Inc.
 *
 * This file is part of Power*Architect.
 *
 * Power*Architect is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Power*Architect is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.architect.util;

import ca.sqlpower.architect.ArchitectProject;
import ca.sqlpower.architect.ProjectSettings;
import ca.sqlpower.architect.ProjectSettings.ColumnVisibility;
import ca.sqlpower.architect.etl.kettle.KettleSettings;
import ca.sqlpower.architect.olap.OLAPObject;
import ca.sqlpower.architect.olap.OLAPRootObject;
import ca.sqlpower.architect.olap.OLAPSession;
import ca.sqlpower.architect.olap.MondrianModel.Cube;
import ca.sqlpower.architect.olap.MondrianModel.Dimension;
import ca.sqlpower.architect.olap.MondrianModel.Schema;
import ca.sqlpower.architect.profile.ColumnProfileResult;
import ca.sqlpower.architect.profile.ColumnValueCount;
import ca.sqlpower.architect.profile.ProfileManager;
import ca.sqlpower.architect.profile.ProfileManagerImpl;
import ca.sqlpower.architect.profile.ProfileSettings;
import ca.sqlpower.architect.profile.TableProfileResult;
import ca.sqlpower.architect.swingui.ArchitectSwingProject;
import ca.sqlpower.architect.swingui.PlayPenComponent;
import ca.sqlpower.architect.swingui.PlayPenContentPane;
import ca.sqlpower.architect.swingui.TablePane;
import ca.sqlpower.architect.swingui.olap.CubePane;
import ca.sqlpower.architect.swingui.olap.DimensionPane;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.PlDotIni;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.SQLColumn;
import ca.sqlpower.sqlobject.SQLObjectException;
import ca.sqlpower.sqlobject.SQLTable;
import ca.sqlpower.testutil.GenericNewValueMaker;

public class ArchitectNewValueMaker extends GenericNewValueMaker {      

    private final ArchitectSwingProject valueMakerProject;
    
    private final MondrianNewValueMaker mondrianValueMaker;
    
    public ArchitectNewValueMaker(SPObject root) {
        this(root, new PlDotIni());
    }
    
    public ArchitectNewValueMaker(final SPObject root, DataSourceCollection<SPDataSource> dsCollection) {
        super(root, dsCollection);
        mondrianValueMaker = new MondrianNewValueMaker(root, dsCollection);
        valueMakerProject = (ArchitectSwingProject) makeNewValue(ArchitectSwingProject.class, null, null);
        valueMakerProject.setPlayPenContentPane(new PlayPenContentPane());
    }    
    
    @Override
    public Object makeNewValue(Class<?> valueType, Object oldVal, String propName) {
        if (valueType == ProfileSettings.class) {
            ProfileSettings settings = new ProfileSettings();
            getRootObject().addChild(settings, 0);
            return settings;
        } else if (valueType == ProjectSettings.class) {
            ProjectSettings settings = new ProjectSettings();
            root.addChild(settings, 0);
            return settings;
        } else if (valueType == KettleSettings.class) {
            return ((ArchitectSwingProject) makeNewValue(ArchitectSwingProject.class, null, null)).getKettleSettings();
        } else if (valueType == ColumnVisibility.class) { 
            if (oldVal != ColumnVisibility.ALL) {
                return ColumnVisibility.ALL;
            } else {
                return ColumnVisibility.PK;
            }
        } else if (valueType == TableProfileResult.class) {
            TableProfileResult tpr = new TableProfileResult(
                    (SQLTable) makeNewValue(SQLTable.class, null, ""), 
                    (ProfileSettings) makeNewValue(ProfileSettings.class, null, ""));
            getRootObject().addChild(tpr, 0);
            return tpr;
        } else if (valueType == ColumnProfileResult.class) {
            TableProfileResult tpr = (TableProfileResult) makeNewValue(TableProfileResult.class, null, "");
            ColumnProfileResult cpr = new ColumnProfileResult(
                    (SQLColumn) makeNewValue(SQLColumn.class, null, ""), tpr);
            cpr.setParent(tpr);
            return cpr;
        } else if (valueType == ColumnValueCount.class) {
            ColumnValueCount cvc = new ColumnValueCount(Integer.MAX_VALUE, 2, 42);
            getRootObject().addChild(cvc, 0);
            return cvc;
        } else if (valueType == ArchitectSwingProject.class || valueType == ArchitectProject.class || 
                valueType == ArchitectSwingProject.class) {
            ArchitectSwingProject project;
            try {
                project = new ArchitectSwingProject();
            } catch (SQLObjectException e) {
                throw new RuntimeException(e);
            }
            getRootObject().addChild(project, 0);
            return project;
        } else if (ProfileManager.class.isAssignableFrom(valueType)) {
            ProfileManagerImpl profileManager = new ProfileManagerImpl();
            valueMakerProject.setProfileManager(profileManager);            
            return profileManager;
        } else if (valueType == PlayPenContentPane.class) {
            PlayPenContentPane pane = new PlayPenContentPane();
            ((ArchitectSwingProject) makeNewValue(ArchitectSwingProject.class, null, null)).setPlayPenContentPane(pane);            
            return pane;
        } else if (valueType == PlayPenComponent.class) {            
            return makeNewValue(TablePane.class, null, null);
        } else if (valueType == TablePane.class) {
            PlayPenContentPane p = valueMakerProject.getPlayPenContentPane();
            TablePane tp = new TablePane((SQLTable) super.makeNewValue(SQLTable.class, null, null), p);            
            p.addChild(tp, p.getChildren(TablePane.class).size());
            return tp;
        } else if (valueType == DimensionPane.class) {
            PlayPenContentPane contentPane = makeOlapContentPane();
            Schema schema = ((OLAPSession) contentPane.getModelContainer()).getSchema();
            Dimension dimension = new Dimension();
            schema.addDimension(dimension);
            DimensionPane dPane = new DimensionPane(dimension, contentPane);
            contentPane.addChild(dPane, 0);
            return dPane;
        } else if (valueType == CubePane.class) {
            PlayPenContentPane contentPane = makeOlapContentPane();
            Schema schema = ((OLAPSession) contentPane.getModelContainer()).getSchema();
            Cube cube = new Cube();
            schema.addCube(cube);
            CubePane cPane = new CubePane(cube, contentPane);
            contentPane.addChild(cPane, 0);
            return cPane;
        } else if (valueType == OLAPSession.class) {
            OLAPSession session = new OLAPSession(new Schema());
            OLAPRootObject root = new OLAPRootObject();
            root.addChild(session);
            getRootObject().addChild(root, 0);
            return session;
        } else if (valueType == OLAPRootObject.class) {
            return ((ArchitectSwingProject) makeNewValue(ArchitectSwingProject.class, null, null)).getOlapRootObject();
        } else if (OLAPObject.class.isAssignableFrom(valueType)) {
            return mondrianValueMaker.makeNewValue(valueType, oldVal, propName);
        } else {
            return super.makeNewValue(valueType, oldVal, propName);
        }
    }
    
    private PlayPenContentPane makeOlapContentPane() {
        OLAPSession session = new OLAPSession(new Schema());
        valueMakerProject.getOlapRootObject().addChild(session);            
        PlayPenContentPane contentPane = new PlayPenContentPane(session);
        contentPane.setParent(valueMakerProject);
        valueMakerProject.addOLAPContentPane(contentPane); 
        return contentPane;
    }

}

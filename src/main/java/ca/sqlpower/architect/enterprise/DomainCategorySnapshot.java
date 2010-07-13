/*
 * Copyright (c) 2010, SQL Power Group Inc.
 *
 * This file is part of SQL Power Architect.
 *
 * SQL Power Architect is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SQL Power Architect is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.architect.enterprise;

import java.util.Collections;
import java.util.List;

import ca.sqlpower.object.SystemSPObjectSnapshot;
import ca.sqlpower.object.ObjectDependentException;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.object.annotation.Accessor;
import ca.sqlpower.object.annotation.Constructor;
import ca.sqlpower.object.annotation.ConstructorParameter;
import ca.sqlpower.object.annotation.ConstructorParameter.ParameterType;

public class DomainCategorySnapshot extends SystemSPObjectSnapshot<DomainCategory> {
    private final DomainCategory spObject;
    
    @Constructor
    public DomainCategorySnapshot(
            @ConstructorParameter (isProperty = ParameterType.CHILD, 
                    propertyName = "spObject") DomainCategory spObject,
            @ConstructorParameter (propertyName = "originalUUID") String originalUUID,
            @ConstructorParameter (propertyName = "workspaceRevision", defaultValue = "0") int systemRevision) {
        super(originalUUID, systemRevision);
        this.spObject = spObject;
        this.spObject.setParent(this);
    }
    
    public DomainCategorySnapshot(DomainCategory original,
            int systemRevision) throws IllegalArgumentException,
            ObjectDependentException {
        super(original.getUUID(), systemRevision);
        setName(original.getName());
        spObject = new DomainCategory(original.getName());
        spObject.setParent(this);
    }

    /**
     * An unmodifiable {@link List} of allowed child types
     */
    public static final List<Class<? extends SPObject>> allowedChildTypes = 
         Collections.<Class<? extends SPObject>>singletonList(DomainCategory.class);
    
    public List<Class<? extends SPObject>> getAllowedChildTypes() {
        return allowedChildTypes;
    }

    public List<? extends SPObject> getChildren() {
        return Collections.singletonList(spObject);
    }

    @Accessor
    public DomainCategory getSPObject() {
        return spObject;
    }
}

package ca.sqlpower.architect;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import org.apache.log4j.Logger;

public class SQLTable extends SQLObject {

	private static Logger logger = Logger.getLogger(SQLTable.class);

	protected SQLDatabase parentDatabase;
	protected SQLObject parent;
	protected SQLCatalog catalog;
	protected SQLSchema schema;
	protected String tableName;
	protected String remarks;
	protected String objectType;
	protected String primaryKeyName;
	
	/**
	 * A List of SQLColumn objects which make up all the columns of
	 * this table.
	 */
	protected List columns;
	protected Folder columnsFolder;

	/**
	 * A List of SQLRelationship objects describing keys that this
	 * table exports.  This SQLTable is the "pkTable" in its exported
	 * keys.
	 */
	protected List exportedKeys;
	protected Folder exportedKeysFolder;

	/**
	 * A List of SQLRelationship objects describing keys that this
	 * table imports.  This SQLTable is the "fkTable" in its imported
	 * keys.
	 */
	protected List importedKeys;
	protected Folder importedKeysFolder;

	protected boolean columnsPopulated;
	protected boolean relationshipsPopulated;

	public SQLTable(SQLDatabase parentDb, SQLObject parent, SQLCatalog catalog, SQLSchema schema, String name, String remarks, String objectType) {
		this.parentDatabase = parentDb;
		this.parent = parent;
		this.catalog = catalog;
		this.schema = schema;
		this.tableName = name;
		this.remarks = remarks;
		this.columnsPopulated = false;
		this.relationshipsPopulated = false;
		this.objectType = objectType;

		this.columns = new ArrayList();
		this.importedKeys = new ArrayList();
		this.exportedKeys = new ArrayList();
		this.children = new ArrayList();
		children.add(columnsFolder = new Folder("Columns", this, columns));
		children.add(exportedKeysFolder = new Folder("Exported Keys", this, exportedKeys));
		children.add(importedKeysFolder = new Folder("Imported Keys", this, importedKeys));
	}
	
	/**
	 * Creates a new SQLTable with parent as its parent and a null
	 * schema and catalog.
	 */
	public SQLTable(SQLDatabase parent) {
		this(parent, parent, null, null, "", "", "TABLE");
	}

	protected static void addTablesToDatabase(SQLDatabase addTo) 
		throws SQLException, ArchitectException {
		HashMap catalogs = new HashMap();
		HashMap schemas = new HashMap();
		synchronized (addTo) {
			Connection con = addTo.getConnection();
			DatabaseMetaData dbmd = con.getMetaData();
			ResultSet mdTables = null;
			try {
				mdTables = dbmd.getTables(null,
										  null,
										  "%",
										  new String[] {"SYSTEM TABLE", "TABLE", "VIEW"});
				while (mdTables.next()) {
					SQLObject tableParent = addTo;

					String catName = mdTables.getString(1);
					SQLCatalog cat = null;

					if (catName != null) {
						cat = (SQLCatalog) catalogs.get(catName);
						if (cat == null) {
							cat = new SQLCatalog(addTo, catName);
							addTo.children.add(cat);
							catalogs.put(catName, cat);
						}
						tableParent = cat;
					}

					String schName = mdTables.getString(2);
					SQLSchema schema = null;
					if (schName != null) {
						schema = (SQLSchema) schemas.get(catName+"."+schName);
						if (schema == null) {
							if (cat == null) {
								schema = new SQLSchema(addTo, schName);
								addTo.children.add(schema);
							} else {
								schema = new SQLSchema(cat, schName);
								cat.children.add(schema);
							}
							schemas.put(catName+"."+schName, schema);
						}
						tableParent = schema;
					}

					tableParent.children.add(new SQLTable(addTo,
														  tableParent,
														  cat,
														  schema,
														  mdTables.getString(3),
														  mdTables.getString(5),
														  mdTables.getString(4)
														  ));
				}
			} finally {
				if (mdTables != null) mdTables.close();
			}
		}
	}

	protected synchronized void populateColumns() throws ArchitectException {
		if (columnsPopulated) return;
		int oldSize = columns.size();
		try {
			SQLColumn.addColumnsToTable(this,
										getCatalogName(),
										getSchemaName(),
										tableName);
			columnsPopulated = true;
		} catch (SQLException e) {
			throw new ArchitectException("table.populate", e);
		} finally {
			columnsPopulated = true;
			int newSize = columns.size();
			if (newSize > oldSize) {
				int[] changedIndices = new int[newSize - oldSize];
				for (int i = 0, n = newSize - oldSize; i < n; i++) {
					changedIndices[i] = oldSize + i;
				}
				columnsFolder.fireDbChildrenInserted(changedIndices,
													 columns.subList(oldSize, newSize));
			}
		}
	}
	
	/**
	 * Populates all the imported key relationships.  This has the
	 * side effect of populating the exported key side of the
	 * relationships for the exporting tables.
	 */
	public synchronized void populateRelationships() throws ArchitectException {
		if (!columnsPopulated) throw new IllegalStateException("Table must be populated before relationships are added");
		if (relationshipsPopulated) return;
		int oldSize = importedKeys.size();
		try {
			SQLRelationship.addRelationshipsToTable(this);
			relationshipsPopulated = true;
		} finally {
			relationshipsPopulated = true;
			int newSize = importedKeys.size();
			if (newSize > oldSize) {
				int[] changedIndices = new int[newSize - oldSize];
				for (int i = 0, n = newSize - oldSize; i < n; i++) {
					changedIndices[i] = oldSize + i;
				}
				importedKeysFolder.fireDbChildrenInserted(changedIndices,
														  children.subList(oldSize, newSize));
			}
		}
	}

	public void addImportedKey(SQLRelationship r) {
		importedKeys.add(r);
	}

	public void addExportedKey(SQLRelationship r) {
		exportedKeys.add(r);
	}

	public static SQLTable getDerivedInstance(SQLTable source, SQLDatabase parent)
		throws ArchitectException {
		source.populate();
		SQLTable t = new SQLTable(parent);
		t.columnsPopulated = true;
		t.relationshipsPopulated = true;
		t.tableName = source.tableName;
		t.remarks = source.remarks;
		t.inherit(source);
		parent.addChild(t);
		return t;
	}

	/**
	 * Adds all the columns of the given source table to the end of
	 * this table's column list.
	 */
	public void inherit(SQLTable source) throws ArchitectException {
		SQLColumn c;
		Iterator it = source.getColumns().iterator();
		while (it.hasNext()) {
			SQLObject child = (SQLObject) it.next();
			if (child instanceof SQLColumn) {
				c = SQLColumn.getDerivedInstance((SQLColumn) child, this);
				columnsFolder.addChild(c);
			} else {
				logger.warn("inherit doesn't support child of type "+child.getClass().getName());
			}
		}
	}

	/**
	 * Inserts all the columns of the given source table into this
	 * table at position <code>pos</code>.
	 */
	public void inherit(int pos, SQLTable source) throws ArchitectException {
		SQLColumn c;
		Iterator it = source.getColumns().iterator();
		while (it.hasNext()) {
			SQLObject child = (SQLObject) it.next();
			if (child instanceof SQLColumn) {
				c = SQLColumn.getDerivedInstance((SQLColumn) child, this);
				columnsFolder.addChild(pos, c);
				pos += 1;
			} else {
				logger.warn("inherit doesn't support child of type "+child.getClass().getName());
			}
		}
	}

	public SQLColumn getColumn(int idx) throws ArchitectException {
		return (SQLColumn) columnsFolder.getChild(idx);
	}

	/**
	 * Populates this table then searches for the named column.
	 */
	public SQLColumn getColumnByName(String colName) throws ArchitectException {
		return getColumnByName(colName, true);
	}
	
	/**
	 * Searches for the named table.
	 *
	 * @param populate If true, this table will retrieve its column
	 * list from the database; otherwise it just searches the current
	 * list.
	 */
	public SQLColumn getColumnByName(String colName, boolean populate) throws ArchitectException {
		if (populate && !columnsPopulated) populate();
		logger.debug("Looking for column "+colName+" in "+children);
		Iterator it = columns.iterator();
		while (it.hasNext()) {
			SQLColumn col = (SQLColumn) it.next();
			if (col.getColumnName().equalsIgnoreCase(colName)) {
				logger.debug("FOUND");
				return col;
			}
		}
		logger.debug("NOT FOUND");
		return null;
	}

	public void addColumn(SQLColumn col) {
		columnsFolder.addChild(col);
	}

	public void addColumn(int index, SQLColumn col) {
		columnsFolder.addChild(index, col);
	}
	
	public String toString() {
		return getShortDisplayName();
	}

	// ---------------------- SQLObject support ------------------------

	public SQLObject getParent() {
		return parent;
	}

	public String getName() {
		return tableName;
	}

	/**
	 * The table's name.
	 */
	public String getShortDisplayName() {
		if (schema != null) {
			return schema.getSchemaName()+"."+tableName+" ("+objectType+")";
		} else {
			return tableName+" ("+objectType+")";
		}
	}

	/**
	 * Just calls populateColumns and populateRelationships.
	 */
	public void populate() throws ArchitectException {
		populateColumns();
		populateRelationships();
	}
	
	public boolean isPopulated() {
		return columnsPopulated && relationshipsPopulated;
	}

	/**
	 * Returns true (tables have several folders as children).
	 */
	public boolean allowsChildren() {
		return true;
	}

	public void removeDependencies() {
		Iterator it = importedKeys.iterator();
		while (it.hasNext()) {
			SQLRelationship r = (SQLRelationship) it.next();
			// FIXME: have to actually remove relationships
			logger.debug(r);
		}
	}

	/**
	 * The Folder class is a SQLObject that holds a SQLTable's child
	 * folders (columns and relationships).
	 */
	public static class Folder extends SQLObject {
		protected String name;
		protected SQLObject parent;

		public Folder(String name, SQLObject parent, List children) {
			this.name = name;
			this.parent = parent;
			this.children = children;
		}

		public String getName() {
			return name;
		}

		public SQLObject getParent() {
			return parent;
		}

		public void populate() {
		}

		public boolean isPopulated() {
			return true;
		}

		public String getShortDisplayName() {
			return name;
		}

		public boolean allowsChildren() {
			return true;
		}

		public String toString() {
			return name;
		}

	}

	// ------------------ Accessors and mutators below this line ------------------------

	/**
	 * Gets the value of parentDatabase
	 *
	 * @return the value of parentDatabase
	 */
	public SQLDatabase getParentDatabase()  {
		return this.parentDatabase;
	}

	/**
	 * Sets the value of parentDatabase
	 *
	 * @param argParentDatabase Value to assign to this.parentDatabase
	 */
	public void setParentDatabase(SQLDatabase argParentDatabase) {
		this.parentDatabase = argParentDatabase;
		// XXX: fire event?
	}

	/**
	 * @return An empty string if the catalog for this table is null;
	 * otherwise, catalog.getCatalogName().
	 */
	public String getCatalogName() {
		if (catalog == null) {
			return "";
		} else {
			return catalog.getCatalogName();
		}
	}

	public SQLCatalog getCatalog()  {
		return this.catalog;
	}

	protected void setCatalog(SQLCatalog argCatalog) {
		this.catalog = argCatalog;
		// XXX: fire event?
	}

	/**
	 * @return An empty string if the schema for this table is null;
	 * otherwise, schema.getSchemaName().
	 */
	public String getSchemaName() {
		if (schema == null) {
			return "";
		} else {
			return schema.getSchemaName();
		}
	}

	public SQLSchema getSchema()  {
		return this.schema;
	}

	protected void setSchema(SQLSchema argSchema) {
		this.schema = argSchema;
		// XXX: fire event?
	}

	public Folder getColumnsFolder() {
		return columnsFolder;
	}

	/**
	 * Gets the value of tableName
	 *
	 * @return the value of tableName
	 */
	public String getTableName()  {
		return this.tableName;
	}

	/**
	 * Sets the value of tableName
	 *
	 * @param argTableName Value to assign to this.tableName
	 */
	public void setTableName(String argTableName) {
		this.tableName = argTableName;
		fireDbObjectChanged("tableName");
	}

	/**
	 * Gets the value of remarks
	 *
	 * @return the value of remarks
	 */
	public String getRemarks()  {
		return this.remarks;
	}

	/**
	 * Sets the value of remarks
	 *
	 * @param argRemarks Value to assign to this.remarks
	 */
	public void setRemarks(String argRemarks) {
		this.remarks = argRemarks;
		fireDbObjectChanged("remarks");
	}

	/**
	 * Gets the value of columns
	 *
	 * @return the value of columns
	 */
	public synchronized List getColumns() throws ArchitectException {
		return columnsFolder.getChildren();
	}

	// 	/**
	// 	 * Sets the value of columns
	// 	 *
	// 	 * @param argColumns Value to assign to this.columns
	// 	 */
	// 	public synchronized void setColumns(List argColumns) {
	// 		List old = columns;
	// 		this.columns = argColumns;
	// 		columnsPopulated = true;
	// 		firePropertyChange("children", old, columns);
	// 	}

	/**
	 * Gets the value of importedKeys
	 *
	 * @return the value of importedKeys
	 */
	public List getImportedKeys()  {
		return this.importedKeys;
	}

	/**
	 * Gets the value of exportedKeys
	 *
	 * @return the value of exportedKeys
	 */
	public List getExportedKeys()  {
		return this.exportedKeys;
	}

	/**
	 * Sets the value of importedKeys
	 *
	 * @param argImportedKeys Value to assign to this.importedKeys
	 */
	public void setImportedKeys(List argImportedKeys) {
		this.importedKeys = argImportedKeys;
		fireDbObjectChanged("importedKeys");
	}

	/**
	 * Gets the value of columnsPopulated
	 *
	 * @return the value of columnsPopulated
	 */
	public boolean isColumnsPopulated()  {
		return this.columnsPopulated;
	}

	/**
	 * Sets the value of columnsPopulated
	 *
	 * @param argColumnsPopulated Value to assign to this.columnsPopulated
	 */
	protected void setColumnsPopulated(boolean argColumnsPopulated) {
		this.columnsPopulated = argColumnsPopulated;
	}

	/**
	 * Gets the value of relationshipsPopulated
	 *
	 * @return the value of relationshipsPopulated
	 */
	public boolean isRelationshipsPopulated()  {
		return this.relationshipsPopulated;
	}

	/**
	 * Sets the value of relationshipsPopulated
	 *
	 * @param argRelationshipsPopulated Value to assign to this.relationshipsPopulated
	 */
	protected void setRelationshipsPopulated(boolean argRelationshipsPopulated) {
		this.relationshipsPopulated = argRelationshipsPopulated;
	}

	/**
	 * Gets the value of primaryKeyName
	 *
	 * @return the value of primaryKeyName
	 */
	public String getPrimaryKeyName()  {
		return this.primaryKeyName;
	}

	/**
	 * Sets the value of primaryKeyName
	 *
	 * @param argPrimaryKeyName Value to assign to this.primaryKeyName
	 */
	public void setPrimaryKeyName(String argPrimaryKeyName) {
		this.primaryKeyName = argPrimaryKeyName;
		fireDbObjectChanged("primaryKeyName");
	}
	
	
	/**
	 * Gets the value of objectType
	 *
	 * @return the value of objectType
	 */
	public String getObjectType()  {
		return this.objectType;
	}

	/**
	 * Sets the value of objectType
	 *
	 * @param argObjectType Value to assign to this.objectType
	 */
	public void setObjectType(String argObjectType) {
		this.objectType = argObjectType;
		fireDbObjectChanged("objectType");
	}

}

package ca.sqlpower.architect.ddl;

import ca.sqlpower.architect.*;
import java.sql.*;
import java.util.*;
import java.io.File;

public class GenericDDLGenerator {

	public static final String GENERATOR_VERSION = "$Revision$";

	/**
	 * This property says whether or not the user will allow us to
	 * connect to the target system in order to determine database
	 * meta-data.  This generic base class will fail if
	 * allowConnection == false.
	 */
	protected boolean allowConnection;

	/**
	 * This is the user's selected file for saving the generated DDL.
	 * This class does not actually write DDL to disk, but you should
	 * use this file when you save the generated DDL string.
	 */
	protected File file;

	/**
	 * This is where each DDL statement gets accumulated while it is
	 * being generated.
	 */
	private StringBuffer ddl;

	/**
	 * Complete DDL statements are accumulated in this list.
	 */
	private List ddlStatements;

	/**
	 * This is initialized to the System line.separator property.
	 */
	protected static final String EOL = System.getProperty("line.separator");

	/**
	 * A mapping from JDBC type code (Integer values) to
	 * GenericTypeDescriptor objects which describe that data type.
	 */
	protected Map typeMap;

	/**
	 * This variable will be a live, non-null connection to the target
	 * database (set up by writeDDL) if allowConnection is true.
	 */
	protected Connection con;

	/**
	 * As table and relationship creation statements are generated,
	 * their SQL identifiers are stored in this map (key is name,
	 * value is object having that name).  Warnings are created when
	 * multiple objects in this top-level scope use the same name.
	 */
	protected Map topLevelNames;

	/**
	 * This list contains 0 or more {@link NameChangeWarning} objects.  It is
	 * populated as statements are added to the
	 * <code>ddlStatements</code> list.  If non-empty, this list of
	 * warnings should be presented to the user before the generated
	 * DDL is saved or executed (to give them a chance to fix the
	 * warnings and try again).
	 */
	protected List warnings;

	public GenericDDLGenerator() {
		allowConnection = true;
	}

	public StringBuffer generateDDL(SQLDatabase source) throws SQLException, ArchitectException {
		List statements = generateDDLStatements(source);

		ddl = new StringBuffer(4000);
		writeHeader();
		writeCreateDB(source);
		writeDDLTransactionBegin();

		Iterator it = statements.iterator();
		while (it.hasNext()) {
			ddl.append(it.next());
			writeStatementTerminator();
		}
		
		writeDDLTransactionEnd();
		return ddl;
	}

	public List generateDDLStatements(SQLDatabase source) throws SQLException, ArchitectException {
		warnings = new ArrayList();
		ddlStatements = new ArrayList();
		ddl = new StringBuffer(500);
		topLevelNames = new HashMap();  // for tracking dup table/relationship names

		if (allowConnection) {
			con = source.getConnection();
		} else {
			con = null;
		}
		
		createTypeMap();

		Iterator it = source.getChildren().iterator();
		while (it.hasNext()) {
			SQLTable t = (SQLTable) it.next();
			writeTable(t);
			writePrimaryKey(t);
		}
		it = source.getChildren().iterator();
		while (it.hasNext()) {
			SQLTable t = (SQLTable) it.next();
			writeExportedRelationships(t);
		}

		return ddlStatements;
	}

	/**
	 * Stores all the ddl since the last call to endStatement as a SQL
	 * statement. You have to call this at the end of each statement.
	 */
	public final void endStatement() {
		ddlStatements.add(ddl.toString());
		ddl = new StringBuffer(500);
	}

	public void writeHeader() {
		println("-- Created by SQLPower Generic DDL Generator "+GENERATOR_VERSION+" --");
	}

	/**
	 * Prints a single semicolon character (no newline).  If your
	 * database needs something else, override this method.
	 */
	public void writeStatementTerminator() {
		print(";");
	}

	/**
	 * Does nothing.  If your target system supports transactional
	 * DDL, override this method and print the appropriate statement.
	 */
	public void writeDDLTransactionBegin() {
	}

	/**
	 * Does nothing.  If your target system supports transactional
	 * DDL, override this method and print the appropriate statement.
	 */
	public void writeDDLTransactionEnd() {
	}

	public void writeCreateDB(SQLDatabase db) {
		println("-- Would Create Database "+db.getName()+" here. --");
	}

	public void writeTable(SQLTable t) throws SQLException, ArchitectException {
		Map colNameMap = new HashMap();  // for detecting duplicate column names

		String origTableName = t.getName();
		if (topLevelNames.get(t.getName()) != null) {
			int i = 1;
			String newName = origTableName+"_"+i;
			while (topLevelNames.get(newName) != null) {
				i++;
				newName = origTableName+"_"+i;
			}
			t.setTableName(newName);
			warnings.add(new NameChangeWarning(t, "Duplicate Table Name", origTableName));
		}
		topLevelNames.put(t.getName(), t);

		print("\nCREATE TABLE ");
		printIdentifier(t.getName());
		println(" (");
		boolean firstCol = true;
		Iterator it = t.getColumns().iterator();
		while (it.hasNext()) {
			SQLColumn c = (SQLColumn) it.next();
			String origName = c.getName();
			if (colNameMap.get(origName) != null) {
				int i = 1;
				String newName = origName+"_"+i;
				while (colNameMap.get(newName) != null) {
					i++;
					newName = origName+"_"+i;
				}
				c.setColumnName(newName);
				warnings.add(new NameChangeWarning(c, "Duplicate Col Name", origName));
			}
			colNameMap.put(c.getName(), c);

			GenericTypeDescriptor td = (GenericTypeDescriptor) typeMap.get(new Integer(c.getType()));
			if (td == null) {
				td = (GenericTypeDescriptor) typeMap.get(new Integer(Types.VARCHAR)); //better be non-null!
				GenericTypeDescriptor oldType = new GenericTypeDescriptor
					(c.getSourceDBTypeName(), c.getType(), c.getPrecision(),
					 null, null, c.getNullable(), false, false);
				oldType.determineScaleAndPrecision();
				warnings.add(new TypeMapWarning(c, "Unknown Target Type", oldType, td));
			}
			if (!firstCol) println(",");
			print("                ");
			printIdentifier(c.getName());
			print(" ");
			print(td.getName());
			if (td.getHasScale()) {
				print("("+c.getScale());
				if (td.getHasPrecision()) {
					print(","+c.getPrecision());
				}
				print(")");
			}

			if (c.isNullable()) {
				if (! td.isNullable()) {
					throw new UnsupportedOperationException
						("The data type "+td.getName()+" is not nullable on the target database platform.");
				}
				print(" NULL");
			} else {
				print(" NOT NULL");
			}

			// XXX: default values handled only in ETL?

			firstCol = false;
		}
		println("");
		print(")");
		endStatement();
		println("");
	}
	
	protected void writePrimaryKey(SQLTable t) throws ArchitectException {
		boolean firstCol = true;
		Iterator it = t.getColumns().iterator();
		while (it.hasNext()) {
			SQLColumn col = (SQLColumn) it.next();
			if (col.getPrimaryKeySeq() == null) break;
			if (firstCol) {
				println("");
				print("ALTER TABLE ");
				printIdentifier(t.getName());
				print(" ADD CONSTRAINT ");
				printIdentifier(t.getPrimaryKeyName());
				println("");
				print("PRIMARY KEY (");
				firstCol = false;
			} else {
				print(", ");
			}
			printIdentifier(col.getName());
		}
		if (!firstCol) {
			print(")");
			endStatement();
			println("");
		}
	}

	protected void writeExportedRelationships(SQLTable t) throws ArchitectException {
		Iterator it = t.getExportedKeys().iterator();
		while (it.hasNext()) {
			SQLRelationship rel = (SQLRelationship) it.next();

			String origRelName = rel.getName();
			if (topLevelNames.get(rel.getName()) != null) {
				int i = 1;
				String newName = origRelName+"_"+i;
				while (topLevelNames.get(newName) != null) {
					i++;
					newName = origRelName+"_"+i;
				}
				rel.setName(newName);
				warnings.add(new NameChangeWarning(rel, "Duplicate FK Name", origRelName));
			}
			topLevelNames.put(rel.getName(), t);

			println("");
			print("ALTER TABLE ");
			printIdentifier(rel.getFkTable().getName());
			print(" ADD CONSTRAINT ");
			printIdentifier(rel.getName());
			println("");
			print("FOREIGN KEY (");
			StringBuffer pkCols = new StringBuffer();
			StringBuffer fkCols = new StringBuffer();
			boolean firstCol = true;
			Iterator mappings = rel.getChildren().iterator();
			while (mappings.hasNext()) {
				SQLRelationship.ColumnMapping cmap = (SQLRelationship.ColumnMapping) mappings.next();
				if (!firstCol) {
					pkCols.append(", ");
					fkCols.append(", ");
				}
				appendIdentifier(pkCols, cmap.getPkColumn().getName());
				appendIdentifier(fkCols, cmap.getFkColumn().getName());
				firstCol = false;
			}
			print(fkCols.toString());
			println(")");
			print("REFERENCES ");
			printIdentifier(rel.getPkTable().getName());
			print(" (");
			print(pkCols.toString());
			print(")");
			endStatement();
			println("");
		}
	}

	/**
	 * Creates and populates <code>typeMap</code> using
	 * DatabaseMetaData.  Subclasses for specific DB platforms will be
	 * able to override this implementation with one that uses a
	 * static, pre-defined type map.
	 */
	protected void createTypeMap() throws SQLException {
		if (con == null || !allowConnection) {
			throw new UnsupportedOperationException("Can't create a type map without DatabaseMetaData");
		}
		typeMap = new HashMap();
		DatabaseMetaData dbmd = con.getMetaData();
		ResultSet rs = dbmd.getTypeInfo();
		while (rs.next()) {
			GenericTypeDescriptor td = new GenericTypeDescriptor(rs);
			typeMap.put(new Integer(td.getDataType()), td);
		}
	}

	protected void println(String text) {
		ddl.append(text).append(EOL);
	}

	protected void print(String text) {
		ddl.append(text);
	}

	/**
	 * Calls {@link #appendIdentifier} on <code>ddl</code>, the
	 * internal StringBuffer that accumulates the results of DDL
	 * generation.  It should never be necessary to override this
	 * method, because changes to appendIdentifier will always affect
	 * this method's behaviour.
	 */
	protected final void printIdentifier(String text) {
		appendIdentifier(ddl, text);
	}

	/**
	 * Converts space to underscore in <code>text</code> and appends
	 * the result to <code>sb</code>.  This will not be completely
	 * sufficient because it leaves ".", "%", and lots of other
	 * non-alphanumeric characters alone. Subclasses might choose to
	 * quote and leave everything alone, or whatever.
	 */
	protected void appendIdentifier(StringBuffer sb, String text) {
		sb.append(text.replace(' ', '_'));		
	}

	// ---------------------- accessors and mutators ----------------------
	
	/**
	 * Gets the value of allowConnection
	 *
	 * @return the value of allowConnection
	 */
	public boolean getAllowConnection()  {
		return this.allowConnection;
	}

	/**
	 * Sets the value of allowConnection
	 *
	 * @param argAllowConnection Value to assign to this.allowConnection
	 */
	public void setAllowConnection(boolean argAllowConnection) {
		this.allowConnection = argAllowConnection;
	}

	/**
	 * Gets the value of file
	 *
	 * @return the value of file
	 */
	public File getFile()  {
		return this.file;
	}

	/**
	 * Sets the value of file
	 *
	 * @param argFile Value to assign to this.file
	 */
	public void setFile(File argFile) {
		this.file = argFile;
	}

	/**
	 * Gets the value of typeMap
	 *
	 * @return the value of typeMap
	 */
	public Map getTypeMap()  {
		return this.typeMap;
	}

	/**
	 * Sets the value of typeMap
	 *
	 * @param argTypeMap Value to assign to this.typeMap
	 */
	public void setTypeMap(Map argTypeMap) {
		this.typeMap = argTypeMap;
	}

	/**
	 * Gets the value of con
	 *
	 * @return the value of con
	 */
	public Connection getCon()  {
		return this.con;
	}

	/**
	 * Sets the value of con
	 *
	 * @param argCon Value to assign to this.con
	 */
	public void setCon(Connection argCon) {
		this.con = argCon;
	}

	/**
	 * Returns {@link #warnings}.
	 */
	public List getWarnings() {
		return warnings;
	}
}

package ca.sqlpower.architect.ddl;

import java.util.Vector;

import org.apache.log4j.Logger;

import ca.sqlpower.architect.swingui.ASUtils;
import ca.sqlpower.architect.swingui.ASUtils.LabelValueBean;

/**
 * DDLUtils is a collection of utilities related to creating and
 * executing Data Definition Language (DDL) statements.
 */
public class DDLUtils {

	private static final Logger logger = Logger.getLogger(DDLUtils.class);

	/**
	 * DDLUtils is a container for static methods.  You can't make an instance of it.
	 */
	private DDLUtils() {
        // this never gets used
	}

    /**
     * Formats the components of a fully qualified database object name
     * into the standard SQL "dot notation".
     * 
     * @param catalog The catalog name of the object, or null if it has no catalog
     * @param schema The schema name of the object, or null if it has no schema
     * @param name The name of the object (null is not acceptable)
     * @return A dot-separated string of all the non-null arguments.
     */
    public static String toQualifiedName(String catalog, String schema, String name) {
        StringBuffer qualName = new StringBuffer();
        if (catalog != null) {
            qualName.append(catalog);
        }
        if (schema != null) {
            if (qualName.length() > 0)
                qualName.append(".");
            qualName.append(schema);
        }
        if (qualName.length() > 0)
            qualName.append(".");
        qualName.append(name);
        return qualName.toString();
    }
    
    public static Vector<LabelValueBean> getDDLTypes()
    {
    	
    		Vector<LabelValueBean> dbTypeList = new Vector();
		dbTypeList.add(ASUtils.lvb("Generic JDBC", GenericDDLGenerator.class));
		dbTypeList.add(ASUtils.lvb("DB2", DB2DDLGenerator.class));
		dbTypeList.add(ASUtils.lvb("Oracle 8i/9i", OracleDDLGenerator.class));
		dbTypeList.add(ASUtils.lvb("PostgreSQL", PostgresDDLGenerator.class));
		dbTypeList.add(ASUtils.lvb("SQLServer 2000", SQLServerDDLGenerator.class));
		return dbTypeList;
    }
    

}

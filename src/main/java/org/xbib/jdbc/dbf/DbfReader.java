package org.xbib.jdbc.dbf;

import org.xbib.jdbc.io.DataReader;
import org.xbib.jdbc.csv.CsvResources;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class DbfReader extends DataReader {
    private Object table = null;
    private List fields;
    private Integer recordCount;
    private Object record;
    private int rowNo;
    private Class<?> fieldClass;
    private Class<?> recordClass;
    private Class<?> tableClass;
    private Method tableOpenMethod;
    private Method tableGetFieldsMethod;
    private Method tableCloseMethod;
    private Method fieldGetNameMethod;
    private Method tableGetRecordCountMethod;
    private Method tableGetRecordAtMethod;
    private Method recordGetTypedValueMethod;
    private Method fieldGetTypeMethod;
    private Method fieldGetLengthMethod;
    private Map<String, String> dbfTypeToSQLType;
    private String tableAlias;

    public DbfReader(String path, String tableAlias, String charset) throws SQLException {
        super();
        try {
            fieldClass = Class.forName("nl.knaw.dans.common.dbflib.Field");
            recordClass = Class.forName("nl.knaw.dans.common.dbflib.Record");
            tableClass = Class.forName("nl.knaw.dans.common.dbflib.Table");
        } catch (ClassNotFoundException e) {
            throw new SQLException(CsvResources.getString("noDansDbf") + ": " + e);
        }
        try {
            tableOpenMethod = tableClass.getMethod("open", new Class[]{});
            tableCloseMethod = tableClass.getMethod("close", new Class[]{});
            tableGetFieldsMethod = tableClass.getMethod("getFields", new Class[]{});
            fieldGetNameMethod = fieldClass.getMethod("getName", new Class[]{});
            tableGetRecordCountMethod = tableClass.getMethod("getRecordCount", new Class[]{});
            tableGetRecordAtMethod = tableClass.getMethod("getRecordAt", new Class[]{Integer.TYPE});
            recordGetTypedValueMethod = recordClass.getMethod("getTypedValue", new Class[]{String.class});
            fieldGetTypeMethod = fieldClass.getMethod("getType", new Class[]{});
            fieldGetLengthMethod = fieldClass.getMethod("getLength", new Class[]{});
        } catch (Exception e) {
            throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
        }
        try {
            Constructor<?> tableConstructor;
            if (charset != null) {
                tableConstructor = tableClass.getConstructor(new Class[]{File.class, String.class});
                table = tableConstructor.newInstance(new Object[]{new File(path), charset});
            } else {
                tableConstructor = tableClass.getConstructor(new Class[]{File.class});
                table = tableConstructor.newInstance(new Object[]{new File(path)});
            }
            tableOpenMethod.invoke(table, new Object[]{});
            fields = (List) tableGetFieldsMethod.invoke(table, new Object[]{});
            recordCount = (Integer) tableGetRecordCountMethod.invoke(table, new Object[]{});
            record = null;
            rowNo = -1;
            this.tableAlias = tableAlias;
        } catch (Exception e) {
            throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
        }
        dbfTypeToSQLType = new HashMap<String, String>();
        dbfTypeToSQLType.put("CHARACTER", "String");
        dbfTypeToSQLType.put("NUMBER", "Double");
        dbfTypeToSQLType.put("LOGICAL", "Boolean");
        dbfTypeToSQLType.put("DATE", "Date");
        dbfTypeToSQLType.put("MEMO", "String");
        dbfTypeToSQLType.put("FLOAT", "Double");
    }

    public void close() throws SQLException {
        if (table != null) {
            try {
                tableCloseMethod.invoke(table, new Object[]{});
            } catch (Exception e) {
                throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
            }
        }
        table = null;
    }

    public String[] getColumnNames() throws SQLException {
        int columnCount = fields.size();
        String[] result = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            Object field = fields.get(i);
            try {
                result[i] = (String) fieldGetNameMethod.invoke(field, new Object[]{});
            } catch (Exception e) {
                throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
            }
        }
        return result;
    }

    public Object getField(int i) throws SQLException {
        Object field = fields.get(i - 1);
        try {
            String fieldName = (String) fieldGetNameMethod.invoke(field, new Object[]{});
            Object result = recordGetTypedValueMethod.invoke(record, new Object[]{fieldName});
            if (result instanceof String) {
                result = ((String) result).trim();
            } else if (result instanceof java.util.Date) {
                result = new java.sql.Date(((java.util.Date) result).getTime());
            }
            return result;
        } catch (Exception e) {
            throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
        }
    }

    public boolean next() throws SQLException {
        rowNo++;

        if (rowNo >= recordCount.intValue()) {
            return false;
        }

        try {
            record = tableGetRecordAtMethod.invoke(table, new Object[]{Integer.valueOf(rowNo)});
        } catch (InvocationTargetException e) {
            /*
			 * Re-throw the exception from inside the dans library.
			 */
            throw new SQLException(e.getCause());
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public String[] getColumnTypes() throws SQLException {
        String[] result = new String[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            String dbfType = "";
            try {
                dbfType = fieldGetTypeMethod.invoke(fields.get(i), new Object[]{}).toString();
            } catch (Exception e) {
                throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
            }
            result[i] = dbfTypeToSQLType.get(dbfType);
            if (result[i] == null) {
                throw new SQLException(CsvResources.getString("dbfTypeNotSupported") + ": " + dbfType);
            }
        }
        return result;
    }

    public int[] getColumnSizes() throws SQLException {
        int[] result = new int[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            try {
                Object fieldLength = fieldGetLengthMethod.invoke(fields.get(i), new Object[]{});
                result[i] = ((Number) fieldLength).intValue();
            } catch (Exception e) {
                throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
            }
        }
        return result;
    }

    public Map<String, Object> getEnvironment() throws SQLException {
        Map<String, Object> result = new HashMap<String, Object>();
        for (int i = 0; i < fields.size(); i++) {
            Object field = fields.get(i);
            try {
                String fieldName = (String) fieldGetNameMethod.invoke(field, new Object[]{});
                Object o = getField(i + 1);

				/*
				 * Convert column names to upper case because
				 * that is what query environment uses.
				 */
                fieldName = fieldName.toUpperCase();
                result.put(fieldName, o);
                if (tableAlias != null) {
					/*
					 * Also allow field value to be accessed as S.ID  if table alias S is set.
					 */
                    result.put(tableAlias + "." + fieldName, o);
                }
            } catch (Exception e) {
                throw new SQLException(CsvResources.getString("dansDbfError") + ": " + e);
            }
        }
        return result;
    }

    public String getTableAlias() {
        return tableAlias;
    }
}

package com.hkust.ustar.database;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper{
	 
    //The Android's default system path of your application database.
    private static final String DB_PATH = "/data/data/com.hkust.ustar/databases/";
    private static final String DB_NAME = "ustarDB.sqlite";
    private static final int DB_VERSION = 1;
    private SQLiteDatabase myDataBase; 
    private final Context myContext;
    private static DatabaseHelper mInstance = null;
    
    /**
     * Constructor
     * Takes and keeps a reference of the passed context in order to access to the application assets and resources.
     * @param context
     */
    private DatabaseHelper(Context context) {
    	super(context, DB_NAME, null, DB_VERSION);
        this.myContext = context;
    }	
 
    public static DatabaseHelper getInstance(Context context) throws IOException {
        /** 
         * use the application context as suggested by CommonsWare.
         */
        if (mInstance == null) {
            mInstance = new DatabaseHelper(context.getApplicationContext());
            mInstance.createDatabase();
            mInstance.openDatabase();
        }
        return mInstance;
    }
    
    /**
     * Creates a empty database on the system and rewrites it with your own database.
     * */
    public void createDatabase() throws IOException{
    	boolean dbExist = checkDatabase();
    	if(dbExist){
    		//do nothing - database already exist
    	}else{ 
    		//By calling this method and empty database will be created into the default system path.
        	this.getReadableDatabase();
        	try {
    			copyDatabase();
    		} catch (IOException e) {
        		throw new Error("Error copying database");
        	}
    	}
    }
 
    /**
     * Check if the database already exist to avoid re-copying the file each time you open the application.
     * @return true if it exists, false if it doesn't
     */
    private boolean checkDatabase(){
    	SQLiteDatabase checkDB = null;
    	try{
    		String myPath = DB_PATH + DB_NAME;
    		checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS|SQLiteDatabase.OPEN_READONLY);
    	}catch(SQLiteException e){
    		//database doesn't exist yet.
    	}
    	if(checkDB != null){
    		checkDB.close();
    	}
    	return checkDB != null ? true : false;
    }
 
    /**
     * Copies your database from your local assets-folder to the just created empty database in the system folder, from where it can be accessed and handled.
     * This is done by transferring bytestream.
     */
    private void copyDatabase() throws IOException{
    	//Open your local db as the input stream
    	InputStream myInput = myContext.getAssets().open(DB_NAME);
    	// Path to the just created empty db
    	String outFileName = DB_PATH + DB_NAME;
    	//Open the empty db as the output stream
    	OutputStream myOutput = new FileOutputStream(outFileName);
    	//transfer bytes from the inputfile to the outputfile
    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = myInput.read(buffer))>0){
    		myOutput.write(buffer, 0, length);
    	}
    	//Close the streams
    	myOutput.flush();
    	myOutput.close();
    	myInput.close();
    }
 
    public void openDatabase() throws SQLException{
    	//Open the database
        String myPath = DB_PATH + DB_NAME;
    	myDataBase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS|SQLiteDatabase.OPEN_READONLY);
    }
    
    public SQLiteDatabase getDatabase() {
    	return myDataBase;
    }
    
    @Override
	public synchronized void close() {
    	    if(myDataBase != null)
    		    myDataBase.close();
    	    super.close();
	}
       // Add your public helper methods to access and get content from the database.
	@Override
	public void onCreate(SQLiteDatabase db) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub
		
	}
}
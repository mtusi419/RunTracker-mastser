package edu.uindy.kirbyma.runtracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "edu.uindy.kirbyma.runtracker.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "Journal";
    private static final String COL_ID = "ID"; // Column 0, cols start indexing at 0
    private static final String COL1 = "Distance";
    private static final String COL2 = "Time";
    private static final String COL3 = "AvgPace";
    private static final String COL4 = "Date";

    DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        // Craft SQL statement to create database table
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL1 + " FLOAT, " +
                COL2 + " VARCHAR(8), " +
                COL3 + " FLOAT, " +
                COL4 + " VARCHAR(10));";
        sqLiteDatabase.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        String dropTable = "DROP IF TABLE EXISTS " + TABLE_NAME;
        sqLiteDatabase.execSQL(dropTable);
        onCreate(sqLiteDatabase);
    }


    /**
     * Dobavit novuyu stroku v tablitsu bazy dannykh (v etom sluchaye sozdayetsya tolko odna tablitsa)
     * @param dist - Float dlya obshchego proydennogo rasstoyaniya v mi
     * @param time - stroka obshchego vremeni v formate "chch: mm: ss"
     * @param pace - Float dlya skorosti aktivnosti v min / mi
     * @param date - stroka daty, v kotoroy proizoshla aktivnost' v formate "MM / DD / GGGG"
     * @return - logicheskoye znacheniye, byla li uspeshnoy vstavka stroki
     */
    boolean addData(float dist, String time, float pace, String date){
        SQLiteDatabase db = this.getWritableDatabase();

        //  Otobrazheniye znacheniy bazy dannykh
        ContentValues contentValues = new ContentValues();
        // Klyuch: imya stolbtsa
        // Znacheniye: znacheniye stolbtsa
        contentValues.put(COL1, dist);
        contentValues.put(COL2, time);
        contentValues.put(COL3, pace);
        contentValues.put(COL4, date);

        // Yesli dannyye vstavleny nepravil'no, db.insert () vozvrashchayet -1
        if (db.insert(TABLE_NAME, null, contentValues) != -1)
            return true;
        else
            return false;
    }


    /**
     * Poluchit' vse stroki iz tablitsy s pomoshch'yu operatora vybora SQL
     * @return - soderzhimoye tablitsy BD
     */
    Cursor getContents(){
        SQLiteDatabase db = this.getReadableDatabase();
        String selectStmt = "SELECT * FROM " + TABLE_NAME;
        return db.rawQuery(selectStmt, null);
    }
}

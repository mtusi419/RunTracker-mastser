package edu.uindy.kirbyma.runtracker;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

public class JournalActivity extends AppCompatActivity {

    // stolbtsy bazy dannykh
    // Stolbtsy indeksiruyutsya, nachinaya s 0, COL 0 - ID
    private static final int COL1 = 1;
    private static final int COL2 = 2;
    private static final int COL3 = 3;
    private static final int COL4 = 4;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ListView listView = findViewById(R.id.listView);

        DatabaseHelper databaseHelper = new DatabaseHelper(this);

        ArrayList<Float> distList = new ArrayList<>();
        ArrayList<String> timeList = new ArrayList<>();
        ArrayList<Float> avgPaceList = new ArrayList<>();
        ArrayList<String> dateList = new ArrayList<>();

        Cursor contents = databaseHelper.getContents();

        if (contents.getCount() == 0) {
            Toast.makeText(this, "No activities saved", Toast.LENGTH_SHORT).show();
        } else {
            while (contents.moveToNext()) {
                distList.add(contents.getFloat(COL1));
                timeList.add(contents.getString(COL2));
                avgPaceList.add(contents.getFloat(COL3));
                dateList.add(contents.getString(COL4));
            }
        }

        ItemAdapter itemAdapter = new ItemAdapter(this, distList, timeList,
                avgPaceList, dateList);
        listView.setAdapter(itemAdapter);



    }

}
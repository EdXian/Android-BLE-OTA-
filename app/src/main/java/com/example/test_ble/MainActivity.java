package com.example.test_ble;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.w3c.dom.Text;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import static com.example.test_ble.global.DisplayToast;
import static java.lang.String.format;

public class MainActivity extends AppCompatActivity {
    String TAG = "MAIN";
    public BLEManager BLEM;
    TextView TV_Out;
    TextView TV_Status;
    TextView TV_path;
    Button B_Disconnect;
    Button B_Upgrade;
    Button B_Loadfile;
    Button B_Start_cmd;
    Button B_Verify_cmd;
    Button B_Clear_cmd;
    TextView Timestamp_text;
    ProgressBar pg_bar;
    TextView pg_tv;
    boolean UpdateUART = false;
    public int progress_count = 0;
    public int PERMISSION_REQUEST_STORAGE = 1000;
    public int READ_REQUEST_CODE =42; //request file code
    public long filesize = 0;
    FileInputStream fs;
    byte[] ota_binary_data;
    protected PowerManager.WakeLock mWakeLock;
    //Timer that recularly calles itself
    private Handler hdlr = new Handler();
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Update_UI();
                }
            });
            timerHandler.postDelayed(this, 100);
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e(TAG,"onCreate()");
        checkPermission();
        TV_Status = findViewById(R.id.TV_Status);
        B_Disconnect = findViewById(R.id.B_Disconnect);
        B_Upgrade = findViewById(R.id.B_upgrade_cmd);
        B_Loadfile = findViewById(R.id.B_loadfile);
        B_Start_cmd = findViewById(R.id.B_start_cmd);
        B_Verify_cmd = findViewById(R.id.B_verify_cmd);
        B_Clear_cmd = findViewById(R.id.B_clear_cmd);
        Timestamp_text = findViewById(R.id.Timestamp_text);
        TV_path = findViewById(R.id.TV_path);
        pg_bar = findViewById(R.id.progressBar3);
        pg_tv = findViewById(R.id.progress_text);
        global.getInstance().init(this);
        String ota_device_name = "Biologue_OTA";
        BLEM = global.getInstance().BLEMan;
        if(!BLEM.isBluetoothEnabled())
        {
            DisplayToast("Bluetooth disabled");
        }
        else{
            BLEM.Connect(this, ota_device_name);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //Start UI Update Timer
        timerHandler.postDelayed(timerRunnable, 0);
        pg_bar.setProgress(progress_count);
        B_Upgrade.setEnabled(false);
    }

    
    public void checkPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_STORAGE);
        }
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        } else {
            // Permission has already been granted
            Log.e(TAG, "Permission granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_STORAGE){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"Permission granted",Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(this,"Permission denied",Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void performFilesearch(){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent,READ_REQUEST_CODE);
    }

    private String get_file_name_from_uri(Uri uri){
        Cursor returnCursor =getContentResolver().query(uri, null, null, null, null);
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String filename  = returnCursor.getString(nameIndex);
        return filename;
    }

    private long get_file_size_from_uri(Uri fileUri) {
        Cursor returnCursor = getContentResolver().
                query(fileUri, null, null, null, null);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        return returnCursor.getLong(sizeIndex);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK){
            if(data!=null){
                //Uri uri = null;
                Uri uri= data.getData();
                String path = uri.getPath();
                //get correct filename from uri
                Toast.makeText(this,"load file : "+ get_file_name_from_uri(uri), Toast.LENGTH_SHORT).show();
                String filesize = Long.toString(get_file_size_from_uri(uri));
                TV_path.setText(  "rssi;"+Integer.toString(BLEM.ota_rssi)  +"\r\n" +get_file_name_from_uri(uri) +"\r\n" +"size: "+filesize + "bytes");

                try {
                    //String filename = uri.getLastPathSegment();
                    Log.e(TAG, get_binary_data_from_uri(uri));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private String get_binary_data_from_uri(Uri uri) throws IOException {
        StringBuilder stringBuilder=new StringBuilder();
        fs = new FileInputStream(getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor()); //read only
        filesize = get_file_size_from_uri(uri);
        byte[] bytearr= new byte[(int)filesize];
        if(fs !=null){
            fs.read(bytearr,0,(int)filesize);
            ota_binary_data = bytearr;
        }
        return stringBuilder.toString();
    }

    String output = "";
    public void Log(final String TAG, final String MSG) {
        Log(TAG + ": " + MSG + "\n");
    }

    public void Log(final String MSG) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                output += MSG;
                TV_Out.setText(output);
            }
        });
    }
	
	
    public void Update_UI()
    {
        TV_Status.setText(BLEM.isConnected()? format("CONNECTED to %s", BLEM.SERVER_NAME) : "DISCONNECTED");
        TV_Status.setBackgroundColor(ContextCompat.getColor(this, (BLEM.isConnected()? R.color.color_temp_low : R.color.light_gray)));
        B_Disconnect.setText(BLEM.isConnected()? "DISCONNECT" : "CONNECT");
        B_Disconnect.setBackgroundColor(ContextCompat.getColor(this, (BLEM.isConnected()? R.color.light_gray : R.color.color_temp_low)));

        if(BLEM.isConnected()){
            B_Start_cmd.setEnabled(true);
            B_Verify_cmd.setEnabled(true);
            B_Clear_cmd.setEnabled(true);
            B_Loadfile.setEnabled(true);
            if(fs != null){
                B_Upgrade.setEnabled(true);
            }else{
                B_Upgrade.setEnabled(false);
            }
        }else{
            B_Start_cmd.setEnabled(false);
            B_Verify_cmd.setEnabled(false);
            B_Clear_cmd.setEnabled(false);
            B_Loadfile.setEnabled(false);
            B_Upgrade.setEnabled(false);
        }


    }

    public void update_progress(int count){
        pg_tv.setText( Integer.toString(count) + "%");
        pg_bar.setProgress(count);
    }

    public void B_Disconnect_onClick(View v)
    {
        if(BLEM.isConnected())
            BLEM.Disconnect();
        else
            BLEM.Connect(this);
    }


    public void ota_start_cmd(int addr, int len){

        byte[] pack =new byte[10];

        pack[0] = (byte)0x50;
        pack[1] = (byte)0x08;

        pack[2] = (byte)(len>>0);
        pack[3] = (byte)(len>>8);
        pack[4] = (byte)(len>>16);
        pack[5] = (byte)(len>>24);

        pack[6] = (byte)(addr>>0);
        pack[7] = (byte)(addr>>8);
        pack[8] = (byte)(addr>>16);
        pack[9] = (byte)(addr>>24);

        BLEM.UART_Writebytes(pack);
    }




    public void ota_write_cmd(byte[] pdata){
        byte[] pack = new byte[pdata.length+2];
        pack[0] = 0x51;
        pack[1] = 0x01;
        for(int i=2; i<pack.length; i++){
            pack[i] = pdata[i-2];
        }
        BLEM.UART_Writebytes(pack);
    }

    public void ota_verify_cmd(){
        byte[] data={(byte)0x52,(byte)0x01,(byte)0x43,(byte)0x12, (byte) 0xab, (byte) 0xcd};
        BLEM.UART_Writebytes(data);
    }

    public void ota_clearrom_cmd(){
        byte[] data={(byte)0x53,(byte)0x08,(byte)0x00,(byte)0x00, (byte) 0x05, (byte) 0x00
                ,(byte)0x00,(byte)0x00, (byte) 0x07, (byte) 0x00};
        BLEM.UART_Writebytes(data);
    }

    public void ota_flash_cmd(){
        byte[] data={(byte)0x54,(byte)0x01,(byte)0x43,(byte)0x12, (byte) 0xab, (byte) 0xcd};
        BLEM.UART_Writebytes(data);
    }
    public void ota_write_page(int address, byte[] page){

        ota_start_cmd((int)address,page.length);
        int remain_size  = page.length;
        int count = 0;
        int pack_size = 128;
        while (remain_size > 0 ) {
            if (remain_size >= pack_size) {
                ota_write_cmd(Arrays.copyOfRange(page, count, count+pack_size));
                count += pack_size;
                remain_size -= pack_size;
            } else {

                ota_write_cmd(Arrays.copyOfRange(page, count, count+remain_size));
                count += remain_size;
                remain_size = 0;
            }


        }
        ota_flash_cmd();
        ota_verify_cmd();
    }

    public void ota_upgrade() {
        int address = 0x50000;
        int remain_size = ota_binary_data.length;
        int count = 0;
        int page_size = 4096;
        ota_clearrom_cmd();
        while(remain_size>0){
            if(remain_size >= page_size){

                ota_write_page((int)address,   Arrays.copyOfRange(ota_binary_data, count, count+page_size));
                address += page_size;
                count = count + page_size;
                remain_size -= page_size;
            }else{

                ota_write_page((int)address,   Arrays.copyOfRange(ota_binary_data, count, count+remain_size));
                address += remain_size;
                count = count + remain_size;
                remain_size = 0;
            }
            progress_count = 100 - (remain_size*100/ ota_binary_data.length);
            hdlr.post(new Runnable() {
                public void run() {
                    update_progress(progress_count);
                }
            });

        }
    }

/*
    public void ota_upgrade1() throws InterruptedException {


        int remain_size = ota_binary_data.length;
        int count = 0;


        ota_clearrom_cmd();
        ota_start_cmd((int)0x50000,ota_binary_data.length);

        while (remain_size > 0 ) {
            if (remain_size > 128) {
                //ota_write_cmd(Arrays.copyOfRange(ota_binary_data, count, count+128));
                //count += 128;
                //remain_size -= 128;
            } else {
                //ota_write_cmd(Arrays.copyOfRange(ota_binary_data, count, count+remain_size));
                //ota_write_cmd(pack);
                count += remain_size;
                remain_size = 0;
            }
            progress_count = 100 - (remain_size*100/ ota_binary_data.length);
            hdlr.post(new Runnable() {
                public void run() {
                    update_progress(progress_count);
                }
            });
        }

        ota_flash_cmd();


    }
*/
    public void B_Start_cmd_onclick(View v){
        ota_start_cmd(0x50000,0);
    }

    public void B_Verify_cmd_onclick(View v){
        ota_verify_cmd();
    }


    public void B_loadfile_onClick(View view) {
        Log.e(TAG, "load file from external flash");
        performFilesearch();
    }

    public void B_upgrade_cmd_onclick(View view) {
        progress_count = 0;
        hdlr.post(new Runnable() {
            public void run() {
                update_progress(progress_count);
            }
        });

        new Thread(new Runnable() {
            public void run() {



            long startTime =   System.nanoTime();
            ota_upgrade();
            Log.e("Measure", "TASK took : " +Long.toString((System.nanoTime()-startTime)/1000000)+"ms");

            Timestamp_text.setText("Time duration:" + ((System.nanoTime()-startTime)/1000000)+"ms");



            }
        }).start();
    }

    public void B_clear_cmd_onclick(View view) {
        ota_clearrom_cmd();
    }

    public void B_close(View view) {
        BLEM.Disconnect();
        finish();
        System.exit(0);
    }
}
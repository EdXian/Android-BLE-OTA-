package com.example.test_ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static android.content.Context.BLUETOOTH_SERVICE;
import static java.lang.String.format;

public class BLEManager {
    String TAG = "BLEManager";
    String SERVER_NAME = "UART Service";

    private static final int PARAM_UART_INPUT_BUFFER_MAX_LENGTH = 1024;

    final UUID SERVICE_UUID_UART = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    final UUID CHARACTERISTIC_UUID_UART_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"); //TX
    final UUID CHARACTERISTIC_UUID_UART_RX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"); //RX
    final UUID DESCRIPTOR_UUID_ID_TX_UART = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;
    BluetoothManager bluetoothManager;
    BluetoothScanCallback bluetoothScanCallback;

    BluetoothGatt GattClient = null;
    BluetoothGattService Service_UART = null;
    BluetoothGattCharacteristic CHARACTERISTIC_UART_TX = null;
    BluetoothGattCharacteristic CHARACTERISTIC_UART_RX = null;

    Boolean get_ack_from_ble = false;
    public int verify_pass_count = 0;
    public int verify_fail_count = 0;

    public int clear_pass_count = 0;
    public int clear_fail_count = 0;
    public boolean is_ble_ack = false;
    public int ota_rssi;

    public StringBuilder UART_INPUT_BUFFER = new StringBuilder();



    public BLEManager(Activity activity)
    {
        /*
        final BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();



        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    */

    }

    public boolean isBluetoothEnabled()
    {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // Bluetooth is not enabled :)
        // Bluetooth is enabled
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            return false;
        }
        else
            return mBluetoothAdapter.isEnabled();
    }

    public void Connect(Activity activity)
    {
        Log.e(TAG,"Connect() to " + SERVER_NAME);
        bluetoothManager = (BluetoothManager) activity.getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        startScan();
    }

    public void Connect(Activity activity, String ServerName)
    {
        SERVER_NAME = ServerName;
        Connect(activity);
    }

    public void Disconnect()
    {
        if(isConnected())
            GattClient.disconnect();
    }

    public boolean isConnected()
    {
        return GattClient != null;
    }

    public String ReadValue_String(BluetoothGattCharacteristic characteristic)
    {
        Log.e(TAG, "Read Value");

        String value_string = characteristic.getStringValue(0);
        if(value_string == null)
            Log.e(TAG,"onServicesDiscovered() Value=null!");
        else
            Log.e(TAG,"onServicesDiscovered() Value=\"" + value_string + "\"");

        return value_string;
    }

    public byte[] ReadValue_ByteArray(BluetoothGattCharacteristic characteristic)
    {
        Log.e(TAG, "Read Value");

        byte[] value_bytes = characteristic.getValue();

        if(value_bytes == null)
            Log.e(TAG,"onServicesDiscovered() Value=null!");
        else {
            String output = new BigInteger(1, value_bytes).toString(16);
            Log.e(TAG, "onServicesDiscovered() Value=" + output);
        }
        return value_bytes;
    }

    public void RQST_ReadValue(BluetoothGattCharacteristic characteristic)
    {
        Log.i(TAG, "RQST Read Value");

        GattClient.readCharacteristic(characteristic);
    }

    public void WriteValue(BluetoothGattCharacteristic characteristic, String value_string)
    {
        /*
        Log.i(TAG, "Read Value");

        if(value_string.length() > 0) {
            Log.i(TAG, "onServicesDiscovered() setting value");
            characteristic.setValue(value_string);

            Log.i(TAG, "onServicesDiscovered() sending characteristic");
            GattClient.writeCharacteristic(characteristic);
        }

         */
    }


    public void UART_Write(String msg)
    {
        WriteValue(CHARACTERISTIC_UART_RX, msg);
    }


    public void UART_Writebytes(byte[] data){

        long startTime = System.nanoTime();
        long interval = 0;

       //Long.toString((System.nanoTime()-startTime)/1000000)
        //Timestamp_text.setText("Time duration:" + ((System.nanoTime()-startTime)/1000000)+"ms");
        get_ack_from_ble = false;

        UART_Writebyte(CHARACTERISTIC_UART_RX,data);
        // waiting for ack from ble device

        while ((get_ack_from_ble !=true) && (interval <3000)){      //timeout 3s
            interval  = (System.nanoTime() - startTime)/1000000  ;
        }

        if( interval >=3000 ) {
            is_ble_ack = false;
            Log.e("OTA","device  nack");
        }else{
            is_ble_ack = true;
           // Log.e("OTA","device  ack");
        }

    }

    public void UART_Writebyte(BluetoothGattCharacteristic characteristic, byte[] data){

        if(data.length>0){
            characteristic.setValue(data);
            GattClient.writeCharacteristic(characteristic);
        }

    }

    private void ResetConnection()
    {
        GattClient = null;
        Service_UART = null;
        CHARACTERISTIC_UART_RX = null;
        CHARACTERISTIC_UART_TX = null;

        if(false) //TODO true here if try to reconnect after disconnect
            startScan();
    }

    private void startScan()
    {
        Log.e(TAG,"startScan()");
        if(isConnected()) {
            Log.e(TAG,"startScan(): already connected");
            return;
        }
        if(bluetoothLeScanner != null)
        {
            Log.e(TAG,"startScan(): already scanning");
            return;
        }

        bluetoothScanCallback = new BluetoothScanCallback();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if(bluetoothLeScanner == null)
        {
            Log.e(TAG,"startScan(): bluetoothLeScanner == null");
            return;
        }
        bluetoothLeScanner.startScan(bluetoothScanCallback); //callback -> onScanResult(int callbackType, ScanResult result)
    }

    //System Calls this when BLE is ready to subscribe to Services
    private void InitCharacteristics()
    {
        InitCharacteristics_Service_UART(); //Service for RX and TX UART
    }

    private void InitCharacteristics_Service_UART()
    {
        Log.i(TAG,"onServicesDiscovered() getting service UART");
        Service_UART = GattClient.getService(SERVICE_UUID_UART);

        if(Service_UART == null)
        {
            Log.e(TAG,"onServicesDiscovered() Service_UART not found");
            return;
        }

        Log.i(TAG,"onServicesDiscovered() getting characteristic UART");
        CHARACTERISTIC_UART_TX = Service_UART.getCharacteristic(CHARACTERISTIC_UUID_UART_TX);

        CHARACTERISTIC_UART_RX = Service_UART.getCharacteristic(CHARACTERISTIC_UUID_UART_RX);

        Log.i(TAG,"onServicesDiscovered() enable Notification on characteristic UART");
        GattClient.setCharacteristicNotification(CHARACTERISTIC_UART_TX,true);

        //Enable Notification for UART TX
        BluetoothGattDescriptor _descriptor = CHARACTERISTIC_UART_TX.getDescriptor(DESCRIPTOR_UUID_ID_TX_UART);
        if(_descriptor !=null)
        {
            Log.i(TAG, "onServicesDiscovered() Write to Descriptor ENABLE_NOTIFICATION_VALUE");
            _descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            //_descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            GattClient.writeDescriptor(_descriptor);
        }
        else
            Log.i(TAG, "onServicesDiscovered() descriptor == null");

    }

    private void connectDevice(BluetoothDevice device) {
        Log.i(TAG,"connectDevice()");
        if (device == null)
            Log.e(TAG,"connectDevice(): Device is null");
        else {
            Log.i(TAG,"connectDevice(): connecting to Gatt");
            if(GattClient == null) {
                GattClientCallback gattClientCallback = new GattClientCallback();
                GattClient = device.connectGatt(global.getInstance().context, false, gattClientCallback);
            }
            else
            {
                Log.e(TAG,"connectDevice(): Gatt Client already created -> Stopping");
            }
        }
    }

    // BLE Scan Callbacks
    private class BluetoothScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "onScanResult()");
            if (result.getDevice().getName() != null){
                if (result.getDevice().getName().equals(SERVER_NAME)) {
                    Log.i(TAG, "onScanResult(): Found BLE Device");
                    ota_rssi =  result.getRssi();
                    connectDevice(result.getDevice());

                    Log.i(TAG, "onScanResult(): stopping scan");
                    if(bluetoothLeScanner != null)
                        bluetoothLeScanner.stopScan(bluetoothScanCallback);
                    bluetoothLeScanner = null;
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(TAG, "onBathScanResults");
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "ErrorCode: " + errorCode);
        }
    }



    // Bluetooth GATT Client Callback
    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(TAG, "onConnectionStateChange()");
            
            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.e(TAG, "onConnectionStateChange(): GATT FAILURE");
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onConnectionStateChange(): status != GATT_SUCCESS");
                //Connection lost to BLE-Server <- u sure?
                //ResetConnection();
                //return;
            }

            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onConnectionStateChange(): status == GATT_SUCCESS");
            }

            Log.i(TAG, "onConnectionStateChange(): New State: " + newState);

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange CONNECTED");


                //Here connection to Gatt was successful

                global.Log("BluetoothGattCallback", "CONNECTED");

                Log.i(TAG, "onConnectionStateChange(): start discover Services");
                gatt.discoverServices();
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //Connection lost to BLE-Server

                Log.i(TAG, "onConnectionStateChange DISCONNECTED");

                global.Log("BluetoothGattCallback", "DISCONNECTED");

                ResetConnection();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(TAG,"onMtuChanged()");
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(TAG, "onServicesDiscovered()");
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServicesDiscovered() status != BluetoothGatt.GATT_SUCCESS");
                return;
            }

            Log.i(TAG, "Connected to " + SERVER_NAME);

            Log.i(TAG, "onServicesDiscovered() status == BluetoothGatt.GATT_SUCCESS");

            InitCharacteristics();

        }


        @Override
        /*Callback reporting the result of a characteristic read operation.*/
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(TAG,"onCharacteristicRead()");

            Log.i(TAG,"onServicesDiscovered() reading Value");
            byte[] value_bytes = characteristic.getValue();

            //read as bytes
            if(value_bytes == null)
                Log.e(TAG,"onServicesDiscovered() Value=null!");
            else {
                String output = new BigInteger(1, value_bytes).toString(16);
                Log.e(TAG, "onServicesDiscovered() Value=" + output);
            }

            //read as String
            String value_string = characteristic.getStringValue(0);
            if(value_string == null)
                Log.e(TAG,"onServicesDiscovered() Value=null!");
            else
                Log.i(TAG,"onServicesDiscovered() Value=\"" + value_string + "\"");


        }

        @Override
        /*Callback indicating the result of a characteristic write operation.*/
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.i(TAG,"onCharacteristicWrite() with Status=" + status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if(characteristic.equals(CHARACTERISTIC_UART_TX))
            {
                byte[] ack = characteristic.getValue();
                /*
                key_state = 0x49,
                start_state = 0x50,
                write_state =0x51 ,
                verify_state = 0x52,
                clearrom_state =0x53,
                flash_state =0x54,
                check_version_state=0x55,
                label_state=0x56
                 */
                get_ack_from_ble = true;
                switch (ack[0]) {
                    case 0x49:  

                        break;
                    case 0x50:
                        if(ack[1] == 14){
                            clear_fail_count = clear_fail_count +1;
                        }
                        break;

                    case  0x51:

                        break;

                    case  0x52:

                        if(ack[1] == 10){
                            verify_pass_count = verify_pass_count+1;
                        }else if(ack[1] == 11){
                            verify_fail_count = verify_fail_count+1;
                        }
                        break;
                    case 0x53:
                        if(ack[1] == 12){
                            clear_pass_count = clear_pass_count +1;
                        }else if(ack[1] == 13){
                            clear_fail_count = clear_fail_count +1;
                        }
                        break;
                }



            }

        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.i(TAG,"onDescriptorRead()");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.i(TAG,"onDescriptorWrite()");
        }
    }
}

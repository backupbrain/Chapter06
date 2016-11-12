package tonyg.example.com.exampleblescan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import tonyg.example.com.exampleblescan.ble.BleCommManager;
import tonyg.example.com.exampleblescan.ble.BlePeripheral;
import tonyg.example.com.exampleblescan.models.BleGattCharacteristicListItem;
import tonyg.example.com.exampleblescan.adapters.BleGattProfileListAdapter;
import tonyg.example.com.exampleblescan.models.BleGattServiceListItem;

/**
 * Connect to a BLE Device, list its GATT services
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2015-12-21
 */
public class ConnectActivity extends AppCompatActivity {
    /** Constants **/
    private static final String TAG = ConnectActivity.class.getSimpleName();
    public static final String PERIPHERAL_MAC_ADDRESS_KEY = "com.example.com.exampleble.PERIPHERAL_MAC_ADDRESS";
    private static final int REQUEST_ENABLE_BT = 1;

    /** Bluetooth Stuff **/
    private BleCommManager mBleCommManager;
    private BlePeripheral mBlePeripheral;

    /** Functional stuff **/
    private String mPeripheralMacAddress;

    /** Activity State **/
    private boolean mBleConnected = false;
    private boolean mLeaveActivity = false;

    /** UI Stuff **/
    private MenuItem mProgressSpinner;
    private MenuItem mConnectItem, mDisconnectItem;
    private ExpandableListView mGattProfileListView;
    private TextView mPeripheralBroadcastNameTV, mPeripheralAddressTV, mGattProfileListEmptyTV;
    private BleGattProfileListAdapter mGattProfileListAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // grab information passed to the savedInstanceState,
        // from when the user clicked on the list in MainActivty
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                mPeripheralMacAddress = extras.getString(PERIPHERAL_MAC_ADDRESS_KEY);
            }
        } else {
            mPeripheralMacAddress = savedInstanceState.getString(PERIPHERAL_MAC_ADDRESS_KEY);
        }


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadUI();

        mBlePeripheral = new BlePeripheral();

    }

    /**
     * Prepare the UI elements
     */
    public void loadUI() {
        mPeripheralBroadcastNameTV = (TextView)findViewById(R.id.broadcast_name);
        mPeripheralAddressTV = (TextView)findViewById(R.id.mac_address);
        mGattProfileListEmptyTV = (TextView)findViewById(R.id.gatt_profile_list_empty);

        mGattProfileListView = (ExpandableListView) findViewById(R.id.peripherals_list);
        mGattProfileListAdapter = new BleGattProfileListAdapter();


        mGattProfileListView.setAdapter(mGattProfileListAdapter);
        mGattProfileListView.setEmptyView(mGattProfileListEmptyTV);
    }


    @Override
    public void onPause() {
        super.onPause();
        disconnect();
    }


    /**
     * Create the menu
     *
     * @param menu
     * @return <b>true</b> if successful
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_connect, menu);

        mConnectItem = menu.findItem(R.id.action_connect);
        mDisconnectItem =  menu.findItem(R.id.action_disconnect);
        mProgressSpinner = menu.findItem(R.id.scan_progress_item);

        // initialize Bluetooth
        initializeBluetooth();

        return true;
    }


    /**
     * User clicked a menu button
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                // User chose the "Scan" item
                connect();
                return true;

            case R.id.action_disconnect:
                // User chose the "Stop" item
                mLeaveActivity = true;
                quitActivity();
                return true;

            default:
                // The user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    /**
     * Turn on Bluetooth radio
     */
    public void initializeBluetooth() {

        // notify when bluetooth is turned on or off
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        try {
            mBleCommManager = new BleCommManager(this);
            connect();
        } catch (Exception e) {
            Toast.makeText(this, "Could not initialize bluetooth", Toast.LENGTH_SHORT).show();
            Log.e(TAG, e.getMessage());
            finish();
        }


        // should prompt user to open settings if Bluetooth is not enabled.
        if (mBleCommManager.getBluetoothAdapter().isEnabled()) {
            connect();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

    }

    /**
     * Connect to Peripheral
     */
    public void connect() {
        // grab the Peripheral Device address and attempt to connect
        BluetoothDevice bluetoothDevice = mBleCommManager.getBluetoothAdapter().getRemoteDevice(mPeripheralMacAddress);
        mProgressSpinner.setVisible(true);
        try {
            mBlePeripheral.connect(bluetoothDevice, mGattCallback, getApplicationContext());
        } catch (Exception e) {
            mProgressSpinner.setVisible(false);
            Log.e(TAG, "Error connecting to peripheral");
        }
    }

    /**
     * Disconnect from Peripheral
     */
    public void disconnect() {
        // disconnect from the Peripheral.
        mProgressSpinner.setVisible(true);
        mBlePeripheral.disconnect();
        try {
            unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "receiver not registered");
        }
    }


    /**
     * Bluetooth Peripheral connected.  Update UI
     */
    public void onBleConnected() {
        // update UI to reflect a connection
        BluetoothDevice bluetoothDevice = mBlePeripheral.getBluetoothDevice();
        mPeripheralBroadcastNameTV.setText(bluetoothDevice.getName());
        mPeripheralAddressTV.setText(bluetoothDevice.getAddress());
        mConnectItem.setVisible(false);
        mDisconnectItem.setVisible(true);
        mProgressSpinner.setVisible(false);

    }

    /**
     *  Quit the activity if the Peripheral is disconnected.  Otherwise disconnect and try again
     */
    public void quitActivity() {
        if (!mBleConnected) {
            finish();
        } else {
            disconnect();
        }
    }


    /**
     * Bluetooth Peripheral GATT Profile being scanned.  Update UI
     *
     * New in this chapter
     */
    public void onBleServiceDiscoveryStarted() {
        mProgressSpinner.setVisible(true);
    }

    /**
     * Bluetooth Peripheral GATT Profile discovered.  Update UI
     *
     * New in this chapter
     */
    public void onBleServiceDiscoveryStopped() {
        // update UI to reflect the GATT profile of the connected Perihperal
        mProgressSpinner.setVisible(false);
        mConnectItem.setVisible(false);
        mDisconnectItem.setVisible(true);
    }


    /**
     * When the Bluetooth radio turns on, initialize the Bluetooth connection
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        initializeBluetooth();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        connect();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /**
         * Characteristic value changed
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // We don't care about this here as we aren't communicating with Characteristics
        }

        /**
         * Peripheral connected or disconnected
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // There has been a connection or a disconnection with a Peripheral.
            // If this is a connection, update the UI to reflect the change
            // and discover the GATT profile of the connected Peripheral
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.v(TAG, "Connected to peripheral");
                mBleConnected = true;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onBleConnected();
                        onBleServiceDiscoveryStarted();
                    }
                });

                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mBlePeripheral.close();
                mBleConnected = false;
                if (mLeaveActivity) quitActivity();
            }
        }

        /**
         * Gatt Profile discovered
         *
         * New in this chapter
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {

            // if services were discovered, then let's iterate through them and display them on screen
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = bluetoothGatt.getServices();
                for (BluetoothGattService service : services) {
                    if (service != null) {
                        Log.v(TAG, "Service uuid: " + service.getUuid());

                        // add the gatt service to our list
                        mGattProfileListAdapter.addService(service);
                        // update the UI to reflect the new Service
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mGattProfileListAdapter.notifyDataSetChanged();
                            }
                        });

                        // while we are here, let's ask for this service's characteristics:
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            if (characteristic != null) {
                                // if there are Characteristics, add them to the Service's list
                                try {
                                    mGattProfileListAdapter.addCharacteristic(service, characteristic);
                                    // update the ListView UI
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mGattProfileListAdapter.notifyDataSetChanged();
                                        }
                                    });

                                } catch (Exception e) {
                                    Log.e(TAG, e.getMessage());
                                }

                            }
                        }
                    }


                }
                disconnect(); // disconnect from the Peripheral so that a connection is possible again in TalkActivity
            } else {
                Log.e(TAG, "Something went wrong while discovering GATT services from this peripheral");
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleServiceDiscoveryStopped();
                }
            });

        }
    };



}

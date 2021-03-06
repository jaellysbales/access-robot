package ksmaragh.c4q.nyc.accessrobot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

public class MoziActivity extends AppCompatActivity {

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final String LEVEL = "Level";
    private static final String TUTORIAL = "Tutorial";
    private static final String BLOCKLY_URL = "file:///android_asset/blockly/blockly.html";
    private static final String BLOCKLY_TUT_URL = "file:///android_asset/blockly/tut.html";
    private int currentLevel;

    private String mConnectedDeviceName = null;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothArduinoService mChatService = null;
    private final BluetoothHandler mHandler = new BluetoothHandler(this);
    private WebView webView, tutWebView;

    private WebSettings wSettings, tutSettings;
    private boolean tutorial;

    private static final String TAG = "MoziActivity";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code);
        loadPrefs();

        initProgramWebView();
        initTutWebView();
        initBluetooth();
        initActionBar();
    }

    public void initProgramWebView() {
        webView = new WebView(this);
        webView.setClickable(true);
        wSettings = webView.getSettings();
        wSettings.setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new myJsInterface(this), "Android");
        webView.loadUrl(BLOCKLY_URL);
        for (int i = 0; i < 200; i++) {
            webView.zoomOut();
        }
    }

    public void initTutWebView() {
        tutWebView = new WebView(this);
        tutWebView.setClickable(true);
        tutSettings = tutWebView.getSettings();
        tutSettings.setJavaScriptEnabled(true);
        tutWebView.addJavascriptInterface(new myJsInterface(this), "Android");
        tutWebView.loadUrl(BLOCKLY_TUT_URL);
    }

    private void initActionBar() {

        // setup action bar for tabs
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowTitleEnabled(false);

        ActionBar.Tab tab = actionBar.newTab()
                .setText("Build")
                .setTabListener(new TabListener(
                        this, "build", webView));
        actionBar.addTab(tab, false);

        tab = actionBar.newTab()
                .setText("Learn")
                .setTabListener(new TabListener(
                        this, "learn", tutWebView));
        actionBar.addTab(tab, tutorial);

        tab = actionBar.newTab()
                .setText("Program")
                .setTabListener(new TabListener(
                        this, "program", webView));
        actionBar.addTab(tab, !tutorial);

    }

    private void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }
    }

    public class myJsInterface {
        private Context con;

        public myJsInterface(Context con) {
            this.con = con;
        }

        @JavascriptInterface
        public void talkToArduino(String msg) {
            //Toast.makeText(MoziActivity.this, msg, Toast.LENGTH_SHORT).show();
            sendMessage(msg);
        }

        @JavascriptInterface
        public void saveLevel(int num) {
            savePrefs(num);
        }

        @JavascriptInterface
        public int getCurrentLevel() {
            return currentLevel;
        }

        @JavascriptInterface
        public void reloadWebview() {

            tutWebView.post(new Runnable() {

                @Override
                public void run() {
                    tutWebView.reload();
                }
            });
        }

        @JavascriptInterface
        public void reloadMoziActivity() {
            finish();
            startActivity(getIntent());
        }
    }

    /**
     * Shared Preferences
     */
    private void savePrefs(int level) {
        currentLevel = level;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(LEVEL, level);

        if (currentLevel > 8) {
            editor.putBoolean(TUTORIAL, false);
        } else {
            editor.putBoolean(TUTORIAL, true);
        }
        editor.apply();

    }

    private void loadPrefs() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        tutorial = preferences.getBoolean(TUTORIAL, true);
        currentLevel = preferences.getInt(LEVEL, 1);
    }


    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothArduinoService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    public void sendMessage(String message) {
        Log.d(TAG, message);
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothArduinoService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {

    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {

    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private class BluetoothHandler extends Handler {

        private final MoziActivity mActivity;

        public BluetoothHandler(MoziActivity activity) {
            mActivity = activity;
        }


        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothArduinoService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));

                            break;
                        case BluetoothArduinoService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothArduinoService.STATE_LISTEN:
                        case BluetoothArduinoService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
//                    // construct a string from the buffer
//                    String writeMessage = new String(writeBuf);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
//                    String readMessage = new String(readBuf, 0, msg.arg1);
//                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != mActivity) {
                        Toast.makeText(mActivity, "Mozi Connected!", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != mActivity) {
                        Toast.makeText(mActivity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    }

    ;

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    break;
                }
        }
    }

    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else if (mChatService == null) {
            setupChat();
        }
    }

    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothArduinoService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_mozi, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.send_to_mozi: {
                if (webView != null) {
                    webView.evaluateJavascript("runCode()", null);
                }
                return true;
            }

            case R.id.share: {
                Intent shareIntent = new Intent(this, LoginActivity.class);
                startActivity(shareIntent);
                return true;
            }

            case R.id.connect: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }

            case R.id.my_programs: {
                Intent myProgramsIntent = new Intent(this, MyProgramsActivity.class);
                startActivity(myProgramsIntent);
                return true;
            }

            case R.id.action_settings: {
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            }

            case R.id.runProgram: {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    tutWebView.evaluateJavascript("checkAnswer()", null);
                } else {
                    tutWebView.loadUrl("javascript:checkAnswer();");
                }

            }
        }
        return super.onOptionsItemSelected(item);
    }

    public static class TabListener implements ActionBar.TabListener {
        private final MoziActivity mActivity;
        private final String mTag;
        private final WebView mProgram;


        public TabListener(MoziActivity activity, String tag, WebView program) {
            mActivity = activity;
            mTag = tag;
            mProgram = program;
        }

    /* The following are each of the ActionBar.TabListener callbacks */


        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            // Check if the fragment is already initialized
            Log.d(TAG, mTag);

            if (mTag.equals("build")) {
                Intent intent = new Intent(mActivity, LearnActivity.class);
                mActivity.startActivity(intent);
            } else if (mTag.equals("learn")) {
                mActivity.setContentView(mProgram);
            } else if (mTag.equals("program")) {
                mActivity.setContentView(mProgram);
            }
        }

        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {

        }

        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
            // User selected the already selected tab. Usually do nothing.
        }
    }

}




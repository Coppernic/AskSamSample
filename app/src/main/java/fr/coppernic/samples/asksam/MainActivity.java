package fr.coppernic.samples.asksam;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import fr.coppernic.sdk.ask.Defines;
import fr.coppernic.sdk.ask.Reader;
import fr.coppernic.sdk.powermgmt.PowerMgmt;
import fr.coppernic.sdk.powermgmt.PowerMgmtFactory;
import fr.coppernic.sdk.powermgmt.PowerUtilsNotifier;
import fr.coppernic.sdk.powermgmt.cone.identifiers.InterfacesCone;
import fr.coppernic.sdk.powermgmt.cone.identifiers.ManufacturersCone;
import fr.coppernic.sdk.powermgmt.cone.identifiers.ModelsCone;
import fr.coppernic.sdk.powermgmt.cone.identifiers.PeripheralTypesCone;
import fr.coppernic.sdk.utils.core.CpcBytes;
import fr.coppernic.sdk.utils.core.CpcDefinitions;
import fr.coppernic.sdk.utils.core.CpcResult;
import fr.coppernic.sdk.utils.io.InstanceListener;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private CommunicationExchangesAdapter mAdapter;
    private ArrayList<CommunicationExchanges> mExchanges;

    // Power management
    private PowerMgmt mPowerMgmt;
    // RFID reader
    private Reader mReader;

    // UI
    private EditText mEtApdu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mEtApdu = (EditText)findViewById(R.id.etApdu);

        TextView tvEmpty = (TextView)findViewById(R.id.empty);

        ListView lv = (ListView)findViewById(R.id.lvLogs);
        lv.setEmptyView(tvEmpty);
        mExchanges = new ArrayList<>();
        mAdapter = new CommunicationExchangesAdapter(this, R.layout.exchanges_row, mExchanges);
        lv.setAdapter(mAdapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Command sent", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                byte[] dataToSend = CpcBytes.parseHexStringToArray(mEtApdu.getText().toString());
                Log.d(TAG, "Data to send: " + CpcBytes.byteArrayToString(dataToSend));
                byte[] dataReceived = new byte[256];
                int[] dataReceivedLength = new int[1];
                int res = mReader.cscIsoCommandSam(dataToSend, dataToSend.length, dataReceived,dataReceivedLength);

                byte[] status = null;

                if (res != Defines.RCSC_Ok) {
                    status = new byte[1];
                    status[0] = mReader.getBufOut()[4];
                } else {
                    if (dataReceivedLength[0] >= 2) {
                        status = new byte[2];
                        System.arraycopy(dataReceived, dataReceivedLength[0] - 2, status, 0, 2);
                    }
                }

                Log.d(TAG, "Data received: " + CpcBytes.byteArrayToString(dataReceived, dataReceivedLength[0]));

                byte[] data = null;

                if (dataReceivedLength[0] - 2 > 0) {
                    data = new byte[dataReceivedLength[0] - 2];
                    System.arraycopy(dataReceived, 0, data, 0, data.length);
                }

                mExchanges.add(0, new CommunicationExchanges(dataToSend, data, status));
                mAdapter.notifyDataSetChanged();
            }
        });

        mPowerMgmt = PowerMgmtFactory.get().setContext(this)
                .setNotifier(new PowerUtilsNotifier() {
                    @Override
                    public void onPowerUp(CpcResult.RESULT res, int vid, int pid) {
                        // ASK RFID reader is powered on
                        Log.d(TAG, "RFID powered on");

                        Reader.getInstance(MainActivity.this, new InstanceListener<Reader>() {
                            @Override
                            public void onCreated(Reader instance) {
                                Log.d(TAG, "Reader instantiated");
                                mReader = instance;
                                // Opens reader
                                mReader.cscOpen(CpcDefinitions.ASK_READER_PORT, 115200, false);
                                SystemClock.sleep(750);
                                // Initializes reader
                                StringBuilder sb = new StringBuilder();
                                mReader.cscVersionCsc(sb);
                                Log.d(TAG, "Firmware version: " + sb.toString());
                                // Powers on SAM
                                // Sets the IO for the C-ONE
                                mReader.cscConfigIoExt((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00);
                                // Powers on SAM 1
                                mReader.cscWriteIoExt((byte)0x80, (byte)0x80);
                                // Resets SAM 1
                                mReader.cscSelectSam((byte)0, Defines.SAM_PROT_HSP_INNOVATRON);
                                byte[] atr = new byte[256];
                                int[] atrLength = new int[1];
                                mReader.cscResetSam((byte) 0x01, atr, atrLength);
                                Log.d(TAG, "SAM ATR: " + CpcBytes.byteArrayToString(atr, atrLength[0]));
                            }

                            @Override
                            public void onDisposed(Reader instance) {

                            }
                        });
                    }

                    @Override
                    public void onPowerDown(CpcResult.RESULT res, int vid, int pid) {

                    }
                }).setPeripheralTypes(PeripheralTypesCone.RfidSc)
                .setManufacturers(ManufacturersCone.Ask)
                .setModels(ModelsCone.Ucm108)
                .setInterfaces(InterfacesCone.ExpansionPort)
                .build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_clear) {
            mAdapter.clear();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setRfidPower(true);

    }

    @Override
    protected void onStop() {
        super.onStop();
        setRfidPower(false);
    }

    private void setRfidPower(boolean on) {
        if (on) {
            mPowerMgmt.powerOn();
        } else {
            mPowerMgmt.powerOff();
        }
    }
}

package asia.eyekandi.emw;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import asia.eyekandi.emw.busevents.BluetoothNotEnabledEvent;
import asia.eyekandi.emw.busevents.ConnectionEvent;
import asia.eyekandi.emw.busevents.RowClickedEvent;
import asia.eyekandi.emw.busevents.ServerStateEvent;
import asia.eyekandi.emw.busevents.WandStartedEvent;
import asia.eyekandi.emw.busevents.WandStoppedEvent;
import asia.eyekandi.emw.di.EventBus;
import roboguice.util.Ln;

public class MainActivity2 extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, AdapterView.OnItemClickListener {
    private static final int REQUEST_ENABLE_BT = 20;
    @Inject
    EventBus bus;
    @Inject
    BLEScanner2 scanner;
    AQuery aq;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private List<String> list = new ArrayList<>();
    private TextView statusText;
    private int lastClickedRow = -1;
    // prevent multiple requests from firing
    private boolean requestingEnableBluetooth = false;

    public static boolean isDebugMode(final Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if ("".equals(Ln.config.packageName)) {
            Ln.config = new Ln.BaseConfig(getApplication());
        }
        WaveFormView.setResources(getResources());
        WaveFormSurfaceView.setResources(getResources());
        WaveFormTextureView.setResources(getResources());
        Ln.e("Starting");

        super.onCreate(savedInstanceState);
        ((MyApplication) getApplication()).component().inject(this);

        setContentView(R.layout.activity_main2);
        aq = new AQuery(this);
        this.statusText = aq.id(R.id.textView).getTextView();
        onConnectionEvent(new ConnectionEvent(BLEScanner2.connectionState));
        /*
        aq.id(R.id.textLeft).clicked(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ln.d("OFF clicked");
                scanner.writeReset();
                aq.id(R.id.seekBar).getProgressBar().setProgress(0);
            }
        });
        */

        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        /*
        fab.setLongClickable(true);
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Ln.d("onLongClick");
                return true;
            }
        });
        */
        list.add("one");
        list.add("two");
        list.add("three");
        list.add("four");
        list.add("five");

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Ln.d("No bluetooth enabled, requesting");

            statusText.setText(R.string.BluetoothNotEnabled);

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (isDebugMode(this)) {
            statusText.setLongClickable(true);
            statusText.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Ln.d("Longclick text");
                    startActivity(new Intent(getBaseContext(), Feedback.class));
                    return true;
                }
            });
        }

        aq.id(R.id.sendReset).clicked(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ln.d("Stopping");
                scanner.writeReset();
            }
        });

        aq.id(R.id.sendPattern).clicked(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int begin1 = Integer.parseInt(aq.id(R.id.etNRBegin).getText().toString());
                int begin2 = Integer.parseInt(aq.id(R.id.etNRBegin2).getText().toString());
                int end1 = Integer.parseInt(aq.id(R.id.etNREnd).getText().toString());
                int end2 = Integer.parseInt(aq.id(R.id.etNREnd2).getText().toString());
                int transition1 = Integer.parseInt(aq.id(R.id.etNRTransition).getText().toString());
                int transition2 = Integer.parseInt(aq.id(R.id.etNRTransition2).getText().toString());

                String selectedPattern = (String) aq.id(R.id.patternTypeSpinner).getSpinner().getSelectedItem();
                byte ret;
                if("Non-Repeating".equals(selectedPattern)) {
                    ret = scanner.writeNonRepeating2(begin1, begin2, end1, end2, transition1, transition2);
                }else if("Repeating".equals(selectedPattern)){
                    ret = scanner.writeRepeating2(begin1, begin2, end1, end2, transition1, transition2);
                } else { // if ("Timeout Test".equals(selectedPattern)) {
                    scanner.timeoutTest = true;
                    ret = scanner.writeNonRepeating2(begin1, begin2, end1, end2, transition1, transition2);
                }

                if(ret == ServerStateEvent.PATTERN_ERROR_OR_IDLE) {
                    aq.id(R.id.txtPatternId).text("Pattern ID: ERROR");
                }else {
                    aq.id(R.id.txtPatternId).text(String.format("Pattern ID: 0x%x", ret));
                }

                Ln.i("Sent %s pattern 0x%x", selectedPattern, ret);
            }
        });

        aq.id(R.id.sendSine).clicked(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int amp = Integer.parseInt(aq.id(R.id.etAmplitude).getText().toString());
                int period = Integer.parseInt(aq.id(R.id.etPeriod).getText().toString());
                int center = Integer.parseInt(aq.id(R.id.etCenter).getText().toString());
                Ln.d("Button clicked %d %d %d", amp, period, center);
                byte ret = scanner.writeSinValue(amp, period, center);
                if(ret == ServerStateEvent.PATTERN_ERROR_OR_IDLE) {
                    aq.id(R.id.txtSinePatternId).text("Pattern ID: ERROR");
                }else {
                    aq.id(R.id.txtSinePatternId).text(String.format("Pattern ID: 0x%x", ret));
                }
            }
        });

        aq.id(R.id.sendGauss).clicked(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int start = Integer.parseInt(aq.id(R.id.gaussStart).getText().toString());
                int end = Integer.parseInt(aq.id(R.id.gaussEnd).getText().toString());
                int steep = Integer.parseInt(aq.id(R.id.gaussSteep).getText().toString());
                int duration = Integer.parseInt(aq.id(R.id.gaussDuration).getText().toString());

                int start2 = Integer.parseInt(aq.id(R.id.gaussStart2).getText().toString());
                int end2 = Integer.parseInt(aq.id(R.id.gaussEnd2).getText().toString());
                int steep2 = Integer.parseInt(aq.id(R.id.gaussSteep2).getText().toString());
                int duration2 = Integer.parseInt(aq.id(R.id.gaussDuration2).getText().toString());

                String selectedPattern = (String) aq.id(R.id.gaussPatternTypeSpinner).getSpinner().getSelectedItem();
                byte ret;
                if("Non-Repeating".equals(selectedPattern)) {
                    ret = scanner.writeGaussValue2(BLEScanner2.MESSAGE_ID_NONREPEATING, start, end, steep, duration, start2, end2, steep2, duration2);
                }else if("Repeating".equals(selectedPattern)){
                    ret = scanner.writeGaussValue2(BLEScanner2.MESSAGE_ID_REPEATING, start, end, steep, duration, start2, end2, steep2, duration2);
                }else{
                    scanner.timeoutTest = true;
                    ret = scanner.writeGaussValue2(BLEScanner2.MESSAGE_ID_NONREPEATING, start, end, steep, duration, start2, end2, steep2, duration2);
                }
                if(ret == ServerStateEvent.PATTERN_ERROR_OR_IDLE) {
                    aq.id(R.id.txtGaussPatternId).text("Pattern ID: ERROR");
                }else {
                    aq.id(R.id.txtGaussPatternId).text(String.format("Pattern ID: 0x%x", ret));
                }
            }
        });

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.patternTypes, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        aq.id(R.id.patternTypeSpinner).getSpinner().setAdapter(spinnerAdapter);
        aq.id(R.id.gaussPatternTypeSpinner).getSpinner().setAdapter(spinnerAdapter);

        aq.id(R.id.MICBar).getSeekBar().setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                aq.id(R.id.MICText).text(String.format("MIC %d%%", progress));
                scanner.MIC = (byte) progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scanner.sendMICChange();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        Ln.d("onResume");
        bus.register(this);
        if (BLEScanner2.isBLESupported(this) == false) {
            // should display an alert maybe
            statusText.setText(R.string.BLENotSupported);
            //noinspection deprecation
            statusText.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            statusText.setTextColor(Color.WHITE);
            return;
        }

        scanner.onResume();
        if (checkBTEnabled()) {
            scanner.startScan();
        } else {
            setStatusBTDisabled();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (BLEScanner2.isBLESupported(this)) {
            scanner.onPause();
        }
        bus.unregister(this);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_ENABLE_BT) {
            requestingEnableBluetooth = false;
            Ln.d("onActivityResult: Maybe bluetooth is enabled now?");
            if (checkBTEnabled()) {
                // just in case this is received after onResume
                onConnectionEvent(new ConnectionEvent(ConnectionEvent.EVENT_SEARCHING));
                scanner.startScan();
            } else {
                setStatusBTDisabled();
            }
        }
    }

    private void setStatusBTDisabled() {
        statusText.setText(R.string.BluetoothNotEnabled);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_crash) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        //Ln.d("onProgressChanged %d %b", progress, fromUser);
        float intensity = (float) (progress) / (float) seekBar.getMax();
        WaveFormTextureView.setIntensity(intensity, bus);
        //Ln.d("INTENSITY: %f", intensity);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        /*
        if(!checkBTEnabled()) {
            bus.postOnMain(new BluetoothNotEnabledEvent());
        }
        */
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Ln.d("CLICKED ITEM %d", position);
        if (!checkBTEnabled()) {
            bus.postOnMain(new BluetoothNotEnabledEvent());
            return;
        }
        if (scanner.getConnectionState() == BLEScanner2.STATE_DISCONNECTED) {
            Ln.i("Scanner not connected");
            scanner.startScan();
        }
        lastClickedRow = position;
        bus.post(new RowClickedEvent(position));
        //((WaveFormSurfaceView) view).toggleAnim();
    }

    @Subscribe
    public void onServerStateEvent(ServerStateEvent event) {
        Ln.d("Server state: %s", event.toString());
        aq.id(R.id.serverState).text(event.toString());
    }

    @Subscribe
    public void onConnectionEvent(ConnectionEvent event) {
        if (event.event == ConnectionEvent.EVENT_CONNECTED) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Ln.d("Got connectionEvent CONNECTED");
            statusText.setText(R.string.Connected);
            scanner.sendMICChange();
        } else if (event.event == ConnectionEvent.EVENT_CONNECTING) {
            Ln.d("Got connectionEvent CONNECTING");
            statusText.setText(R.string.Connecting);
        } else if (event.event == ConnectionEvent.EVENT_SEARCHING) {
            Ln.d("Got connectionEvent SEARCHING");
            statusText.setText(R.string.Searching);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            Ln.d("Got connectionEvent %d", event.event);
        }
    }

    @Subscribe
    public void onWandStarted(WandStartedEvent event) {
        Ln.d("onWandStarted");
        // keep screen on while wand is operating
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Subscribe
    public void onWandStopped(WandStoppedEvent event) {
        Ln.d("onWandStopped");
    }

    @Subscribe
    public void onBluetoothNotEnabled(BluetoothNotEnabledEvent event) {
        if (checkBTEnabled()) {
            requestingEnableBluetooth = false;
            Ln.i("Ignoring old BT not enabled event");
            return;
        }
        setStatusBTDisabled();

        if (requestingEnableBluetooth == false) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            requestingEnableBluetooth = true;
        }
    }

    private boolean checkBTEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Ln.d("No BT adapter");
            return false;
        }
        if (adapter.isEnabled() == false) {
            Ln.d("Adapter not enabled");
            return false;
        }
        return true;
    }

}

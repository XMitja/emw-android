package asia.eyekandi.emw;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import asia.eyekandi.emw.busevents.BluetoothNotEnabledEvent;
import asia.eyekandi.emw.busevents.ConnectionEvent;
import asia.eyekandi.emw.busevents.IntensityChangedEvent;
import asia.eyekandi.emw.busevents.RowClickedEvent;
import asia.eyekandi.emw.busevents.ServerStateEvent;
import asia.eyekandi.emw.busevents.StopAnimEvent;
import asia.eyekandi.emw.busevents.WandStartedEvent;
import asia.eyekandi.emw.busevents.WandStoppedEvent;
import asia.eyekandi.emw.di.EventBus;
import roboguice.util.Ln;

public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, AdapterView.OnItemClickListener {
    @Inject EventBus bus;
    @Inject BLEScanner2 scanner;
    private static final int REQUEST_ENABLE_BT = 20;
    private AQuery aq;
    private List<String> list = new ArrayList<>();
    private TextView statusText;
    private FloatingActionButton fab;
    int lastClickedRow = -1;
    private ImageView btImage;

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

        setContentView(R.layout.activity_main);
        aq = new AQuery(this);
        aq.id(R.id.seekBar).getSeekBar().setOnSeekBarChangeListener(this);
        this.statusText = aq.id(R.id.textView).getTextView();
        this.btImage = aq.id(R.id.btImage).getImageView();
        onConnectionEvent(new ConnectionEvent(BLEScanner2.connectionState));
        /*
        aq.id(R.id.textLeft).clicked(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ln.d("OFF clicked");
                scanner.stopWand();
                aq.id(R.id.seekBar).getProgressBar().setProgress(0);
            }
        });
        */

        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (lastClickedRow >= 0) {
                    bus.post(new RowClickedEvent(lastClickedRow));
                }
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        });
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
        ListAdapter listAdapter = new ListAdapter(this, list);
        aq.id(android.R.id.list).adapter(listAdapter);
        aq.id(android.R.id.list).itemClicked(this);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Ln.d("No bluetooth enabled, requesting");

            statusText.setText(R.string.BluetoothNotEnabled);
            //noinspection deprecation
            btImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_bluetooth_disabled_24dp));

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
        /*
        statusText.setClickable(true);
        statusText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkBTEnabled()) {
                    scanner.startScan();
                }else{
                    setStatusBTDisabled();
                }
            }
        });
        */
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
            //noinspection deprecation
            btImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_bluetooth_disabled_24dp));
            return;
        }

        scanner.onResume();
        if (checkBTEnabled()) {
            scanner.startScan();
        } else {
            setStatusBTDisabled();
        }
        SeekBar seekBar = aq.id(R.id.seekBar).getSeekBar();
        onProgressChanged(seekBar, seekBar.getProgress(), false);
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
        //noinspection deprecation
        btImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_bluetooth_disabled_24dp));
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

    class ListAdapter extends ArrayAdapter<String> {
        public ListAdapter(Context context, List<String> objects) {
            super(context, 0, objects);
        }

        // prevent item reuse
        @Override
        public int getViewTypeCount() {
            return getCount();
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        /*
        @Override
        public int getItemViewType(int position) {
            return IGNORE_ITEM_VIEW_TYPE;
        }
        */
        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull final ViewGroup parent) {
            if (convertView == null) {
                Ln.d("Creating new view for position %d", position);
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(R.layout.list_row, parent, false);
            }
            WaveFormTextureView waveView = (WaveFormTextureView) convertView.findViewById(R.id.waveFormView);
            if (waveView != null) {
                waveView.setIndex(position, (MyApplication) getApplication());
            }
            return convertView;
        }
    }

    @SuppressWarnings("deprecation")
    @Subscribe
    public void onConnectionEvent(ConnectionEvent event) {
        if (event.event == ConnectionEvent.EVENT_CONNECTED) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Ln.d("Got connectionEvent CONNECTED");
            statusText.setText(R.string.Connected);
            btImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_bluetooth_connected_24dp));
            int progress = aq.id(R.id.seekBar).getSeekBar().getProgress();
            int progressMax = aq.id(R.id.seekBar).getSeekBar().getMax();
            float intensity = (float) (progress) / (float) progressMax;
            bus.post(new IntensityChangedEvent(intensity));
        } else if (event.event == ConnectionEvent.EVENT_CONNECTING) {
            Ln.d("Got connectionEvent CONNECTING");
            statusText.setText(R.string.Connecting);
            btImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_bluetooth_connected_yellow_24dp));
        } else if (event.event == ConnectionEvent.EVENT_SEARCHING) {
            Ln.d("Got connectionEvent SEARCHING");
            statusText.setText(R.string.Searching);
            btImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_bluetooth_searching_24dp));
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            Ln.d("Got connectionEvent %d", event.event);
        }
    }

    @Subscribe
    public void onWandStarted(WandStartedEvent event) {
        Ln.d("onWandStarted");
        fab.show();
        // keep screen on while wand is operating
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Subscribe
    public void onWandStopped(WandStoppedEvent event) {
        Ln.d("onWandStopped");
        fab.hide();
    }

    // prevent multiple requests from firing
    private boolean requestingEnableBluetooth = false;

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

    public static boolean isDebugMode(final Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        Ln.d("onKeyDown %d %s", keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN: {
                Ln.d("Voldown");
                int progress = aq.id(R.id.seekBar).getProgressBar().getProgress() - 25;
                if (progress > 0) {
                    aq.id(R.id.seekBar).getProgressBar().setProgress(progress);
                } else {
                    aq.id(R.id.seekBar).getProgressBar().setProgress(1);
                }
                return true;
            }
            case KeyEvent.KEYCODE_VOLUME_UP: {
                Ln.d("Volup");
                int progress = aq.id(R.id.seekBar).getProgressBar().getProgress() + 25;
                int max = aq.id(R.id.seekBar).getProgressBar().getMax();
                if (progress <= max) {
                    aq.id(R.id.seekBar).getProgressBar().setProgress(progress);
                } else {
                    aq.id(R.id.seekBar).getProgressBar().setProgress(max);
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Subscribe
    public void onServerStateEvent(ServerStateEvent event) {
        Ln.d("Server state: %s", event.toString());
        if(event.patternId == ServerStateEvent.PATTERN_SERVER) {
            Ln.d("Physical button pushed");
            bus.post(new StopAnimEvent());
        }
    }
}

package de.amproved.app.poitorch;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private static final int MY_DATA_CHECK_CODE = 1234; //arbitrary value
    private TextToSpeech mTts;

    private List<PoiModel> pois;
    private LocationManager locationManager;
    private SensorManager mSensorManager;
    private Sensor vector;
    Handler mHandler;

    private TextView titleText;
    private TextView detailText;
    private Button readButton;

    private final int MIN_DISTANCE_UPDATE = 3; //m
    private final int MIN_TIME_UPDATE = 10000; //ms
    private final int PROX_RADIUS = 150; //m
    private final int ANGLE_DIFF = 20; //°
    private final int LOCATION_UPDATE = 2000; //s
    private int mAzimuth = -1; // degree
    private Location mLocation = null;
    private PoiModel shownLocation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleText = (TextView) findViewById(R.id.instructionText);
        detailText = (TextView) findViewById(R.id.detailText);
        readButton = (Button) findViewById(R.id.readButton);

        initData();
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        vector = mSensorManager.getDefaultSensor( Sensor.TYPE_ROTATION_VECTOR );

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);//onActivityResult-Callback

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                LocationMessage m = (LocationMessage) msg.obj;

                if (!m.heading) {
                    shownLocation = null;
                    if (detailText.getVisibility() == View.VISIBLE) {
                        titleText.setText(R.string.title_instruction);
                        readButton.setVisibility(View.GONE);
                        detailText.setVisibility(View.GONE);
                        mTts.stop();
                    }
                }
                else {
                    final PoiModel poiModel = m.poiModel;
                    if (shownLocation != poiModel) {
                        shownLocation = poiModel;
                        mTts.stop();

                        titleText.setText(poiModel.getName());
                        detailText.setText(poiModel.getDescription() + "\n\n" + poiModel.getSource());
                        readButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            @SuppressWarnings("deprecation")
                            public void onClick(View view) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    String utteranceId = this.hashCode() + "";
                                    mTts.speak(poiModel.getName() + poiModel.getDescription(), TextToSpeech.QUEUE_FLUSH, null, utteranceId);
                                } else {
                                    mTts.speak(poiModel.getName() + poiModel.getDescription(), TextToSpeech.QUEUE_FLUSH, null);
                                }
                            }
                        });

                        detailText.setVisibility(View.VISIBLE);
                        readButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorEventListener, vector, SensorManager.SENSOR_DELAY_GAME);
        trackLocation();

        mTts=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    mTts.setLanguage(Locale.GERMANY);
                }
            }
        });

        mHandler.post(locationCheckTask);
    }

    Runnable locationCheckTask = new Runnable() {
        @Override
        public void run() {
//            Log.d(TAG, "locationCheckTask");

            if(mLocation != null && mAzimuth > -1) {
                boolean someHeading = false;
                for (PoiModel poi : pois) {
                    if (mLocation.distanceTo(poi.getLocation()) <= PROX_RADIUS) {
                        Log.d(TAG, "Proximity to "+poi.getName());
                        float bearing = mLocation.bearingTo(poi.getLocation());
                        float posBearing = (bearing < 0) ? (360+bearing) : bearing;

                        boolean heading = false;
                        if(posBearing > (360 - ANGLE_DIFF) && mAzimuth >= 0 && mAzimuth < ANGLE_DIFF) {
                            if (mAzimuth <= 0 + (ANGLE_DIFF - (360 - posBearing))) {
                                heading = true;
                            }
                        }
                        else if(posBearing < ANGLE_DIFF && mAzimuth > (360 - ANGLE_DIFF)) {
                            if (mAzimuth >= 360 - (ANGLE_DIFF - posBearing)) {
                                heading = true;
                            }
                        }
                        else if((posBearing - mAzimuth) >= -ANGLE_DIFF && (posBearing - mAzimuth) <= ANGLE_DIFF) {
                            heading = true;
                        }

                        if(heading == true) {
                            Log.d(TAG, "Heading: "+poi.getName());
                            someHeading = true;
                            Message m = new Message();
                            m.obj = new LocationMessage(poi);
                            mHandler.sendMessage(m);
                        }
                    }
                }

                if (!someHeading) {
                    Message m = new Message();
                    m.obj = new LocationMessage();
                    mHandler.sendMessage(m);
                }
            }

            mHandler.postDelayed(locationCheckTask, LOCATION_UPDATE);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        mTts.shutdown();
        mHandler.removeCallbacks(locationCheckTask);
        mSensorManager.unregisterListener(mSensorEventListener);
        locationManager.removeUpdates(mLocationListener);
    }

    private void trackLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String locationProvider = null;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationProvider = LocationManager.GPS_PROVIDER;
        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationProvider = LocationManager.NETWORK_PROVIDER;
        }

        if (locationProvider != null) {
            locationManager.requestLocationUpdates(
                    locationProvider, MIN_TIME_UPDATE, MIN_DISTANCE_UPDATE, mLocationListener
            );
        }
        else {
            Toast.makeText(this, "Keine Positionsdaten, bitte Ortung aktivieren", Toast.LENGTH_LONG);
//            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//            startActivity(intent);
        }
    }

    private void initData() {
        pois = new ArrayList<>();

        String name = "Fakultät Informatik";
        String description = "Die Fakultät Informatik der TU Dresden ist im neu errichteten Andreas-Pfitzmann-Bau untergebracht. \n" +
                "Mit über 1800 Studierenden gehört die Fakultät Informatik an der Exzellenz-Universität TU Dresden zu den größten Ausbildungsstätten für Informatik in Deutschland." +
                "Die Fakultät besteht aus sechs Instituten mit 26 Professoren und ca. 300 Mitarbeitern, die in ihrer Forschung das Spektrum der Informatik von der Theorie bis zur Praxis, von der Grundlagenforschung bis zur Anwendung in verschiedensten Domänen abdecken.";
        String source = "Quelle: www.inf.tu-dresden.de";
        Location location = new Location("Informatik");
        location.setLatitude(51.0255d);
        location.setLongitude(13.722597d);
        pois.add(new PoiModel(name, description, source, location));

        name = "Alte Mensa";
        description = "Die Alte Mensa in der Mommsenstraße wurde am 15.11.1925 als eines der ersten deutschen Studentenhäuser eröffnet." +
                "Vom 14.02.2004 bis Ende Dezember 2006 wurde die umfangreiche Sanierung der Alten Mensa durchgeführt.\n" +
                "Die offizielle Wiedereröffnung der Alten Mensa fand am 15.01.2007 statt. Seitdem ist die Mensa Mommsenstraße wieder zentraler Punkt im studentischen Leben und kulinarische Schlagader im Herzen des Campus.";
        source = "Quelle: www.studentenwerk-dresden.de";
        location = new Location("aMensa");
        location.setLatitude(51.026694d);
        location.setLongitude(13.726536d);
        pois.add(new PoiModel(name, description, source, location));

        name = "Münchner Platz";
        description = "Der Münchner Platz entstand 1899 im Zuge des weiteren Ausbaus der Südvorstadt Dresdens.\n" +
                "Ursprünglich sollte auf diesem Areal eine Kirche errichtet werden. Nachdem man sich jedoch für einen anderen Standort an der Ecke Nürnberger / Hohe Straße entschieden hatte, entstand an dieser Stelle 1902/07 der Bau des Dresdner Landgerichts.\n"+
                "Das Landgericht ist heute Teil des Campus der TU Dresden und eine Gedänkstätte für die Opfer politischer Strafjustiz.";
        source = "Quelle: www.dresdner-stadtteile.de";
        location = new Location("Muenchner");
        location.setLatitude(51.029994d);
        location.setLongitude(13.721733d);
        pois.add(new PoiModel(name, description, source, location));

        name = "Nürnberger Platz";
        description = "Der Nürnberger Platz ist Teil der Dresdner Südvorstadt und grenzt an die TU Dresden an.";
        location = new Location("Nuernberger");
        location.setLatitude(51.032111d);
        location.setLongitude(13.726158d);
        pois.add(new PoiModel(name, description, source, location));

        name = "Neue Mensa";
        description = "Die Neue Mensa, auch Mensa Bergstraße genannt, liegt zentral im Herzen des Campus am Autobahnzubringer zur A17. Dank guter Nahverkehrsanbindung mit den Buslinien 72,76 und 61 und vielen großen Wohnheimen im Gehbereich ist die Mensa seit ihrer Eröffnung ein sehr beliebter Treffpunkt der Dresdener Studentenschaft.\n" +
                "Aufgrund der geplanten Sanierung sind die Speisesäle im Oberschoss seit August 2014 geschlossen. Als Übergangslösung wurde mit dem Beginn des Wintersemesters im Oktober 2014 am Nürnberger Platz die Übergangsmensa „Zeltschlösschen“ eröffnet.\n" +
                "Seit Oktober 2015 wird die Neue Mensa vom Land Sachsen als Erstaufnahmeeinrichtung genutzt.";
        source = "Quellen: www.studentenwerk-dresden.de, de.wikipedia.org";
        location = new Location("nMensa");
        location.setLatitude(51.028881d);
        location.setLongitude(13.731928d);
        pois.add(new PoiModel(name, description, source, location));
    }

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "onLocationChanged");
            mLocation = location;
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    private SensorEventListener mSensorEventListener = new SensorEventListener() {

        float[] orientation = new float[3];
        float[] rMat = new float[9];

        public void onAccuracyChanged( Sensor sensor, int accuracy ) {}

        @Override
        public void onSensorChanged( SensorEvent event ) {
            if( event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR ){
                // calculate th rotation matrix
                SensorManager.getRotationMatrixFromVector( rMat, event.values );
                // get the azimuth value (orientation[0]) in degree
                mAzimuth = (int) ( Math.toDegrees( SensorManager.getOrientation( rMat, orientation )[0] ) + 360 ) % 360;
            }
        }
    };
}
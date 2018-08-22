package com.example.traffic_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Timer;

import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;//目前位置
    Marker mCurrLocationMarker;//資料庫點的marker
    LocationRequest mLocationRequest;

    LatLng yourposition;//定位點
    LatLng dbposition; //資料庫經緯度資料
    double distance;
    //資料庫透過PHP將資料轉換成JSON連結的網址(使用Amazon)
    String url = "http://traffic-env.eennja8tqr.ap-northeast-1.elasticbeanstalk.com/";

    private static final long INTERVAL = 1000 * 2;
    private static final long FASTEST_INTERVAL = 1000 * 1;
    static TextView speedtext;
    double speed;

    private static final int NOTIF_ID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        speedtext = (TextView) findViewById(R.id.speedtext);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        getRetrofitArray();//取得資料庫的資料
    }

    /********定時器*******/
    @Override
    protected void onResume() {
        super.onResume();
        startTimer();
    }

    Timer timer;
    java.util.TimerTask timerTask;

    final Handler handler = new Handler();

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        //第一次執行3秒, 之後每隔2秒執行一次
        timer.schedule(timerTask, 3000, 2000); //
    }

//    public void stoptimertask(View v) {
//        if (timer != null) {
//            timer.cancel();
//            timer = null;
//        }
//    }

    public void initializeTimerTask() {

        timerTask = new java.util.TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        getRetrofitArray();//執行尋找資料庫的點及通知
                        speed();//檢查是否超速及通知
                    }
                });
            }
        };
    }
    /********************/

    /********定位********/
    @Override
    public void onConnected(Bundle bundle) {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        yourposition = new LatLng(location.getLatitude(), location.getLongitude()); //定位點

        //經緯度及距離的資料
        String str_origin = "origin=" + yourposition.latitude + "," + yourposition.longitude;
        String str_dest = "destination=" + dbposition.latitude + "," + dbposition.longitude;
        String sensor = "sensor=false";
        String parameters = str_origin + "&" + str_dest + "&" + sensor;
        String output = "json";
        //定位點的URL
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        Log.d("onMapClick", url.toString());
        //取得定位URL
        FetchUrl FetchUrl = new FetchUrl();
        //取得定位URL轉換成JSON的資料結果
        FetchUrl.execute(url);

        mMap.moveCamera(CameraUpdateFactory.newLatLng(yourposition));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

        //計算速度
        speed = location.getSpeed() * 18 / 5;
        MapsActivity.speedtext.setText("speed: " + new DecimalFormat("#.##").format(speed) + " km/hr");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
    /********************/

    /*******Map準備*******/
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }
    }

    public void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    //檢查Map的認證
    public boolean checkLocationPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    //Map的認證結果
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }

                } else {
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }
    /********************/

    /****資料庫Retrofit****/
    //使用Retrofit取得資料後計算距離
    void getRetrofitArray() {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitArrayAPI service = retrofit.create(RetrofitArrayAPI.class);
        //取得Traffic的資料類別及類別對應的資料
        Call<List<Traffic>> call = service.getTrafficDetails();
        //取得處理完的資料
        call.enqueue(new Callback<List<Traffic>>() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onResponse(Response<List<Traffic>> response, Retrofit retrofit) {

                try {

                    List<Traffic> TrafficData = response.body();

                    for (int i = 0;i <= TrafficData.size();i++) {
                        //資料庫的經緯度資料的點
                        dbposition = new LatLng(Double.valueOf(TrafficData.get(i).getLatitude()), Double.valueOf(TrafficData.get(i).getLongitude()));

                        Location yourposition_location = new Location("Yourposition");
                        yourposition_location.setLatitude(yourposition.latitude);
                        yourposition_location.setLongitude(yourposition.longitude);

                        Location dbposition_location = new Location("Dbposition");
                        dbposition_location.setLatitude(dbposition.latitude);
                        dbposition_location.setLongitude(dbposition.longitude);

                        distance = (yourposition_location.distanceTo(dbposition_location));

                        if (distance<=505 && distance>=500) { //距離接近500公尺時通知 distance<=505 && distance>=500
                            mMap.clear();
                            mCurrLocationMarker = mMap.addMarker(new MarkerOptions().position(dbposition).title("Dbposition"));//Mark資料庫的點
                            Toast.makeText(getBaseContext(),"距離："+ distance, Toast.LENGTH_LONG).show();
                            //使用聲音
                            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.dan_ten);
                            // 取得NotificationManager系統服務
                            NotificationManager notiMgr = (NotificationManager)
                                    getSystemService(NOTIFICATION_SERVICE);
                            // 建立狀態列顯示的通知訊息
                            NotificationCompat.Builder noti =
                                    new NotificationCompat.Builder(MapsActivity.this)
                                            .setSound(soundUri)
                                            .setSmallIcon(R.mipmap.ic_launcher)
                                            .setContentTitle("注意")
                                            .setContentText("前方約五百公尺處為危險路段");
                            Intent intent = new Intent(MapsActivity.this, NotificationActivity.class);
                            intent.putExtra("NOTIFICATION_ID", NOTIF_ID);
                            // 建立PendingIntent物件
                            PendingIntent pIntent = PendingIntent.getActivity(MapsActivity.this, 0, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT);
                            noti.setContentIntent(pIntent);  // 指定PendingIntent
                            Notification note = noti.build();

                            // 使用振動
                            note.vibrate= new long[] {100, 250, 100, 500};
                            // 使用LED
                            note.ledARGB = Color.RED;
                            note.flags |= Notification.FLAG_SHOW_LIGHTS;
                            note.ledOnMS = 200;
                            note.ledOffMS = 300;
                            notiMgr.notify(NOTIF_ID, note);// 送出通知訊息
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.d("onResponse", "There is an error");
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.d("onFailure", t.toString());
            }
        });
    }
    public void speed(){
        if(speed>=110) { //國道規定最高速110~120
            //使用聲音
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.speeding);
            // 取得NotificationManager系統服務
            NotificationManager notiMgr = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
            // 建立狀態列顯示的通知訊息
            NotificationCompat.Builder speed =
                    new NotificationCompat.Builder(MapsActivity.this)
                            .setSound(soundUri)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("注意")
                            .setContentText("超速, 您已進入危險路段, 請減速！");
            Intent intent = new Intent(MapsActivity.this, NotificationActivity.class);
            intent.putExtra("NOTIFICATION_ID", NOTIF_ID);
            // 建立PendingIntent物件
            PendingIntent pIntent = PendingIntent.getActivity(MapsActivity.this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            speed.setContentIntent(pIntent);  // 指定PendingIntent
            Notification note = speed.build();

            // 使用振動
            note.vibrate = new long[]{100, 250, 100, 500};
            // 使用LED
            note.ledARGB = Color.RED;
            note.flags |= Notification.FLAG_SHOW_LIGHTS;
            note.ledOnMS = 200;
            note.ledOffMS = 300;
            notiMgr.notify(NOTIF_ID, note);   // 送出通知訊息
        }
    }
    /********************/
    public void goto_Main(View view) {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        timer.cancel();
        timer = null;
        Intent intent=new Intent(MapsActivity.this,MainActivity.class);
        startActivity(intent);
    }
}

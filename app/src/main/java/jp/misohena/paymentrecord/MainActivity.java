package jp.misohena.paymentrecord;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.PersistableBundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PointOfInterest;
import com.google.android.gms.location.LocationServices;

import java.security.Permission;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private MapView mapView_;
    private GoogleMap googleMap_;

    private LocationTracker locationTracker_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        updateSpinnerPaymentMethods();

        locationTracker_ = new LocationTracker(this);
        locationTracker_.connectGoogleApi();

        mapView_ = (MapView)findViewById(R.id.mapView);
        if(mapView_ != null){
            mapView_.onCreate(savedInstanceState);
            mapView_.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    googleMap_ = googleMap;
                    //googleMap.setMyLocationEnabled(true);
                    enableMyLocation();

                    googleMap.setOnPoiClickListener(new GoogleMap.OnPoiClickListener() {
                        @Override
                        public void onPoiClick(PointOfInterest poi) {
                            Toast.makeText(getApplicationContext(), "Clicked: " +
                                            poi.name + "\nPlace ID:" + poi.placeId +
                                            "\nLatitude:" + poi.latLng.latitude +
                                            " Longitude:" + poi.latLng.longitude,
                                    Toast.LENGTH_SHORT).show();

                        }
                    });
                }
            });
        }

        final Button buttonPost = (Button)findViewById(R.id.button_post);
        buttonPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                postRecord();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mapView_ != null){mapView_.onDestroy();}
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if(mapView_ != null){mapView_.onLowMemory();}
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mapView_ != null){mapView_.onPause();}
        locationTracker_.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mapView_ != null){mapView_.onResume();}
        locationTracker_.onResume();
        updateSpinnerPaymentMethods();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mapView_ != null){mapView_.onSaveInstanceState(outState);}
    }

    @Override
    protected void onStart() {
        locationTracker_.onStart();
        super.onStart();
    }

    @Override
    protected void onStop() {
        locationTracker_.onStop();
        super.onStop();
    }


    //
    // UI
    //

    void updateSpinnerPaymentMethods()
    {
        final Spinner sp = (Spinner)findViewById(R.id.spinner_payment_method);

        // backup selection value
        final Object selectedItem = sp.getSelectedItem();
        final String selectedValue = selectedItem != null ? (String)selectedItem : null;

        // get methods and set list
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final String methodsString = prefs.getString("payment_methods", getString(R.string.pref_default_payment_methods));
        final String[] methods = methodsString.split(",");

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, methods);

        sp.setAdapter(adapter);

        // restore selection
        int pos = 0;
        if(selectedValue != null) {
            for (int i = 0; i < methods.length; ++i) {
                if (methods[i].equals(selectedValue)) {
                    pos = i;
                    break;
                }
            }
        }

        sp.setSelection(pos);
    }

    //
    // Menu
    //

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //
    // Post
    //

    static final int POSTING_METHOD_INTENT_SEND = 0;
    static final int POSTING_METHOD_INTENT_SEND_EVERNOTE = 1;
    private void postRecord()
    {
        // Get settings
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final String tagname = prefs.getString("posting_tagname", getString(R.string.pref_default_posting_tagname));
        final int postingMethod = Integer.parseInt(prefs.getString("posting_method", Integer.toString(POSTING_METHOD_INTENT_SEND)));

        // Get view
        final EditText editPaymentAmount = (EditText)findViewById(R.id.edit_payment_amount);
        final EditText editPaymentPurpose = (EditText)findViewById(R.id.edit_payment_purpose);
        final Spinner spinnerPaymentMethod = (Spinner)findViewById(R.id.spinner_payment_method);

        // Make content
        int paymentAmount;
        try {
            paymentAmount = Integer.parseInt(editPaymentAmount.getText().toString());
        }
        catch(NumberFormatException e){
            return;
        }

        final String purpose = editPaymentPurpose.getText().toString();
        final String method = (String)spinnerPaymentMethod.getSelectedItem();

        final String title = Integer.toString(paymentAmount) + purpose;
        final String br = "\n";
        String body =
                getString(R.string.payment_amount) + ": " + paymentAmount + br +
                getString(R.string.payment_purpose) + ": " + purpose + br +
                getString(R.string.payment_method) + ": " + method + br +
                getString(R.string.record_datetime) + ": " + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()) + br;
        final Location loc = locationTracker_.getLastLocation();
        if(loc != null) {
            body += (getString(R.string.record_location) + ": " + Double.toString(loc.getLatitude()) + " , " + Double.toString(loc.getLongitude())) + br;
        }

        final ArrayList<String> tags = new ArrayList<>();
        tags.add(tagname);

        // Send
        Intent intent = new Intent();
        switch(postingMethod){
            case POSTING_METHOD_INTENT_SEND:
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, title);
                intent.putExtra(Intent.EXTRA_TEXT, body);
                break;
            case POSTING_METHOD_INTENT_SEND_EVERNOTE:
                intent.setAction("com.evernote.action.CREATE_NEW_NOTE");
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, title);
                intent.putExtra(Intent.EXTRA_TEXT, body);
                intent.putExtra("TAG_NAME_LIST", tags);
                if(loc != null) {
                    intent.putExtra("LATITUDE", loc.getLatitude());
                    intent.putExtra("LONGITUDE", loc.getLongitude());
                }
                //intent.putExtra("QUICK_SEND", true);
                intent.putExtra("FORCE_NO_UI", true);
                break;
            default:
                return;
        }
        startActivity(Intent.createChooser(intent, getString(R.string.post_record_to_intent)));
    }



    //
    // Google Map
    //

    private void enableMyLocation()
    {
        requirePermissions(
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                new Runnable(){@Override public void run(){
                    if(googleMap_ == null) {
                        return;
                    }

                    try {
                        googleMap_.setMyLocationEnabled(true);
                        googleMap_.getUiSettings().setMyLocationButtonEnabled(true);
                    }
                    catch(SecurityException e){}

                    locationTracker_.setLocationListener(new LocationListener() {
                        private boolean first = true;
                        @Override
                        public void onLocationChanged(Location location) {
                            Log.d("PaymentRecord", "onLocationChanged");
                            if(location != null){
                                if(first) {
                                    first = false;
                                    googleMap_.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 17));
                                }
                                else {
                                    googleMap_.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
                                }
                            }
                        }
                    });
                    locationTracker_.startTracking();
                } } );
    }



    //
    // Runtime Permission
    //

    private HashMap<Integer, Runnable> perms_handlers_ = new HashMap<>();
    private int perms_code_ = 1;

    private void requirePermissions(String[] permissions, Runnable callback)
    {
        final String[] nonGrantedPerms = perms_getNonGrantedPermissions(permissions);

        if(nonGrantedPerms.length == 0){
            callback.run();
        }
        else {
            perms_request(nonGrantedPerms, callback);
        }
    }
    private String[] perms_getNonGrantedPermissions(String[] permissions)
    {
        final ArrayList<String> nonGrantedPerms = new ArrayList<>();
        for(String permission : permissions) {
            if (PermissionChecker.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // not granted
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    //@todo show rationale dialog
                }
                nonGrantedPerms.add(permission);
            }
        }
        return (String[])nonGrantedPerms.toArray(new String[nonGrantedPerms.size()]);
    }
    private void perms_request(String[] nonGrantedPerms, Runnable callback)
    {
        // allocate a code
        final int code = perms_code_++;
        perms_handlers_.put(code, callback);
        // request
        ActivityCompat.requestPermissions(
                this,
                nonGrantedPerms,
                code);
    }
    private void perms_handleResult(int requestCode, int[] grantResults)
    {
        // deallocate the code
        final Runnable callback = perms_handlers_.get(requestCode);
        perms_handlers_.remove(requestCode);

        // find not granted
        boolean grantedAll = true;
        for(int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                grantedAll = false;
                break;
            }
        }

        // callback
        if(grantedAll){
            if(callback != null) {
                callback.run();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        perms_handleResult(requestCode, grantResults);
    }
}



class LocationTracker
{
    private Context context_;
    private GoogleApiClient googleApiClient_;
    private LocationRequest locationRequest_;
    private Location lastLocation_;
    private long lastTime_;
    private LocationListener locationListener_;
    private boolean tracking_ = false;

    public LocationTracker(Context context)
    {
        context_ = context;
    }

    public void setLocationListener(LocationListener locationListener)
    {
        locationListener_ = locationListener;
    }


    // call from Activity

    public void onStart()
    {
        if(googleApiClient_ != null){
            Log.d("PaymentRecord", "onStart connect");
            googleApiClient_.connect();
        }
    }
    public void onStop()
    {
        if(googleApiClient_ != null){
            Log.d("PaymentRecord", "onStop disconnect");
            googleApiClient_.disconnect();
        }
    }
    public void onPause()
    {
        if(tracking_) {
            stopTrackingInner();
        }
    }
    public void onResume()
    {
        if(tracking_) {
            startTrackingInner();
        }
    }

    public void connectGoogleApi()
    {
        if (googleApiClient_ == null) {
            Log.d("PaymentRecord", "connectGoogleApi start");
            googleApiClient_ = new GoogleApiClient.Builder(context_)
                    .addConnectionCallbacks(callbackConn_)
                    .addOnConnectionFailedListener(callbackConnFailed_)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    private GoogleApiClient.ConnectionCallbacks callbackConn_ = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d("PaymentRecord", "onConnected");
            if(tracking_){
                startTrackingInner();
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("PaymentRecord", "onConnectionSuspended");
        }
    };

    private GoogleApiClient.OnConnectionFailedListener callbackConnFailed_ = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("PaymentRecord", "onConnectionFailed");
        }
    };


    private void setLastLocation(Location loc)
    {
        lastLocation_ = loc;
        lastTime_ = System.currentTimeMillis();
        if(locationListener_ != null){
            locationListener_.onLocationChanged(loc);
        }
    }

    /**
     * @see https://developer.android.com/training/location/retrieve-current.html#last-known
     */
    public Location getLastLocation()
    {
        if(googleApiClient_ != null) {
            try {
                // if googleApiClient_ is not connected, return null
                final Location loc = LocationServices.FusedLocationApi.getLastLocation(googleApiClient_);
                if(loc != null){
                    setLastLocation(loc);
                    return loc;
                }
            } catch (SecurityException e) {}
        }
        return null;
    }


    /**
     * @see https://developer.android.com/training/location/change-location-settings.html#location-request
     */
    private static LocationRequest createLocationRequest()
    {
        final LocationRequest req = new LocationRequest();
        req.setInterval(10000);
        req.setFastestInterval(5000);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return req;
    }
    private LocationRequest getLocationRequest()
    {
        if(locationRequest_ == null){
            locationRequest_ = createLocationRequest();
        }
        return locationRequest_;
    }


    /**
     * @see https://developer.android.com/training/location/change-location-settings.html#get-settings
     */
    public void requestCurrentLocation()
    {
        if(googleApiClient_ == null){
            connectGoogleApi();
            if(googleApiClient_ == null) {
                return;
            }
        }
        // googleApiClient_ does not need to be connected.

        final LocationRequest locationRequest = getLocationRequest();
        final ResultCallback<LocationSettingsResult> callback = new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch(status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        getLastLocation();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        //status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                        break;
                }
            }
        };
        final LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        final PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient_, builder.build());
        result.setResultCallback(callback);
    }

    public void startTracking()
    {
        if(!tracking_){
            tracking_ = true;
            if(googleApiClient_ == null){
                connectGoogleApi();
            }
            else if(!googleApiClient_.isConnected()){
                return;
            }
            else{
                startTrackingInner();
            }
        }
    }
    public void stopTracking()
    {
        if(tracking_){
            tracking_ = false;
            stopTrackingInner();
        }
    }


    /**
     * @see https://developer.android.com/training/location/receive-location-updates.html#updates
     */
    private void startTrackingInner()
    {
        if(googleApiClient_ == null || !googleApiClient_.isConnected()){
            return;
        }
        Log.d("PaymentRecord", "startTrackingInner");

        try {
            final LocationRequest request = getLocationRequest();
            final LocationListener listener = locationListener_;
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient_, request, listener);
        }
        catch(SecurityException e){
            Log.d("PaymentRecord", "startTrackingInner exception occured.");
        }
    }

    /**
     * @see https://developer.android.com/training/location/receive-location-updates.html#stop-updates
     */
    private void stopTrackingInner()
    {
        if(googleApiClient_ == null || !googleApiClient_.isConnected()){
            return;
        }

        final LocationListener listener = locationListener_;
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient_, listener);
    }

    private LocationListener locationTracker_ = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            setLastLocation(location);
        }
    };
}

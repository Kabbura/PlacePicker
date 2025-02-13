package com.sucho.placepicker

import android.content.Intent
import android.content.res.ColorStateList
import android.location.Address
import android.location.Geocoder
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class PlacePickerActivity : AppCompatActivity(), OnMapReadyCallback {

  companion object {
    private const val TAG = "PlacePickerActivity"
  }

  private lateinit var map: GoogleMap
  private lateinit var markerImage: ImageView
  private lateinit var markerShadowImage: ImageView
  private lateinit var bottomSheet: CurrentPlaceSelectionBottomSheet
  private lateinit var fab: FloatingActionButton

  private var latitude = Constants.DEFAULT_LATITUDE
  private var longitude = Constants.DEFAULT_LONGITUDE
  private var showLatLong = true
  private var zoom = Constants.DEFAULT_ZOOM
  private var addressRequired: Boolean = true
  private var shortAddress = ""
  private var fullAddress = ""
  private var hideMarkerShadow = false
  private var markerDrawableRes: Int = -1
  private var markerColorRes: Int = -1
  private var fabColorRes: Int = -1
  private var primaryTextColorRes: Int = -1
  private var secondaryTextColorRes: Int = -1
  private var addresses: List<Address>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_place_picker)
    getIntentData()
    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    val mapFragment = supportFragmentManager
        .findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

    bottomSheet = findViewById(R.id.bottom_sheet)
    bottomSheet.showCoordinatesTextView(showLatLong)
    markerImage = findViewById(R.id.marker_image_view)
    markerShadowImage = findViewById(R.id.marker_shadow_image_view)
    fab = findViewById(R.id.place_chosen_button)

    fab.setOnClickListener {
      if (addresses != null) {
        val addressData = AddressData(latitude, longitude, addresses)
        val returnIntent = Intent()
        returnIntent.putExtra(Constants.ADDRESS_INTENT, addressData)
        setResult(RESULT_OK, returnIntent)
        finish()
      } else {
        if (!addressRequired) {
          val addressData = AddressData(latitude, longitude, null)
          val returnIntent = Intent()
          returnIntent.putExtra(Constants.ADDRESS_INTENT, addressData)
          setResult(RESULT_OK, returnIntent)
          finish()
        } else {
          Toast.makeText(this@PlacePickerActivity, R.string.no_address, Toast.LENGTH_LONG)
              .show()
        }
      }
    }

    setIntentCustomization()
  }

  private fun getIntentData() {
    latitude = intent.getDoubleExtra(Constants.INITIAL_LATITUDE_INTENT, Constants.DEFAULT_LATITUDE)
    longitude = intent.getDoubleExtra(Constants.INITIAL_LONGITUDE_INTENT, Constants.DEFAULT_LONGITUDE)
    showLatLong = intent.getBooleanExtra(Constants.SHOW_LAT_LONG_INTENT, false)
    addressRequired = intent.getBooleanExtra(Constants.ADDRESS_REQUIRED_INTENT, true)
    hideMarkerShadow = intent.getBooleanExtra(Constants.HIDE_MARKER_SHADOW_INTENT, false)
    zoom = intent.getFloatExtra(Constants.INITIAL_ZOOM_INTENT, Constants.DEFAULT_ZOOM)
    markerDrawableRes = intent.getIntExtra(Constants.MARKER_DRAWABLE_RES_INTENT, -1)
    markerColorRes = intent.getIntExtra(Constants.MARKER_COLOR_RES_INTENT, -1)
    fabColorRes = intent.getIntExtra(Constants.FAB_COLOR_RES_INTENT, -1)
    primaryTextColorRes = intent.getIntExtra(Constants.PRIMARY_TEXT_COLOR_RES_INTENT, -1)
    secondaryTextColorRes = intent.getIntExtra(Constants.SECONDARY_TEXT_COLOR_RES_INTENT, -1)
  }

  private fun setIntentCustomization() {
    markerShadowImage.visibility = if (hideMarkerShadow) View.GONE else View.VISIBLE
    if (markerColorRes != -1) {
      markerImage.setColorFilter(ContextCompat.getColor(this, markerColorRes))
    }
    if (markerDrawableRes != -1) {
      markerImage.setImageDrawable(ContextCompat.getDrawable(this, markerDrawableRes))
    }
    if (fabColorRes != -1) {
      fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, fabColorRes))
    }
    if(primaryTextColorRes!=-1) {
      bottomSheet.setPrimaryTextColor(ContextCompat.getColor(this, primaryTextColorRes))
    }
    if(secondaryTextColorRes!=-1) {
      bottomSheet.setSecondaryTextColor(ContextCompat.getColor(this, secondaryTextColorRes))
    }
  }

  override fun onMapReady(googleMap: GoogleMap) {
    map = googleMap

    map.setOnCameraMoveStartedListener {
      if (markerImage.translationY == 0f) {
        markerImage.animate()
            .translationY(-75f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(250)
            .start()
      }
    }

    map.setOnCameraIdleListener {
      markerImage.animate()
          .translationY(0f)
          .setInterpolator(OvershootInterpolator())
          .setDuration(250)
          .start()

      bottomSheet.showLoadingBottomDetails()
      val latLng = map.cameraPosition.target
      latitude = latLng.latitude
      longitude = latLng.longitude
      AsyncTask.execute {
        getAddressForLocation()
        runOnUiThread { bottomSheet.setPlaceDetails(latitude, longitude, shortAddress, fullAddress) }
      }
    }
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
  }

  private fun getAddressForLocation() {
    setAddress(latitude, longitude)
  }

  private fun setAddress(
    latitude: Double,
    longitude: Double
  ) {
    val geoCoder = Geocoder(this, Locale.getDefault())
    try {
      val addresses = geoCoder.getFromLocation(latitude, longitude, 1)
      this.addresses = addresses
      return if (addresses != null && addresses.size != 0) {
        fullAddress = addresses[0].getAddressLine(
            0
        ) // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
        shortAddress = generateFinalAddress(fullAddress).trim()
      } else {
        shortAddress = ""
        fullAddress = ""
      }
    } catch (e: Exception) {
      //Time Out in getting address
      Log.e(TAG, e.message)
      shortAddress = ""
      fullAddress = ""
      addresses = null
    }
  }

  private fun generateFinalAddress(
    address: String
  ): String {
    val s = address.split(",")
    return if (s.size >= 3) s[1] + "," + s[2] else if (s.size == 2) s[1] else s[0]
  }
}

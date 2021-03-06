package com.vanillaplacepicker.presentation.autocomplete

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.widget.RxTextView
import com.vanillaplacepicker.R
import com.vanillaplacepicker.data.AutocompletePredictionResponse
import com.vanillaplacepicker.data.PlaceDetailsResponse
import com.vanillaplacepicker.data.common.PlaceDetailsMapper
import com.vanillaplacepicker.domain.common.Resource
import com.vanillaplacepicker.domain.common.SafeObserver
import com.vanillaplacepicker.domain.common.Status
import com.vanillaplacepicker.extenstion.hideView
import com.vanillaplacepicker.extenstion.showView
import com.vanillaplacepicker.presentation.common.VanillaBaseViewModelActivity
import com.vanillaplacepicker.utils.KeyUtils
import com.vanillaplacepicker.utils.Logger
import com.vanillaplacepicker.utils.ToastUtils
import kotlinx.android.synthetic.main.activity_vanilla_autocomplete.*
import kotlinx.android.synthetic.main.lo_recyclremptyvw_appearhere.*

class VanillaAutocompleteActivity : VanillaBaseViewModelActivity<VanillaAutocompleteViewModel>(),
        View.OnClickListener {

    private val TAG = VanillaAutocompleteActivity::class.java.simpleName
    private var apiKey = ""
    private var region: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var radius: Int? = null
    private var language: String? = null
    private var minPrice: Int? = null
    private var maxPrice: Int? = null
    private var openNow: Boolean? = null
    private var pageToken: String? = null
    private var types: String? = null
    private var tintColor: Int? = null
    private var minCharLimit: Int = 3
    private val autoCompleteAdapter by lazy { VanillaAutoCompleteAdapter(this::onItemSelected) }

    override fun getContentResource() = R.layout.activity_vanilla_autocomplete

    override fun initViews() {
        super.initViews()
        // HIDE ActionBar(if exist in style) of root project module
        supportActionBar?.hide()
        getBundle()
        setTintColor()
        ivBack.setOnClickListener(this)
        ivClear.setOnClickListener(this)
        rvPlaces.setHasFixedSize(true)
        rvPlaces.setEmptyView(rvEmptyView)
        rvPlaces.adapter = autoCompleteAdapter
        etQuery.requestFocus()

        // call this method only once
        val hashMap = HashMap<String, String>()
        hashMap[KeyUtils.API_KEY] = apiKey
        region?.let {
            hashMap.put(KeyUtils.REGION, it)
        }
        if (latitude != null && longitude != null) {
            hashMap[KeyUtils.LOCATION] = "$latitude,$longitude"
        }
        radius?.let {
            hashMap.put(KeyUtils.RADIUS, it.toString())
        }
        language?.let {
            hashMap.put(KeyUtils.LANGUAGE, it)
        }
        minPrice?.let {
            hashMap[KeyUtils.MIN_PRICE] = minPrice.toString()
        }
        maxPrice?.let {
            hashMap[KeyUtils.MAX_PRICE] = maxPrice.toString()
        }
        openNow?.let {
            hashMap[KeyUtils.OPEN_NOW] = openNow.toString()
        }
        pageToken?.let {
            hashMap[KeyUtils.PAGE_TOKEN] = pageToken.toString()
        }
        types?.let {
            hashMap.put(KeyUtils.TYPES, it)
        }

        viewModel.configureAutoComplete(hashMap, minCharLimit)

        RxTextView.afterTextChangeEvents(etQuery)
                .skipInitialValue()
                .subscribe {
                    viewModel.onInputStateChanged(it.editable()?.trim().toString(), minCharLimit)
                }.collect()
    }

    private fun getBundle() {
        apiKey = intent.getStringExtra(KeyUtils.API_KEY)
        if (hasExtra(KeyUtils.REGION)) {
            region = intent.getStringExtra(KeyUtils.REGION)
        }
        if (hasExtra(KeyUtils.LATITUDE)) {
            latitude = intent.getDoubleExtra(KeyUtils.LATITUDE, 0.0)
        }
        if (hasExtra(KeyUtils.LONGITUDE)) {
            longitude = intent.getDoubleExtra(KeyUtils.LONGITUDE, 0.0)
        }
        if (hasExtra(KeyUtils.RADIUS)) {
            radius = intent.getIntExtra(KeyUtils.RADIUS, 0)
        }
        if (hasExtra(KeyUtils.LANGUAGE)) {
            language = intent.getStringExtra(KeyUtils.LANGUAGE)
        }
        if (hasExtra(KeyUtils.MIN_PRICE)) {
            minPrice = intent.getIntExtra(KeyUtils.MIN_PRICE, 0)
        }
        if (hasExtra(KeyUtils.MAX_PRICE)) {
            maxPrice = intent.getIntExtra(KeyUtils.MAX_PRICE, 0)
        }
        if (hasExtra(KeyUtils.OPEN_NOW)) {
            openNow = intent.getBooleanExtra(KeyUtils.OPEN_NOW, false)
        }
        if (hasExtra(KeyUtils.PAGE_TOKEN)) {
            pageToken = intent.getStringExtra(KeyUtils.PAGE_TOKEN)
        }
        if (hasExtra(KeyUtils.TYPES)) {
            types = intent.getStringExtra(KeyUtils.TYPES)
        }
        if (hasExtra(KeyUtils.TINT_COLOR)) {
            tintColor = intent.getIntExtra(KeyUtils.TINT_COLOR, 0)
        }
        if (hasExtra(KeyUtils.MIN_CHAR_LIMIT)) {
            minCharLimit = intent.getIntExtra(KeyUtils.MIN_CHAR_LIMIT, 3)
        }
    }

    private fun hasExtra(key: String): Boolean {
        return intent.hasExtra(key)
    }

    // Apply Tint color to Back, Clear button
    private fun setTintColor() {
        try {
            tintColor?.let { tintColorResId ->
                ImageViewCompat.setImageTintList(
                        ivBack,
                        ColorStateList.valueOf(ContextCompat.getColor(this, tintColorResId))
                )
                ImageViewCompat.setImageTintList(
                        ivClear,
                        ColorStateList.valueOf(ContextCompat.getColor(this, tintColorResId))
                )
                etQuery.setTextColor(ContextCompat.getColor(this, tintColorResId))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Invalid color resource ID. Error: $e")
        }
    }

    override fun buildViewModel(): VanillaAutocompleteViewModel {
        return ViewModelProviders.of(this)[VanillaAutocompleteViewModel::class.java]
    }

    override fun initLiveDataObservers() {
        super.initLiveDataObservers()
        viewModel.showClearButtonLiveData.observe(
                this,
                SafeObserver(this::handleClearButtonVisibility)
        )
        viewModel.autoCompleteLiveData.observe(this, SafeObserver(this::handleAutoCompleteData))
        viewModel.vanillaAddressLiveData.observe(this, SafeObserver(this::handleSelectedPlaceDetails))
    }

    private fun handleClearButtonVisibility(visible: Boolean) {
        if (visible) ivClear.showView()
        else ivClear.hideView()
    }

    private fun handleAutoCompleteData(response: Resource<AutocompletePredictionResponse>) {
        when (response.status) {
            Status.LOADING -> progressBar.showView()
            Status.SUCCESS -> handleAutocompleteSuccessResponse(response.item)
            Status.ERROR -> handleAutocompleteErrorResponse(response)
        }
    }

    private fun handleAutocompleteSuccessResponse(result: AutocompletePredictionResponse?) {
        progressBar.hideView()
        result?.let {
            autoCompleteAdapter.setList(it.predictions)
        }
    }

    private fun handleAutocompleteErrorResponse(response: Resource<AutocompletePredictionResponse>) {
        progressBar.hideView()
        autoCompleteAdapter.clearList()
        response.item?.let {
            if (it.errorMessage != null) {
                ToastUtils.showToast(this, it.errorMessage)
            } else {
                ToastUtils.showToast(this, R.string.no_address_found)
            }
        }

        response.throwable?.let {
            ToastUtils.showToast(this, it.message)
        }
    }


    private fun handleSelectedPlaceDetails(response: Resource<PlaceDetailsResponse>) {
        when (response.status) {
            Status.LOADING -> {
                progressBar.showView()
                disableTouchInteraction()
            }
            Status.ERROR -> handleSelectedPlaceDetailsError(response)
            Status.SUCCESS -> handleSelectedPlaceSuccess(response.item)
        }
    }

    private fun handleSelectedPlaceSuccess(placeDetailsResponse: PlaceDetailsResponse?) {
        enableTouchInteraction()
        progressBar.hideView()
        placeDetailsResponse?.let {
            val intent = Intent().apply {
                putExtra(KeyUtils.SELECTED_PLACE, PlaceDetailsMapper.apply(it))
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun handleSelectedPlaceDetailsError(response: Resource<PlaceDetailsResponse>) {
        progressBar.hideView()
        enableTouchInteraction()
        response.item?.let {
            if (it.errorMessage != null) {
                ToastUtils.showToast(this, it.errorMessage)
            } else {
                ToastUtils.showToast(this, R.string.no_address_found)
            }
        }

        response.throwable?.let {
            ToastUtils.showToast(this, it.message)
        }
    }


    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack -> onBackPressed()
            R.id.ivClear -> etQuery.setText("")
        }
    }

    private fun onItemSelected(selectedPlace: AutocompletePredictionResponse.PredictionsBean) {
        if (!selectedPlace.placeId.isNullOrEmpty())
            viewModel.fetchPlaceDetails(selectedPlace.placeId!!, apiKey)
        else
            ToastUtils.showToast(this, R.string.something_went_worng)
    }

    private fun disableTouchInteraction() {
        window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    private fun enableTouchInteraction() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    override fun onDestroy() {
        enableTouchInteraction()
        super.onDestroy()
    }
}

package org.androidlabs.filewall

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.auth.AuthActivity
import org.androidlabs.filewall.auth.AuthManager
import org.androidlabs.filewall.auth.DeviceLockReceiver
import org.androidlabs.filewall.auth.biometric.BiometricPlaceholderActivity
import org.androidlabs.filewall.auth.pin.PinActivity
import org.androidlabs.filewall.confirm.DocumentConfirmationActivity
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.logger.FileLogger
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.logger.LogWrapper
import org.androidlabs.filewall.auth.biometric.Biometric
import org.androidlabs.filewall.auth.pin.PIN
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore
import kotlin.system.exitProcess

class SFApplication : Application() {
    private val TAG = "SFApplication"

    private val _isAccessGranted: MutableLiveData<Boolean> = MutableLiveData(false)

    val isAccessGranted: LiveData<Boolean>
        get() = _isAccessGranted

    private val logWrapper = LogWrapper()

    private var dbHelper = Database(this)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var expirationJob: Job? = null
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false
    private var isMainAppInForeground: Boolean = false
    private var lastGateStartedAt = 0L
    private var activitiesObserver: ActivityLifecycleCallbacks? = null
    private lateinit var deviceLockReceiver: DeviceLockReceiver
    private lateinit var isAccessGrantedObserver: Observer<Boolean>

    /**
     * Whether access logging is on, kept in memory because the provider consults it on
     * every access and a DataStore read there would be on the hot path of a bulk copy.
     *
     * Until the first read has landed this asks DataStore directly, like the auth flags
     * below. The provider can be called before the collector has delivered anything, and a
     * mirror that defaulted to false would drop the log row of the access that woke the
     * process - the one an audit log most needs to hold.
     */
    @Volatile
    private var accessLogLoaded = false

    @Volatile
    private var cachedAccessLogEnabled = false

    val isAccessLogEnabled: Boolean
        get() = if (accessLogLoaded) cachedAccessLogEnabled else runBlocking {
            applicationContext.dataStore.data
                .map { it[SettingsKeys.IS_ACCESS_LOG_ENABLED] == true }
                .first()
        }

    /**
     * Whether each authentication method is on, mirrored here because every access asks.
     *
     * PIN.isEnabled and Biometric.isEnabled each runBlocking a DataStore collection, and
     * validateSecurity calls both before it decides anything - four blocking reads per
     * created document, since a creation goes through validateAccess twice.
     *
     * Until the first read has landed these fall back to asking DataStore directly. A
     * content provider is created before Application.onCreate, so an access can arrive
     * before any mirror is warm, and a mirror that defaulted to false would tell
     * validateSecurity that authentication is off and let that access straight through.
     */
    @Volatile
    private var authFlagsLoaded = false

    @Volatile
    private var cachedPinEnabled = false

    @Volatile
    private var cachedBiometricEnabled = false

    val isPinEnabled: Boolean
        get() = if (authFlagsLoaded) cachedPinEnabled else PIN.isEnabled(this)

    val isBiometricEnabled: Boolean
        get() = if (authFlagsLoaded) cachedBiometricEnabled else Biometric.isEnabled(this)

    private val _isProtectionDisabled = MutableStateFlow(false)
    val isProtectionDisabled: StateFlow<Boolean> = _isProtectionDisabled
    private var disableJob: Job? = null

    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime

    var showRequestsDialog = mutableStateOf(true)
        private set

    fun confirmRequestsDialog() {
        showRequestsDialog.value = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        dbHelper.clearAllAuth(this)
        deviceLockReceiver = DeviceLockReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(deviceLockReceiver, filter)

        val fileLogger = FileLogger(this)
        logWrapper.next = fileLogger

        Log.addLogNode(logWrapper)

        setupGlobalErrorHandler()

        refreshAccessStatus()

        observeSettingsChanges()

        isAccessGrantedObserver = Observer { isGranted ->
            Log.d(TAG, "isGranted - $isGranted")
        }

        isAccessGranted.observeForever(isAccessGrantedObserver)

        listenActivities()

        applicationScope.launch {
            val prefs = applicationContext.dataStore.data.first()
            Log.d(TAG, "DataStore loaded: ${prefs.asMap().size} entries")
        }

        applicationScope.launch {
            applicationContext.dataStore.data
                .map { it[SettingsKeys.IS_ACCESS_LOG_ENABLED] == true }
                .collect {
                    cachedAccessLogEnabled = it
                    accessLogLoaded = true
                }
        }

        applicationScope.launch {
            applicationContext.dataStore.data.collect {
                cachedPinEnabled = it[SettingsKeys.IS_PIN_ENABLED] == true
                cachedBiometricEnabled = it[SettingsKeys.IS_BIOMETRIC_ENABLED] == true
                authFlagsLoaded = true
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "onTerminate")

        isAccessGranted.removeObserver(isAccessGrantedObserver)
        unregisterReceiver(deviceLockReceiver)
        activitiesObserver?.let {
            unregisterActivityLifecycleCallbacks(it)
        }
        Log.removeLogNode(logWrapper)
    }

    fun refreshAccessStatus() {
        Log.d(TAG, "refreshAccessStatus")

        if (!AuthManager.instance.isEnabled(this)) {
            AuthManager.instance.isBioWas = false
            _isAccessGranted.postValue(true)
            stopExpirationTimer()
            return
        }

        val packageName = packageName

        val (isAuthorized, expiration) = dbHelper.isAuthorized(packageName)
        _isAccessGranted.postValue(isAuthorized)

        expiration?.let {
            startExpirationTimer(it)
        }
    }

    fun removeGrantedAccessIfNeeded() {
        wasDialogShown = false
        if (AuthManager.instance.isEnabled(this)) {
            AuthManager.instance.isBioWas = false
            _isAccessGranted.postValue(false)
        }
    }

    private fun setupGlobalErrorHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("UncaughtException", "Uncaught exception in thread: ${thread.name} $throwable")

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }

    private fun observeSettingsChanges() {
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.data
                .flowOn(Dispatchers.IO)
                .collectLatest { preferences ->
                    Log.isEnabled = preferences[SettingsKeys.IS_DEBUG_MODE_ENABLED] == true
                }
        }
    }

    private fun onEnterForeground(lastActivity: Activity) {
        Log.d(TAG, "App is in the Foreground, $lastActivity")
        refreshAccessStatus()
        isMainAppInForeground = true
        restoreProtection()
    }

    private fun onEnterBackground(lastActivity: Activity) {
        Log.d(TAG, "App is in the Background, $lastActivity")
        stopExpirationTimer()

        isMainAppInForeground = false
    }

    private fun listenActivities() {
        fun isAuthActivity(activity: Activity): Boolean {
            return activity is PinActivity || activity is BiometricPlaceholderActivity || activity is DocumentConfirmationActivity
        }
        activitiesObserver = object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                Log.d(TAG, "Start $activity ${isAuthActivity(activity)}")
                if (!isAuthActivity(activity) && ++activityReferences == 1 && !isActivityChangingConfigurations) {
                    onEnterForeground(activity)
                }
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                Log.d(TAG, "Stop $activity ${isAuthActivity(activity)}")
                if (!isAuthActivity(activity)) {
                    isActivityChangingConfigurations = activity.isChangingConfigurations
                    if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                        onEnterBackground(activity)
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        }

        registerActivityLifecycleCallbacks(activitiesObserver)
    }

    private fun stopExpirationTimer() {
        expirationJob?.let {
            Log.d(TAG, "stopExpirationTimer")
            it.cancel()
            expirationJob = null
        }
    }

    private fun startExpirationTimer(expiration: Long) {
        stopExpirationTimer()

        val currentTime = System.currentTimeMillis()
        val delay = expiration - currentTime
        Log.d(TAG, "startExpirationTimer $delay")
        expirationJob = applicationScope.launch {
            while (true) {
                delay(delay)

                stopExpirationTimer()

                refreshAccessStatus()

                break
            }
        }
    }

    fun checkAndRunAuth() {
        val granted = isAccessGranted.value ?: return
        if (granted) {
            return
        }

        // The guard used to be AuthActivity.isRunning, set before the start and cleared in
        // onStop. Bringing the main task forward resumes MainActivity before the auth task
        // stops, so onResume found the flag still set, skipped the gate, and the app came
        // forward unlocked with nothing left to re-arm it. A short debounce keeps a burst of
        // onResume calls from stacking prompts without ever describing a state that has
        // since passed.
        val now = SystemClock.elapsedRealtime()
        if (now - lastGateStartedAt < GATE_DEBOUNCE_MS) {
            return
        }
        lastGateStartedAt = now

        val options = ActivityOptions.makeCustomAnimation(applicationContext, 0, 0)
        val intent = Intent(applicationContext, AuthActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent, options.toBundle())
    }

    fun enableProtectionFor(durationMinutes: Int) {
        val endTime = System.currentTimeMillis() + durationMinutes * 60_000L
        _isProtectionDisabled.value = true
        saveProtectionState(true, endTime)
        startCountdown(endTime)
    }

    fun disableProtection() {
        disableJob?.cancel()
        _isProtectionDisabled.value = false
        _remainingTime.value = 0L
        saveProtectionState(false, 0L)
    }

    fun restoreProtection() {
        applicationScope.launch {
            val prefs = applicationContext.dataStore.data.first()
            val enabled = prefs[SettingsKeys.IS_PROTECTION_DISABLED] == true
            val endTimestamp = prefs[SettingsKeys.PROTECTION_END_TIMESTAMP] ?: 0L

            if (enabled && endTimestamp > System.currentTimeMillis()) {
                _isProtectionDisabled.value = true
                startCountdown(endTimestamp)
            } else {
                _isProtectionDisabled.value = false
                _remainingTime.value = 0L
            }
        }
    }

    private fun startCountdown(endTimestamp: Long) {
        disableJob?.cancel()
        disableJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val remaining = endTimestamp - System.currentTimeMillis()
                if (remaining <= 0) {
                    _isProtectionDisabled.value = false
                    _remainingTime.value = 0L
                    saveProtectionState(false, 0L)
                    break
                } else {
                    _remainingTime.value = remaining
                }
                delay(1000L)
            }
        }
    }

    private fun saveProtectionState(enabled: Boolean, endTime: Long) {
        applicationScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[SettingsKeys.IS_PROTECTION_DISABLED] = enabled
                settings[SettingsKeys.PROTECTION_END_TIMESTAMP] = endTime
            }
        }
    }

    companion object {
        var wasDialogShown = false

        /** Long enough to swallow a burst of onResume calls, short enough to never lie. */
        private const val GATE_DEBOUNCE_MS = 750L
    }

}
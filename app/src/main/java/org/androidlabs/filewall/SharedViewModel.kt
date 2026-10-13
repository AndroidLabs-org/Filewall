package org.androidlabs.filewall

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.accessrequests.ListAccessRequestItem
import org.androidlabs.filewall.confirm.RequestsSignal
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.database.RuleItem
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.provider.AccessDuration
import org.androidlabs.filewall.provider.FilewallProvider
import org.androidlabs.filewall.rules.ListRuleItem
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore
import org.androidlabs.filewall.utils.Utils

/** AccessDuration.MINUTES_10 — what a request dialog opens on unless a timed choice is remembered. */
private val CONSERVATIVE_DURATION = AccessDuration.MINUTES_10.ordinal

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SharedViewModel"

    private val dataStore = application.dataStore
    private val dbHelper: Database = Database(application.applicationContext)
    private val _requestsCount = MutableLiveData<Long>()
    val requestsCount: LiveData<Long> get() = _requestsCount

    private val _navigateToScreen = MutableStateFlow<String?>(null)
    val navigateToScreen: StateFlow<String?> = _navigateToScreen

    fun openScreen(screenRoute: String) {
        _navigateToScreen.value = screenRoute
    }


    fun resetNavigation() {
        _navigateToScreen.value = null
    }
    fun loadRequestsCount() {
        // The badge counts decisions to make, which is cards, not rows in the table.
        viewModelScope.launch(Dispatchers.IO) {
            refreshRequests()
        }
    }

    private val _rulesCount = MutableLiveData<Long>()
    val rulesCount: LiveData<Long> get() = _rulesCount

    fun loadRulesCount() {
        viewModelScope.launch(Dispatchers.IO) {
            _rulesCount.postValue(dbHelper.getRulesCount())
        }
    }

    fun deleteAllRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteAllRequests()
            refreshRequests()
        }
    }

    fun deleteAllRules() {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteAllRules()
            refreshRules()
        }
    }

    fun deleteRequestById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteRequestById(id)
            refreshRequests()
        }
    }

    fun deleteRequestsByIds(ids: List<Long>) {
        if (ids.isEmpty()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteRequestsByIds(ids)
            refreshRequests()
        }
    }


    private val _rules = MutableLiveData<List<ListRuleItem>>()

    val rules: LiveData<List<ListRuleItem>>
        get() = _rules

    private val _requests = MutableLiveData<List<ListAccessRequestItem>>()

    val requests: LiveData<List<ListAccessRequestItem>>
        get() = _requests

    private var timerJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    init {
        checkExpiredRules()
        startTimer()
        viewModelScope.launch(Dispatchers.IO) {
            RequestsSignal.changes.collect { refreshRequests() }
        }
    }

    fun checkExpiredRules(){
        dbHelper.clearExpiredRules()
        refreshRequests()
        refreshRules()
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (isActive) {
                checkExpiredRules()
                delay(30000L)
            }
        }
    }

    fun refreshRequests() {
        val requests = dbHelper.getAllRequests()
        if (requests.isEmpty()) {
            _requests.postValue(emptyList())
            _requestsCount.postValue(0)
            return
        }
        val context = getApplication<Application>().applicationContext

        val uniquePkgs = requests.map { it.callingPackage }.toSet()

        val identifiers = uniquePkgs.associateWith { pkg ->
            Utils.getAppName(context, pkg)
        }

        val icons = uniquePkgs.associateWith { pkg ->
            Utils.getAppIcon(context, pkg)
        }

        // One card per app, folder and operation. A copy of a directory raises a request
        // per file, and ten thousand cards is not a decision anyone can make.
        val list = requests
            .groupBy {
                listOf(
                    it.callingPackage,
                    it.mode,
                    it.action,
                    it.isCreation.toString(),
                    FilewallProvider.getParentFolderOfDocumentId(it.documentId)
                )
            }
            .map { (_, members) ->
                val first = members.first()
                ListAccessRequestItem(
                    identifiers[first.callingPackage]!!,
                    icons[first.callingPackage],
                    first,
                    members
                )
            }

        _requests.postValue(list)
        _requestsCount.postValue(list.size.toLong())
    }

    fun refreshRules() {
        val rules = dbHelper.getAllRules()
        if (rules.isEmpty()) {
            _rules.postValue(emptyList())
            _rulesCount.postValue(0)
            return
        }
        val context = getApplication<Application>().applicationContext

        val uniquePkgs = rules.map { it.pkg }.toSet()

        val identifiers = uniquePkgs.associateWith { pkg ->
            Utils.getAppName(context, pkg)
        }

        val icons = uniquePkgs.associateWith { pkg ->
            Utils.getAppIcon(context, pkg)
        }

        val list = rules.map {
            ListRuleItem(identifiers[it.pkg]!!, icons[it.pkg], it)
        }

        _rules.postValue(list)
        _rulesCount.postValue(list.size.toLong())
    }

    fun reorderRules(from: Int, to: Int) {
        if (from == to) return

        val mutableRules = _rules.value!!.toMutableList()
        val movedItem = mutableRules.removeAt(from)
        mutableRules.add(to, movedItem)

        val newRules = mutableRules

        _rules.postValue(newRules)

        viewModelScope.launch {
            dbHelper.updateRulesOrderPositions(
                newRules.mapIndexed { index, item ->
                    Pair(item.rule.id!!, index.toLong())
                }
            )
        }
    }

    fun deleteRuleById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteRuleById(id)
            refreshRules()
        }
    }

    fun updateRule(rule: RuleItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.updateRule(rule)
            refreshRules()
        }
    }

    /**
     * The duration a request dialog opens on.
     *
     * The last choice is remembered, which is the convenient thing for the timed options,
     * but not for Forever: granting one app unlimited access once would then pre-select
     * unlimited access for every later request from any app. An unlimited grant is chosen
     * each time or not at all, so a stored Forever falls back to the 10-minute default.
     */
    val getDefaultDuration: Flow<Int> = dataStore.data
        .map { preferences ->
            val stored = preferences[SettingsKeys.DEFAULT_DURATION] ?: CONSERVATIVE_DURATION
            if (stored == AccessDuration.FOREVER.ordinal) CONSERVATIVE_DURATION else stored
        }

    fun setDefaultDuration(value: Int) {
        if (value == AccessDuration.FOREVER.ordinal) {
            return
        }
        try {
            viewModelScope.launch(Dispatchers.IO) {
                dataStore.edit { settings ->
                    settings[SettingsKeys.DEFAULT_DURATION] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change default duration: ${e.message}")
        }
    }

    /*private val _rulesNoExtend = MutableLiveData<Boolean>()
    val rulesNoExtend: LiveData<Boolean> get() = _rulesNoExtend

    fun loadRulesNoExtend() {
        viewModelScope.launch(Dispatchers.IO) {
            _rulesNoExtend.postValue(dbHelper.hasRulesNoExtend())
        }
    }*/

    val ruleConfirmedFlow: Flow<Boolean> = dataStore.data
        .map { it[booleanPreferencesKey("rule_confirmed")] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun saveRuleConfirmed(value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit {
                it[booleanPreferencesKey("rule_confirmed")] = value
            }
        }
    }
}
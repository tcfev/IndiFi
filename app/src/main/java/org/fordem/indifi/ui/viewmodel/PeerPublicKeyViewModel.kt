package org.fordem.indifi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fordem.indifi.ui.dao.PeerPublicKeyDao
import org.fordem.indifi.ui.model.PeerPublicKeyEntity
import javax.inject.Inject

@HiltViewModel
class PeerPublicKeyViewModel @Inject constructor(
    private val peerPublicKeyDao: PeerPublicKeyDao
) : ViewModel() {

    val allKeys: StateFlow<List<PeerPublicKeyEntity>> = peerPublicKeyDao.getAllKeys().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertKey(key: PeerPublicKeyEntity) {
        viewModelScope.launch {
            peerPublicKeyDao.insertKey(key)
        }
    }

    fun insertAll(keys: List<PeerPublicKeyEntity>) {
        viewModelScope.launch {
            peerPublicKeyDao.insertAll(keys)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            peerPublicKeyDao.clearAll()
        }
    }

    suspend fun getKeyByIp(ip: String): PeerPublicKeyEntity? {
        return peerPublicKeyDao.getKeyByIp(ip)
    }
}

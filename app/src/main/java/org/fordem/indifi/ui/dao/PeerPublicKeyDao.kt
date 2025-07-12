package org.fordem.indifi.ui.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fordem.indifi.ui.model.PeerPublicKeyEntity

@Dao
interface PeerPublicKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: PeerPublicKeyEntity)

    @Query("SELECT * FROM peer_public_keys WHERE ip = :ip")
    suspend fun getKeyByIp(ip: String): PeerPublicKeyEntity?

    @Query("DELETE FROM peer_public_keys")
    suspend fun clearAll()
}

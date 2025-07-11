package org.fordem.indifi.ui.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peer_public_keys")
data class PeerPublicKeyEntity(
    @PrimaryKey val ip: String,
    val base64Key: String
)

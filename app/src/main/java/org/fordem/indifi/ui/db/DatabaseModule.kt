package org.fordem.indifi.ui.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChatMessageDao(appDatabase: AppDatabase): ChatMessageDao {
        return appDatabase.chatMessageDao()
    }
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "device_info_db"
        ).build()
    }

    @Provides
    fun provideDeviceInfoDao(db: AppDatabase): DeviceInfoDao = db.deviceInfoDao()

    @Provides
    fun providePeerPublicKeyDao(database: AppDatabase): PeerPublicKeyDao {
        return database.peerPublicKeyDao()
    }
}

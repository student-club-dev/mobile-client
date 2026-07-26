package uz.studentclub.app

import android.app.Application
import com.chuckerteam.chucker.api.ChuckerInterceptor
import dev.core.di.initKoin
import dev.core.network.OkHttpInterceptors
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class StudentClubApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Chucker — Debug HTTP inspektori. Klient qurilishidan OLDIN ro'yxatga qo'shiladi
        // (Koin `HttpClient` ni birinchi so'ralganda quradi — undan keyin ro'yxat o'qilmaydi).
        // Debug'da: har API so'rovi bildirishnomada ko'rinadi, bosganda alohida ekran ochadi.
        // Release'da: chucker-noop tufayli hech narsa qilmaydi.
        OkHttpInterceptors.interceptors += ChuckerInterceptor.Builder(this).build()

        initKoin {
            androidLogger()
            androidContext(this@StudentClubApp)
        }
    }
}

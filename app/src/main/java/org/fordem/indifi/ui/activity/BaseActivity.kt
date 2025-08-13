package org.fordem.indifi.ui.activity

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import org.fordem.indifi.R

@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navigationView: NavigationView

    protected fun setupNavigationUI(rootView: View, toolbarTitle: String = "") {
        setContentView(rootView)

        toolbar = findViewById(R.id.topAppBar)
        toolbar.title = toolbarTitle
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (this !is MainActivity)
                        startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_scan -> {
                    if (this !is WifiDirectScreen1Activity)
                        startActivity(Intent(this, WifiDirectScreen1Activity::class.java))
                    true
                }
                R.id.nav_hotspots -> {
                    if (this !is WifiScanActivity)
                        startActivity(Intent(this, WifiScanActivity::class.java))
                    true
                }
                R.id.nav_about -> {
                    Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }.also {
                drawerLayout.closeDrawers()
            }
        }
    }

}
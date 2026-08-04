package com.example.mindmap

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.mindmap.data.AppDatabase
import com.example.mindmap.data.LineRepository
import com.example.mindmap.data.MediaRepository
import com.example.mindmap.data.NodeRepository
import com.example.mindmap.data.SectionRepository
import com.example.mindmap.data.SettingsRepository
import com.example.mindmap.ui.screens.MindMapApp
import com.example.mindmap.ui.theme.MindMapTheme
import com.example.mindmap.ui.viewmodel.LineViewModel
import com.example.mindmap.ui.viewmodel.LineViewModelFactory
import com.example.mindmap.ui.viewmodel.MediaViewModel
import com.example.mindmap.ui.viewmodel.MediaViewModelFactory
import com.example.mindmap.ui.viewmodel.MindMapViewModel
import com.example.mindmap.ui.viewmodel.MindMapViewModelFactory
import com.example.mindmap.ui.viewmodel.SectionViewModel
import com.example.mindmap.ui.viewmodel.SectionViewModelFactory
import com.example.mindmap.ui.viewmodel.SettingsViewModel
import com.example.mindmap.ui.viewmodel.SettingsViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "mindmap_db")
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .addMigrations(AppDatabase.MIGRATION_8_9)
            .addMigrations(AppDatabase.MIGRATION_9_10)
            .addMigrations(AppDatabase.MIGRATION_10_11)
            .fallbackToDestructiveMigration()
            .build()

        val settingsRepository = SettingsRepository(applicationContext)
        val factory = MindMapViewModelFactory(NodeRepository(db.dao()))
        val sectionFactory = SectionViewModelFactory(SectionRepository(db.sectionDao()), settingsRepository)
        val lineFactory = LineViewModelFactory(LineRepository(db.lineDao()))
        val mediaFactory = MediaViewModelFactory(MediaRepository(db.mediaDao()))
        val settingsFactory = SettingsViewModelFactory(settingsRepository)

        setContent {
            MindMapTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: MindMapViewModel = viewModel(factory = factory)
                    val sectionViewModel: SectionViewModel = viewModel(factory = sectionFactory)
                    val lineViewModel: LineViewModel = viewModel(factory = lineFactory)
                    val mediaViewModel: MediaViewModel = viewModel(factory = mediaFactory)
                    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
                    MindMapApp(viewModel, settingsViewModel, sectionViewModel, lineViewModel, mediaViewModel)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ExternalOpenState.pendingPdfUri.value = uri.toString()
            }
        }
    }
}

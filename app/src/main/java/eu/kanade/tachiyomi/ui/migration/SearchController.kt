package eu.kanade.tachiyomi.ui.migration

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.popControllerWithTag
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchController
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchPresenter
import uy.kohesive.injekt.injectLazy

class SearchController(
    private var manga: Manga? = null
) : GlobalSearchController(manga?.title) {

    private var newManga: Manga? = null

    override fun createPresenter(): GlobalSearchPresenter {
        return SearchPresenter(initialQuery, manga!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(::manga.name, manga)
        outState.putSerializable(::newManga.name, newManga)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        manga = savedInstanceState.getSerializable(::manga.name) as? Manga
        newManga = savedInstanceState.getSerializable(::newManga.name) as? Manga
    }

    fun migrateManga() {
        val manga = manga ?: return
        val newManga = newManga ?: return

        (presenter as? SearchPresenter)?.migrateManga(manga, newManga, true)
    }

    fun copyManga() {
        val manga = manga ?: return
        val newManga = newManga ?: return

        (presenter as? SearchPresenter)?.migrateManga(manga, newManga, false)
    }

    override fun onMangaClick(manga: Manga) {
        newManga = manga
        val dialog = MigrationDialog()
        dialog.targetController = this
        dialog.showDialog(router)
    }

    override fun onMangaLongClick(manga: Manga) {
        // Call parent's default click listener
        super.onMangaClick(manga)
    }

    fun renderIsReplacingManga(isReplacingManga: Boolean) {
        if (isReplacingManga) {
            if (router.getControllerWithTag(LOADING_DIALOG_TAG) == null) {
                LoadingController().showDialog(router, LOADING_DIALOG_TAG)
            }
        } else {
            router.popControllerWithTag(LOADING_DIALOG_TAG)
            router.popController(this)
        }
    }

    class MigrationDialog : DialogController() {

        private val preferences: PreferencesHelper by injectLazy()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val prefValue = preferences.migrateFlags().get()

            val preselected = MigrationFlags.getEnabledFlagsPositions(prefValue)

            return MaterialDialog.Builder(activity!!)
                    .content(R.string.migration_dialog_what_to_include)
                    .items(MigrationFlags.titles.map { resources?.getString(it) })
                    .alwaysCallMultiChoiceCallback()
                    .itemsCallbackMultiChoice(preselected.toTypedArray()) { _, positions, _ ->
                        // Save current settings for the next time
                        val newValue = MigrationFlags.getFlagsFromPositions(positions)
                        preferences.migrateFlags().set(newValue)

                        true
                    }
                    .positiveText(R.string.migrate)
                    .negativeText(R.string.copy)
                    .neutralText(android.R.string.cancel)
                    .onPositive { _, _ ->
                        (targetController as? SearchController)?.migrateManga()
                    }
                    .onNegative { _, _ ->
                        (targetController as? SearchController)?.copyManga()
                    }
                    .build()
        }
    }

    class LoadingController : DialogController() {

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog.Builder(activity!!)
                    .progress(true, 0)
                    .content(R.string.migrating)
                    .cancelable(false)
                    .build()
        }
    }

    companion object {
        const val LOADING_DIALOG_TAG = "LoadingDialog"
    }
}

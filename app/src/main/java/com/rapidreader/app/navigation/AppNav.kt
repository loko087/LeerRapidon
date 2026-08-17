package com.rapidreader.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rapidreader.app.data.OriginalKind
import com.rapidreader.app.ui.screens.AddBookScreen
import com.rapidreader.app.ui.screens.BrowseTextScreen
import com.rapidreader.app.ui.screens.EpubViewerScreen
import com.rapidreader.app.ui.screens.LibraryScreen
import com.rapidreader.app.ui.screens.PdfViewerScreen
import com.rapidreader.app.ui.screens.ReaderScreen

private fun originalRoute(id: String, kind: OriginalKind) = when (kind) {
    OriginalKind.PDF -> "original/pdf/$id"
    OriginalKind.EPUB -> "original/epub/$id"
}

/** Mode switches REPLACE the current reading destination rather than stacking,
 *  so Back always returns to the library no matter how many times you toggle. */
private fun NavHostController.navigateMode(route: String) =
    navigate(route) { popUpTo("library"); launchSingleTop = true }

@Composable
fun AppNavHost() {
    val nav: NavHostController = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onOpenBook = { id -> nav.navigate("reader/$id") },
                onOpenOriginal = { id, kind -> nav.navigate(originalRoute(id, kind)) },
                onAddBook = { nav.navigate("add") }
            )
        }
        composable("add") {
            AddBookScreen(
                onBack = { nav.popBackStack() },
                onSaved = { id -> nav.navigate("reader/$id") { popUpTo("library") } }
            )
        }
        composable("reader/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            ReaderScreen(
                bookId = bookId,
                onBack = { nav.popBackStack() },
                onOpenOriginal = { kind -> nav.navigateMode(originalRoute(bookId, kind)) },
                onBrowseText = { nav.navigate("browse/$bookId") }
            )
        }
        composable("browse/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            BrowseTextScreen(
                bookId = bookId,
                onBack = { nav.popBackStack() },
                // Picking a word writes the new position to the DB, so the
                // reader needs a fresh instance to pick it up — same reload
                // path a mode switch already uses, not a plain pop back.
                onWordSelected = { nav.navigateMode("reader/$bookId") }
            )
        }
        composable("original/pdf/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            PdfViewerScreen(
                bookId = bookId,
                onBack = { nav.popBackStack() },
                onFastRead = { nav.navigateMode("reader/$bookId") }
            )
        }
        composable("original/epub/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            EpubViewerScreen(
                bookId = bookId,
                onBack = { nav.popBackStack() },
                onFastRead = { nav.navigateMode("reader/$bookId") }
            )
        }
    }
}

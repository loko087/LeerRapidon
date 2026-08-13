package com.rapidreader.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rapidreader.app.ui.screens.AddBookScreen
import com.rapidreader.app.ui.screens.LibraryScreen
import com.rapidreader.app.ui.screens.ReaderScreen

@Composable
fun AppNavHost() {
    val nav: NavHostController = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onOpenBook = { id -> nav.navigate("reader/$id") },
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
            ReaderScreen(bookId = bookId, onBack = { nav.popBackStack() })
        }
    }
}

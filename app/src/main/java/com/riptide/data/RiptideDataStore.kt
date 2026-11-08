package com.riptide.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** The single DataStore for the app's preferences. There must be only
 * one delegate for a given name: a second preferencesDataStore with
 * the same name creates a second DataStore for the same file, and
 * DataStore throws IllegalStateException ("multiple DataStores active
 * for the same file") when both are used. That crashed the app on
 * launch in v0.5, when ReceiveFolderRepository declared its own. */
internal val Context.riptideDataStore by preferencesDataStore(name = "riptide")

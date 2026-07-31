package com.shelfie.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Deliberately empty. The cold-start budget is under 500ms on a 4GB device, and
 * the fastest way to blow that is to do eager work here. Everything is lazy:
 * Hilt builds the graph on first injection, Room opens the database on first
 * query, and indexing is kicked off from the shelf, not from here.
 */
@HiltAndroidApp
class ShelfieApplication : Application()

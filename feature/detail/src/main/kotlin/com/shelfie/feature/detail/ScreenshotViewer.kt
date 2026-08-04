package com.shelfie.feature.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.shelfie.core.designsystem.action.ScreenshotActionLauncher
import kotlinx.coroutines.launch

/** Bounds on zoom. Below 1 the image would float inside a border of empty space. */
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

/** Where a double tap zooms to, when starting from unzoomed. */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * The screenshot itself, full screen, as the first thing a tap produces.
 *
 * Tapping a screenshot used to open a bottom sheet of extracted text over a small
 * preview — useful once you were already looking for text, and baffling as the
 * response to tapping a picture. Every gallery on every phone opens the image, so
 * that is what this does, and the text moves behind a button.
 *
 * Rendered by the shell above the whole scaffold, rather than as a navigation
 * destination or a dialog. A destination is drawn inside the scaffold and leaves the
 * bottom navigation bar on top of a supposedly full-screen image; a dialog fixes that
 * but puts the viewer in its own window, and the bottom sheets opened from here would
 * then be sheets in one window trying to appear above another — behaviour that
 * depends on window ordering and is not worth relying on. A plain overlay in the same
 * window keeps ordinary composition rules, so a sheet is simply drawn on top.
 */
@Composable
fun ScreenshotViewer(
    screenshotId: Long,
    onDismiss: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(key = "detail-$screenshotId"),
) {
    LaunchedEffect(screenshotId) { viewModel.load(screenshotId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = remember(context) { ScreenshotActionLauncher(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showText by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val copiedMessage = stringResource(R.string.viewer_copied)

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
            val screenshot = state.screenshot

            if (state.isLoading || screenshot == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                ZoomableImage(model = screenshot.uri, modifier = Modifier.fillMaxSize())

                // Controls sit above the image and inside the system insets, so the
                // picture itself stays edge to edge while nothing is unreachable
                // under a status bar or gesture area.
                Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.viewer_back),
                                tint = Color.White,
                            )
                        }

                        Box(modifier = Modifier.weight(1f))

                        TextButton(onClick = { showText = true }) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = Color.White,
                            )
                            Text(
                                text = "  " + stringResource(R.string.viewer_copy_text),
                                color = Color.White,
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showDetails = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color.White,
                            )
                            Text(
                                text = "  " + stringResource(R.string.viewer_details),
                                color = Color.White,
                            )
                        }
                    }
                }
            }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding(),
        )
    }

    if (showText) {
        RecognisedTextSheet(
            text = state.text,
            onCopyAll = {
                launcher.copy(state.text)
                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
            },
            onDismiss = { showText = false },
        )
    }

    if (showDetails) {
        DetailSheet(
            screenshotId = screenshotId,
            onDismiss = { showDetails = false },
        )
    }
}

/**
 * Pinch to zoom, drag to pan, double tap to toggle.
 *
 * Zoom is anchored to the midpoint between the fingers rather than the centre of
 * the view, so the part of the image being pinched stays under the fingers instead
 * of sliding away — the difference between zooming feeling attached to the content
 * and feeling like the image is fighting you.
 *
 * Panning is clamped to the overflow the current zoom actually produces, so the
 * image can never be dragged off screen and abandoned somewhere it cannot be
 * recovered from without a double tap.
 */
@Composable
private fun ZoomableImage(model: Any, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, atScale: Float): Offset {
        // At scale s the content overflows the view by (s - 1) in each axis, half of
        // it on each side, which is exactly how far it may be moved.
        val maxX = (viewSize.width * (atScale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (viewSize.height * (atScale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            // Tap detection is declared first so a double tap is recognised before
            // the transform detector claims the pointer as a one-finger pan.
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > MIN_SCALE) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                            offset = Offset.Zero
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)

                    // Keeping the pinched point fixed. With the default transform
                    // origin at the centre, a content point maps to
                    //   screen = centre + (content - centre) * scale + offset
                    // Solving that for the offset which leaves the centroid where it
                    // is, after applying scale `next` and translation `pan`, gives
                    // the expression below. With zoom == 1 it reduces to
                    // offset + pan, i.e. a plain drag.
                    val centre = Offset(viewSize.width / 2f, viewSize.height / 2f)
                    val fromCentre = centroid - centre
                    val anchored = fromCentre + pan - (fromCentre - offset) * (next / scale)

                    scale = next
                    offset = clampOffset(anchored, next)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/**
 * The recognised text on its own, for reading and copying.
 *
 * Copy sits at the top left, where it is reachable without scrolling to the end of
 * a long transcript. The text stays individually selectable as well, because
 * copying one booking reference out of a whole page is the more common need.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecognisedTextSheet(
    text: String?,
    onCopyAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasText = !text.isNullOrBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCopyAll, enabled = hasText) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("  " + stringResource(R.string.viewer_copy_all))
                }
                Box(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.viewer_close))
                }
            }

            Text(
                text = stringResource(R.string.detail_text_heading),
                style = MaterialTheme.typography.labelLarge,
            )

            SelectionContainer(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = text?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_no_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/*
 * There was an Edit button here, which handed the screenshot to the phone's own
 * editor with ACTION_EDIT. Removed, because it crashed the app rather than editing
 * anything.
 *
 * The cause: the intent carried FLAG_GRANT_WRITE_URI_PERMISSION for a MediaStore
 * URI. An app can only pass on a URI permission it was itself granted, and read
 * access here comes from holding READ_MEDIA_IMAGES — a manifest permission, not a
 * grant. So starting the activity threw SecurityException, and only
 * ActivityNotFoundException was being caught.
 *
 * Not simply patched, because the honest version of this feature is bigger than a
 * caught exception: writing back to an image the app does not own needs
 * MediaStore.createWriteRequest and its own system consent dialog, and then the
 * screenshot has to be re-read because an edit can crop away the text it was filed
 * under. That is a feature, not a fix, and editing is already one tap away in the
 * gallery.
 */

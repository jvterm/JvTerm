/*
 * Copyright 2026 Gagik Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gagik.terminal.ui.swing.viewport

import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.shared.render.TerminalRenderMetrics
import com.gagik.terminal.ui.shared.viewport.TerminalViewportRepaintPlanner
import com.gagik.terminal.ui.swing.render.SwingRenderCacheSnapshot

/**
 * Swing adapter over the shared terminal viewport repaint planner.
 */
internal class TerminalSwingRepaintPlanner {
    private val shared = TerminalViewportRepaintPlanner()
    private val snapshot = SwingRenderCacheSnapshot()

    /**
     * Clears remembered cursor and cache state.
     */
    fun reset() {
        shared.reset()
    }

    /**
     * Requests repaint regions for a newly published render cache.
     */
    fun requestFrameRepaint(
        cache: TerminalRenderCache,
        metrics: TerminalRenderMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintSink: TerminalRepaintSink,
    ) {
        shared.requestFrameRepaint(
            snapshot = snapshot.wrap(cache),
            metrics = metrics,
            componentWidth = componentWidth,
            componentHeight = componentHeight,
            contentYOffset = contentYOffset,
            repaintSink = repaintSink,
        )
    }

    /**
     * Requests repaint regions for a blinking cursor toggle.
     */
    fun requestCursorBlinkRepaint(
        cache: TerminalRenderCache,
        metrics: TerminalRenderMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintSink: TerminalRepaintSink,
    ) {
        shared.requestCursorBlinkRepaint(
            snapshot = snapshot.wrap(cache),
            metrics = metrics,
            componentWidth = componentWidth,
            componentHeight = componentHeight,
            contentYOffset = contentYOffset,
            repaintSink = repaintSink,
        )
    }
}

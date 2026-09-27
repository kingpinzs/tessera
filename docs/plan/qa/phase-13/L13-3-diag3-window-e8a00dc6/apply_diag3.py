# DIAG3 (never committed): timing lines for the L13-3 window proof on the final code.
# Logs: back closes band (the Back's write), layer placed/unplaced (OverlayLayer transitions), scrim down (a down that
# reached a band's scrim), row down + row outcome (HoldRow), band entered/left composition.
import sys
R = sys.argv[1] + '/app/src/main/kotlin/app/tileshell/'
D = 'app.tileshell.diag.Diagnostics.add("l13", '
NOW = '${android.os.SystemClock.uptimeMillis()}'

def edit(path, old, new):
    p = R + path
    s = open(p).read()
    if s.count(old) != 1:
        sys.exit(f'anchor not unique in {path}: {old[:60]!r} x{s.count(old)}')
    open(p, 'w').write(s.replace(old, new))

edit('applist/AppListMenu.kt',
     '''                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true''',
     f'''                awaitEachGesture {{
                    val d0 = awaitFirstDown()
                    {D}"row down eventUptime=${{d0.uptimeMillis}} now={NOW}")
                    pressed = true''')
edit('applist/AppListMenu.kt',
     '''                    pressed = false
                    when {
                        up != null -> onClick()''',
     f'''                    pressed = false
                    {D}"row outcome " + (if (up != null) "click" else if (cancelled) "cancelled" else "hold") + " now={NOW}")
                    when {{
                        up != null -> onClick()''')
edit('applist/AppListMenu.kt',
     '''    val firstFrame = remember { booleanArrayOf(false) }
''',
     f'''    val firstFrame = remember {{ booleanArrayOf(false) }}
    androidx.compose.runtime.DisposableEffect(Unit) {{
        {D}"band entered composition now={NOW}")
        onDispose {{ {D}"band left composition now={NOW}") }}
    }}
''')
edit('applist/AppListPage.kt',
     '''    BackHandler(enabled = visible && menu != null) { dismissOverlay { menu = null } }''',
     f'''    BackHandler(enabled = visible && menu != null) {{ {D}"back closes band now={NOW}"); dismissOverlay {{ menu = null }} }}''')
edit('ui/components/ModalOverlay.kt',
     '''            val down = awaitFirstDown(requireUnconsumed = false)
''',
     f'''            val down = awaitFirstDown(requireUnconsumed = false)
            {D}"scrim down eventUptime=${{down.uptimeMillis}} now={NOW}")
''')
edit('ui/components/ModalOverlay.kt',
     '''fun OverlayLayer(active: () -> Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(''',
     '''fun OverlayLayer(active: () -> Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val placedLast = androidx.compose.runtime.remember { booleanArrayOf(false) }
    Layout(''')
edit('ui/components/ModalOverlay.kt',
     '''            if (active()) placeables.forEach { it.place(0, 0) }''',
     f'''            val on = active()
            if (on != placedLast[0] && placeables.isNotEmpty()) {D}"layer " + (if (on) "placed" else "unplaced") + " n=${{placeables.size}} now={NOW}")
            placedLast[0] = on
            if (on) placeables.forEach {{ it.place(0, 0) }}''')
print('diag3 applied')

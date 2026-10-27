p = "desktopApp/src/main/kotlin/example/nucleus/navigation/Navigation.kt"
s = open(p, encoding="utf-8").read()
old = """                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back) && rootComponent.canGoBack()) {
                        rootComponent.onBack()
                        true
                    } else false
                },"""
new = """                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back) && rootComponent.canGoBack()) {
                        rootComponent.onBack()
                        true
                    } else false
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons == PointerButtons(8) && rootComponent.canGoBack()) {
                                rootComponent.onBack()
                            }
                        }
                    }
                },"""
assert old in s
s = s.replace(old, new, 1)

# add imports
if "import androidx.compose.ui.input.pointer.PointerEventPass" not in s:
    s = s.replace(
        "import androidx.compose.ui.input.key.type\n",
        "import androidx.compose.ui.input.key.type\nimport androidx.compose.ui.input.pointer.PointerButtons\nimport androidx.compose.ui.input.pointer.PointerEventPass\nimport androidx.compose.ui.input.pointer.PointerEventType\nimport androidx.compose.ui.input.pointer.awaitPointerEventScope\nimport androidx.compose.ui.input.pointer.pointerInput\n",
        1,
    )
open(p, "w", encoding="utf-8").write(s)
print("ok")